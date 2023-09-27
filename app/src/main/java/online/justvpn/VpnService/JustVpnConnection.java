package online.justvpn.VpnService;

import static java.nio.charset.StandardCharsets.US_ASCII;

import android.content.Intent;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import online.justvpn.Definitions.Connection;

public class JustVpnConnection implements Runnable {
    private Thread mReceiverThread;
    private Thread mCheckConnectionThread;
    Instant mLastKeepaliveReceivedTimestamp;

    ParcelFileDescriptor mVPNInterface = null;
    private int mMTU;

    private Connection.State mConnectionState = Connection.State.IDLE;
    private VpnService.Builder mBuilder;
    private VpnService mService;
    private JustVpnAPI.ServerDataModel mServerAddress;
    private DatagramChannel mServerChannel;

    // This is the time in milliseconds indicating how often the server will send keepalive control messages
    private int mKeepaliveIntervalMs = - 1;

    // The number of attempts to handshake with the server
    private int MAX_HANDSHAKE_ATTEMPTS = 10;

    JustVpnConnection(JustVpnService service, JustVpnAPI.ServerDataModel ServerDataModel) // TODO: ", Server server"
    {
        mService = service;
        mServerAddress = ServerDataModel;
        mReceiverThread = null;
        mCheckConnectionThread = null;
    }

    public JustVpnAPI.ServerDataModel GetServerDataModel()
    {
        return mServerAddress;
    }
    public Connection.State GetConnectionState()
    {
        return mConnectionState;
    }

    @Override
    public void run() {
        while (!mConnectionState.equals(Connection.State.DISCONNECTED))
        {
            try
            {
                final SocketAddress serverAddress = new InetSocketAddress(InetAddress.getByName(mServerAddress.sIp), 8811);
                start(serverAddress);
            }
            catch (IOException | IllegalArgumentException | IllegalStateException | InterruptedException e)
            {
                Log.d("JUSTVPN:", "An exception occurred: " + e);
            }
            finally
            {
                // we got disconnected by some reason either we detected the traffic
                // is not flowing through or the server has disconnected us for some reason
                // We want to protect our traffic to come though public network,
                // so keep the VPN tunnel established and keep reconnecting.
                if (mConnectionState != Connection.State.DISCONNECTED)
                {
                    setConnectionState(Connection.State.RECONNECTING);
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
                else
                {
                    if ( mVPNInterface != null)
                    {
                        try {
                            mVPNInterface.close();
                            mVPNInterface = null;
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                    setConnectionState(Connection.State.DISCONNECTED);
                }
            }
        }
    }

    public void Disconnect()
    {
        try {

            if (mConnectionState.equals(Connection.State.ACTIVE))
            {
                if (Thread.currentThread().equals(this)) // only if running inside the connection thread
                {
                    sendControlMsg(JustVpnAPI.CONTROL_ACTION_DISCONNECT);
                }
                else
                {
                    Thread thread = new Thread(() ->
                    {
                        try
                        {
                            sendControlMsg(JustVpnAPI.CONTROL_ACTION_DISCONNECT);
                        }
                        catch (Exception e)
                        {
                            e.printStackTrace();
                        }
                    });

                    thread.start();
                    try
                    {
                        thread.join();
                    }
                    catch (InterruptedException e)
                    {
                        e.printStackTrace();
                    }
                }
            }

            setConnectionState(Connection.State.DISCONNECTING);
            if (mVPNInterface != null)
            {
                mVPNInterface.close();
                mVPNInterface = null;
            }
            if (mReceiverThread != null)
            {
                mReceiverThread.interrupt();
            }
            if (mCheckConnectionThread != null)
            {
                mCheckConnectionThread.interrupt();
            }

        } catch (IOException e) {
            Log.d("JUSTVPN:","Exception: " + e + e.getMessage());
        }

        setConnectionState(Connection.State.DISCONNECTED);
    }

    private void start(SocketAddress server) throws IOException, InterruptedException {
        setConnectionState(Connection.State.CONNECTING);
        // Connect to the server
        mServerChannel = DatagramChannel.open();

        DatagramSocket socket = mServerChannel.socket();

        if (!mService.protect(socket)) // fails if user permissions are not given, see requestVpnServicePermissionDialog()
        {
            setConnectionState(Connection.State.FAILED);
            throw new IllegalStateException("Cannot protect the tunnel");
        }

        // Connect to the server.
        socket.connect(server);

        // Handshake with the server
        if (!handshake())
        {
            setConnectionState(Connection.State.HANDSHAKE_FAILED);
            return;
        }

        // Configure VPN interface
        if (!configure())
        {
            setConnectionState(Connection.State.FAILED);
            return;
        }

        setConnectionState(Connection.State.CONNECTED);

        // start VPN routine
        if (mVPNInterface != null)
        {
            mVPNInterface.close();
        }
        mVPNInterface = mBuilder.establish();

        // Tell the server we're configured
        sendControlMsg(JustVpnAPI.CONTROL_ACTION_CONFIGURED);

        // Verify subscription on server side
        // token=debug_debug will allow the server to accept this connection even if this is an unpaid one
        sendControlMsg(JustVpnAPI.CONTROL_ACTION_VERIFY_SUBSCRIPTION_TOKEN, "token=debug_debug");

        setConnectionState(Connection.State.ACTIVE);

        mLastKeepaliveReceivedTimestamp = Instant.now();

        // start check connection thread
        startCheckConnectionThread();

        // Start VPN routine
        startReceiverThread();

        // process outgoing packages
        int nEmptyPacketCounter = 0;
        FileInputStream in = new FileInputStream(mVPNInterface.getFileDescriptor());
        ByteBuffer packet = ByteBuffer.allocate(mMTU);

        while (mConnectionState == Connection.State.ACTIVE)
        {
            // Read the outgoing packet from the input stream.
            int length = in.read(packet.array());
            if (length > 0)
            {
                // Write the outgoing packet to the tunnel.
                packet.limit(length);
                mServerChannel.write(packet);
                packet.clear();
                nEmptyPacketCounter = 0;
            }
            else
            {
                nEmptyPacketCounter++;
                if (nEmptyPacketCounter > 15)
                {
                    // To minimize busy looping, sleep thread if data remains 0 for a few cycles
                    Thread.sleep(TimeUnit.MILLISECONDS.toMillis(100));
                }
            }
        }
        Log.e("JUSTVPN","Send thread stopped");
    }

    private void startCheckConnectionThread() {
        if (mCheckConnectionThread != null)
        {
            mCheckConnectionThread.interrupt();
        }
        mCheckConnectionThread = new Thread(() ->
        {
            while (mConnectionState.equals(Connection.State.ACTIVE) ||
                    mConnectionState.equals(Connection.State.CONNECTED))
            {
                Duration delta = Duration.between(mLastKeepaliveReceivedTimestamp, Instant.now());
                if (delta.toMillis() > mKeepaliveIntervalMs * 3)
                {
                    // We don't get keepalives for a while indicate timeout and start reconnecting routine
                    setConnectionState(Connection.State.TIMED_OUT);
                }
                try {
                    if (mKeepaliveIntervalMs > 0)
                    {
                        Thread.sleep(mKeepaliveIntervalMs);
                    }
                    else {
                        Thread.sleep(15000);
                    }

                } catch (InterruptedException e) {
                    Log.d("JUSTVPN:","Exception: " + e + e.getMessage());
                }
            }
            Log.e("JUSTVPN","Check connection thread stopped");
        });
        mCheckConnectionThread.start();
    }

    private void startReceiverThread()
    {
        if (mReceiverThread != null)
        {
            mReceiverThread.interrupt();
        }
        mReceiverThread = new Thread(() ->
        {
            try
            {
                ByteBuffer packet = ByteBuffer.allocate(mMTU);
                FileOutputStream out = new FileOutputStream(mVPNInterface.getFileDescriptor());

                while (mConnectionState == Connection.State.ACTIVE)
                {
                    // Read the incoming packet from the tunnel.
                    int len = mServerChannel.read(packet);
                    if (len > 0)
                    {
                        // Ignore control messages, which start with zero.
                        if (packet.get(0) == 0)
                        {
                            ProcessControl(packet, len);
                        }
                        // whatever comes from the server, put into the system's out stream
                        else
                        {
                            out.write(packet.array(), 0, len);
                        }
                        packet.clear();
                    }
                }

            } catch (Exception e) {
                Log.d("JUSTVPN:","Exception: " + e + e.getMessage());
            }
            finally {
                Log.e("JUSTVPN","Receive thread stopped");
            }
        });

        mReceiverThread.start();
    }

    private void ProcessControl(ByteBuffer p, int len) throws IOException {
        switch (JustVpnAPI.actionFromText(new String(p.array(), 1, len -1)))
        {
            case JustVpnAPI.CONTROL_ACTION_KEEPALIVE:
                // Response to the server with keepalive
                sendControlMsg(JustVpnAPI.CONTROL_ACTION_KEEPALIVE);
                mLastKeepaliveReceivedTimestamp = Instant.now();
                break;

            case JustVpnAPI.CONTROL_ACTION_DISCONNECTED:
                String sReason = JustVpnAPI.getReason(new String(p.array(), 1, len -1));
                if (sReason.equals("reason:timedout"))
                {
                    setConnectionState(Connection.State.TIMED_OUT);
                }
                break;
            default:
                break;
        }
    }

    private void setConnectionState(Connection.State state)
    {
        mConnectionState = state;
        Intent intent = new Intent("online.justvpn.connection.state");
        intent.putExtra("state", mConnectionState.ordinal());
        mService.sendBroadcast(intent);
    }

    private boolean configure() throws IOException {
        mBuilder = ((JustVpnService)mService).getNewBuilder();

        boolean bConfigureOK = false;

        // Buffer for data
        ByteBuffer packet = ByteBuffer.allocate(1500);
        int nParemLen = 0;

        // Request network parameters from the server
        if (sendControlMsg(JustVpnAPI.CONTROL_ACTION_GET_PARAMETERS) > 0 && // we have sent > 0 bytes
            (nParemLen = mServerChannel.read(packet)) > 0 && // we have read > 0 bytes
            packet.get(0) == 0)// All control messages begin with 0

        {
            String parameters = new String(packet.array(), 1, nParemLen - 1, US_ASCII).trim();

            if (!(parameters.contains("mtu") &&
                    parameters.contains("address") &&
                    parameters.contains("route") &&
                    parameters.contains("dns")))
            {

                throw new IllegalArgumentException("Bad parameters: " + parameters);
            }

            for (String parameter : parameters.split(";"))
            {
                String key = parameter.split(":")[0];
                String value = parameter.split(":")[1];

                switch (key)
                {
                    case "mtu":
                        mMTU = Short.parseShort(value);
                        mBuilder.setMtu(Short.parseShort(value));
                        break;
                    case "address":
                        mBuilder.addAddress(value, Integer.parseInt(parameter.split(":")[2]));
                        break;
                    case "route":
                        mBuilder.addRoute(value, Integer.parseInt(parameter.split(":")[2]));
                        break;
                    case "dns":
                        mBuilder.addDnsServer(value);
                        break;
                    case "keepalive_interval_ms":
                        mKeepaliveIntervalMs = Integer.parseInt(value);
                        break;
                    default:
                        break;
                }
            }

            bConfigureOK = true;
        }

        return bConfigureOK;
    }

    private boolean handshake() throws IOException {
        // Buffer for data
        ByteBuffer packet = ByteBuffer.allocate(128);

        boolean bHandshakeOK = false;
        for (int i = 0; i < MAX_HANDSHAKE_ATTEMPTS; i++)
        {
            if (sendControlMsg(JustVpnAPI.CONTROL_ACTION_CONNECT) > 0 )
            {
                mServerChannel.configureBlocking(false);

                // Below we register a selector in order to be able to detect receive timeout
                Selector selector = Selector.open();
                mServerChannel.register(selector, SelectionKey.OP_READ);

                if (selector.select(1000) > 0) {
                    // The channel is readable, so we can read the response
                    Set<SelectionKey> selectedKeys = selector.selectedKeys();
                    for (SelectionKey key : selectedKeys) {
                        if (key.isReadable()) {
                            mServerChannel.receive(packet);
                        }
                    }

                    if (packet.get(0) == 0 &&
                        new String(packet.array()).contains("action:connected"))
                    {
                        for (SelectionKey key : selectedKeys)
                        {
                            key.cancel();
                        };

                        mServerChannel.configureBlocking(true);

                        bHandshakeOK = true;
                        break; // we have been connected to the server
                    }
                }
            }
            else
            {
                // TODO: Proper error handling
                // possibly just write log
                continue;
            }
        }

        return bHandshakeOK;
    }

    int sendControlMsg(int control_action, String sParam) throws IOException {
        ByteBuffer packet = ByteBuffer.allocate(128);
        String action = JustVpnAPI.actionToText(control_action);

        int nSent = 0;

        if (!action.isEmpty())
        {
            if (!sParam.isEmpty())
            {
                action += ";" + sParam;
            }

            packet.put((byte) 0).put(action.getBytes()).flip(); // control message always starts with 0
            packet.position(0);
            nSent = mServerChannel.write(packet);
            packet.clear();
        }

        return nSent;
    }

    int sendControlMsg(int control_action) throws IOException {
        return sendControlMsg(control_action, "");
    }
}
