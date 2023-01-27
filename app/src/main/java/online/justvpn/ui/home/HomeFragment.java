package online.justvpn.ui.home;

import static online.justvpn.ui.home.State.IDLE;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.TransitionDrawable;
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

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import java.util.ArrayList;
import java.util.Objects;

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
        updateLocationSelector();
        setupOnOffButtonOnClickListener();
        mState = IDLE;
    }

    private void updateLocationSelector()
    {
        // request servers listD
        mApi.getServers(getContext(), servers -> {
            ArrayList<JustVpnAPI.ServerDataModel> dataModels = new ArrayList<>();

            // automatic selection item goes on top
            JustVpnAPI.ServerDataModel modelAuto = new JustVpnAPI.ServerDataModel();
            modelAuto.sCountry = "auto";
            dataModels.add(modelAuto);

            // add the rest of the servers
            for (JustVpnAPI.ServerDataModel s: servers)
            {
                dataModels.add(s);
                LocationSelectorAdapter ad = new LocationSelectorAdapter(getContext(), dataModels);

                Spinner spinner = Objects.requireNonNull(getView()).findViewById(R.id.locationSelectorSpinner);

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
        ImageView v = Objects.requireNonNull(getView()).findViewById(R.id.onoffButtonImageView);
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
        TextView v = Objects.requireNonNull(getView()).findViewById(R.id.StatusText);
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

    private void Connecting(View view) {
        // update the state
        mState = State.CONNECTING;
        // update Status line to "Connecting..."
        UpdateStatusText(view);

        Vibrator vibe = (Vibrator) Objects.requireNonNull(getActivity()).getSystemService(Context.VIBRATOR_SERVICE);
        vibe.vibrate(100);

        // Start JustVPN service
        Intent intent = new Intent(getContext(), JustVpnService.class);
        getContext().startService(intent);

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