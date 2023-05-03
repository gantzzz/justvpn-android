package online.justvpn.ui.home;

import static online.justvpn.ui.home.State.IDLE;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.AnimationDrawable;
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
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import online.justvpn.R;
import online.justvpn.databinding.FragmentHomeBinding;
import online.justvpn.ui.VpnService.JustVpnAPI;
import online.justvpn.ui.VpnService.JustVpnService;
import online.justvpn.ui.adaptors.LocationSelectorAdapter;
enum  State
{
    IDLE, CONNECTING, CONNECTED, FAILED;
}

public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;
    private final JustVpnAPI mApi = new JustVpnAPI();
    private boolean mUserVpnAllowed = false;

    private List<JustVpnAPI.StatsDataModel> mServerStats;

    State mState = IDLE;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        HomeViewModel homeViewModel =
                new ViewModelProvider(this).get(HomeViewModel.class);

        binding = FragmentHomeBinding.inflate(inflater, container, false);

        return binding.getRoot();
    }

    @MainThread
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        updateLocationSelector(); // TODO: Should be called periodically?
        setupOnOffButtonOnClickListener();
        requestVpnServicePermissionDialog();
        mState = IDLE;
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
                        mUserVpnAllowed = result.getResultCode() == Activity.RESULT_OK;
                    });
            VpnServiceActivityResultLauncher.launch(dialog);
        }
        else
        {
            // already permitted
            mUserVpnAllowed = true;
        }
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
        ImageView v = requireView().findViewById(R.id.onoffButtonImageView);
        v.setOnClickListener(this::onOnOffButtonPressed);
    }
    private void onOnOffButtonPressed(View view) {

        switch (mState)
        {
            case IDLE:
                Connecting(view);
                break;
            case CONNECTING:
                // just for now change the state to connect
                mState = State.CONNECTED;
                UpdateStatusText(view);
                ImageView iv = ((ImageView)view);
                ((AnimationDrawable)iv.getDrawable()).stop();
                iv.setImageDrawable(getResources().getDrawable(R.drawable.button_state_on));
                break;
            case CONNECTED:
                break;
            default:
                break;
        }
    }

    private void UpdateStatusText(View view) {
        TextView v = requireView().findViewById(R.id.StatusText);
        switch (mState)
        {
            case IDLE:
                v.setText(R.string.status_tap_to_connect);
                break;
            case CONNECTING:
                v.setText(R.string.status_connecting);
                break;
            case CONNECTED:
                v.setText(R.string.status_connected);
                break;
            default:
                // just for now stop the animation
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
        if (server.sCountry.equals(getContext().getResources().getString(R.string.select_fastest_location)))
        {
            server_address = getFastestLocation();
        }
        else
        {
            server_address = server.sIp;
        }

        Intent intent = new Intent(getContext(), JustVpnService.class);

        intent.putExtra("server_address", server_address);
        getContext().startService(intent);
    }

    private String getFastestLocation()
    {
        String sAddress = "";

        if (mServerStats.size() < 1)
        {
            return sAddress;
        }

        List stats = new ArrayList<>(mServerStats);
        Collections.sort(stats, (Comparator<JustVpnAPI.StatsDataModel>) (a, b) -> a.mConnNumber - b.mConnNumber);
        JustVpnAPI.StatsDataModel fastest = (JustVpnAPI.StatsDataModel)stats.get(0);
        return fastest.sIp;
    }

    private void stopVpnService()
    {
        Intent intent = new Intent(getContext(), JustVpnService.class);
        getContext().stopService(intent);
    }

    private void Connecting(View view) {
        // update the state
        mState = State.CONNECTING;
        // update Status line to "Connecting..."
        UpdateStatusText(view);

        Vibrator vibe = (Vibrator) requireActivity().getSystemService(Context.VIBRATOR_SERVICE);
        vibe.vibrate(100);

        // Start JustVPN service
        startVpnService();

        ImageView iv = ((ImageView)view);

        /* int transitionDuration = 500;
        TransitionDrawable transitionDrawable = (TransitionDrawable) iv.getDrawable();
        transitionDrawable.startTransition(transitionDuration);

        final Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(() -> {
            // after transition has finished one way, start reverse transition
            transitionDrawable.reverseTransition(transitionDuration);
        }, transitionDuration);*/

        final Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(() -> {
            // after transition has finally finished, start the animation
            iv.setImageResource(R.drawable.onoff_button_animation);
            ((AnimationDrawable)iv.getDrawable()).start();
        }, 0);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}