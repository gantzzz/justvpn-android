package online.justvpn.ui.home;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.net.VpnService;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextSwitcher;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.android.volley.VolleyError;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import online.justvpn.R;
import online.justvpn.databinding.FragmentHomeBinding;
import online.justvpn.VpnService.JustVpnAPI;
import online.justvpn.VpnService.JustVpnService;
import online.justvpn.ui.adaptors.LocationSelectorAdapter;
import online.justvpn.Definitions.Connection;

public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;
    private final JustVpnAPI mApi = new JustVpnAPI();

    private List<JustVpnAPI.ServerDataModel> mServerStats;

    Connection.State mState = Connection.State.IDLE;

    private boolean mVpnServiceRunning = false;

    BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.e("Receiver", "Received action: " + action);

            if ("online.justvpn.connection.state".equals(intent.getAction())) {
                int s = intent.getIntExtra("state", -1);
                mState = Connection.State.values()[s];
                OnConnectionStatusChanged();
            }
            else if ("online.justvpn.connection.info".equals(intent.getAction())) {
                int s = intent.getIntExtra("state", -1);
                mState = Connection.State.values()[s];
                OnConnectionStatusChanged();


                // Set selected server
                if (mServerStats != null && !mServerStats.equals(Connection.State.IDLE))
                {
                    JustVpnAPI.ServerDataModel ServerDataModel = (JustVpnAPI.ServerDataModel) intent.getSerializableExtra("ServerDataModel");
                    drawServersList(ServerDataModel);
                }
            }
        }
    };

    private void RequestConnectionInfo()
    {
        // Request connection info from the service
        Intent i = new Intent("online.justvpn.get.connection.info");
        requireContext().sendBroadcast(i);
    }
    @Override
    public void onResume() {
        super.onResume();

        IntentFilter filter = new IntentFilter();
        filter.addAction("online.justvpn.connection.state");
        filter.addAction("online.justvpn.connection.info");
        getContext().registerReceiver(mBroadcastReceiver, filter);

        RequestConnectionInfo();
    }
    @Override
    public void onPause() {
        super.onPause();
        getContext().unregisterReceiver(mBroadcastReceiver);
    }

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        //HomeViewModel homeViewModel =
        //        new ViewModelProvider(this).get(HomeViewModel.class);

        binding = FragmentHomeBinding.inflate(inflater, container, false);

        return binding.getRoot();
    }

    @MainThread
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        updateServersList(false);
        setupOnOffButtonOnClickListener();
        requestVpnServicePermissionDialog();
    }

    private void requestVpnServicePermissionDialog()
    {
        // Ask for vpn service permission
        Intent dialog = VpnService.prepare(getContext());
        if (dialog != null)
        {
            ActivityResultLauncher<Intent> VpnServiceActivityResultLauncher = registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                    });
            VpnServiceActivityResultLauncher.launch(dialog);
        }
        // already permitted
    }

    private void drawServersList(JustVpnAPI.ServerDataModel ServerDataModel)
    {
        if (mServerStats == null) {
            return;
        }
        Spinner spinner = requireView().findViewById(R.id.locationSelectorSpinner);

        ArrayList<JustVpnAPI.ServerDataModel> adapterItems = new ArrayList<>();

        // automatic selection item goes on top
        JustVpnAPI.ServerDataModel modelAuto = new JustVpnAPI.ServerDataModel();
        modelAuto.sCountry = getResources().getString(R.string.select_fastest_location);
        adapterItems.add(modelAuto);

        int nSelectedItemIndex = -1;
        // add the rest of the stats
        for (JustVpnAPI.ServerDataModel s: mServerStats)
        {
            adapterItems.add(s);
        }

        LocationSelectorAdapter ad = new LocationSelectorAdapter(getContext(), adapterItems);
        spinner.setAdapter(ad);

        // Set selected server
        if (ServerDataModel != null)
        {
            nSelectedItemIndex = adapterItems.indexOf(ServerDataModel);
        }

        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                ad.setSelectedItem(i, view);
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        if (nSelectedItemIndex != -1)
        {
            spinner.setSelection(nSelectedItemIndex);
        }
    }

    private void updateServersList(boolean bForced)
    {
        // Download only if we don't have it yet, or if forced
        if (bForced || mServerStats == null)
        {
            mApi.getStats(getContext(), new JustVpnAPI.onGetStatsInterface() {
                @Override
                public void onGetStatsReady(List<JustVpnAPI.ServerDataModel> stats) {
                    mServerStats = stats;
                    drawServersList(null);
                    RequestConnectionInfo();
                }

                @Override
                public void onGetStatsError(VolleyError error) {

                }
            });
        }
        else
        {
            drawServersList(null);
        }
    }

    private void setupOnOffButtonOnClickListener()
    {
        // set onclick listener for the on/off button
        ImageView v = requireView().findViewById(R.id.buttonImageView);
        v.setOnClickListener(this::onOnOffButtonPressed);
    }
    private void onOnOffButtonPressed(View view)
    {
        switch (mState)
        {
            case IDLE:
            case DISCONNECTED:
            default:
                StartConnection();
                break;
            case CONNECTING:
            case CONNECTED:
            case ACTIVE:
                stopVpnService();
                break;
        }
    }

    public void SetStatusViewText(int id)
    {
        String textToSet = getResources().getString(id);

        // Don't update with the same text
        if (GetStatusViewText().equals(textToSet))
        {
            return;
        }
        TextSwitcher v = requireView().findViewById(R.id.StatusText);

        // Cleanup the view first
        // Set an empty string for the currently displayed view (if any)
        View currentView = v.getCurrentView();
        if (currentView instanceof TextView) {
            TextView currentTextView = (TextView) currentView;
            currentTextView.setText("");
        }

        // Set an empty string for the next view (if any)
        View nextView = v.getNextView();
        if (nextView instanceof TextView) {
            TextView nextTextView = (TextView) nextView;
            nextTextView.setText("");
        }

        // set new string
        v.setText(textToSet);
    }

    public String GetStatusViewText()
    {
        View currentView = ((TextSwitcher)requireView().findViewById(R.id.StatusText)).getCurrentView();
        if (currentView instanceof TextView) {
            // If the current child view is a TextView, you can get its text
            TextView currentTextView = (TextView) currentView;
            return currentTextView.getText().toString();

        } else {
            return "";
        }
    }

    private void OnConnectionStatusChanged() {

        ImageView iv = requireView().findViewById(R.id.buttonImageView);

        switch (mState)
        {
            case IDLE:
            case DISCONNECTED:
                SetStatusViewText(R.string.status_tap_to_connect);
                iv.setImageDrawable(getResources().getDrawable(R.drawable.lock_icon));
                break;
            case CONNECTING:
                SetStatusViewText(R.string.status_connecting);
                StartConnectingAnimation();
                break;
            case CONNECTED:
            case ACTIVE:
                SetStatusViewText(R.string.status_connected);
                iv.setImageDrawable(getResources().getDrawable(R.drawable.vpn_on_icon));
                break;
            case DISCONNECTING:
                SetStatusViewText(R.string.status_disconnecting);
                iv.setImageDrawable(getResources().getDrawable(R.drawable.lock_icon));

                break;
            case HANDSHAKE_FAILED:
                SetStatusViewText(R.string.status_handshake_failed);
                resetStatusMessageDelayed(5000);
                break;
            case RECONNECTING:
                StartConnectingAnimation();
                SetStatusViewText(R.string.status_reconnecting);
                break;
            default:
                // just for now stop the animation
                resetStatusMessageDelayed(5000);
                iv.setImageDrawable(getResources().getDrawable(R.drawable.lock_icon));
                break;
        }
    }

    private void resetStatusMessageDelayed(int delay)
    {
        final Handler handler = new Handler(Looper.getMainLooper());

        handler.postDelayed(() ->
        {
            // Don't change back to idle if the text has been already changed from some other place
            if (GetStatusViewText().equals(getResources().getString(R.string.status_handshake_failed)))
            {
                SetStatusViewText(R.string.status_tap_to_connect);
            }
        }, delay);
    }
    private void startVpnService()
    {
        // Get the selected item's ip address
        Spinner spinner = requireView().findViewById(R.id.locationSelectorSpinner);
        LocationSelectorAdapter ad = (LocationSelectorAdapter) spinner.getAdapter();
        JustVpnAPI.ServerDataModel server = ad.getSelectedItem();

        // In case of auto location selection, pick up the fastest location via API
        Context c = getContext();
        Resources resources = null;
        if (c != null)
        {
            resources = c.getResources();
        }

        if (resources != null &&
            server.sCountry.equals(resources.getString(R.string.select_fastest_location)))
        {
            server = getFastestLocation();
        }

        Intent intent = new Intent(c, JustVpnService.class);

        intent.putExtra("ServerDataModel", server);
        c.startService(intent);

        mVpnServiceRunning = true;
    }

    private JustVpnAPI.ServerDataModel getFastestLocation()
    {
        if (mServerStats.size() < 1)
        {
            return null;
        }

        List<JustVpnAPI.ServerDataModel> stats = new ArrayList<>(mServerStats);
        Collections.sort(stats, (a, b) -> a.mConnNumber - b.mConnNumber);
        JustVpnAPI.ServerDataModel fastest = stats.get(0);
        return fastest;
    }

    private void stopVpnService()
    {
        Context c= getContext();
        Intent intent = new Intent(c, JustVpnService.class);
        intent.putExtra("action", "stop");
        c.startService(intent);
        mVpnServiceRunning = false;
    }

    private void StartConnectingAnimation()
    {
        ImageView v = requireView().findViewById(R.id.buttonImageView);

        Drawable[] backgrounds = new Drawable[2];
        Resources res = getResources();
        backgrounds[0] = res.getDrawable(R.drawable.lock_icon);
        backgrounds[1] = res.getDrawable(R.drawable.vpn_on_icon);

        TransitionDrawable crossfader = new TransitionDrawable(backgrounds);

        v.setImageDrawable(crossfader);

        final Handler handler = new Handler(Looper.getMainLooper());
        Runnable animationRunnable = new Runnable() {
            public int dir = 0;
            @Override
            public void run() {
                int animTimeMillis = 600;
                if (mState == Connection.State.CONNECTING || mState == Connection.State.RECONNECTING) {
                    if (dir == 0) // forward
                    {
                        crossfader.startTransition(animTimeMillis);
                        dir = 1;
                    }
                    else
                    {
                        crossfader.reverseTransition(animTimeMillis);
                        dir = 0;
                    }

                    handler.postDelayed(this, animTimeMillis); // Delay for the next iteration (200ms + 200ms)
                }
            }
        };
        handler.post(animationRunnable);
    }

    private void StartConnection() {
        // Start JustVPN service
        startVpnService();

        Vibrator vibe = (Vibrator) requireActivity().getSystemService(Context.VIBRATOR_SERVICE);
        vibe.vibrate(100);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}