package online.justvpn.ui.adaptors;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Locale;

import online.justvpn.R;
import online.justvpn.VpnService.JustVpnAPI;

public class LocationSelectorAdapter extends BaseAdapter{
    private final Context mContext;
    private final ArrayList<JustVpnAPI.StatsDataModel> mData;
    private int nSelectedItem = -1;

    public LocationSelectorAdapter(Context context, ArrayList<JustVpnAPI.StatsDataModel> data) {
        this.mContext = context;
        this.mData = data;
    }

    @Override
    public long getItemId(int position) {
        return mData.get(position).mId;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent)
    {
        if (convertView == null) {
            LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            //convertView = inflater.inflate(R.layout.location_selector_item_layout, parent.findViewById(R.id.locationSelectorCardView));
            convertView = inflater.inflate(R.layout.location_selector_item_layout, parent, false);
        }

        int icon = 0;

        // convert country short name to full name
        Locale l = new Locale("", mData.get(position).sCountry);
        String sCountry = l.getDisplayCountry();
        String titleText;
        if (!mData.get(position).sCountry.equals(mContext.getResources().getString(R.string.select_fastest_location)))
        {
            if (!sCountry.isEmpty())
            {
                titleText = sCountry;
            }
            else
            {
                // If country is not available, display the ip address
                titleText = mData.get(position).sIp;
            }
        }
        // special handling for auto selection
        else
        {
            titleText = mData.get(position).sCountry;
            icon = R.mipmap.ic_world_foreground;
        }

        // get icon resource id
        switch (mData.get(position).sCountry)
        {
            case "US":
                icon = R.mipmap.ic_usa_foreground;
                break;
            case "NL":
                icon = R.mipmap.ic_nl_foreground;
                break;
            default:
                icon = R.mipmap.ic_world_foreground;
                break;
        }

        ((TextView)convertView.findViewById(R.id.loc_selector_item_country_text)).setText(titleText);
        ((ImageView)convertView.findViewById(R.id.loc_selector_item_country_icon)).setImageResource(icon);

        if (position == nSelectedItem)
        {
            ((ImageView)convertView.findViewById(R.id.loc_selector_item_country_check)).setImageResource(R.mipmap.ic_check_foreground);
        }

        return convertView;
    }

    @Override
    public int getCount()
    {
        return mData.size();
    }

    @Override
    public JustVpnAPI.StatsDataModel getItem(int position) {
        return mData.get(position);
    }

    public void setSelectedItemIcon(int nSelectedItem, View view) {
        this.nSelectedItem = nSelectedItem;
    }

    public JustVpnAPI.StatsDataModel getSelectedItem() {
        return getItem(nSelectedItem);
    }
}
