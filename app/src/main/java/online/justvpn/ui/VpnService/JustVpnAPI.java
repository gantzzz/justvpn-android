package online.justvpn.ui.VpnService;

import android.content.Context;
import android.util.Log;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class JustVpnAPI {

    // TODO: make this class a singletone as we won't need more than 1 instances of it for HTTP
    static final int CONTROL_ACTION_CONNECT = 0;
    static final int CONTROL_ACTION_KEEPALIVE = 1;
    static final int CONTROL_ACTION_DISCONNECT = 2;
    static final int CONTROL_ACTION_GET_PARAMETERS = 3;
    static final int CONTROL_ACTION_CONFIGURED = 4;
    static final int CONTROL_ACTION_VERIFY_SUBSCRIPTION_TOKEN = 5;

    public static final String CONTROL_ACTION_CONNECT_TEXT = "action:connect";
    public static final String CONTROL_ACTION_KEEPALIVE_TEXT = "action:keepalive";
    public static final String CONTROL_ACTION_DISCONNECT_TEXT = "action:disconnect";
    public static final String CONTROL_ACTION_GET_PARAMETERS_TEXT = "action:getparameters";
    public static final String CONTROL_ACTION_CONFIGURED_TEXT = "action:configured";
    public static final String CONTROL_ACTION_VERIFY_SUBSCRIPTION_TOKEN_TEXT = "action:verifysubscription";

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
            case CONTROL_ACTION_GET_PARAMETERS:
                action_text = CONTROL_ACTION_GET_PARAMETERS_TEXT;
                break;
            case CONTROL_ACTION_CONFIGURED:
                action_text = CONTROL_ACTION_CONFIGURED_TEXT;
                break;
            case CONTROL_ACTION_VERIFY_SUBSCRIPTION_TOKEN:
                action_text = CONTROL_ACTION_VERIFY_SUBSCRIPTION_TOKEN_TEXT;
                break;
            default:
                break;
        }
        return action_text;
    }

    public static int actionFromText(String action)
    {
        int act = -1;
        switch (action) {
            case CONTROL_ACTION_CONNECT_TEXT:
                act = CONTROL_ACTION_CONNECT;
                break;
            case CONTROL_ACTION_KEEPALIVE_TEXT:
                act = CONTROL_ACTION_KEEPALIVE;
                break;
            case CONTROL_ACTION_DISCONNECT_TEXT:
                act = CONTROL_ACTION_DISCONNECT;
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
            default:
                break;
        }
        return act;
    }

    public interface onServersDownloadedInterface {
        void onServersDownloadReady(List<JustVpnAPI.ServerDataModel> servers);
    }
    public static class ServerDataModel
    {
        public int mId = -1;
        public String sIp;
        public String sCountry;
        public boolean bActive = false;
    }
    public void getServers(Context context, onServersDownloadedInterface callback)
    {
        ArrayList<ServerDataModel> servers = new ArrayList<>();

        RequestQueue queue = Volley.newRequestQueue(context);
        String get_servers = "http://justvpn.online/api/getservers";
        StringRequest signalRequest = new StringRequest(Request.Method.GET, get_servers,
                resp ->
                {
                    try
                    {
                        JSONArray jArray = new JSONArray(resp);
                        for (int i = 0; i < jArray.length(); i++)
                        {
                            ServerDataModel server = new ServerDataModel();
                            JSONObject jsonObject = jArray.getJSONObject(i);
                            if (jsonObject.has("id") &&
                                jsonObject.has("ip") &&
                                jsonObject.has("country") &&
                                jsonObject.has("active"))
                            {
                                server.mId = jsonObject.getInt("id");
                                server.sIp = jsonObject.getString("ip");
                                server.sCountry = jsonObject.getString("country");
                                server.bActive = jsonObject.getBoolean("active");
                            }
                            servers.add(server);
                        }

                        callback.onServersDownloadReady(servers);
                    }
                    catch (JSONException e)
                    {
                        e.printStackTrace();
                    }
                }
                , error ->
                {
                    // TODO: Retry? I don't know what to do in this case.
                    Log.w("JustVpn API error: ", error);
                });

        queue.add(signalRequest);
        queue.start();
    }
}
