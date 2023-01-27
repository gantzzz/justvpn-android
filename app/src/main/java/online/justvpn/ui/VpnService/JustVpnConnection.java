package online.justvpn.ui.VpnService;

import static java.nio.charset.StandardCharsets.US_ASCII;

import android.net.VpnService;
import android.os.ParcelFileDescriptor;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.stream.IntStream;
public class JustVpnConnection implements Runnable {
    enum ControlAction {
        CONTROL_ACTION_CONNECT,
        CONTROL_ACTION_KEEPALIVE,
        CONTROL_ACTION_DISCONNECT,
        CONTROL_ACTION_GET_PARAMETERS,
        CONTROL_ACTION_CONFIGURED,
        CONTROL_ACTION_VERIFY_SUBSCRIPTION_TOKEN
    }
    private VpnService.Builder mBuilder;
    private VpnService mService;
    private DatagramChannel mServerChannel;

    // The number of attempts to handshake with the server
    private int MAX_HANDSHAKE_ATTEMPTS = 10;

    JustVpnConnection(VpnService.Builder builder, JustVpnService service) // TODO: ", Server server"
    {
        mBuilder = builder;
        mService = service;
    }

    @Override
    public void run() {
        try
        {
            final SocketAddress serverAddress = new InetSocketAddress(InetAddress.getByName("justvpn.online"), 8811);
            start(serverAddress);
        }
        catch (IOException | IllegalArgumentException  | IllegalStateException e)
        {
            // TODO: notify
        }
        finally
        {
            // TODO: notify
        }
    }

    private void start(SocketAddress server) throws IOException {

        // Connect to the server
        mServerChannel = DatagramChannel.open();
        DatagramSocket socket = mServerChannel.socket();

        if (!mService.protect(socket)) // fails if user permissions are not given, see requestVpnServicePermissionDialog()
        {
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

        // start VPN routine
        ParcelFileDescriptor vpnInterface = mBuilder.establish();

        // Tell the server we're configured
        sendControlMsg(ControlAction.CONTROL_ACTION_CONFIGURED);

        // Verify subscription on server side
        // token=debug_debug will allow the server to accept this connection even if this is an unpaid one
        sendControlMsg(ControlAction.CONTROL_ACTION_VERIFY_SUBSCRIPTION_TOKEN, "token=debug_debug");

        // Start VPN routine TBD
    }

    private boolean configure() throws IOException {
        boolean bConfigureOK = false;

        // Buffer for data
        ByteBuffer packet = ByteBuffer.allocate(1500);
        int nParemLen = 0;

        // Request network parameters from the server
        if (sendControlMsg(ControlAction.CONTROL_ACTION_GET_PARAMETERS) > 0 && // we have sent > 0 bytes
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
        ByteBuffer packet = ByteBuffer.allocate(1500);

        boolean bHandshakeOK = false;
        for (int i = 0; i < MAX_HANDSHAKE_ATTEMPTS; i++)
        {
            if (sendControlMsg(ControlAction.CONTROL_ACTION_CONNECT) > 0 && // we have sent > 0 bytes
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
                continue;
            }

        }

        return bHandshakeOK;
    }

    int sendControlMsg(ControlAction control_action, String sParam) throws IOException {
        ByteBuffer packet = ByteBuffer.allocate(1500);
        String action = "";
        int nSent = 0;
        switch (control_action)
        {
            case CONTROL_ACTION_CONNECT:
                action = "action:connect"; // upon receive, server will send back network configuration
                break;
            case CONTROL_ACTION_GET_PARAMETERS:
                action = "action:getparameters";
                break;
            case CONTROL_ACTION_DISCONNECT:
                break;
            case CONTROL_ACTION_KEEPALIVE:
                break;
            case CONTROL_ACTION_CONFIGURED:
                action = "action:configured";
                break;
            case CONTROL_ACTION_VERIFY_SUBSCRIPTION_TOKEN:
                action = "action:verifysubscription";
                break;
            default:
                break;
        }

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

    int sendControlMsg(ControlAction control_action) throws IOException {
        return sendControlMsg(control_action, "");
    }
}
