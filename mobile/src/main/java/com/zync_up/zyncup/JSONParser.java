package com.zync_up.zyncup;

import android.content.Context;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class JSONParser {

    private final String LOG_TAG = JSONParser.class.getSimpleName();

    public JSONParser() {}

    public JSONObject getLoginObject(Context context, URL url, String requestMethod,
                                              String basicAuth) {

        HttpURLConnection urlConnection;

        try {

            // Create the request to server, and open the connection
            urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setRequestMethod(requestMethod);
            urlConnection.setRequestProperty("ApiKey", context.getString(R.string.api_key));
            urlConnection.setRequestProperty("Content-Type", context.getString(R.string.content_type));
            urlConnection.setRequestProperty("Authorization", basicAuth);

        } catch (IOException e) {

            Log.e(LOG_TAG, "Error ", e);
            e.printStackTrace();
            return null;
        }

        try {

            String bufferString = getBufferString(urlConnection);
            if (bufferString != null) {
                return new JSONObject(bufferString);
            } else {
                return null;
            }

        } catch (JSONException e) {

            Log.e(LOG_TAG, e.getMessage(), e);
            e.printStackTrace();

        }

        return null;
    }

    public JSONObject getRegisterObject(Context context, URL url, String requestMethod,
                                        String basicAuth, JSONObject bodyObject){

        HttpURLConnection urlConnection;

        try {

            urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setRequestMethod(requestMethod);
            urlConnection.setRequestProperty("ApiKey", context.getString(R.string.api_key));
            urlConnection.setRequestProperty("Content-Type", context.getString(R.string.content_type));
            urlConnection.setRequestProperty("Authorization", basicAuth);
            urlConnection.setDoOutput(false);

            //Send request
            DataOutputStream outputStream = new DataOutputStream (urlConnection.getOutputStream());
            outputStream.writeBytes(bodyObject.toString());
            outputStream.flush();
            outputStream.close();

        } catch (IOException e) {

            Log.e(LOG_TAG, "Error ", e);

            return null;

        }

        try {

            String bufferString = getBufferString(urlConnection);
            if (bufferString != null) {
                return new JSONObject(bufferString);
            } else {
                return null;
            }

        } catch (JSONException e) {

            Log.e(LOG_TAG, e.getMessage(), e);
            e.printStackTrace();

        }

        return null;
    }

    private String getBufferString(HttpURLConnection urlConnection) {

        BufferedReader bufferedReader = null;

        try {

            urlConnection.connect();

            InputStream inputStream = urlConnection.getInputStream();
            // Read the input stream into a String
            StringBuilder buffer = new StringBuilder();
            if (inputStream == null) {
                // Nothing to do.
                return null;

            }

            bufferedReader = new BufferedReader(new InputStreamReader(inputStream));

            String line;
            while ((line = bufferedReader.readLine()) != null) {
                // Since it's JSON, adding a newline isn't necessary (it won't affect parsing)
                // But it does make debugging a lot easier
                buffer.append(line).append("\n");
            }

            if (buffer.length() == 0) {
                // Stream was empty.  No point in parsing.
                return null;
            }

            return buffer.toString();

        } catch (IOException e) {
            Log.e(LOG_TAG, e.toString());
            return null;

        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
            if (bufferedReader != null) {
                try {
                    bufferedReader.close();
                } catch (final IOException e) {
                    Log.e(LOG_TAG, "Error closing stream", e);
                }
            }
        }
    }
}
