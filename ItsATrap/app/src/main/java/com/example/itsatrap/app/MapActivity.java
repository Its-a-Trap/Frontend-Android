package com.example.itsatrap.app;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.location.Criteria;
import android.location.LocationListener;
import android.os.Bundle;
import android.support.v4.widget.DrawerLayout;
import android.location.Location;
import android.location.LocationManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import com.google.android.gms.maps.*;
import com.google.android.gms.maps.model.*;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MapActivity extends Activity implements GoogleMap.OnMapClickListener, GoogleMap.OnInfoWindowClickListener, LocationListener
{

    private DrawerLayout drawerLayout;
    private ListView drawerList;
    private ArrayAdapter listAdapter;

    private GoogleMap map;
    private GameController gameController;
    private Marker plantableToPlace;

    private SharedPreferences sharedPrefs;

    private List<Marker> currentlyDisplayedPlantables;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);

        currentlyDisplayedPlantables = new ArrayList<Marker>();

        plantableToPlace = null;

        sharedPrefs = getSharedPreferences(getString(R.string.SharedPrefName), 0);

        gameController = new GameController(new User(sharedPrefs.getString(getString(R.string.PrefsEmailString), ""), "537e48763511c15161a1ed9b", ""), (LocationManager) getSystemService(Context.LOCATION_SERVICE), this);

        drawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawerList = (ListView) findViewById(R.id.left_drawer);

        // Set the adapter for the list view
        listAdapter = new ScoreArrayAdapter(this, R.layout.drawer_list_item, gameController.getHighScores());
        drawerList.setAdapter(listAdapter);
        // Get a handle to the Map Fragment
        map = ((MapFragment) getFragmentManager()
                .findFragmentById(R.id.map)).getMap();

        if  (map != null)
        {
            LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

            Location curLocation = locationManager.getLastKnownLocation(locationManager.getBestProvider(new Criteria(), true));

            LatLng curLoc = new LatLng(curLocation.getLatitude(), curLocation.getLongitude());

            map.setMyLocationEnabled(true);
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(curLoc, 13));

            //Set the listener
            map.setOnMapClickListener(this);
            map.setOnInfoWindowClickListener(this);

            //Set this activity to listen for location changes
            Criteria criteria = new Criteria();
            criteria.setAccuracy(Criteria.ACCURACY_FINE);
            locationManager.requestLocationUpdates(1000, 1, criteria, this, null);
        }
    }


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

    public void sweep(View view)
    {
    }

    @Override
    public void onMapClick(LatLng latLng)
    {
        if (plantableToPlace == null)
        {
            plantableToPlace = map.addMarker(new MarkerOptions().position(latLng).title("Place").alpha((float) 0.4));
            plantableToPlace.showInfoWindow();
        }
        else
        {
            plantableToPlace.remove();
            plantableToPlace = null;
        }
    }

    @Override
    public void onInfoWindowClick(Marker marker)
    {
        marker.setAlpha(1);
        marker.setDraggable(false);
        marker.setTitle("");
        marker.hideInfoWindow();
        gameController.addUserPlantable(marker.getPosition());
    }

    @Override
    public void onLocationChanged(Location location) {

        gameController.updateLocation(new LatLng(location.getLatitude(), location.getLongitude()));
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

    public void updateHighScores()
    {
        listAdapter.notifyDataSetChanged();
    }

    public void updateMyMines()
    {
        for (Marker marker : currentlyDisplayedPlantables)
        {
            marker.remove();
        }

        currentlyDisplayedPlantables.clear();

        List<Plantable> myPlantables = gameController.getUserPlantables();
        synchronized (myPlantables)
        {
            //Add map markers for previously set mines
            for (int i = 0; i < myPlantables.size(); ++i) {
                map.addMarker(new MarkerOptions().position(myPlantables.get(i).getLocation()).title("It's a trap!"));
            }
        }
    }


}
