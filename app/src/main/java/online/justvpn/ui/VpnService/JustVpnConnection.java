package online.justvpn.ui.VpnService;

import android.net.VpnService;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.stream.IntStream;

public class JustVpnConnection implements Runnable {
    private VpnService.Builder mBuilder;
    private VpnService mService;
    private DatagramChannel mServerChannel;

    // The number of attempts to handshake with the server
    private int MAX_HANDSHAKE_ATTEMPTS = 10;

    JustVpnConnection(VpnService.Builder builer, JustVpnService service)
    {
        mBuilder = builer;
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

        if (!mService.protect(socket))
        {
            throw new IllegalStateException("Cannot protect the tunnel");
        }

        // Connect to the server.
        socket.connect(server);

        // Configure the network
        handshake();
    }

    private void handshake() throws IOException {
        // Allocate packet for sending/receiving data
        ByteBuffer packet = ByteBuffer.allocate(1500);

        for (int i = 0; i < MAX_HANDSHAKE_ATTEMPTS; i++)
        {
            String action = "action:connect"; // upon receive, server will send back network configuration
            packet.put((byte) 0).put(action.getBytes()).flip(); // control message always starts with 0
            packet.position(0);
            mServerChannel.write(packet);
            packet.clear();

            int length = mServerChannel.read(packet);
            if (length > 0 && packet.get(0) == 0) // control message always starts with 0
            {

            }
        }
    }
}
