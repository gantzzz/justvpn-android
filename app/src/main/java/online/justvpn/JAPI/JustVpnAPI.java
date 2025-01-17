package online.justvpn.JAPI;

import android.content.Context;
import android.util.Pair;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class JustVpnAPI {

    // TODO: make this class a singletone as we won't need more than 1 instances of it for HTTP
    public static final int CONTROL_ACTION_CONNECT = 0;
    public static final int CONTROL_ACTION_KEEPALIVE = 1;
    public static final int CONTROL_ACTION_DISCONNECT = 2;
    public static final int CONTROL_ACTION_DISCONNECTED = 3;
    public static final int CONTROL_ACTION_GET_PARAMETERS = 4;
    public static final int CONTROL_ACTION_CONFIGURED = 5;
    public static final int CONTROL_ACTION_VERIFY_SUBSCRIPTION_TOKEN = 6;
    public static final int CONTROL_ACTION_GET_PUBLIC_KEY = 7;
    public static final int CONTROL_ACTION_USE_PUBLIC_KEY = 8;
    public static final int CONTROL_ACTION_USE_SESSION_KEY = 9;
    public static final int CONTROL_ACTION_USE_SESSION_IV = 10;
    public static final int STATUS_FURTHER_ENCRYPTED = 11;

    public static final String CONTROL_ACTION_CONNECT_TEXT = "action:connect";
    public static final String CONTROL_ACTION_KEEPALIVE_TEXT = "action:keepalive";
    public static final String CONTROL_ACTION_DISCONNECT_TEXT = "action:disconnect";
    public static final String CONTROL_ACTION_DISCONNECTED_TEXT = "action:disconnected";
    public static final String CONTROL_ACTION_GET_PARAMETERS_TEXT = "action:getparameters";
    public static final String CONTROL_ACTION_CONFIGURED_TEXT = "action:configured";
    public static final String CONTROL_ACTION_VERIFY_SUBSCRIPTION_TOKEN_TEXT = "action:verifysubscription";
    public static final String CONTROL_ACTION_GET_PUBLIC_KEY_TEXT = "action:getpublickey";
    public static final String CONTROL_ACTION_USE_PUBLIC_KEY_TEXT = "action:usepublickey";

    public static final String CONTROL_ACTION_USE_SESSION_KEY_TEXT = "action:usesessionkey";
    public static final String CONTROL_ACTION_USE_SESSION_IV_TEXT = "action:usesessioniv";
    public static final String STATUS_FURTHER_ENCRYPTED_TEXT = "status:furtherencrypted";
    public static String actionToText(int action)
    {
        String action_text = "";
        switch (action)
        {
            case CONTROL_ACTION_CONNECT:
                action_text = CONTROL_ACTION_CONNECT_TEXT;
                break;
            case CONTROL_ACTION_KEEPALIVE:
                action_text = CONTROL_ACTION_KEEPALIVE_TEXT;
                break;
            case CONTROL_ACTION_DISCONNECT:
                action_text = CONTROL_ACTION_DISCONNECT_TEXT;
                break;
            case CONTROL_ACTION_DISCONNECTED:
                action_text = CONTROL_ACTION_DISCONNECTED_TEXT;
                break;
            case CONTROL_ACTION_GET_PARAMETERS:
                action_text = CONTROL_ACTION_GET_PARAMETERS_TEXT;
                break;
            case CONTROL_ACTION_CONFIGURED:
                action_text = CONTROL_ACTION_CONFIGURED_TEXT;
                break;
            case CONTROL_ACTION_VERIFY_SUBSCRIPTION_TOKEN:
                action_text = CONTROL_ACTION_VERIFY_SUBSCRIPTION_TOKEN_TEXT;
                break;
            case CONTROL_ACTION_GET_PUBLIC_KEY:
                action_text = CONTROL_ACTION_GET_PUBLIC_KEY_TEXT;
                break;
            case CONTROL_ACTION_USE_PUBLIC_KEY:
                action_text = CONTROL_ACTION_USE_PUBLIC_KEY_TEXT;
                break;
            case CONTROL_ACTION_USE_SESSION_KEY:
                action_text = CONTROL_ACTION_USE_SESSION_KEY_TEXT;
                break;
            case CONTROL_ACTION_USE_SESSION_IV:
                action_text = CONTROL_ACTION_USE_SESSION_IV_TEXT;
                break;
            case STATUS_FURTHER_ENCRYPTED:
                action_text = STATUS_FURTHER_ENCRYPTED_TEXT;
                break;
            default:
                break;
        }
        return action_text;
    }
    public static String getReason(String action)
    {
        String[] splitAction = action.split(";");
        if (splitAction.length > 1)
        {
            return splitAction[1];
        }
        return "";
    }
    public static int actionFromText(String action)
    {
        int act = -1;

        // some controls have reason
        String splitAction = action.split(";")[0];

        switch (splitAction) {
            case CONTROL_ACTION_CONNECT_TEXT:
                act = CONTROL_ACTION_CONNECT;
                break;
            case CONTROL_ACTION_KEEPALIVE_TEXT:
                act = CONTROL_ACTION_KEEPALIVE;
                break;
            case CONTROL_ACTION_DISCONNECT_TEXT:
                act = CONTROL_ACTION_DISCONNECT;
                break;
            case CONTROL_ACTION_DISCONNECTED_TEXT:
                act = CONTROL_ACTION_DISCONNECTED;
                break;
            case CONTROL_ACTION_GET_PARAMETERS_TEXT:
                act = CONTROL_ACTION_GET_PARAMETERS;
                break;
            case CONTROL_ACTION_CONFIGURED_TEXT:
                act = CONTROL_ACTION_CONFIGURED;
                break;
            case CONTROL_ACTION_VERIFY_SUBSCRIPTION_TOKEN_TEXT:
                act = CONTROL_ACTION_VERIFY_SUBSCRIPTION_TOKEN;
                break;
            case CONTROL_ACTION_GET_PUBLIC_KEY_TEXT:
                act = CONTROL_ACTION_GET_PUBLIC_KEY;
                break;
            case CONTROL_ACTION_USE_PUBLIC_KEY_TEXT:
                act = CONTROL_ACTION_USE_PUBLIC_KEY;
                break;
            case CONTROL_ACTION_USE_SESSION_KEY_TEXT:
                act = CONTROL_ACTION_USE_SESSION_KEY;
                break;
            case CONTROL_ACTION_USE_SESSION_IV_TEXT:
                act = CONTROL_ACTION_USE_SESSION_IV;
                break;
            case STATUS_FURTHER_ENCRYPTED_TEXT:
                act = STATUS_FURTHER_ENCRYPTED;
                break;
            default:
                break;
        }
        return act;
    }

    public interface onGetStatsInterface {
        void onGetStatsReady(List<ServerDataModel> servers);
        void onGetStatsError(VolleyError error);
    }
    public static class JustvpnSettings implements Serializable
    {
        public Boolean mIsEcnryptionEnabled = false;
        public Set<Pair<String, String>> mLocations;
    }
    public static class ServerDataModel implements Serializable
    {
        public int mId = -1;
        public String sIp;
        public String sCountry;
        public boolean bActive = false;
        public int mCapacity = -1;
        public int mConnNumber = -1;

        @Override
        public int hashCode() {
            // return Objects.hash(mId, sIp, sCountry, bActive, mCapacity, mConnNumber);
            return Objects.hash(mId, sIp);
        }
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ServerDataModel other = (ServerDataModel) o;

            // Compare relevant fields for equality
            /* return mId == other.mId &&
                    Objects.equals(sIp, other.sIp) &&
                    Objects.equals(sCountry, other.sCountry) &&
                    Objects.equals(bActive, other.bActive) &&
                    mCapacity == other.mCapacity &&
                    mConnNumber== other.mConnNumber; */
            return mId == other.mId &&
                    Objects.equals(sIp, other.sIp);
        }
    }


    public void getStats(Context context, onGetStatsInterface callback)
    {
        if (context == null)
        {
            return;
        }
        ArrayList<ServerDataModel> stats = new ArrayList<>();

        RequestQueue queue = Volley.newRequestQueue(context);
        String get_stats = "http://justvpn.online/api/getstats";
        StringRequest signalRequest = new StringRequest(Request.Method.GET, get_stats,
                resp ->
                {
                    try
                    {
                        JSONArray jArray = new JSONArray(resp);
                        for (int i = 0; i < jArray.length(); i++)
                        {
                            ServerDataModel stat = new ServerDataModel();
                            JSONObject jsonObject = jArray.getJSONObject(i);
                            if (jsonObject.has("id") &&
                                jsonObject.has("ip") &&
                                jsonObject.has("country") &&
                                jsonObject.has("active") &&
                                jsonObject.has("capacity") &&
                                jsonObject.has("conn_number"))
                            {
                                stat.mId = jsonObject.getInt("id");
                                stat.sIp = jsonObject.getString("ip");
                                stat.sCountry = jsonObject.getString("country");
                                stat.bActive = jsonObject.getBoolean("active");
                                stat.mCapacity = jsonObject.getInt("capacity");
                                stat.mConnNumber = jsonObject.getInt("conn_number");
                            }
                            stats.add(stat);
                        }

                        callback.onGetStatsReady(stats);
                    }
                    catch (JSONException e)
                    {
                        e.printStackTrace();
                    }
                }
                , callback::onGetStatsError);

        queue.add(signalRequest);
        queue.start();
    }
}
