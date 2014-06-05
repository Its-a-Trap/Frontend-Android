package com.example.itsatrap.app;

import android.os.Vibrator;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.TaskStackBuilder;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Point;
import android.graphics.drawable.ShapeDrawable;
import android.location.Criteria;
import android.location.LocationListener;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.support.v4.widget.DrawerLayout;
import android.location.Location;
import android.location.LocationManager;
import android.util.Log;
import android.view.Display;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.github.amlcurran.showcaseview.OnShowcaseEventListener;
import com.github.amlcurran.showcaseview.ShowcaseView;
import com.github.amlcurran.showcaseview.targets.PointTarget;
import com.github.amlcurran.showcaseview.targets.ViewTarget;
import com.google.android.gms.maps.*;
import com.google.android.gms.maps.model.*;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class MapActivity extends Activity implements GoogleMap.OnMapClickListener,
        GoogleMap.OnInfoWindowClickListener, LocationListener, GoogleMap.OnMarkerClickListener, GoogleMap.OnCameraChangeListener, View.OnClickListener{
    public static final String TAG = "IATMapActivity";
    private MapActivity self;

    private MapView mapView;
    private DrawerLayout drawerLayout;
    private ListView drawerList;
    private ArrayAdapter listAdapter;
    private int yourScoreIndex;
    private int yourScore;

    private GoogleMap map;
    private static GameController gameController = null;
    private Marker plantableToPlace;
    private boolean removingPlantable;
    private HashMap<Marker, Plantable> markerData;

    private SharedPreferences sharedPrefs;

    private boolean trapsLeftToggle = true;

    private Date lastSweeped;
    private List<Marker> sweepMinesVisible;

    //Putting the Java back in Javascript
    private MapActivity thisref;

    private LatLng lastLocation;

    //The set of all people who have killed the user since they last opened the app
    private HashSet<String> killers;
    //The number of times the user has been killed since they last opened the app
    private int deathCount;

    private ShapeDrawable cooldownShape;

    //The sweep cooldown, in minutes
    private final int SWEEP_COOLDOWN = 30;
    //The amount of time sweeped mines should be visible, in seconds
    private final int SWEEP_DURATION = 5;
    //The radius of the sweep, in meters
    private final int SWEEP_RADIUS = 30;
    //Register a new location with the server after travelling this far (currently 5 miles)
    private final double UPDATE_DISTANCE = 8046.72;

    public static final String serverAddress = "http://107.170.182.13:3000";


    private Intent intent;
    public static String PACKAGE_NAME;
    private BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "PUSH message received");
            try {
                Log.d(TAG + "PUSH message", intent.getExtras().getString("message"));

                // Do stuff if we need to refresh data
                if (intent.getExtras().getString("message").equals("refreshdata")){
                    LatLng curLoc = getCurLatLng();
                    if (curLoc != null)
                        updateLocation(curLoc);
                } else if (intent.getExtras().getString("message").equals("killed")){ // Tell someone they were killed by someone
                    String killed = intent.getExtras().getString("killed");
                    displayTrappedSomeone(killed);
                }

                Log.d(TAG + "PUSH", "Received push notification!!");
            } catch (NullPointerException e) {

            }

        }
    };



    @Override
    protected void onCreate(Bundle savedInstanceState){
        self = this;

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);

        // Setup Push
        intent = new Intent(this, GcmIntentService.class);
        PACKAGE_NAME = getApplicationContext().getPackageName();

        //Initialize internal state information
        sweepMinesVisible = new ArrayList<Marker>();

        killers = new HashSet<String>();
        deathCount = 0;

        plantableToPlace = null;
        removingPlantable = false;

        thisref = this;

        sharedPrefs = getSharedPreferences(getString(R.string.SharedPrefName), 0);

        //Set listeners
        findViewById(R.id.drawer_button).setOnClickListener(this);
        findViewById(R.id.sweep_button).setOnClickListener(this);

        //Create the game controller object
        if (gameController == null)
            gameController = new GameController(new User(sharedPrefs.getString(getString(R.string.PrefsEmailString), ""), sharedPrefs.getString(getString(R.string.PrefsIdString), ""), sharedPrefs.getString(getString(R.string.PrefsNameString), "")));

        drawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawerList = (ListView) findViewById(R.id.left_drawer);

        // Set the adapter for the list view
        listAdapter = new ScoreArrayAdapter(this, R.layout.drawer_list_item, gameController.getHighScores());
        drawerList.setAdapter(listAdapter);
        drawerLayout.setScrimColor(getResources().getColor(R.color.drawer_scrim));

        //Set up the sweep button
        View coolDownDisplay = findViewById(R.id.cooldown_display);
        View sweepButton = findViewById(R.id.sweep_button);
        cooldownShape = new ShapeDrawable(new VariableArcShape(0f, 0f, sweepButton.getWidth(), sweepButton.getHeight()));
        cooldownShape.getPaint().setARGB(180, 255, 255, 255);
        coolDownDisplay.setBackground(cooldownShape);

        // Get a handle to the Map Fragment
        map = ((MapFragment) getFragmentManager()
                .findFragmentById(R.id.map)).getMap();

        markerData = new HashMap<Marker, Plantable>();

        if  (map != null){
            LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            LatLng curLoc = getCurLatLng();

            map.setMyLocationEnabled(true);
            if (curLoc != null)
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(curLoc, 13));

            //Set the listener
            map.setOnMapClickListener(this);
            map.setOnInfoWindowClickListener(this);
            map.setOnMarkerClickListener(this);
            map.setOnCameraChangeListener(this);


            //Set this activity to listen for location changes
            Criteria criteria = new Criteria();
            criteria.setAccuracy(Criteria.ACCURACY_FINE);
            locationManager.requestLocationUpdates(1000, 1, criteria, this, null);
        }

        // Tell the MapViewGroup about us
        mapView = (MapView)findViewById(R.id.map_view);
        mapView.setMapActivity(this);

        //There are 5 tutorial steps - step 6 means we're done
        if (sharedPrefs.getInt(getString(R.string.TutorialCompleteFlag), 0) <= 5){
            showTutorial(sharedPrefs.getInt(getString(R.string.TutorialCompleteFlag), 0));

        }

        LatLng curLoc = getCurLatLng();
        if (curLoc != null)
            updateLocation(curLoc);

        drawerLayout.setDrawerListener(new DrawerLayout.DrawerListener(){
            @Override
            public void onDrawerSlide(View drawerView, float slideOffset) { }

            @Override
            public void onDrawerOpened(View drawerView){
                drawerList.setSelection(yourScoreIndex);
            }

            @Override
            public void onDrawerClosed(View drawerView) {}
            @Override
            public void onDrawerStateChanged(int newState) {}
        });

        ImageView trapsLeftImg = (ImageView) findViewById(R.id.your_plantable_image);
        // set a onclick listener for when the button gets clicked
        trapsLeftImg.setOnClickListener(new View.OnClickListener() {
            // Start new list activity
            public void onClick(View v) {
                ((Vibrator)getSystemService(VIBRATOR_SERVICE)).vibrate(20);
                if (trapsLeftToggle){
                    ((TextView) findViewById(R.id.your_plantable_count))
                            .setText(String.valueOf(gameController.getNumUserPlantablesUsed()) + getString(R.string.traps_placed));
                    trapsLeftToggle = false;
                }
                else{
                    ((TextView) findViewById(R.id.your_plantable_count))
                            .setText(String.valueOf(gameController.getNumUserPlantablesLeft())+getString(R.string.traps_left));
                    trapsLeftToggle = true;
                }
            }
        });
    }

    public void onStart() {
        super.onStart();
        //Since the user has opened the app, remove the notification and clear the death records
        killers.clear();
        deathCount = 0;
        NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationManager.cancel(0);
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "Resuming.");
        Log.d(TAG, "Starting service.");
        startService(intent);
        Log.d(TAG, "Registering receiver.");
        registerReceiver(broadcastReceiver, new IntentFilter(GcmIntentService.BROADCAST_ACTION)); // GcmIntentService.BROADCAST_ACTION
        Log.d(TAG, "Done resuming.");
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterReceiver(broadcastReceiver);
        stopService(intent);
    }

    @Override
    protected void onDestroy() {
        suicide();
        super.onDestroy();
    }

    private void suicide() {
        // TODO: Implement me
    }

    /*
    ---------------- Override event listeners
     */

    @Override
    public boolean onCreateOptionsMenu(Menu menu){

        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.map, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item){
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
    public void onMapClick(LatLng latLng){
        // If you're out of plantable items, don't let them do it
        if (gameController.getNumUserPlantablesLeft() <= 0){
            Toast.makeText(this, getString(R.string.no_traps_left), Toast.LENGTH_SHORT).show();
        }
        else{
            if (removingPlantable){
                removingPlantable = false;
            }
            else if (plantableToPlace == null){
                plantableToPlace = map.addMarker(new MarkerOptions()
                        .position(latLng)
                        .title(getString(R.string.placeTrap))
                        .alpha((float) 0.4)
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)));
                plantableToPlace.showInfoWindow();
            }
            else{
                plantableToPlace.remove();
                plantableToPlace = null;
            }
        }
    }

    private void updatePlantableCount(){
        if (trapsLeftToggle) {
            ((TextView) findViewById(R.id.your_plantable_count))
                    .setText(String.valueOf(gameController.getNumUserPlantablesLeft()) + getString(R.string.traps_left));
        } else {
            ((TextView) findViewById(R.id.your_plantable_count))
                    .setText(String.valueOf(gameController.getNumUserPlantablesUsed()) + getString(R.string.traps_placed));
        }
    }

    @Override
    public void onInfoWindowClick(Marker marker){
        ((Vibrator)getSystemService(VIBRATOR_SERVICE)).vibrate(20);
        // If you're placing a trap
        if (plantableToPlace != null){
            addUserPlantable(marker);
        }

        // If you're removing a trap
        else{
            if (marker.getTitle().equals(getString(R.string.yourTrap))){
                Plantable plantableToRemove = markerData.get(marker);
                if (plantableToRemove != null) {
                    markerData.remove(marker);
                    marker.remove();
                    removeUserPlantable(plantableToRemove);
                    removingPlantable = false;
                }
            }
        }
    }

    @Override
    public boolean onMarkerClick(Marker marker){
        boolean sameMarker = plantableToPlace != null && marker.getPosition().equals(plantableToPlace.getPosition());
        //Always remove the pending marker
        if (plantableToPlace != null){
            plantableToPlace.remove();
            plantableToPlace = null;
        }
        //If it's a different marker, show the remove API
        if (!sameMarker) {
            marker.showInfoWindow();
            removingPlantable = true;
        }
        return true;
    }

    @Override
    public void onLocationChanged(Location location) {
        updateLocation(new LatLng(location.getLatitude(), location.getLongitude()));
    }

    @Override
    public void onCameraChange(CameraPosition cameraPosition) {
        if (plantableToPlace != null){
            plantableToPlace.remove();
            plantableToPlace = null;
        }
    }

    /**
     * Handles onclick events for some of the buttons
     * @param view
     */
    @Override
    public void onClick(View view){
        if (view.equals(findViewById(R.id.sweep_button))) {
            if (sweep(view)) {
                sweepCooldownAnimation();
            }
        }
        else if (view.equals(findViewById(R.id.drawer_button))){
            pullDrawerOut(view);
        }
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

    /**
     * Forces the high scores list to refresh
     */
    public void updateHighScores(){
        listAdapter.notifyDataSetChanged();
    }

    /**
     * Removes all user mines currently shown and redraws them
     */
    public void updateMyMines(){
        for (Marker marker : markerData.keySet()){
            marker.remove();
        }

        markerData.clear();

        List<Plantable> myPlantables = gameController.getUserPlantables();
        synchronized (myPlantables){
            //Add map markers for previously set mines
            for (int i = 0; i < myPlantables.size(); ++i) {
                markerData.put(map.addMarker(new MarkerOptions()
                                .position(myPlantables.get(i).getLocation())
                                .title(getString(R.string.yourTrap))
                                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))),
                        myPlantables.get(i)
                );
            }
            updatePlantableCount();
        }
    }

    /**
     * Removes the markers for all mines revealed in the last sweep
     */
    public void removeSweepedMines(){
        for (Marker sweepMine : sweepMinesVisible){
            sweepMine.remove();
        }
    }

    /**
     * Handles displaying a notification informing the user they they have been trapped.
     * Creates a system-level notification if one does not already exists, or updates an existing
     * notification if it already exists. There can be only one notification from this app at a time.
     * @param name The name of the user who set the trap that just trapped the user.
     */
    public void displayTrapped(String name){
        //Add the new killer to the list and update the death count
        killers.add(name);
        ++deathCount;

        //Create the long string listing all killers
        StringBuilder killersList = new StringBuilder();
        for (String killer : killers){
            if (killersList.length() > 0)
                killersList.append(", ");
            killersList.append(killer);
        }
        //Set up the notification
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this)
                .setSmallIcon(R.drawable.its_a_trap_icon)
                .setContentTitle(getString(R.string.trapped_title))
                .setContentText(getString(R.string.trapped_short_message_1)+deathCount+getString(R.string.trapped_short_message_2));

        //Set up the notification to take the user to this app on click
        Intent resultIntent = new Intent(this, MapActivity.class);
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
        stackBuilder.addParentStack(MapActivity.class);
        stackBuilder.addNextIntent(resultIntent);
        PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
        mBuilder.setContentIntent(resultPendingIntent);

        //Set up expanded version of notification with full explanation
        NotificationCompat.BigTextStyle bigStyle = new NotificationCompat.BigTextStyle();
        bigStyle.bigText(getString(R.string.trapped_message_1) + deathCount + getString(R.string.trapped_message_2) + killersList.toString() + getString(R.string.trapped_message_3) + 50 * deathCount + getString(R.string.trapped_message_4));
        mBuilder.setStyle(bigStyle);

        NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationManager.notify(0, mBuilder.build());
    }

    /**
     * Handles displaying a notification informing the user they they have trapped someone.
     * Creates a system-level notification if one does not already exists, or updates an existing
     * notification if it already exists. There can be only one notification from this app at a time.
     * @param name The name of the user who set the trap that just trapped the user.
     */
    public void displayTrappedSomeone(String name){
        //Add the new victim to the list and update the death count
//        killers.add(name);
//        ++deathCount;

        //Create the long string listing all killers
//        StringBuilder killersList = new StringBuilder();
//        for (String killer : killers){
//            if (killersList.length() > 0)
//                killersList.append(", ");
//            killersList.append(killer);
//        }
        //Set up the notification
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this)
                .setSmallIcon(R.drawable.its_a_trap_icon)
                .setContentTitle(getString(R.string.trapped_title))
                .setContentText("You have trapped" + name);

        //Set up the notification to take the user to this app on click
        Intent resultIntent = new Intent(this, MapActivity.class);
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
        stackBuilder.addParentStack(MapActivity.class);
        stackBuilder.addNextIntent(resultIntent);
        PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
        mBuilder.setContentIntent(resultPendingIntent);

        //Set up expanded version of notification with full explanation
        NotificationCompat.BigTextStyle bigStyle = new NotificationCompat.BigTextStyle();
        bigStyle.bigText("You have trapped" + name);
        mBuilder.setStyle(bigStyle);

        NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationManager.notify(0, mBuilder.build());
    }

    /**
     * Runs the build in tutorial. Replaces the user data to something garuanteed to be useful, and
     * steps through a series of showcase views designed to lead the user through the major elements
     * of the game.
     * As it goes through, it reassigns various listeners to progress the tutorial
     * @param step - the step of the tutorial to start on
     */
    public void showTutorial(int step){
        //Step 5
        final GameController realController = gameController;
        if (! (gameController instanceof TutorialGameController))
            gameController = new TutorialGameController(gameController.getUser());

        final ShowcaseView highScores = new ShowcaseView.Builder(this)
                .setTarget(new ViewTarget(R.id.drawer_button, this))
                .setContentTitle(getString(R.string.high_scores_title))
                .setContentText(getString(R.string.high_scores_text))
                .build();
        highScores.hideButton();
        if (step != 5) {
            highScores.hide();
        }
        else{
            highScores.show();
            thisref.findViewById(R.id.drawer_button).setOnClickListener(new View.OnClickListener() {
                //Method for ending the high scores section
                @Override
                public void onClick(View view) {
                    highScores.hide();
                    thisref.findViewById(R.id.drawer_button).setOnClickListener(thisref);
                    thisref.onClick(view);
                    thisref.gameController = realController;
                    lastSweeped = null;
                    sharedPrefs.edit().putInt(getString(R.string.TutorialCompleteFlag), 6).commit();
                }
            });
            return;
        }

        //Step 4
        final ShowcaseView yourScore = new ShowcaseView.Builder(this)
                .setTarget(new ViewTarget(R.id.your_score, this))
                .setContentTitle(getString(R.string.your_score_title))
                .setContentText(getString(R.string.your_score_text))
                .build();
        //Method for ending step 4
        yourScore.overrideButtonClick(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                yourScore.hide();
                highScores.show();
                //Method for ending step 5
                thisref.findViewById(R.id.drawer_button).setOnClickListener(new View.OnClickListener() {
                    //Method for ending the high scores section
                    @Override
                    public void onClick(View view) {
                        highScores.hide();
                        thisref.findViewById(R.id.drawer_button).setOnClickListener(thisref);
                        thisref.onClick(view);
                        thisref.gameController = realController;
                        lastSweeped = null;
                        sharedPrefs.edit().putInt(getString(R.string.TutorialCompleteFlag), 6).commit();
                    }
                });
                sharedPrefs.edit().putInt(getString(R.string.TutorialCompleteFlag), 5).commit();
            }
        });
        if (step != 4) {
            yourScore.hide();
        }
        else {
            return;
        }

        //Step 3
        final ShowcaseView sweep = new ShowcaseView.Builder(this)
                .setTarget(new ViewTarget(R.id.sweep_button, this))
                .setContentTitle(getString(R.string.sweep_title))
                .setContentText(getString(R.string.sweep_text_1) + SWEEP_COOLDOWN + getString(R.string.sweep_text_2))
                .build();
        sweep.hideButton();
        if (step != 3) {
            sweep.hide();
        }
        else {
            thisref.findViewById(R.id.sweep_button).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    sweep.hide();
                    yourScore.show();
                    thisref.findViewById(R.id.sweep_button).setOnClickListener(thisref);
                    thisref.sweep(view);
                    sharedPrefs.edit().putInt(getString(R.string.TutorialCompleteFlag), 4).commit();
                }
            });
            return;
        }

        //The point to place and remove mines
        Display display = getWindowManager().getDefaultDisplay();
        Point tapPoint = new Point();
        display.getSize(tapPoint);
        tapPoint.set(tapPoint.x*2/3, tapPoint.y*2/3);

        //Step 2
        final ShowcaseView remove = new ShowcaseView.Builder(this)
                .setTarget(new PointTarget(tapPoint))
                .setContentTitle(getString(R.string.removemine_title))
                .setContentText(getString(R.string.removemine_text))
                .build();
        remove.hideButton();
        remove.setOnShowcaseEventListener(new OnShowcaseEventListener() {
            //Method for ending the sweep section (step 3)
            @Override
            public void onShowcaseViewHide(ShowcaseView showcaseView) {
                thisref.findViewById(R.id.sweep_button).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        sweep.hide();
                        yourScore.show();
                        thisref.findViewById(R.id.sweep_button).setOnClickListener(thisref);
                        thisref.sweep(view);
                        sharedPrefs.edit().putInt(getString(R.string.TutorialCompleteFlag), 4).commit();
                    }
                });
            }

            @Override
            public void onShowcaseViewDidHide(ShowcaseView showcaseView) {}

            @Override
            public void onShowcaseViewShow(ShowcaseView showcaseView) {}
        });

        if (step != 2) {
            remove.hide();
        }
        else{
            //Function for ending step 2
            thisref.map.setOnInfoWindowClickListener(new GoogleMap.OnInfoWindowClickListener() {
                //Method for ending the remove section
                @Override
                public void onInfoWindowClick(Marker marker) {
                    if (plantableToPlace == null || (plantableToPlace != null && !marker.getPosition().equals(plantableToPlace.getPosition()))){
                        remove.hide();
                        sweep.show();
                        thisref.map.setOnInfoWindowClickListener(thisref);
                    }
                    sharedPrefs.edit().putInt(getString(R.string.TutorialCompleteFlag), 3).commit();
                    thisref.onInfoWindowClick(marker);
                }
            });
            return;
        }

        //Step 1
        final ShowcaseView plant = new ShowcaseView.Builder(this)
                .setTarget(new PointTarget(tapPoint))
                .setContentTitle(getString(R.string.plant_title))
                .setContentText(getString(R.string.plant_text))
                .build();
        plant.hideButton();
        //Function for ending step 0
        if (step != 1) {
            plant.hide();
        }

        //We need to set the listener after the hide call
        plant.setOnShowcaseEventListener(new OnShowcaseEventListener() {
            @Override
            public void onShowcaseViewHide(ShowcaseView showcaseView) {
                //Function for ending step 2
                thisref.map.setOnInfoWindowClickListener(new GoogleMap.OnInfoWindowClickListener() {
                    //Method for ending the remove section
                    @Override
                    public void onInfoWindowClick(Marker marker) {
                        if (plantableToPlace == null || (plantableToPlace != null && !marker.getPosition().equals(plantableToPlace.getPosition()))){
                            remove.hide();
                            sweep.show();
                            thisref.map.setOnInfoWindowClickListener(thisref);
                        }

                        thisref.onInfoWindowClick(marker);
                        sharedPrefs.edit().putInt(getString(R.string.TutorialCompleteFlag), 3).commit();
                    }
                });
                sharedPrefs.edit().putInt(getString(R.string.TutorialCompleteFlag), 2).commit();
            }

            @Override
            public void onShowcaseViewDidHide(ShowcaseView showcaseView) {}

            @Override
            public void onShowcaseViewShow(ShowcaseView showcaseView) {}
        });

        if (step == 1) {
            thisref.map.setOnInfoWindowClickListener(new GoogleMap.OnInfoWindowClickListener() {
                //Method for ending the plant section
                @Override
                public void onInfoWindowClick(Marker marker) {
                    plant.hide();
                    remove.show();
                    thisref.onInfoWindowClick(marker);
                    sharedPrefs.edit().putInt(getString(R.string.TutorialCompleteFlag), 2).commit();
                }
            });
            return;
        }

        //Step 0
        final ShowcaseView welcome = new ShowcaseView.Builder(this)
                .setContentTitle(getString(R.string.introduction_title))
                .setContentText(getString(R.string.introduction_text))
                .build();
        //Function for ending step -
        welcome.overrideButtonClick(new View.OnClickListener() {
            //Method for ending the welcome section
            @Override
            public void onClick(View view) {
                welcome.hide();
                plant.show();
                //Function for ending step 1
                thisref.map.setOnInfoWindowClickListener(new GoogleMap.OnInfoWindowClickListener() {
                    //Method for ending the plant section
                    @Override
                    public void onInfoWindowClick(Marker marker) {
                        plant.hide();
                        remove.show();
                        thisref.onInfoWindowClick(marker);
                        sharedPrefs.edit().putInt(getString(R.string.TutorialCompleteFlag), 2).commit();
                    }
                });
                sharedPrefs.edit().putInt(getString(R.string.TutorialCompleteFlag), 1).commit();
            }
        });
    }

    /*
    ---------------- Game logic methods
     */

    /**
     *  Performs a "sweep", revealing all enemy mines within the sweep radius on the map for the sweep duration
     * @param view
     * @return Whether or not the sweep was triggered
     */
    public boolean sweep(View view)
    {
        ((Vibrator)getSystemService(VIBRATOR_SERVICE)).vibrate(500);

        //Check to see if we last sweeped too recently
        if (lastSweeped != null && new Date().getTime() - lastSweeped.getTime() < 1000*60*SWEEP_COOLDOWN){

            long minutesLeft = (SWEEP_COOLDOWN*60*1000 - (new Date().getTime() - lastSweeped.getTime()))/1000/60 + 1;
            Toast.makeText(this, getString(R.string.cant_sweep_1)+minutesLeft+getString(R.string.cant_sweep_2), Toast.LENGTH_SHORT).show();
            return false;
        }

        //Get nearby enemy mines, and add markers to the map
        LatLng curLoc = getCurLatLng();
        if (curLoc != null) {
            List<Plantable> enemyTraps = gameController.getEnemyPlantablesWithinRadius(curLoc, SWEEP_RADIUS);
            for (Plantable plantable : enemyTraps) {
                Marker marker = map.addMarker(new MarkerOptions()
                        .position(plantable.getLocation())
                        .title(getString(R.string.watchOut))
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));
                sweepMinesVisible.add(marker);
            }
        }

        //Record when we last sweeped to prevent excessive sweeping
        lastSweeped = new Date();

        //Set a timer to remove the traps after the sweep duration
        AnimatorSet sweepSet = new AnimatorSet();
        for (Marker sweepMine : sweepMinesVisible){
            ObjectAnimator sweepAnimation = ObjectAnimator.ofFloat(sweepMine, "Alpha", 1, 0);
            sweepSet.play(sweepAnimation);
        }
        sweepSet.setDuration(1000*SWEEP_DURATION);
        sweepSet.setInterpolator(new LinearInterpolator());
        sweepSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                removeSweepedMines();
            }
        });
        sweepSet.start();

        return true;
    }

    public void sweepCooldownAnimation() {
        ((VariableArcShape)cooldownShape.getShape()).setSweepAngle(360f);
        AnimatorSet set = new AnimatorSet();
        ObjectAnimator circleAnimation = ObjectAnimator.ofFloat(cooldownShape.getShape(), "SweepAngle", 350, 0);
        set.play(circleAnimation);
        set.setDuration(SWEEP_COOLDOWN*60*1000);
        set.setInterpolator(new LinearInterpolator());
        circleAnimation.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                cooldownShape.invalidateSelf();
            }
        });
        set.start();
    }

    /**
     * Handles calling the changearea method on the server, updating the current lists of user mines,
     * enemy mines and high scores
     * @param curLoc The current location of the user
     */
    public void updateLocation(final LatLng curLoc){
        //Ensures that we don't update too frequently
        if (lastLocation == null || GameController.distanceBetween(lastLocation, curLoc) < UPDATE_DISTANCE){
            lastLocation = curLoc;

            //Construct JSON object to send to server
            JSONObject toSend = new JSONObject();
            try {
                JSONObject loc = new JSONObject();
                loc.put("lat", curLoc.latitude);
                loc.put("lon", curLoc.longitude);
                toSend.put("location", loc);
                toSend.put("token", sharedPrefs.getString("RegId",""));
                toSend.put("user", gameController.getUser().getId());
                toSend.put("client_type","Android");
            } catch (JSONException e) {
                e.printStackTrace();
            }

            class PostLocationTask extends PostJsonTask<JSONArray> {

                public PostLocationTask(String serverAddress, String endpoint) {
                    super(serverAddress, endpoint);
                }

                @Override
                protected JSONArray parseResponse(String response) {
                    try {
                        JSONObject responseObject = new JSONObject(response);
                        JSONArray plantables = responseObject.getJSONArray("mines");
                        JSONArray scores = responseObject.getJSONArray("scores");
                        JSONArray myPlantables = responseObject.getJSONArray("myMines");
                        yourScore = responseObject.getInt("myScore");
                        yourScoreIndex = responseObject.getInt("myScoreIndex");


                        //Update the enemy plantables
                        synchronized (gameController.getEnemyPlantables()) {
                            gameController.getEnemyPlantables().clear();
                            for (int i = 0; i < plantables.length(); ++i) {
                                gameController.getEnemyPlantables().add(new Plantable(plantables.getJSONObject(i)));
                            }
                        }
                        //Update the user plantables
                        synchronized (gameController.getUserPlantables()) {
                            gameController.getUserPlantables().clear();
                            for (int i = 0; i < myPlantables.length(); ++i) {
                                gameController.getUserPlantables().add(new Plantable(myPlantables.getJSONObject(i)));
                            }
                        }
                        return scores;
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    return null;
                }

                @Override
                protected void onPostExecute(JSONArray newHighScores) {
                    //We have to do the high score updating in the main thread
                    if (newHighScores != null) {
                        synchronized (gameController.getHighScores()) {
                            gameController.getHighScores().clear();
                            for (int i = 0; i < newHighScores.length(); ++i) {
                                try {
                                    gameController.getHighScores().add(new PlayerInfo(newHighScores.getJSONObject(i)));
                                } catch (JSONException e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    }
                    updateHighScores();
                    updateMyMines();
                    checkForCollisions(curLoc);
                    ((TextView) findViewById(R.id.your_score))
                            .setText(String.valueOf(yourScore));
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
     * @param curLoc The current location of the user
     */
    public void checkForCollisions(LatLng curLoc) {
        List<Plantable> possibleCollisions = gameController.checkForCollisions(curLoc);
        for (final Plantable toExplode : possibleCollisions) {
            //Construct JSON object to send to server
            JSONObject toSend = new JSONObject();
            try {
                toSend.put("user", gameController.getUser().getId());
                toSend.put("id", toExplode.getPlantableId());
            } catch (JSONException e) {
                e.printStackTrace();
            }

            class PostExplodeTask extends PostJsonTask<String> {
                public PostExplodeTask(String serverAddress, String endpoint) {
                    super(serverAddress, endpoint);
                }

                /**
                 *
                 * @param response
                 * @return Null if the explosion failed
                 */
                @Override
                protected String parseResponse(String response) {
                    try {
                        JSONObject responseObject = new JSONObject(response);
                        boolean success = responseObject.getBoolean("success");
                        if (success) {
                            return responseObject.getString("ownerName");
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    return null;
                }

                @Override
                protected void onPostExecute(String exploded) {
                    if (exploded != null) {
                        displayTrapped(exploded);
                        gameController.removeEnemyPlantable(toExplode);
                    }
                }
            }
            new PostExplodeTask(serverAddress, "/api/explodemine").execute(toSend);
        }
    }

    /**
     * Handles calls to server to place a new plantable
     * @param marker The marker representing the plantable to add
     */
    public void addUserPlantable(final Marker marker) {
        //Update marker UI and internal state
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
        class AddPlantableTask extends PostJsonTask<Plantable> {
            public AddPlantableTask(String serverAddress, String endpoint) {
                super(serverAddress, endpoint);
            }

            @Override
            protected void onPostExecute(Plantable toAdd) {
                if (toAdd != null) {
                    //Update remaining internal state
                    gameController.addUserPlantable(toAdd);
                    markerData.put(marker, toAdd);
                    updatePlantableCount();
                }
                else {
                    //If we failed to add, and there's no pending plantable re-make this one pending
                    Toast.makeText(self, getString(R.string.addmine_error), Toast.LENGTH_SHORT).show();

                    if (plantableToPlace == null) {
                        marker.setAlpha((float) 0.5);
                        marker.setDraggable(true);
                        marker.setTitle(getString(R.string.placeTrap));
                        marker.showInfoWindow();
                        plantableToPlace = marker;
                    }
                    //Otherwise, just forget it ever existed
                    else {
                        //TODO: This behavior probably isn't ideal - we should rethink it
                        marker.remove();
                    }
                }
            }

            @Override
            protected Plantable parseResponse(String response) {
                //The server should have returned a JSON object representing the new plantable
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

    /**
     * Handles calls to the server to attempt to remove a user plantable
     * @param toRemove The plantable to remove
     */
    public void removeUserPlantable(final Plantable toRemove) {
        //Construct JSON object to send to server
        JSONObject toSend = new JSONObject();
        try {
            toSend.put("id", toRemove.getPlantableId());
        } catch (JSONException e) {
            e.printStackTrace();
        }

        //Make request - use an async task
        class RemovePlantableTask extends PostJsonTask<Boolean> {

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
            protected void onPostExecute(Boolean success) {
                //If we didn't succeed, we need to put it back on the map because it's not gone
                if (!success) {
                    Toast.makeText(self, getString(R.string.removemine_error), Toast.LENGTH_SHORT).show();
                    markerData.put(map.addMarker(new MarkerOptions()
                            .position(toRemove.getLocation())
                            .title(getString(R.string.yourTrap))
                            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))),
                    toRemove);

                }
                //Update the data structure for success
                else {
                    gameController.removeUserPlantable(toRemove);
                    updatePlantableCount();
                }
            }
        }
        new RemovePlantableTask(serverAddress, "/api/removemine").execute(toSend);
    }


    /*
    ---------------- Helper methods
     */

    /**
     * Gets the current location, or null if location service is disabled
     * @return
     */
    private LatLng getCurLatLng() {
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        Location curLocation = locationManager.getLastKnownLocation(locationManager.getBestProvider(new Criteria(), true));
        if (curLocation == null)
            return null;
        LatLng curLoc = new LatLng(curLocation.getLatitude(), curLocation.getLongitude());
        return curLoc;
    }

    public void pullDrawerOut(View view)
    {
        drawerLayout.openDrawer(drawerList);
    }

    /**
     * Returns whether or not the provided marker is currently visible in the map
     * Technically this might return true if the marker isn't strictly visible due to projections and non-vertical orientations,
     * but since this map has non-vertical orientations disabled and
     * @param toCheck
     * @return
     */
    protected boolean markerVisible(Marker toCheck) {
        return map.getProjection().getVisibleRegion().latLngBounds.contains(toCheck.getPosition());
    }
}
