package online.justvpn.VpnService;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.VpnService;

import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

import java.util.concurrent.atomic.AtomicReference;

import online.justvpn.JAPI.JustVpnAPI;


public class JustVpnService extends VpnService {
    private Builder mBuilder;
    private JustVpnConnection mVpnConnection = null;

    // Connection thread reference
    private AtomicReference<Thread> mConnectionThreadReference;

    private void sendConnectionInfo(Context context)
    {
        if (mVpnConnection != null)
        {
            Intent i = new Intent("online.justvpn.connection.info");

            JustVpnAPI.ServerDataModel ServerDataModel = mVpnConnection.GetServerDataModel();
            int state = mVpnConnection.GetConnectionState().ordinal();

            i.putExtra("ServerDataModel", ServerDataModel);
            i.putExtra("state", state);
            context.sendBroadcast(i);
        }
    }

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            String action = intent.getAction();
            Log.e("Receiver", "Received action: " + action);

            if ("online.justvpn.get.connection.info".equals(intent.getAction())) {
                sendConnectionInfo(context);
            }
        }
    };

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        String action = intent.getStringExtra("action");

        if (action != null)
        {
            if (action.equals("stop"))
            {
                StopVpnConnection();
                return START_NOT_STICKY;
            }
        }
        JustVpnAPI.ServerDataModel ServerDataModel = (JustVpnAPI.ServerDataModel) intent.getSerializableExtra("ServerDataModel");
        JustVpnAPI.JustvpnSettings Settings = (JustVpnAPI.JustvpnSettings) intent.getSerializableExtra("settings");

        // Create connection reference
        if (mVpnConnection != null)
        {
            StopVpnConnection();
        }
        mVpnConnection = new JustVpnConnection(this, ServerDataModel, Settings);
        Thread thread = new Thread(mVpnConnection);

        mConnectionThreadReference = new AtomicReference<Thread>();
        mConnectionThreadReference.set(thread);
        mConnectionThreadReference.get().start();

        Log.d("JUSTVPN:","Service started id: " + this.hashCode());

        return START_STICKY;
    }

    public Builder getNewBuilder()
    {
        return new Builder();
    }

    private void StopVpnConnection()
    {
        Log.d("JUSTVPN:","Stopping connection ...");

        if (mVpnConnection != null)
        {
            mVpnConnection.Disconnect();
        }

        if (mConnectionThreadReference.get() != null)
        {
            mConnectionThreadReference.get().interrupt();
        }
        mConnectionThreadReference.set(null);
        mVpnConnection = null;
    }

    @Override
    public void onDestroy() {
        Log.d("JUSTVPN:","Service destroyed id: " + this.hashCode());
        if (mConnectionThreadReference != null)
        {
            if (mConnectionThreadReference.get() != null)
            {
                mConnectionThreadReference.get().interrupt();
            }
        }
        unregisterReceiver(mBroadcastReceiver);
        super.onDestroy();
    }

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    @Override
    public void onCreate()
    {
        IntentFilter filter = new IntentFilter();
        filter.addAction("online.justvpn.get.connection.info");
        registerReceiver(mBroadcastReceiver, filter, Context.RECEIVER_EXPORTED);
        super.onCreate();
    }
}
