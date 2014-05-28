package com.example.itsatrap.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.location.Criteria;
import android.location.LocationListener;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.widget.DrawerLayout;
import android.location.Location;
import android.location.LocationManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.*;
import com.google.android.gms.maps.model.*;

import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class MapActivity extends Activity implements GoogleMap.OnMapClickListener,
        GoogleMap.OnInfoWindowClickListener, LocationListener, GoogleMap.OnMarkerClickListener
{

    private DrawerLayout drawerLayout;
    private ListView drawerList;
    private ArrayAdapter listAdapter;

    private GoogleMap map;
    private GameController gameController;
    private Marker plantableToPlace;
    private boolean removingPlantable;
    private HashMap<Marker, Plantable> markerData;

    private SharedPreferences sharedPrefs;

    private List<Marker> currentlyDisplayedEnemyPlantables;

    private Date lastSweeped;
    private List<Marker> sweepMinesVisible;

    private MapActivity thisref;

    private LatLng lastLocation;

    //The sweep cooldown, in minutes
    private final int SWEEP_COOLDOWN = 30;
    //The amount of time sweeped mines should be visible, in seconds
    private final int SWEEP_DURATION = 30;
    //The radius of the sweep, in meters
    private final int SWEEP_RADIUS = 1000;
    //Register a new location with the server after travelling this far (currently 5 miles)
    private final double UPDATE_DISTANCE = 8046.72;

    //TODO: Remove this; it's just for testing.
//    private final String serverAddress = "http://137.22.164.195:3000";
    public static final String serverAddress = "http://107.170.182.13:3000";

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);

        //Initialize internal state information
        currentlyDisplayedEnemyPlantables = new ArrayList<Marker>();
        sweepMinesVisible = new ArrayList<Marker>();

        plantableToPlace = null;
        removingPlantable = false;

        thisref = this;

        sharedPrefs = getSharedPreferences(getString(R.string.SharedPrefName), 0);

        //Create the game controller object
        gameController = new GameController(new User(sharedPrefs.getString(getString(R.string.PrefsEmailString), ""), sharedPrefs.getString(getString(R.string.PrefsIdString), ""), ""), (LocationManager) getSystemService(Context.LOCATION_SERVICE), this);

        drawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawerList = (ListView) findViewById(R.id.left_drawer);

        // Set the adapter for the list view
        listAdapter = new ScoreArrayAdapter(this, R.layout.drawer_list_item, gameController.getHighScores());
        drawerList.setAdapter(listAdapter);
        // Get a handle to the Map Fragment
        map = ((MapFragment) getFragmentManager()
                .findFragmentById(R.id.map)).getMap();

        markerData = new HashMap<Marker, Plantable>();

        if  (map != null)
        {
            LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            LatLng curLoc = getCurLatLng();

            map.setMyLocationEnabled(true);
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(curLoc, 13));

            //Set the listener
            map.setOnMapClickListener(this);
            map.setOnInfoWindowClickListener(this);
            map.setOnMarkerClickListener(this);

            //Set this activity to listen for location changes
            Criteria criteria = new Criteria();
            criteria.setAccuracy(Criteria.ACCURACY_FINE);
            locationManager.requestLocationUpdates(1000, 1, criteria, this, null);
        }

        //Do the tutorial
        AlertDialog.Builder instructions = new AlertDialog.Builder(this);
        instructions.setTitle("Instructions");
        instructions.setMessage("Welcome to It's a trap. Your goal is to plant traps that nearby players will walk over. Click on the map to place a trap - you can place up to 12. You can sweep to discover enemy traps. You will be notified if you walk over an enemy trap. Swipe from the left side of the screen to view high scores.");
        instructions.setPositiveButton("Ok", null);
        instructions.show();

        updateLocation(getCurLatLng());
    }

    /*
    ---------------- Override event listeners
     */

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.map, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onMapClick(LatLng latLng)
    {
        // If you're out of plantable items, don't let them do it
        if (gameController.getNumUserPlantablesLeft() <= 0)
        {
            Toast.makeText(this, "No traps left", Toast.LENGTH_SHORT).show();
        }
        else
        {
            if (removingPlantable)
            {
                removingPlantable = false;
            }
            else if (plantableToPlace == null)
            {
                plantableToPlace = map.addMarker(new MarkerOptions()
                        .position(latLng)
                        .title(getString(R.string.placeTrap))
                        .alpha((float) 0.4)
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)));
                plantableToPlace.showInfoWindow();
            }
            else
            {
                plantableToPlace.remove();
                plantableToPlace = null;
            }
        }
    }

    @Override
    public void onInfoWindowClick(Marker marker)
    {
        // If you're placing a trap
        if (plantableToPlace != null)
        {
            addUserPlantable(marker);
        }

        // If you're removing a trap
        else
        {
            if (marker.getTitle().equals(getString(R.string.yourTrap)))
            {
                Plantable plantableToRemove = markerData.get(marker);
                markerData.remove(marker);
                marker.remove();
                removeUserPlantable(plantableToRemove);
                ((TextView) findViewById(R.id.your_plantable_count))
                        .setText(String.valueOf(gameController.getNumUserPlantablesLeft())+"\ntraps left");
                removingPlantable = false;
            }
        }
    }

    @Override
    public boolean onMarkerClick(Marker marker)
    {
            removingPlantable = true;
            if (plantableToPlace != null)
            {
                plantableToPlace.remove();
                plantableToPlace = null;
            }
            marker.showInfoWindow();
            return true;
    }

    @Override
    public void onLocationChanged(Location location) {
        updateLocation(new LatLng(location.getLatitude(), location.getLongitude()));
    }

    @Override
    public void onStatusChanged(String s, int i, Bundle bundle) {

    }

    @Override
    public void onProviderEnabled(String s) {

    }

    @Override
    public void onProviderDisabled(String s) {

    }

    /*
    ---------------- UI Update Methods
     */

    public void updateHighScores()
    {
        listAdapter.notifyDataSetChanged();
    }

    public void updateMyMines()
    {
        for (Marker marker : markerData.keySet())
        {
            marker.remove();
        }

        markerData.clear();

        List<Plantable> myPlantables = gameController.getUserPlantables();
        synchronized (myPlantables)
        {
            //Add map markers for previously set mines
            for (int i = 0; i < myPlantables.size(); ++i) {
                markerData.put(map.addMarker(new MarkerOptions()
                                .position(myPlantables.get(i).getLocation())
                                .title(getString(R.string.yourTrap))
                                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))),
                        myPlantables.get(i)
                );
            }
            ((TextView) findViewById(R.id.your_plantable_count))
                    .setText(String.valueOf(gameController.getNumUserPlantablesLeft()) + "\ntraps left");
        }
    }

    public void removeSweepedMines()
    {
        for (Marker sweepMine : sweepMinesVisible)
        {
            sweepMine.remove();
        }
    }

    public void displayExploded()
    {
        Toast.makeText(this, "You have been trapped.", Toast.LENGTH_SHORT).show();
    }

    /*
    ---------------- Game logic methods
     */

    public void sweep(View view)
    {
        if (lastSweeped != null && new Date().getTime() - lastSweeped.getTime() < 1000*60*SWEEP_COOLDOWN)
        {
            long minutesLeft = (SWEEP_COOLDOWN*60*1000 - (new Date().getTime() - lastSweeped.getTime()))/1000/60 + 1;
            Toast.makeText(this, "Can't sweep again for "+minutesLeft+" minutes.", Toast.LENGTH_SHORT).show();
            return;
        }

        List<Plantable> enemyTraps = gameController.getEnemyPlantablesWithinRadius(getCurLatLng(), SWEEP_RADIUS);
        // TODO: make swept enemy traps disappear eventually
        // (and what if they are triggered before disappearing?)
        for (Plantable plantable : enemyTraps)
        {
            Marker marker = map.addMarker(new MarkerOptions()
                    .position(plantable.getLocation())
                    .title(getString(R.string.watchOut))
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));
            sweepMinesVisible.add(marker);
        }

        lastSweeped = new Date();

        Timer sweepTimer = new Timer(true);
        Date afterSweepDuration = new Date();
        afterSweepDuration.setTime(afterSweepDuration.getTime()+1000*SWEEP_DURATION);

        sweepTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                thisref.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        thisref.removeSweepedMines();
                    }
                });
            }
        }, afterSweepDuration);
    }

    /**
     * Handles calling the changelocation method on the server
     * @param curLoc
     */
    public void updateLocation(final LatLng curLoc)
    {
        if (lastLocation == null || GameController.distanceBetween(lastLocation, curLoc) < UPDATE_DISTANCE)
        {
            lastLocation = curLoc;

            //Construct JSON object to send to server
            JSONObject toSend = new JSONObject();
            try {
                JSONObject loc = new JSONObject();
                loc.put("lat", curLoc.latitude);
                loc.put("lon", curLoc.longitude);
                toSend.put("location", loc);
                toSend.put("user", gameController.getUser().getId());
            } catch (JSONException e) {
                e.printStackTrace();
            }

            class PostLocationTask extends PostJsonTask<Void>
            {

                public PostLocationTask(String serverAddress, String endpoint) {
                    super(serverAddress, endpoint);
                }

                @Override
                protected Void parseResponse(String response) {
                    //Parse the data
                    try {
                        JSONObject responseObject = new JSONObject(response);
                        JSONArray plantables = responseObject.getJSONArray("mines");
                        JSONArray scores = responseObject.getJSONArray("scores");
                        JSONArray myPlantables = responseObject.getJSONArray("myMines");

                        synchronized (gameController.getEnemyPlantables())
                        {
                            gameController.getEnemyPlantables().clear();
                            for (int i = 0; i < plantables.length(); ++i)
                            {
                                gameController.getEnemyPlantables().add(new Plantable(plantables.getJSONObject(i)));
                            }
                        }
                        synchronized (gameController.getUserPlantables())
                        {
                            gameController.getUserPlantables().clear();
                            for (int i = 0; i < myPlantables.length(); ++i)
                            {
                                gameController.getUserPlantables().add(new Plantable(myPlantables.getJSONObject(i)));
                            }
                        }
                        synchronized (gameController.getHighScores())
                        {
                            gameController.getHighScores().clear();
                            for (int i = 0; i < scores.length(); ++i)
                            {
                                gameController.getHighScores().add(new PlayerInfo(scores.getJSONObject(i)));
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
                    updateHighScores();
                    updateMyMines();
                    checkForCollisions(curLoc);
                }
            }

            new PostLocationTask(serverAddress, "/api/changearea").execute(toSend);
        }
        else
            checkForCollisions(curLoc);
    }

    /**
     * Checks whether the current location trips any other users' mines, and sends requests to the
     * server for those mines
     * @param curLoc
     */
    public void checkForCollisions(LatLng curLoc)
    {
        List<Plantable> possibleCollisions = gameController.checkForCollisions(curLoc);
        for (final Plantable toExplode : possibleCollisions)
        {
            //Construct JSON object to send to server
            JSONObject toSend = new JSONObject();
            try {
                toSend.put("user", gameController.getUser().getId());
                toSend.put("id", toExplode.getPlantableId());
            } catch (JSONException e) {
                e.printStackTrace();
            }

            class PostExplodeTask extends PostJsonTask<Boolean>
            {
                public PostExplodeTask(String serverAddress, String endpoint) {
                    super(serverAddress, endpoint);
                }

                @Override
                protected Boolean parseResponse(String response) {
                    if (response.equals("true"))
                        return new Boolean(true);
                    else
                        return new Boolean(false);
                }

                @Override
                protected void onPostExecute(Boolean exploded)
                {
                    if (exploded)
                    {
                        displayExploded();
                        gameController.removeEnemyPlantable(toExplode);
                    }
                }
            }
            new PostExplodeTask(serverAddress, "/api/explodemine").execute(toSend);
        }
    }

    public void addUserPlantable(final Marker marker)
    {
        marker.setAlpha(1);
        marker.setDraggable(false);
        marker.setTitle(getString(R.string.yourTrap));
        marker.hideInfoWindow();
        plantableToPlace = null;
        //Construct JSON object to send to server
        JSONObject toSend = new JSONObject();
        try {
            JSONObject loc = new JSONObject();
            loc.put("lat", marker.getPosition().latitude);
            loc.put("lon", marker.getPosition().longitude);
            toSend.put("location", loc);
            toSend.put("user", gameController.getUser().getId());
        } catch (JSONException e) {
            e.printStackTrace();
        }

        //Make request - use an async task
        class AddPlantableTask extends PostJsonTask<Plantable>
        {
            public AddPlantableTask(String serverAddress, String endpoint) {
                super(serverAddress, endpoint);
            }

            @Override
            protected void onPostExecute(Plantable toAdd)
            {
                if (toAdd != null)
                {
                    gameController.addUserPlantable(toAdd);
                    markerData.put(marker, toAdd);
                    ((TextView) findViewById(R.id.your_plantable_count))
                            .setText(String.valueOf(gameController.getNumUserPlantablesLeft()) + "\ntraps left");
                }
                else
                {
                    if (plantableToPlace == null)
                    {
                        marker.setAlpha((float) 0.5);
                        marker.setDraggable(true);
                        marker.setTitle(getString(R.string.placeTrap));
                        marker.showInfoWindow();
                        plantableToPlace = marker;
                    }
                    else
                    {
                        //TODO: This behavior probably isn't ideal - we should rethink it
                        marker.remove();
                    }
                }
            }

            @Override
            protected Plantable parseResponse(String response) {
                try {
                    return new Plantable(new JSONObject(response));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                return null;
            }
        }
        new AddPlantableTask(serverAddress, "/api/placemine").execute(toSend);
    }

    public void removeUserPlantable(final Plantable toRemove)
    {
        //Construct JSON object to send to server
        JSONObject toSend = new JSONObject();
        try {
            toSend.put("id", toRemove.getPlantableId());
        } catch (JSONException e) {
            e.printStackTrace();
        }

        //Make request - use an async task
        class RemovePlantableTask extends PostJsonTask<Boolean>
        {

            public RemovePlantableTask(String serverAddress, String endpoint) {
                super(serverAddress, endpoint);
            }

            @Override
            protected Boolean parseResponse(String response) {
                if (response.equals("true"))
                    return new Boolean(true);
                else
                    return new Boolean(false);
            }

            @Override
            protected void onPostExecute(Boolean success)
            {
                //If we didn't succeed, we need to put it back on the map
                if (!success)
                {
                    markerData.put(map.addMarker(new MarkerOptions()
                            .position(toRemove.getLocation())
                            .title(getString(R.string.yourTrap))
                            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))),
                    toRemove);
                }
                //Update the data structure for success
                else
                {
                    gameController.removeUserPlantable(toRemove);
                    ((TextView) findViewById(R.id.your_plantable_count))
                            .setText(String.valueOf(gameController.getNumUserPlantablesLeft()) + "\ntraps left");
                }
            }

        }
        new RemovePlantableTask(serverAddress, "/api/removemine").execute(toSend);
    }


    /*
    ---------------- Helper methods
     */

    private LatLng getCurLatLng()
    {
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        Location curLocation = locationManager.getLastKnownLocation(locationManager.getBestProvider(new Criteria(), true));
        LatLng curLoc = new LatLng(curLocation.getLatitude(), curLocation.getLongitude());
        return curLoc;
    }



}
