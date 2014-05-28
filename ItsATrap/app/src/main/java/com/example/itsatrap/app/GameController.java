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
    }

    public List<PlayerInfo> getHighScores()
    {
        return highScores;
    }

    public List<Plantable> getUserPlantables()
    {
        return userPlantables;
    }

    public List<Plantable> getEnemyPlantables() { return enemyPlantables; }

    public User getUser() { return curUser; }

    public void removeEnemyPlantable(Plantable toRemove)
    {
        synchronized (enemyPlantables)
        {
            enemyPlantables.remove(toRemove);
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

    //Stub
    public int getNumUserPlantablesLeft()
    {
        synchronized (userPlantables)
        {
            return maxPlantables - userPlantables.size();
        }
    }

    public void addUserPlantable(Plantable toAdd)
    {
        synchronized (userPlantables)
        {
            userPlantables.add(toAdd);
        }
    }

    //Stub
    public void removeUserPlantable(Plantable toRemove)
    {
        synchronized (userPlantables)
        {
            userPlantables.remove(toRemove);
        }
    }

    public static float distanceBetween(LatLng firstLoc, LatLng secondLoc)
    {
        float[] distance = new float[1];
        Location.distanceBetween(firstLoc.latitude, firstLoc.longitude, secondLoc.latitude, secondLoc.longitude, distance);
        return distance[0];
    }



}
