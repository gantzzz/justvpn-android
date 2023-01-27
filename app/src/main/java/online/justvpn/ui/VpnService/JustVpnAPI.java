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
                        /*TimeUnit.SECONDS.sleep(5);*/
                        callback.onServersDownloadReady(servers);
                    }
                    catch (JSONException e)
                    {
                        e.printStackTrace();
                    }
                }
                , error ->
                {
                    // restart
                    Log.w("JustVpn API error: ", error);

                });

        queue.add(signalRequest);
        queue.start();
    }
}
