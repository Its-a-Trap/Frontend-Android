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

    //TODO: Remove this; it's just for testing.
    private final String serverAddress = "http://137.22.164.195:3000";
    private final String realServerAddress = "http://107.170.182.13:3000";

    private User curUser;
    private final List<Plantable> userPlantables = new ArrayList<Plantable>();
    private final List<Plantable> enemyPlantables = new ArrayList<Plantable>();
    private final List<PlayerInfo> highScores = new ArrayList<PlayerInfo>();

    private LatLng lastRegisteredLocation;

    //We need a reference back to the mapActivity so the async tasks can call methods on it
    //TODO: refactor so that the mapactivity handles network connections
    private MapActivity mapActivity;

    private int maxPlantables = 12;

    public GameController(User curUser, LocationManager locManager, MapActivity mapActivity)

    {
        this.curUser = curUser;
        this.mapActivity = mapActivity;

        Location curLocation = locManager.getLastKnownLocation(locManager.getBestProvider(new Criteria(), true));
        LatLng curLoc = new LatLng(curLocation.getLatitude(), curLocation.getLongitude());
        getHighScoresAndMinesFromServer(curLoc);

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
    public void updateLocation(LatLng curLoc)
    {
        //Currently we'll update the server's value for the location every time we've moved five miles.
        if ( lastRegisteredLocation != null && distanceBetween(curLoc, lastRegisteredLocation) > 8046.72)
        {
            getHighScoresAndMinesFromServer(curLoc);
        }
    }

    public void collideWithEnemyMines(LatLng curLoc)
    {
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
            //TODO: Remove this testing if statement
            if (results.size() <= 0 && enemyPlantables.size() > 0)
            {
                results.add(enemyPlantables.get(0));
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
    private void getHighScoresAndMinesFromServer(final LatLng curLoc)
    {
        //Construct JSON object to send to server
        JSONObject toSend = new JSONObject();
        try {
            JSONObject loc = new JSONObject();
            loc.put("lat", curLoc.latitude);
            loc.put("lon", curLoc.longitude);
            toSend.put("location", loc);
            toSend.put("user", curUser.getId());
        } catch (JSONException e) {
            e.printStackTrace();
        }

        //Make request - use an async task

        class PostLocationTask extends AsyncTask<JSONObject, Void, Void>
        {
            private MapActivity mapActivity;

            public PostLocationTask(MapActivity mapActivity)
            {
                this.mapActivity = mapActivity;
            }


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
                    JSONArray myPlantables = responseObject.getJSONArray("myMines");

                    synchronized (enemyPlantables)
                    {
                        enemyPlantables.clear();
                        for (int i = 0; i < plantables.length(); ++i)
                        {
                            enemyPlantables.add(new Plantable(plantables.getJSONObject(i)));
                        }
                    }
                    synchronized (userPlantables)
                    {
                        userPlantables.clear();
                        for (int i = 0; i < myPlantables.length(); ++i)
                        {
                            userPlantables.add(new Plantable(myPlantables.getJSONObject(i)));
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
                mapActivity.updateHighScores();
                mapActivity.updateMyMines();
                collideWithEnemyMines(curLoc);
            }

        }

        new PostLocationTask(mapActivity).execute(toSend);


        //Server magic goes here
//        PlayerInfo[] hardCodedEntries = {new PlayerInfo("Jeff", 9001) , new PlayerInfo("DSM", 6), new PlayerInfo("Calder", 6), new PlayerInfo("Carissa", 6), new PlayerInfo("DermDerm", 5), new PlayerInfo("Tao", 5), new PlayerInfo("Carlton", 5), new PlayerInfo("Quinn", 5)};
//        highScores = Arrays.asList(hardCodedEntries);
//        enemyPlantables = new ArrayList<Plantable>();
        lastRegisteredLocation = curLoc;
    }

    //Stub
    private void attemptToExplodePlantable(final Plantable toExplode)
    {
        //Construct JSON object to send to server
        JSONObject toSend = new JSONObject();
        try {
            toSend.put("user", curUser.getId());
            toSend.put("id", toExplode.getPlantableId());
        } catch (JSONException e) {
            e.printStackTrace();
        }

        //Make request - use an async task

        class PostExplodeTask extends AsyncTask<JSONObject, Void, Boolean>
        {
            private MapActivity mapActivity;

            public PostExplodeTask(MapActivity mapActivity)
            {
                this.mapActivity = mapActivity;
            }


            @Override
            protected Boolean doInBackground(JSONObject... jsonObjects) {
                HttpURLConnection connection = null;
                String response = "";
                //Make the web request to fetch new data
                try {
                    HttpClient client = new DefaultHttpClient();
                    HttpPost request = new HttpPost(serverAddress+"/api/explodemine");
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

                //'Parse' the data
                return response.equals("true");
            }

            @Override
            protected void onPostExecute(Boolean exploded)
            {
                if (exploded)
                {
                    mapActivity.displayExploded();
                    enemyPlantables.remove(toExplode);
                }
            }

        }

        new PostExplodeTask(mapActivity).execute(toSend);
    }


    //Stub
    public int getNumUserPlantablesLeft()
    {
        return maxPlantables - userPlantables.size();
    }

    public Plantable addUserPlantable(LatLng newLoc)
    {
        //These values should change...
        Plantable newPlantable = new Plantable("0", "0", newLoc, new Date(), 10000, 15);
        synchronized (userPlantables)
        {
            userPlantables.add(newPlantable);
        }
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
                    HttpPost request = new HttpPost(serverAddress+"/api/placemine");
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

                // TODO: Handle it returning unsuccessfully (response = ""), which it shouldn't do
                if (response.equals("false")) {
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
        return newPlantable;
    }

    //Stub
    public void removeUserPlantable(Plantable toRemove)
    {
        synchronized (userPlantables)
        {
            userPlantables.remove(toRemove);
        }
        //Construct JSON object to send to server
        JSONObject toSend = new JSONObject();
        try {
            toSend.put("id", toRemove.getPlantableId());
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
                    // removemine
                    HttpClient client = new DefaultHttpClient();
                    HttpPost request = new HttpPost(serverAddress+"/api/removemine");
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

                // TODO: Handle it returning unsuccessfully (response = ""), which it shouldn't do
                if (response.equals("false")) {
                    System.out.println("Problem with server removing trap");
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
    public void removeUserPlantable(String idToRemove)
    {
        synchronized (userPlantables)
        {
            for (int i = 0; i < userPlantables.size(); ++i) {
                if (userPlantables.get(i).getPlantableId() == idToRemove) {
                    userPlantables.remove(i);
                    return;
                }
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
