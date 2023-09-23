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

    private List<JustVpnAPI.StatsDataModel> mServerStats;

    Connection.State mState = Connection.State.IDLE;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("connection.state".equals(intent.getAction())) {
                int s = intent.getIntExtra("state", -1);
                mState = Connection.State.values()[s];
                OnConnectionStatusChanged();
            }
        }
    };

    @Override
    public void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter("connection.state"); // Replace with your desired action
        requireContext().registerReceiver(receiver, filter);
    }
    @Override
    public void onPause() {
        super.onPause();
        requireContext().unregisterReceiver(receiver);
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
        updateLocationSelector(); // TODO: Should be called periodically?
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

    private void updateLocationSelector()
    {
        // request servers listD
        mApi.getStats(getContext(), stats -> {
            mServerStats = stats;

            ArrayList<JustVpnAPI.StatsDataModel> adapterItems = new ArrayList<>();
            // automatic selection item goes on top
            JustVpnAPI.StatsDataModel modelAuto = new JustVpnAPI.StatsDataModel();
            modelAuto.sCountry = getResources().getString(R.string.select_fastest_location);
            adapterItems.add(modelAuto);

            // add the rest of the stats
            for (JustVpnAPI.StatsDataModel s: stats)
            {
                adapterItems.add(s);
                LocationSelectorAdapter ad = new LocationSelectorAdapter(getContext(), adapterItems);

                Spinner spinner = requireView().findViewById(R.id.locationSelectorSpinner);

                spinner.setAdapter(ad);
                spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                        ad.setSelectedItemIcon(i, view);
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> adapterView) {

                    }
                });
            }
        });
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
        String text = getResources().getString(id);
        v.setText(text);
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
                SetStatusViewText(R.string.status_connected);
                iv.setImageDrawable(getResources().getDrawable(R.drawable.vpn_on_icon));
                break;
            case DISCONNECTING:
                SetStatusViewText(R.string.status_disconnecting);
                iv.setImageDrawable(getResources().getDrawable(R.drawable.lock_icon));

                break;
            case HANDSHAKE_FAILED:
                SetStatusViewText(R.string.status_handshake_failed);
                // after 5 seconds, revert status message to default
                final Handler handler = new Handler(Looper.getMainLooper());

                handler.postDelayed(() ->
                {
                    // Don't change back to idle if the text has been already changed from some other place
                    if (GetStatusViewText().equals(getResources().getString(R.string.status_handshake_failed)))
                    {
                        SetStatusViewText(R.string.status_tap_to_connect);
                    }
                }, 5000);
            default:
                // just for now stop the animation
                iv.setImageDrawable(getResources().getDrawable(R.drawable.lock_icon));
                break;
        }
    }

    private void startVpnService()
    {
        // Get the selected item's ip address
        Spinner spinner = requireView().findViewById(R.id.locationSelectorSpinner);
        LocationSelectorAdapter ad = (LocationSelectorAdapter) spinner.getAdapter();
        JustVpnAPI.StatsDataModel server = ad.getSelectedItem();

        String server_address;

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
            server_address = getFastestLocation();
        }
        else
        {
            server_address = server.sIp;
        }

        Intent intent = new Intent(c, JustVpnService.class);

        intent.putExtra("server_address", server_address);
        c.startService(intent);
    }

    private String getFastestLocation()
    {
        String sAddress = "";

        if (mServerStats.size() < 1)
        {
            return sAddress;
        }

        List<JustVpnAPI.StatsDataModel> stats = new ArrayList<>(mServerStats);
        Collections.sort(stats, (a, b) -> a.mConnNumber - b.mConnNumber);
        JustVpnAPI.StatsDataModel fastest = stats.get(0);
        return fastest.sIp;
    }

    private void stopVpnService()
    {
        Context c= getContext();
        Intent intent = new Intent(c, JustVpnService.class);
        intent.putExtra("action", "stop");
        c.startService(intent);
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
                int animTimeMillis = 400;
                if (mState == Connection.State.CONNECTING) {
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