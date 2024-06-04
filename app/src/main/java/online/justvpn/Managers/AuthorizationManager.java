package online.justvpn.Managers;

import android.content.Context;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONException;
import org.json.JSONObject;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

public class AuthorizationManager {

    private static AuthorizationManager mInstance = null;

    public static AuthorizationManager getInstance() {
        if (mInstance == null) {
            mInstance = new AuthorizationManager();
        }
        return mInstance;
    }

    public boolean Authorize(String email, String password) {
        return true;
    }

    private String sha256(String dataIn) {
        String out = "";

        MessageDigest digest = null;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        byte[] hash = digest.digest(dataIn.getBytes());
        StringBuilder hexString = new StringBuilder();

        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }

            hexString.append(hex);
        }

        out = hexString.toString();
        return out;
    }

    public interface onSigninInterface {
        void onSigninSuccess();
        void onSigninError(String error);
    }

    private void signIn(Context context, onSigninInterface callback, String username, String password)
    {
        if (context == null)
        {
            return;
        }

        String hashedPassword = sha256(password);

        Map<String, String> params = new HashMap<>();

        params.put("hashedPassword", hashedPassword);
        params.put("email", username);

        RequestQueue queue = Volley.newRequestQueue(context);
        String signin_url = "http://justvpn.online:8001/signin";
        StringRequest request = new StringRequest(Request.Method.POST, signin_url,
                resp ->
                {
                    try
                    {
                        JSONObject jsonObject = new JSONObject(resp);
                        if (jsonObject.has("status"))
                        {
                            String status = jsonObject.getString("status");
                            if (status.equals("signinSuccess"))
                            {
                                callback.onSigninSuccess();
                            }
                            else
                            {
                                callback.onSigninError(status);
                            }
                        }
                    }
                    catch (JSONException e)
                    {
                        e.printStackTrace();
                    }
                }
                , error ->
        {
            callback.onSigninError("unknown error");
        }) {
            @Override
            protected Map<String, String> getParams()
            {
                return params;
            }
        };

        queue.add(request);
        queue.start();
    }
}
