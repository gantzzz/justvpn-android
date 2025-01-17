package online.justvpn.Managers;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Pair;

import com.android.volley.VolleyError;

import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import online.justvpn.JAPI.JustVpnAPI;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class LocationManager{
    private Thread mThread;
    private final long mInterval = 120000; // Interval between each cycle in milliseconds
    private boolean mRunning = false;
    private final Context mContext;
    private LocationManager(Context context)
    {
        mContext = context;
        start();
    }
    private static LocationManager mInstance = null;

    public static LocationManager getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new LocationManager(context);
        }

        return mInstance;
    }

    private void start() {
        if (mRunning) {
            return; // Already running, do nothing
        }
        mRunning = true;
        mThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (mRunning) {
                    requestLocations();

                    try {
                        Thread.sleep(mInterval);
                    } catch (InterruptedException e) {
                        // Handle interruption
                        Thread.currentThread().interrupt();
                    }
                }
            }
        });
        mThread.start();
    }

    public void stop() {
        mRunning = false;
        if (mThread != null) {
            mThread.interrupt(); // Interrupt the thread to stop immediately
        }
    }

    private void processLocations(List<JustVpnAPI.ServerDataModel> locations)
    {
        if (locations.isEmpty())
        {
            return;
        }

        SharedPreferences sharedPreferences = mContext.getSharedPreferences("preferences", Context.MODE_PRIVATE);

        String json = new Gson().toJson(locations);
        sharedPreferences.edit().putString("locations", json).apply();

    }

    public List<JustVpnAPI.ServerDataModel> getLocations()
    {
        SharedPreferences sharedPreferences = mContext.getSharedPreferences("preferences", Context.MODE_PRIVATE);

        String locationsJson = sharedPreferences.getString("locations", "");
        Type type = new TypeToken<List<JustVpnAPI.ServerDataModel>>() {}.getType();
        return new Gson().fromJson(locationsJson, type);
    }

    private void onStatsReady(List<JustVpnAPI.ServerDataModel> stats)
    {
        processLocations(stats);
    }

    private void requestLocations()
    {
        JustVpnAPI api = new JustVpnAPI();
        api.getStats(mContext, new JustVpnAPI.onGetStatsInterface() {
            @Override
            public void onGetStatsReady(List<JustVpnAPI.ServerDataModel> servers) {
                onStatsReady(servers);
            };

            @Override
            public void onGetStatsError(VolleyError error) {
                // don't do anything as we are cycling anyways
            };
        });
    }
}