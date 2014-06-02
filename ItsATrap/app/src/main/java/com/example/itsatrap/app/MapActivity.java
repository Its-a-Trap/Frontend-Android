package com.example.itsatrap.app;

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
import android.view.Display;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
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
        GoogleMap.OnInfoWindowClickListener, LocationListener, GoogleMap.OnMarkerClickListener, GoogleMap.OnCameraChangeListener, View.OnClickListener
{
    public static final String TAG = "IATMapActivity";
    private MapActivity self;

    private MapView mapView;
    private DrawerLayout drawerLayout;
    private ListView drawerList;
    private ArrayAdapter listAdapter;
    private int yourScoreIndex;
    private int yourScore;

    private GoogleMap map;
    private GameController gameController;
    private Marker plantableToPlace;
    private boolean removingPlantable;
    private HashMap<Marker, Plantable> markerData;

    private SharedPreferences sharedPrefs;

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
    private final int SWEEP_RADIUS = 1000;
    //Register a new location with the server after travelling this far (currently 5 miles)
    private final double UPDATE_DISTANCE = 8046.72;

    public static final String serverAddress = "http://107.170.182.13:3000";


    private Intent intent;
    private BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateLocation(getCurLatLng());
            Log.d(TAG+"PUSH", "Received push notification!!");
        }
    };



    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        self = this;

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);

        // Setup Push
        intent = new Intent(this, GcmIntentService.class);

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
        gameController = new GameController(new User(sharedPrefs.getString(getString(R.string.PrefsEmailString), ""), sharedPrefs.getString(getString(R.string.PrefsIdString), ""), sharedPrefs.getString(getString(R.string.PrefsNameString), "")), (LocationManager) getSystemService(Context.LOCATION_SERVICE), this);

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
        cooldownShape.getPaint().setARGB(128, 255, 255, 255);
        coolDownDisplay.setBackground(cooldownShape);

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
            map.setOnCameraChangeListener(this);


            //Set this activity to listen for location changes
            Criteria criteria = new Criteria();
            criteria.setAccuracy(Criteria.ACCURACY_FINE);
            locationManager.requestLocationUpdates(1000, 1, criteria, this, null);
        }

        // Tell the MapViewGroup about us
        mapView = (MapView)findViewById(R.id.map_view);
        mapView.setMapActivity(this);

        if (!sharedPrefs.contains(getString(R.string.TutorialCompleteFlag)))
        {
            showTutorial();
            SharedPreferences.Editor editor = sharedPrefs.edit();
            editor.putBoolean(getString(R.string.TutorialCompleteFlag), true);
            editor.commit();
        }

        updateLocation(getCurLatLng());

        drawerLayout.setDrawerListener(new DrawerLayout.DrawerListener()
        {
            @Override
            public void onDrawerSlide(View drawerView, float slideOffset) { }

            @Override
            public void onDrawerOpened(View drawerView)
            {
                drawerList.smoothScrollToPosition(yourScoreIndex);
            }

            @Override
            public void onDrawerClosed(View drawerView) {}
            @Override
            public void onDrawerStateChanged(int newState) {}
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
        startService(intent);
        registerReceiver(broadcastReceiver, new IntentFilter(GcmIntentService.BROADCAST_ACTION));
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterReceiver(broadcastReceiver);
        stopService(intent);
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
        boolean sameMarker = plantableToPlace != null && marker.getPosition().equals(plantableToPlace.getPosition());
        //Always remove the pending marker
        if (plantableToPlace != null)
        {
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
        if (plantableToPlace != null)
        {
            plantableToPlace.remove();
            plantableToPlace = null;
        }
    }

    /**
     * Handles onclick events for some of the buttons
     * @param view
     */
    @Override
    public void onClick(View view)
    {
        if (view.equals(findViewById(R.id.sweep_button)))
            sweep(view);
        else if (view.equals(findViewById(R.id.drawer_button)))
            pullDrawerOut(view);
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
    public void updateHighScores()
    {
        listAdapter.notifyDataSetChanged();
    }

    /**
     * Removes all user mines currently shown and redraws them
     */
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

    /**
     * Removes the markers for all mines revealed in the last sweep
     */
    public void removeSweepedMines()
    {
        for (Marker sweepMine : sweepMinesVisible)
        {
            sweepMine.remove();
        }
    }

    /**
     * Removes the markers for all mines revealed in the last sweep
     */
    public void setSweepedMineOpacity(double opacity)
    {
        for (Marker sweepMine : sweepMinesVisible)
        {
            sweepMine.setAlpha((float)opacity);
        }
    }

    /**
     * Handles displaying a notification informing the user they they have been trapped.
     * Creates a system-level notification if one does not already exists, or updates an existing
     * notification if it already exists. There can be only one notification from this app at a time.
     * @param name The name of the user who set the trap that just trapped the user.
     */
    public void displayTrapped(String name)
    {
        //Add the new killer to the list and update the death count
        killers.add(name);
        ++deathCount;

        //Create the long string listing all killers
        StringBuilder killersList = new StringBuilder();
        for (String killer : killers)
        {
            if (killersList.length() > 0)
                killersList.append(", ");
            killersList.append(killer);
        }
        //Set up the notification
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this)
                .setSmallIcon(R.drawable.its_a_trap_icon)
                .setContentTitle("You've been trapped!")
                .setContentText("You have been trapped "+deathCount+" times.");

        //Set up the notification to take the user to this app on click
        Intent resultIntent = new Intent(this, MapActivity.class);
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
        stackBuilder.addParentStack(MapActivity.class);
        stackBuilder.addNextIntent(resultIntent);
        PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
        mBuilder.setContentIntent(resultPendingIntent);

        //Set up expanded version of notification with full explanation
        NotificationCompat.BigTextStyle bigStyle = new NotificationCompat.BigTextStyle();
        bigStyle.bigText("You have been trapped "+deathCount+" times by "+killersList.toString()+". You lost "+50*deathCount+" points.");
        mBuilder.setStyle(bigStyle);

        NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationManager.notify(0, mBuilder.build());
    }

    /**
     * Runs the build in tutorial. Replaces the user data to something garuanteed to be useful, and
     * steps through a series of showcase views designed to lead the user through the major elements
     * of the game.
     */
    public void showTutorial()
    {
        final GameController realController = gameController;
        gameController = new TutorialGameController(gameController.getUser(), (LocationManager) getSystemService(Context.LOCATION_SERVICE), this);

        final ShowcaseView highScores = new ShowcaseView.Builder(this)
                .setTarget(new ViewTarget(R.id.drawer_button, this))
                .setContentTitle("High Scores")
                .setContentText("Click this button, or swipe from the left part of the screen to see high scores.")
                .build();
        highScores.hideButton();
        highScores.hide();

        final ShowcaseView yourScore = new ShowcaseView.Builder(this)
                .setTarget(new ViewTarget(R.id.your_score, this))
                .setContentTitle("Your Score")
                .setContentText("You can see your score at the top of the screen.")
                .build();
        yourScore.overrideButtonClick(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                yourScore.hide();
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
                    }
                });
            }
        });
        yourScore.hide();

        final ShowcaseView sweep = new ShowcaseView.Builder(this)
                .setTarget(new ViewTarget(R.id.sweep_button, this))
                .setContentTitle("Sweep")
                .setContentText("You can sweep to reveal enemy mines on the map.")
                .build();
        sweep.hideButton();
        sweep.hide();

        //The point to place and remove mines
        Display display = getWindowManager().getDefaultDisplay();
        Point tapPoint = new Point();
        display.getSize(tapPoint);
        tapPoint.set(tapPoint.x*2/3, tapPoint.y*2/3);

        final ShowcaseView remove = new ShowcaseView.Builder(this)
                .setTarget(new PointTarget(tapPoint))
                .setContentTitle("Remove a Mine")
                .setContentText("Tap a trap and confirm to remove it.")
                .build();
        remove.hideButton();
        remove.setOnShowcaseEventListener(new OnShowcaseEventListener() {
            //Method for ending the sweep section
            @Override
            public void onShowcaseViewHide(ShowcaseView showcaseView) {
                thisref.findViewById(R.id.sweep_button).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        sweep.hide();
                        yourScore.show();
                        thisref.findViewById(R.id.sweep_button).setOnClickListener(thisref);
                        thisref.onClick(view);
                    }
                });
            }

            @Override
            public void onShowcaseViewDidHide(ShowcaseView showcaseView) {}

            @Override
            public void onShowcaseViewShow(ShowcaseView showcaseView) {}
        });
        remove.hide();

        final ShowcaseView plant = new ShowcaseView.Builder(this)
                .setTarget(new PointTarget(tapPoint))
                .setContentTitle("Plant a Trap")
                .setContentText("Tap on the map and tap again to confirm to place a trap.")
                .build();
        plant.hideButton();
        plant.setOnShowcaseEventListener(new OnShowcaseEventListener() {
            @Override
            public void onShowcaseViewHide(ShowcaseView showcaseView) {
                thisref.map.setOnInfoWindowClickListener(new GoogleMap.OnInfoWindowClickListener() {
                    //Method for ending the remove section
                    @Override
                    public void onInfoWindowClick(Marker marker) {
                        remove.hide();
                        sweep.show();
                        thisref.onInfoWindowClick(marker);
                        thisref.map.setOnInfoWindowClickListener(thisref);
                    }
                });
            }

            @Override
            public void onShowcaseViewDidHide(ShowcaseView showcaseView) {}

            @Override
            public void onShowcaseViewShow(ShowcaseView showcaseView) {}
        });
        plant.hide();

        final ShowcaseView welcome = new ShowcaseView.Builder(this)
                .setContentTitle("Welcome")
                .setContentText("It's a trap is a game of cunning and deception.\n Earn points by placing traps where other people will walk over them, and avoid getting trapped yourself.")
                .build();
        welcome.overrideButtonClick(new View.OnClickListener() {
            //Method for ending the welcome section
            @Override
            public void onClick(View view) {
                welcome.hide();
                plant.show();
                thisref.map.setOnInfoWindowClickListener(new GoogleMap.OnInfoWindowClickListener() {
                    //Method for ending the plant section
                    @Override
                    public void onInfoWindowClick(Marker marker) {
                        plant.hide();
                        remove.show();
                        thisref.onInfoWindowClick(marker);
                    }
                });
            }
        });
    }

    /*
    ---------------- Game logic methods
     */

    /**
     *  Performs a "sweep", revealing all enemy mines within the sweep radius on the map for the sweep duration
     * @param view
     */
    public void sweep(View view)
    {
        //Check to see if we last sweeped too recently
        if (lastSweeped != null && new Date().getTime() - lastSweeped.getTime() < 1000*60*SWEEP_COOLDOWN)
        {
            long minutesLeft = (SWEEP_COOLDOWN*60*1000 - (new Date().getTime() - lastSweeped.getTime()))/1000/60 + 1;
            Toast.makeText(this, "Can't sweep again for "+minutesLeft+" minutes.", Toast.LENGTH_SHORT).show();
            return;
        }

        //Get nearby enemy mines, and add markers to the map
        List<Plantable> enemyTraps = gameController.getEnemyPlantablesWithinRadius(getCurLatLng(), SWEEP_RADIUS);
        for (Plantable plantable : enemyTraps)
        {
            Marker marker = map.addMarker(new MarkerOptions()
                    .position(plantable.getLocation())
                    .title(getString(R.string.watchOut))
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));
            sweepMinesVisible.add(marker);
        }

        //Record when we last sweeped to prevent excessive sweeping
        lastSweeped = new Date();

        //Set a timer to remove the traps after the sweep duration
        new SweepTimerTask(100, 1000*SWEEP_DURATION);

        // Do pie shit
        VariableArcShape pie = (VariableArcShape)cooldownShape.getShape();
        ((VariableArcShape)cooldownShape.getShape()).setSweepAngle(360f);
        View sweepButton = findViewById(R.id.sweep_button);
        pie.setHeight(sweepButton.getHeight());// - sweepButton.getPaddingTop() - sweepButton.getPaddingBottom());
        pie.setWidth(sweepButton.getWidth());// - sweepButton.getPaddingLeft() - sweepButton.getPaddingRight());
        AnimatorSet set = new AnimatorSet();
        ObjectAnimator circleAnimation = ObjectAnimator.ofFloat(cooldownShape.getShape(), "SweepAngle", 350, 0);
        set.play(circleAnimation);
        set.setDuration(2000);
        set.setInterpolator(new LinearInterpolator());
        circleAnimation.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                cooldownShape.invalidateSelf();
            }
        });
        set.start();
    }
    
    class SweepTimerTask extends TimerTask {
        int updateFrequency;
        int timeLeft;
        
        SweepTimerTask(int updateFrequency, int timeLeft) {
            this.updateFrequency = updateFrequency;
            this.timeLeft = timeLeft;

            Date afterSweepDuration = new Date();
            afterSweepDuration.setTime(afterSweepDuration.getTime() + updateFrequency);
            Timer sweepTimer = new Timer(true);
            sweepTimer.schedule(this, afterSweepDuration);
        }
        
        @Override
        public void run() {
            if (timeLeft > 0) {
                thisref.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        thisref.setSweepedMineOpacity(timeLeft/1000.0/SWEEP_DURATION);
                    }
                });

                new SweepTimerTask(updateFrequency, timeLeft-updateFrequency);
            } else {
                thisref.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        thisref.removeSweepedMines();
                    }
                });
            }
        }
    }

    /**
     * Handles calling the changearea method on the server, updating the current lists of user mines,
     * enemy mines and high scores
     * @param curLoc The current location of the user
     */
    public void updateLocation(final LatLng curLoc)
    {
        //Ensures that we don't update too frequently
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
                toSend.put("token", sharedPrefs.getString("RegId",""));
                toSend.put("user", gameController.getUser().getId());
                toSend.put("client_type","Android");
            } catch (JSONException e) {
                e.printStackTrace();
            }

            class PostLocationTask extends PostJsonTask<JSONArray>
            {

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
                        synchronized (gameController.getEnemyPlantables())
                        {
                            gameController.getEnemyPlantables().clear();
                            for (int i = 0; i < plantables.length(); ++i)
                            {
                                gameController.getEnemyPlantables().add(new Plantable(plantables.getJSONObject(i)));
                            }
                        }
                        //Update the user plantables
                        synchronized (gameController.getUserPlantables())
                        {
                            gameController.getUserPlantables().clear();
                            for (int i = 0; i < myPlantables.length(); ++i)
                            {
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
                protected void onPostExecute(JSONArray newHighScores)
                {
                    //We have to do the high score updating in the main thread
                    if (newHighScores != null)
                    {
                        synchronized (gameController.getHighScores())
                        {
                            gameController.getHighScores().clear();
                            for (int i = 0; i < newHighScores.length(); ++i)
                            {
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

            class PostExplodeTask extends PostJsonTask<String>
            {
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
                        if (success)
                        {
                            return responseObject.getString("ownerName");
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    return null;
                }

                @Override
                protected void onPostExecute(String exploded)
                {
                    if (exploded != null)
                    {
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
    public void addUserPlantable(final Marker marker)
    {
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
                    //Update remaining internal state
                    gameController.addUserPlantable(toAdd);
                    markerData.put(marker, toAdd);
                    ((TextView) findViewById(R.id.your_plantable_count))
                            .setText(String.valueOf(gameController.getNumUserPlantablesLeft()) + "\ntraps left");
                }
                else
                {
                    //If we failed to add, and there's no pending plantable re-make this one pending
                    Toast.makeText(self, "Problem with server, trap wasn't placed", Toast.LENGTH_SHORT).show();

                    if (plantableToPlace == null)
                    {
                        marker.setAlpha((float) 0.5);
                        marker.setDraggable(true);
                        marker.setTitle(getString(R.string.placeTrap));
                        marker.showInfoWindow();
                        plantableToPlace = marker;
                    }
                    //Otherwise, just forget it ever existed
                    else
                    {
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
                //If we didn't succeed, we need to put it back on the map because it's not gone
                if (!success)
                {
                    Toast.makeText(self, "Problem with server, trap wasn't removed", Toast.LENGTH_SHORT).show();
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
    protected boolean markerVisible(Marker toCheck)
    {
        return map.getProjection().getVisibleRegion().latLngBounds.contains(toCheck.getPosition());
    }
}
