package com.example.itsatrap.app;

import android.content.Context;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import com.google.android.gms.maps.model.LatLng;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by maegereg on 5/10/14.
 */
public class GameController
{

    private final String serverAddress = "http://192.168.1.140:3000";

    private User curUser;
    private final List<Plantable> userPlantables = new ArrayList<Plantable>();
    private final List<Plantable> enemyPlantables = new ArrayList<Plantable>();
    private final List<PlayerInfo> highScores = new ArrayList<PlayerInfo>();

    private LatLng lastRegisteredLocation;

    private int maxPlantables = 12;

    public GameController(User curUser, LocationManager locManager)
    {
        this.curUser = curUser;

        Location curLocation = locManager.getLastKnownLocation(locManager.getBestProvider(new Criteria(), true));
        LatLng curLoc = new LatLng(curLocation.getLatitude(), curLocation.getLongitude());
        getHighScoresAndMinesFromServer(curLoc, null);

        // Dummy mines for testing
        userPlantables.add(new Plantable("0", "3", new LatLng(44.456799, -93.156410), new Date(), 100, 15));
        userPlantables.add(new Plantable("1", "3", new LatLng(44.459832, -93.151389), new Date(), 100, 15));

    }

    public List<PlayerInfo> getHighScores()
    {
        return highScores;
    }

    public List<Plantable> getUserPlantables()
    {
        return userPlantables;
    }

    /**
     * Perform the necessary updates given that the user is now at the given location
     * @param curLoc
     */
    public void updateLocation(LatLng curLoc, ArrayAdapter toUpdate)
    {
        //Currently we'll update the server's value for the location every time we've moved five miles.
        if ( lastRegisteredLocation != null && distanceBetween(curLoc, lastRegisteredLocation) > 8046.72)
        {
            getHighScoresAndMinesFromServer(curLoc, toUpdate);
        }

        List<Plantable> possiblyExplodeds = checkForCollisions(curLoc);
        for (Plantable toExplode : possiblyExplodeds)
        {
            attemptToExplodePlantable(toExplode);
        }

    }


    /*
        Returns a list of all enemy plantables within their radius of the provided location - used for detecting explosions
     */
    public List<Plantable> checkForCollisions(LatLng currentLocation)
    {
        synchronized (enemyPlantables)
        {
            List<Plantable> results = new ArrayList<Plantable>();
            for (int i = 0; i<enemyPlantables.size(); ++i)
            {
                LatLng otherLocation = enemyPlantables.get(i).getLocation();
                if (distanceBetween(currentLocation, otherLocation) < enemyPlantables.get(i).getRadius())
                {
                    results.add(enemyPlantables.get(i));
                }
            }
            return results;
        }
    }

    /*
        Returns a list of all enemy plantables within the given radius of the given location - used for sweeping
     */
    public List<Plantable> getEnemyPlantablesWithinRadius(LatLng currentLocation, float radius)
    {
        synchronized (enemyPlantables)
        {
            List<Plantable> results = new ArrayList<Plantable>();
            for (int i = 0; i<enemyPlantables.size(); ++i)
            {
                LatLng otherLocation = enemyPlantables.get(i).getLocation();

                if (distanceBetween(currentLocation, otherLocation) < radius)
                {
                    results.add(enemyPlantables.get(i));
                }
            }
            return results;
        }
    }

    //TODO: Do something about all the errors this might throw
    private void getHighScoresAndMinesFromServer(LatLng curLoc, final ArrayAdapter toUpdate)
    {
        //Construct JSON object to send to server
        JSONObject toSend = new JSONObject();
        try {
            JSONObject loc = new JSONObject();
            loc.put("lat", curLoc.latitude);
            loc.put("lon", curLoc.longitude);
            toSend.put("location", loc);
            //TODO: Possibly change to id?
            toSend.put("user", curUser.getId());
        } catch (JSONException e) {
            e.printStackTrace();
        }

        //Make request - use an async task

        class PostLocationTask extends AsyncTask<JSONObject, Void, Void>
        {

            @Override
            protected Void doInBackground(JSONObject... jsonObjects) {
                HttpURLConnection connection = null;
                String response = "";
                //Make the web request to fetch new data
                try {
                    HttpClient client = new DefaultHttpClient();
                    HttpPost request = new HttpPost(serverAddress+"/api/changearea");
                    request.setHeader("Content-Type", "application/json");
                    request.setEntity(new StringEntity(jsonObjects[0].toString()));
                    response = getStreamContent(client.execute(request).getEntity().getContent());

                } catch (MalformedURLException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                finally {
                    if (connection != null)
                    {
                        connection.disconnect();
                    }
                }

                //Parse the data
                try {
                    JSONObject responseObject = new JSONObject(response);
                    JSONArray plantables = responseObject.getJSONArray("mines");
                    JSONArray scores = responseObject.getJSONArray("scores");

                    synchronized (enemyPlantables)
                    {
                        enemyPlantables.clear();
                        for (int i = 0; i < plantables.length(); ++i)
                        {
                            enemyPlantables.add(new Plantable(plantables.getJSONObject(i)));
                        }
                    }
                    synchronized (highScores)
                    {
                        highScores.clear();
                        for (int i = 0; i < scores.length(); ++i)
                        {
                            highScores.add(new PlayerInfo(scores.getJSONObject(i)));
                        }
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }

                return null;
            }

            @Override
            protected void onPostExecute(Void v)
            {
                if (toUpdate != null)
                    toUpdate.notifyDataSetChanged();
            }

        }

        new PostLocationTask().execute(toSend);


        //Server magic goes here
//        PlayerInfo[] hardCodedEntries = {new PlayerInfo("Jeff", 9001) , new PlayerInfo("DSM", 6), new PlayerInfo("Calder", 6), new PlayerInfo("Carissa", 6), new PlayerInfo("DermDerm", 5), new PlayerInfo("Tao", 5), new PlayerInfo("Carlton", 5), new PlayerInfo("Quinn", 5)};
//        highScores = Arrays.asList(hardCodedEntries);
//        enemyPlantables = new ArrayList<Plantable>();
        lastRegisteredLocation = curLoc;
    }

    //Stub
    private void attemptToExplodePlantable(Plantable toExplode)
    {

    }


    //Stub
    public int getNumUserPlantablesLeft()
    {
        return maxPlantables - userPlantables.size();
    }

    public void addUserPlantable(LatLng newLoc)
    {
        //These values should change...
        userPlantables.add(new Plantable("0", "0", newLoc, new Date(), 10000, 15));
        //Construct JSON object to send to server
        JSONObject toSend = new JSONObject();
        try {
            JSONObject loc = new JSONObject();
            loc.put("lat", newLoc.latitude);
            loc.put("lon", newLoc.longitude);
            toSend.put("location", loc);
            //TODO: Possibly change to id?
            toSend.put("user", curUser.getId());
        } catch (JSONException e) {
            e.printStackTrace();
        }

        //Make request - use an async task
        class PostLocationTask extends AsyncTask<JSONObject, Void, Void>
        {
            @Override
            protected Void doInBackground(JSONObject... jsonObjects) {
                HttpURLConnection connection = null;
                String response = "";
                //Make the web request to fetch new data
                try {
                    // /placemine - {location: {lat:___, lon:___}, user:___}  --> true if successful, false otherwise
                    HttpClient client = new DefaultHttpClient();
                    HttpPost request = new HttpPost(serverAddress+"/api/plantmine");
                    request.setHeader("Content-Type", "application/json");
                    request.setEntity(new StringEntity(jsonObjects[0].toString()));
                    response = getStreamContent(client.execute(request).getEntity().getContent());

                } catch (MalformedURLException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                finally {
                    if (connection != null)
                    {
                        connection.disconnect();
                    }
                }

                // TODO: Handle it returning false, which it shouldn't do
                if (!response.equals("true")) {
                    System.out.println("Problem with server planting trap");
                }
                return null;
            }

            @Override
            protected void onPostExecute(Void v)
            {
                // TODO: Notify GUI that myMines list has updated
//                if (toUpdate != null)
//                    toUpdate.notifyDataSetChanged();
            }

        }
        new PostLocationTask().execute(toSend);
    }

    //Stub
    public void removeUserPlantable(Plantable toRemove)
    {
        userPlantables.remove(toRemove);
    }

    //Stub
    public void removeUserPlantable(String idToRemove)
    {
        for (int i = 0; i<userPlantables.size(); ++i)
        {
            if (userPlantables.get(i).getPlantableId() == idToRemove)
            {
                userPlantables.remove(i);
                return;
            }
        }
    }


    public static float distanceBetween(LatLng firstLoc, LatLng secondLoc)
    {
        float[] distance = new float[1];
        Location.distanceBetween(firstLoc.latitude, firstLoc.longitude, secondLoc.latitude, secondLoc.longitude, distance);
        return distance[0];
    }

    public static String getStreamContent(InputStream stream)
    {
        StringBuilder builder = new StringBuilder();
        int c;
        try {
            while ((c = stream.read()) != -1 )
            {
                builder.append((char)c);
            }

        } catch (IOException e) {
        }
        return builder.toString();
    }

}
