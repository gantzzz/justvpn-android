package online.justvpn.Definitions;

public class Connection {
    public enum State
    {
        IDLE,          // default
        CONNECTING,    // this connection is being initialized
        DISCONNECTING, // the client is disconnecting
        CONNECTED,     // handshake complete and the interface is configured
        ACTIVE,        // connection is established completely and IP packets are being forwarded
        DISCONNECTED,  // the client is not connected anymore
        NO_SLOTS,      // selected server cannot handle any more connections as it's full
        TIMED_OUT,     // the connection shall be monitored and this state applies
        // when there was no communication with the server for some time
        HANDSHAKE_FAILED, // This state applies on the handshake failure
        FAILED         // connection attempt has failed by some reason
    }
}
