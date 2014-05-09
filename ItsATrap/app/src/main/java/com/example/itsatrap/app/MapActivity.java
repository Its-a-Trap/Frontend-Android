package com.example.itsatrap.app;

import android.app.Activity;
import android.content.Context;
import android.location.Criteria;
import android.os.Bundle;
import android.support.v4.widget.DrawerLayout;
import android.location.Location;
import android.location.LocationManager;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import com.google.android.gms.maps.*;
import com.google.android.gms.maps.model.*;

public class MapActivity extends Activity {

    private String[] drawerEntries;
    private DrawerLayout drawerLayout;
    private ListView drawerList;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);

        String[] hardCodedEntries = {"Jeff", "DSM", "Calder", "Carissa", "DermDerm", "Tao", "Carlton", "Quinn"};
        drawerEntries = hardCodedEntries;
        drawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawerList = (ListView) findViewById(R.id.left_drawer);

        // Set the adapter for the list view
        drawerList.setAdapter(new ArrayAdapter<String>(this, R.layout.drawer_list_item, R.id.drawer_text_view, drawerEntries));
        // Get a handle to the Map Fragment
        GoogleMap map = ((MapFragment) getFragmentManager()
                .findFragmentById(R.id.map)).getMap();

        if  (map != null)
        {
            LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

            Location curLocation = locationManager.getLastKnownLocation(locationManager.getBestProvider(new Criteria(), true));

            LatLng curLoc = new LatLng(curLocation.getLatitude(), curLocation.getLongitude());

            map.setMyLocationEnabled(true);
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(curLoc, 13));

    //        map.addMarker(new MarkerOptions()
    //                .title("Sydney")
    //                .snippet("The most populous city in Australia.")
    //                .position(sydney));
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.map, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

}
