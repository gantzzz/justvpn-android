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
import java.nio.channels.IllegalBlockingModeException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import online.justvpn.Definitions.Connection;
import online.justvpn.JAPI.JustVpnAPI;
import online.justvpn.JCrypto.JCrypto;

public class JustVpnConnection implements Runnable {
    private Thread mReceiverThread;
    private Thread mCheckConnectionThread;
    Instant mLastKeepAliveReceivedTimestamp;

    ParcelFileDescriptor mVPNInterface = null;
    private final int mMaxBuffLen = 1500; // used to allocate data buffers
    private Connection.State mConnectionState = Connection.State.IDLE;
    private VpnService.Builder mBuilder;
    private final VpnService mService;
    private final JustVpnAPI.ServerDataModel mServerDataModel;
    private DatagramChannel mServerChannel;

    private JCrypto m_Crypto = null;

    // This is the time in milliseconds indicating how often the server will send keepalive control messages
    private int mKeepAliveIntervalMs = - 1;

    private final JustVpnAPI.JustvpnSettings mSettings;

    JustVpnConnection(JustVpnService service, JustVpnAPI.ServerDataModel ServerDataModel, JustVpnAPI.JustvpnSettings Settings) // TODO: ", Server server"
    {
        mService = service;
        mServerDataModel = ServerDataModel;
        mReceiverThread = null;
        mCheckConnectionThread = null;
        mSettings = Settings;
    }

    public JustVpnAPI.ServerDataModel GetServerDataModel()
    {
        return mServerDataModel;
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
                final SocketAddress serverAddress = new InetSocketAddress(InetAddress.getByName(GetServerDataModel().sIp), 8811);
                start(serverAddress);
            }
            catch (InterruptedException e)
            {
                // stop re-attempts on interrupt
                break;
            }
            catch (IOException e)
            {
                Log.d("JUSTVPN:", "An exception occurred: " + e);
            }

            // we got disconnected by some reason either we detected the traffic
            // is not flowing through or the server has disconnected us for some reason
            // We want to protect our traffic to come though public network,
            // so keep the VPN tunnel established and keep reconnecting.
            if (mConnectionState != Connection.State.DISCONNECTED)
            {
                // retry
                sendConnectionState(Connection.State.RECONNECTING);

                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e)
                {
                    break;
                }
            }
            else
            {
                // give up
                if (mReceiverThread != null)
                {
                    mReceiverThread.interrupt();
                }
                if (mCheckConnectionThread != null)
                {
                    mCheckConnectionThread.interrupt();
                }
                if ( mVPNInterface != null)
                {
                    try {
                        mVPNInterface.close();
                        mVPNInterface = null;
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
        sendConnectionState(Connection.State.DISCONNECTED);
    }

    public void Disconnect()
    {
        try {

            if (mConnectionState.equals(Connection.State.ACTIVE))
            {
                Thread thread = new Thread(() ->
                {
                    try
                    {
                        sendControlMsg(JustVpnAPI.CONTROL_ACTION_DISCONNECT);
                        sendConnectionState(Connection.State.DISCONNECTING);
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

        sendConnectionState(Connection.State.DISCONNECTED);
    }

    private void start(SocketAddress server) throws IOException, InterruptedException
    {
        if (mServerChannel != null)
        {
            mServerChannel.close();
        }

        m_Crypto = null; // cleanup crypto on new connect
        sendConnectionState(Connection.State.CONNECTING);
        // Connect to the server
        mServerChannel = DatagramChannel.open();

        DatagramSocket socket = mServerChannel.socket();

        if (!mService.protect(socket)) // fails if user permissions are not given, see requestVpnServicePermissionDialog()
        {
            sendConnectionState(Connection.State.FAILED);
            throw new IllegalStateException("Cannot protect the tunnel");
        }

        // Connect to the server.
        socket.connect(server);

        // Handshake with the server
        if (!handshake())
        {
            sendConnectionState(Connection.State.HANDSHAKE_FAILED);
            return;
        }

        // Configure VPN interface
        if (!configure())
        {
            sendConnectionState(Connection.State.FAILED);
            return;
        }

        sendConnectionState(Connection.State.CONNECTED);

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

        sendConnectionState(Connection.State.ACTIVE);

        mLastKeepAliveReceivedTimestamp = Instant.now();

        // start check connection thread
        startCheckConnectionThread();

        // Start VPN routine
        startReceiverThread();
        // Try to enable encryption
        if (mSettings != null)
        {
            if (mSettings.mIsEcnryptionEnabled)
            {
                sendControlMsg(JustVpnAPI.CONTROL_ACTION_GET_PUBLIC_KEY);
            }
        }

        // process outgoing packages
        int nEmptyPacketCounter = 0;
        FileInputStream in = new FileInputStream(mVPNInterface.getFileDescriptor());
        ByteBuffer packet = ByteBuffer.allocate(mMaxBuffLen);

        while (mConnectionState == Connection.State.ACTIVE ||
               mConnectionState == Connection.State.ENCRYPTED)
        {
            // Read the outgoing packet from the input stream.
            int length = in.read(packet.array());
            if (length > 0)
            {
                // Write the outgoing packet to the tunnel.
                ByteBuffer actualSizeBuffer = ByteBuffer.allocate(length);
                packet.flip();
                packet.limit(length);
                actualSizeBuffer.put(packet);
                actualSizeBuffer.position(0);
                send_to_server(actualSizeBuffer);
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
                   mConnectionState.equals(Connection.State.CONNECTED) ||
                   mConnectionState.equals(Connection.State.ENCRYPTED))
            {
                Duration delta = Duration.between(mLastKeepAliveReceivedTimestamp, Instant.now());
                if (delta.toMillis() > mKeepAliveIntervalMs * 3)
                {
                    // We don't get keepalives for a while indicate timeout and start reconnecting routine
                    sendConnectionState(Connection.State.TIMED_OUT);
                }
                try {
                    if (mKeepAliveIntervalMs > 0)
                    {
                        Thread.sleep(mKeepAliveIntervalMs);
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
                ByteBuffer packet = ByteBuffer.allocate(mMaxBuffLen);
                FileOutputStream out = new FileOutputStream(mVPNInterface.getFileDescriptor());

                while (mConnectionState == Connection.State.ACTIVE ||
                       mConnectionState == Connection.State.ENCRYPTED)
                {
                    // Read the incoming packet from the tunnel.
                    //int len = mServerChannel.read(packet);
                    int len = read_from_server(packet);
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
                mLastKeepAliveReceivedTimestamp = Instant.now();
                break;

            case JustVpnAPI.CONTROL_ACTION_DISCONNECTED:
                String sReason = JustVpnAPI.getReason(new String(p.array(), 1, len -1));
                if (sReason.equals("reason:timedout"))
                {
                    sendConnectionState(Connection.State.TIMED_OUT);
                }
                break;
            case JustVpnAPI.CONTROL_ACTION_USE_PUBLIC_KEY:
                // here p consists of 0 - control flag + control_text + ; and then the key itself
                int offSet = 1 + JustVpnAPI.CONTROL_ACTION_USE_PUBLIC_KEY_TEXT.length() + 1;
                p.position(offSet);
                byte[] publicKey = new byte[len - offSet];
                p.get(publicKey);
                // generate encrypted session key
                m_Crypto = new JCrypto(publicKey);
                if (m_Crypto.GenerateSessionKey() == false)
                {
                    Log.e("JUSTVPN","Could not generate session key");
                }
                else
                {
                    sendControlMsg(JustVpnAPI.CONTROL_ACTION_USE_SESSION_KEY, m_Crypto.GetEncryptedSessionKey());
                    sendControlMsg(JustVpnAPI.CONTROL_ACTION_USE_SESSION_IV, m_Crypto.GetEncryptedIV());
                }
                break;
            case JustVpnAPI.STATUS_FURTHER_ENCRYPTED:
                Log.d("JUSTVPN","The session is now encrypted");
                if (m_Crypto != null)
                {
                    m_Crypto.SetEncryptionEnabled(true);
                }
                sendConnectionState(Connection.State.ENCRYPTED);
                break;
            default:
                break;
        }
    }

    private void sendConnectionState(Connection.State state)
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
                Log.d("JUSTVPN", "bad parameters: " + parameters);
                return false;
            }

            for (String parameter : parameters.split(";"))
            {
                String key = parameter.split(":")[0];
                String value = parameter.split(":")[1];

                switch (key)
                {
                    case "mtu":
                        int mMTU = Short.parseShort(value);
                        // in case of AES encryption, sometimes encrypted data exceeds the MTU,
                        // which causes packets corruption on the wire.
                        // Lower down the mtu a little to make sure we don't corrupt data
                        int mtu = Integer.parseInt(value);
                        mtu = mtu - (mtu/5);
                        mBuilder.setMtu((short)mtu); // here we set less MTU to fit in case of encryption
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
                        mKeepAliveIntervalMs = Integer.parseInt(value);
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
        // The number of attempts to handshake with the server
        int MAX_HANDSHAKE_ATTEMPTS = 10;
        for (int i = 0; i < MAX_HANDSHAKE_ATTEMPTS; i++)
        {
            if (sendControlMsg(JustVpnAPI.CONTROL_ACTION_CONNECT, mServerDataModel.sCountry) > 0 )
            {
                mServerChannel.configureBlocking(false);

                // Below we register a selector in order to be able to detect receive timeout
                Selector selector = Selector.open();
                mServerChannel.register(selector, SelectionKey.OP_READ);

                if (selector.select(30000) > 0) {
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
                        try
                        {
                            mServerChannel.configureBlocking(true);
                            bHandshakeOK = true;
                        } catch (IllegalBlockingModeException e)
                        {
                            bHandshakeOK = false;
                        }

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
        String action = JustVpnAPI.actionToText(control_action);

        ByteBuffer packet = null;

        int nSent = 0;

        if (!action.isEmpty())
        {
            if (!sParam.isEmpty())
            {
                action += ";" + sParam;
            }
            packet = ByteBuffer.allocate(action.length() + 1);

            packet.put((byte) 0); // control message always starts with 0
            packet.put(action.getBytes());
            packet.flip();
            nSent = send_to_server(packet);
            packet.clear();
        }

        return nSent;
    }

    int sendControlMsg(int control_action, byte[] bytes) throws IOException {
        String action = JustVpnAPI.actionToText(control_action);

        ByteBuffer packet = ByteBuffer.allocate(1+ action.length() + 1 + bytes.length); // 0 + control_text + ;

        int nSent = 0;

        if (!action.isEmpty())
        {
            packet.put((byte) 0); // control message always starts with 0
            packet.put(action.getBytes());
            packet.put((byte) ';');
            packet.put(bytes);
            packet.position(0);
            nSent = send_to_server(packet);
            packet.clear();
        }

        return nSent;
    }

    int send_to_server(ByteBuffer buffer)
    {
        int nSent = 0;
        try {
            if (m_Crypto != null && m_Crypto.IsEncryptionEnabled())
            {
                byte[] encrypted = m_Crypto.AESEncrypt(buffer.array());
                nSent = mServerChannel.write(ByteBuffer.wrap(encrypted));
            }
            else
            {
                nSent = mServerChannel.write(buffer);
            }
        } catch (IOException e) {
            Log.d("JUSTVPN", "an exception occured: " + e);
        }

        return nSent;
    }

    int read_from_server(ByteBuffer packet)
    {
        int nReceived = 0;
        try {
            if (m_Crypto != null && m_Crypto.IsEncryptionEnabled())
            {
                ByteBuffer encrypted = ByteBuffer.allocate(mMaxBuffLen);
                nReceived = mServerChannel.read(encrypted);
                ByteBuffer actualSizeBuffer = ByteBuffer.allocate(nReceived);
                encrypted.position(0);
                encrypted.limit(nReceived);

                actualSizeBuffer.put(encrypted);
                actualSizeBuffer.position(0);
                byte[] decrypted = m_Crypto.AESDecrypt(actualSizeBuffer.array());
                if (decrypted != null)
                {
                    packet.limit(decrypted.length);
                    packet.put(decrypted);
                    packet.position(0);
                    nReceived = decrypted.length;
                }
                else
                {
                    nReceived = 0;
                }

            }
            else
            {
                nReceived = mServerChannel.read(packet);
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return nReceived;
    }

    int sendControlMsg(int control_action) throws IOException {
        return sendControlMsg(control_action, "");
    }
}
