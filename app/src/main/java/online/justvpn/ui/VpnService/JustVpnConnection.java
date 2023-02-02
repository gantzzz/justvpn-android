package online.justvpn.ui.VpnService;

import static java.nio.charset.StandardCharsets.US_ASCII;

import android.net.VpnService;
import android.os.ParcelFileDescriptor;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
public class JustVpnConnection implements Runnable {
    private Thread mReceiverThread;
    ParcelFileDescriptor mVPNInterface = null;
    private int mMTU;

    enum CONNECTION_STATE
    {
        CONNECTION_STATE_IDLE,          // default
        CONNECTION_STATE_CONNECTING,    // this connection is being initialized
        CONNECTION_STATE_DISCONNECTING, // the client is disconnecting
        CONNECTION_STATE_CONNECTED,     // handshake complete and the interface is configured
        CONNECTION_STATE_ACTIVE,        // connection is established completely and IP the packets are being forwarded
        CONNECTION_STATE_DISCONNECTED,  // the client is not connected anymore
        CONNECTION_STATE_NO_SLOTS,      // selected server cannot handle any more connections as it's full
        CONNECTION_STATE_TIMED_OUT,     // the connection shall be monitored and this state applies
                                        // when there was no communication with the server for some time
        CONNECTION_STATE_FAILED         // connection attempt has failed by some reason
    }
    private CONNECTION_STATE mConnectionState = CONNECTION_STATE.CONNECTION_STATE_IDLE;
    private VpnService.Builder mBuilder;
    private VpnService mService;
    private String mServerAddress;
    private DatagramChannel mServerChannel;

    // The number of attempts to handshake with the server
    private int MAX_HANDSHAKE_ATTEMPTS = 10;

    JustVpnConnection(VpnService.Builder builder, JustVpnService service, String server_address) // TODO: ", Server server"
    {
        mBuilder = builder;
        mService = service;
        mServerAddress = server_address;
    }

    @Override
    public void run() {
        try
        {
            final SocketAddress serverAddress = new InetSocketAddress(InetAddress.getByName(mServerAddress), 8811);
            start(serverAddress);
        }
        catch (IOException | IllegalArgumentException | IllegalStateException | InterruptedException e)
        {
            // TODO: notify the service
        }
        finally
        {
            // TODO: notify the service
        }
    }

    private void start(SocketAddress server) throws IOException, InterruptedException {
        setConnectionState(CONNECTION_STATE.CONNECTION_STATE_CONNECTING);
        // Connect to the server
        mServerChannel = DatagramChannel.open();
        DatagramSocket socket = mServerChannel.socket();

        if (!mService.protect(socket)) // fails if user permissions are not given, see requestVpnServicePermissionDialog()
        {
            setConnectionState(CONNECTION_STATE.CONNECTION_STATE_FAILED);
            throw new IllegalStateException("Cannot protect the tunnel");
        }

        // Connect to the server.
        socket.connect(server);

        // Connect to the server
        if (!handshake())
        {
            // TODO: Notify UI
            return;
        }

        // Configure VPN interface
        if (!configure())
        {
            // TODO: Notify UI
            return;
        }

        setConnectionState(CONNECTION_STATE.CONNECTION_STATE_CONNECTED);

        // start VPN routine
        mVPNInterface = mBuilder.establish();

        // Tell the server we're configured
        sendControlMsg(JustVpnAPI.CONTROL_ACTION_CONFIGURED);

        // Verify subscription on server side
        // token=debug_debug will allow the server to accept this connection even if this is an unpaid one
        sendControlMsg(JustVpnAPI.CONTROL_ACTION_VERIFY_SUBSCRIPTION_TOKEN, "token=debug_debug");

        setConnectionState(CONNECTION_STATE.CONNECTION_STATE_ACTIVE);

        // Start VPN routine
        startReceiverThread();

        // ... and process outgoing packages
        int nEmptyPacketCounter = 0;
        FileInputStream in = new FileInputStream(mVPNInterface.getFileDescriptor());
        ByteBuffer packet = ByteBuffer.allocate(mMTU);

        while (mConnectionState == CONNECTION_STATE.CONNECTION_STATE_ACTIVE)
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
    }

    private void startReceiverThread()
    {
        mReceiverThread = new Thread(() ->
        {
            try
            {
                ByteBuffer packet = ByteBuffer.allocate(mMTU);
                FileOutputStream out = new FileOutputStream(mVPNInterface.getFileDescriptor());

                while (mConnectionState == CONNECTION_STATE.CONNECTION_STATE_ACTIVE)
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
                e.printStackTrace();
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
                break;
            // TODO: Make server to send "action:disconnect" to the client that has not responded to keepalives.
            default:
                break;
        }
    }

    private void setConnectionState(CONNECTION_STATE state)
    {
        mConnectionState = state;
        // TODO: Notify the service
    }

    private boolean configure() throws IOException {
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
            if (sendControlMsg(JustVpnAPI.CONTROL_ACTION_CONNECT) > 0 && // we have sent > 0 bytes
                mServerChannel.read(packet) > 0 && // we have read > 0 bytes
                packet.get(0) == 0 && // All control messages begin with 0
                new String(packet.array()).contains("action:connected")) // server replied with "connected"
            {
                bHandshakeOK = true;
                break; // we have been connected to the server
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
