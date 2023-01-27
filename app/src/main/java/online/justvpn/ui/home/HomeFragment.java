package online.justvpn.ui.home;

import android.annotation.SuppressLint;
import android.content.Context;
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
import online.justvpn.ui.adaptors.LocationSelectorAdapter;

public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;
    private final JustVpnAPI mApi = new JustVpnAPI();

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
    private void onOnOffButtonPressed(View view)
    {
        // update Status line to "Connecting..."
        TextView v = Objects.requireNonNull(getView()).findViewById(R.id.StatusText);
        v.setText(R.string.status_connecting);

        Vibrator vibe = (Vibrator) Objects.requireNonNull(getActivity()).getSystemService(Context.VIBRATOR_SERVICE);
        vibe.vibrate(100);

        ImageView iv = ((ImageView)view);
        int transitionDuration = 500;

        TransitionDrawable transitionDrawable = (TransitionDrawable) iv.getDrawable();
        transitionDrawable.startTransition(transitionDuration);

        final Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(() -> {
            // after transition has finished one way, start reverse transition
            transitionDrawable.reverseTransition(transitionDuration);
        }, transitionDuration);

        handler.postDelayed(() -> {
            // after transition has finally finished, start the animation
            iv.setImageResource(R.drawable.onoff_button_animation);
            ((AnimationDrawable)iv.getDrawable()).start();
        }, transitionDuration * 2);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}