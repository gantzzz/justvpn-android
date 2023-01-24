package online.justvpn.ui.home;

import android.content.Context;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import online.justvpn.MainActivity;
import online.justvpn.R;
import online.justvpn.databinding.FragmentHomeBinding;

public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        HomeViewModel homeViewModel =
                new ViewModelProvider(this).get(HomeViewModel.class);

        binding = FragmentHomeBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        // set onclick listener for the on/off button
        ImageView v = root.findViewById(R.id.onoffButtonImageView);
        v.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onOnOffButtonPressed(view);
            }
        });

        return root;
    }

    private void onOnOffButtonPressed(View view)
    {
        Vibrator vibe = (Vibrator) getActivity().getSystemService(Context.VIBRATOR_SERVICE);
        vibe.vibrate(100);

        ImageView iv = ((ImageView)view);
        int transitionDuration = 500;

        TransitionDrawable transitionDrawable = (TransitionDrawable) iv.getDrawable();
        transitionDrawable.startTransition(transitionDuration);

        final Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                // after transition has finished one way, start reverse transition
                transitionDrawable.reverseTransition(transitionDuration);
            }
        }, transitionDuration);

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                // after transition has finally finished, start the animation
                iv.setImageResource(R.drawable.onoff_button_animation);
                ((AnimationDrawable)iv.getDrawable()).start();
            }
        }, transitionDuration * 2);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}