package com.example.itsatrap.app;

import android.os.AsyncTask;

import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;

/**
 * Created by maegereg on 5/27/14.
 * Abstract class representing a post request to the backend with a JSON content
 * The response will be passed to the parseResponse method, which should be implemented by subclasses
 * parseResponse should return any data that needs to be passed to onPostExecute, which will be of type T
 * Users are also responsible for overriding onPostExecute
 */
public abstract class PostJsonTask<T> extends AsyncTask<JSONObject, Void, T>{
    private String serverAddress;
    private String endpoint;

    public PostJsonTask(String serverAddress, String endpoint){
        this.serverAddress = serverAddress;
        this.endpoint = endpoint;
    }

    @Override
    protected T doInBackground(JSONObject... jsonObjects) {
        HttpURLConnection connection = null;
        String response = "";
        //Make the web request to fetch new data
        try {
            HttpClient client = new DefaultHttpClient();
            HttpPost request = new HttpPost(serverAddress+endpoint);
            request.setHeader("Content-Type", "application/json");
            request.setEntity(new StringEntity(jsonObjects[0].toString()));
            response = getStreamContent(client.execute(request).getEntity().getContent());

        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        finally {
            if (connection != null){
                connection.disconnect();
            }
        }


        return parseResponse(response);
    }

    public static String getStreamContent(InputStream stream){
        StringBuilder builder = new StringBuilder();
        int c;
        try {
            while ((c = stream.read()) != -1 ) {
                builder.append((char)c);
            }

        } catch (IOException e) {
        }
        return builder.toString();
    }

    protected abstract T parseResponse(String response);

}
