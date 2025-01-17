package online.justvpn.ui.sign;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import online.justvpn.R;
import online.justvpn.databinding.FragmentSignBinding;

public class SignFragment extends Fragment {

    private FragmentSignBinding binding;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {

        //ProfileViewModel profileViewModel =
        //        new ViewModelProvider(this).get(ProfileViewModel.class);

        binding = FragmentSignBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        //final TextView textView = binding.textProfile;
        //profileViewModel.getText().observe(getViewLifecycleOwner(), textView::setText);
        TextView input_view = root.findViewById(R.id.profile_email_view);
        input_view.setText(R.string.email_example);
        input_view.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus)
                {
                    TextView tv = (TextView)v;

                    if (tv.getText().toString().equals(getString(R.string.email_example)))
                    {
                        tv.setText("");
                    }
                }
            }
        });

        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
    @Override
    public void onResume() {
        super.onResume();
        TextView tv = requireView().findViewById(R.id.profile_email_view);
        if (tv.getText().toString().isEmpty())
        {
            tv.setText(getString(R.string.email_example));
        }
    }

    public void onSignInClick(View view) {
        // send authentication post request to the server


    }

}