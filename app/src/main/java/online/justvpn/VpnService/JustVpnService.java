package online.justvpn.VpnService;

import static androidx.constraintlayout.helper.widget.MotionEffect.TAG;

import android.content.Intent;
import android.net.VpnService;

import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.util.concurrent.atomic.AtomicReference;


public class JustVpnService extends VpnService {
    private Builder mBuilder;
    private JustVpnConnection mVpnConnection;

    // Connection thread reference
    private AtomicReference<Thread> mConnectionThreadReference = new AtomicReference<Thread>();

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        String action = intent.getStringExtra("action");

        if (action != null)
        {
            if (action.equals("stop"))
            {
                StopVpnConnection();
                Log.d("JUSTVPN:","Service stopped: id: " + this.hashCode());
                return START_NOT_STICKY;
            }
        }
        String sServerAddress = intent.getStringExtra("server_address");

        // Create a new Builder object
        mBuilder = new Builder();

        // Create connection reference
        mVpnConnection = new JustVpnConnection(mBuilder, this, sServerAddress);
        Thread thread = new Thread(mVpnConnection);

        mConnectionThreadReference.set(thread);
        mConnectionThreadReference.get().start();

        Log.d("JUSTVPN:","Service started id: " + this.hashCode());

        return START_STICKY;
    }

    private void StopVpnConnection()
    {
        Log.d("JUSTVPN:","Stopping connection ...");

        if (mVpnConnection != null)
        {
            mVpnConnection.Disconnect();
        }
    }

    @Override
    public void onDestroy() {
        Log.d("JUSTVPN:","Service destroyed id: " + this.hashCode());
        if (mConnectionThreadReference.get() != null)
        {
            mConnectionThreadReference.get().interrupt();
        }
        super.onDestroy();
    }
}
