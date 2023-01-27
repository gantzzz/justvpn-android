package online.justvpn.ui.VpnService;

import static androidx.constraintlayout.helper.widget.MotionEffect.TAG;

import android.content.Intent;
import android.net.VpnService;

import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.util.concurrent.atomic.AtomicReference;


public class JustVpnService extends VpnService {
    private Builder mBuilder;
    private ParcelFileDescriptor mInterface;

    // Connection thread reference
    private AtomicReference<Thread> mConnectionThread = new AtomicReference<Thread>();

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "VPN service started");

        // Create a new Builder object
        mBuilder = new Builder();

        // Create connection reference
        Thread thread = new Thread(new JustVpnConnection(mBuilder,this));
        mConnectionThread.set(thread);
        thread.start();

        return START_STICKY;
    }
}
