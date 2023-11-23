package online.justvpn.ui.settings;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import online.justvpn.R;
import online.justvpn.databinding.FragmentSettingsBinding;
import online.justvpn.ui.home.HomeFragment;

public class SettingsFragment extends Fragment {
    private FragmentSettingsBinding binding;

    private SharedPreferences sharedPreferences;
    private SharedPreferences.Editor editor;

    private Boolean mSettingsUpdated = false;
    SharedPreferences.OnSharedPreferenceChangeListener mSharedPrefChangeListener;
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        //SettingsViewModel settingsViewModel =
        //        new ViewModelProvider(this).get(SettingsViewModel.class);

        binding = FragmentSettingsBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        final TextView textView = binding.textSettings;
        //settingsViewModel.getText().observe(getViewLifecycleOwner(), textView::setText);
        sharedPreferences = getActivity().getSharedPreferences("preferences", Context.MODE_PRIVATE);
        editor = sharedPreferences.edit();

        mSharedPrefChangeListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s) {
                mSettingsUpdated = true;
            }
        };

        sharedPreferences.registerOnSharedPreferenceChangeListener(mSharedPrefChangeListener);

        SetupSettings();
        // touch a line triggers the checkbox
        binding.getRoot().findViewById(R.id.setting_line).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                CheckBox cb = binding.getRoot().findViewById(R.id.checkBoxSettingEncryption);
                cb.setChecked(!cb.isChecked()); // reverse
                cb.callOnClick();
            }
        });
        return root;
    }
    @Override
    public void onPause()
    {
        super.onPause();

        // apply changes in settings
        editor.commit();

        if (mSettingsUpdated)
        {
            AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity());
            builder.setTitle(R.string.settings_change_dialog_title)
                    .setMessage(R.string.settings_change_dialog_text)
                    .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            // Positive button clicked
                            // You can add code here to handle the OK button click
                        }
                    });
            builder.show();
        }
    }

    private void SetupSettings()
    {
        // Setup for encryption
        Boolean encryption = sharedPreferences.getBoolean("encryption", true);
        CheckBox cb = binding.getRoot().findViewById(R.id.checkBoxSettingEncryption);
        cb.setChecked(encryption);
        cb.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                CheckBox v = (CheckBox) view;
                SharedPreferences sharedPreferences = getActivity().getSharedPreferences("preferences", Context.MODE_PRIVATE);
                editor.putBoolean("encryption", v.isChecked());
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(mSharedPrefChangeListener);
    }
}