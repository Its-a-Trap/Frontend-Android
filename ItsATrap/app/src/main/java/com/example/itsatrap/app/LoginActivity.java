package com.example.itsatrap.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.gcm.GoogleCloudMessaging;
import com.google.android.gms.plus.Plus;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOError;
import java.io.IOException;

/**
 * Created by carissa on 5/10/14.
 * Sample code drawn from https://developers.google.com/+/mobile/android/getting-started
 * and https://developers.google.com/+/mobile/android/sign-in
 */
public class LoginActivity extends Activity implements OnConnectionFailedListener, ConnectionCallbacks, View.OnClickListener{


    private GoogleApiClient gClient;

    /* A flag indicating that a PendingIntent is in progress and prevents
     * us from starting further intents.
     */
    private boolean mIntentInProgress;

    /* Request code used to invoke sign in user interactions. */
    private static final int RC_SIGN_IN = 0;

    /* Store the connection result from onConnectionFailed callbacks so that we can
     * resolve them when the user clicks sign-in.
     */
    private ConnectionResult mConnectionResult;

    private SharedPreferences sharedPrefs;

    // For Push
    private final static int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;
    GoogleCloudMessaging gcm;
    String SENDER_ID = "301006106178"; // From our Project Number in the Google Developer's Console
    Context context;
    String regid;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        context = getApplicationContext();

        sharedPrefs = getSharedPreferences(getString(R.string.SharedPrefName), 0);
        if (sharedPrefs.contains(getString(R.string.PrefsEmailString)))
        {
            signInCompleted(sharedPrefs.getString(getString(R.string.PrefsEmailString), ""), sharedPrefs.getString(getString(R.string.PrefsNameString), ""));
        }
        else
        {
            //Set Google login button onclick
            findViewById(R.id.google_sign_in_button).setOnClickListener(this);

            //Google services client initialization
            gClient = new GoogleApiClient.Builder(this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(Plus.API, null)
                    .addScope(Plus.SCOPE_PLUS_LOGIN)
                    .build();
        }

    }

    protected void onStart() {
        super.onStart();

        if (gClient != null) {
            //Initialize connection to Google server

        }
    }

    protected void onStop() {
        super.onStop();


        if (gClient != null && gClient.isConnected()) {
            gClient.disconnect();
        }
    }

    /**
     * Onclick method for facebook button
     * @param view
     */
    public void login_facebook(View view)
    {
        signInCompleted("maegereg@gmail.com", "'Steve'");
    }

    /**
     * Onclick method for google button
     * @param view
     */
    public void login_google(View view)
    {
        gClient.connect();
    }

    /**
     * Onclick method for twitter button
     */
    public void login_twitter(View view)
    {
        signInCompleted("maegereg@gmail.com", "'Steve'");
    }

    /**
     * Method to be called once some sort of sign in has been completed. Sets the username and moves to the next screen
     * @param email
     */
    protected void signInCompleted(String email, String name){
        //Save current user information
        final SharedPreferences.Editor editor = sharedPrefs.edit();
        editor.putString(getString(R.string.PrefsEmailString), email);
        if (name != null)
            editor.putString(getString(R.string.PrefsNameString), name);
        editor.commit();

        JSONObject toSend = new JSONObject();
        try {
            toSend.put("email", email);
            if (name != null)
                toSend.put("name", name);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        final LoginActivity thisref = this;

        class getRegIdTask extends AsyncTask {

            Context context;
            private getRegIdTask(Context context) {
                this.context = context.getApplicationContext();
            }

            @Override
            protected Object doInBackground(Object... params) {
                try {
                    if (gcm == null) {
                        gcm = GoogleCloudMessaging.getInstance(context);
                    }
                    regid = gcm.register(SENDER_ID);
                    editor.putString("RegId", regid);
                    editor.putBoolean("RegSuccess",true);
                    editor.commit();
                    Log.d("IATRegId",regid);
                } catch (IOException e) {
                    editor.putBoolean("RegSuccess",false);
                    Log.d("IATRegId","failed.");
                    Log.d("IATError:", e.getMessage());
//                    Log.d("Error:", e.getCause().toString());
                    e.printStackTrace();

                    finish();
                }
                return new Object();
            }

            @Override
            protected void onPostExecute(Object result) {
                Intent intent = new Intent(thisref, MapActivity.class);
                startActivity(intent);
            }
        }

        class GetIdTask extends PostJsonTask<String> {

            public GetIdTask(String serverAddress, String endpoint) {
                super(serverAddress, endpoint);
            }

            @Override
            protected String parseResponse(String response) {
                //Apparently the id that's returned is NOT in a JSON object...
                return response.replace("\"", "");
            }

            @Override
            protected void onPostExecute(String id) {
                editor.putString(getString(R.string.PrefsIdString), id);
                editor.commit();


                new getRegIdTask(context).execute(null, null, null);
            }
        }
        new GetIdTask(MapActivity.serverAddress, "/api/getuserid").execute(toSend);
    }

    @Override
    public void onConnected(Bundle bundle){

        String email = Plus.AccountApi.getAccountName(gClient);
        String name = Plus.PeopleApi.getCurrentPerson(gClient).getDisplayName();

        Toast.makeText(getApplicationContext(), email+" is connected!", Toast.LENGTH_SHORT).show();
        signInCompleted(email, name);
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    public void onActivityResult(int requestCode, int responseCode, Intent intent) {
        if (requestCode == RC_SIGN_IN) {

            mIntentInProgress = false;

            if (!gClient.isConnecting()) {
                gClient.connect();
            }
        }
    }


        @Override
    public void onConnectionFailed(ConnectionResult result) {
        if (!mIntentInProgress) {
            // Store the ConnectionResult so that we can use it later when the user clicks
            // 'sign-in'.
            mConnectionResult = result;

            resolveSignInError();
        }
    }

    /* A helper method to resolve the current ConnectionResult error. */
    private void resolveSignInError() {
        if (mConnectionResult.hasResolution()) {
            try {
                mIntentInProgress = true;
                mConnectionResult.startResolutionForResult(this, RC_SIGN_IN);
            } catch (IntentSender.SendIntentException e) {
                // The intent was canceled before it was sent.  Return to the default
                // state and attempt to connect to get an updated ConnectionResult.
                mIntentInProgress = false;
                gClient.connect();
            }
        }
    }

    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.google_sign_in_button) {
            login_google(view);
        }
    }

    // For Push
    private boolean checkPlayServices() {
        int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
        if (resultCode != ConnectionResult.SUCCESS) {
            if (GooglePlayServicesUtil.isUserRecoverableError(resultCode)) {
                GooglePlayServicesUtil.getErrorDialog(resultCode, this,
                        PLAY_SERVICES_RESOLUTION_REQUEST).show();
            } else {
                Log.i("Uh oh:", "This device is not supported.");
                finish();
            }
            return false;
        }
        return true;
    }


}
