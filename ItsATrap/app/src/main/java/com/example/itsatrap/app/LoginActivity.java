package com.example.itsatrap.app;

import android.app.Activity;
import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.plus.Plus;

/**
 * Created by carissa on 5/10/14.
 * Sample code drawn from https://developers.google.com/+/mobile/android/getting-started
 * and https://developers.google.com/+/mobile/android/sign-in
 */
public class LoginActivity extends Activity implements OnConnectionFailedListener, ConnectionCallbacks, View.OnClickListener
{


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


    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        sharedPrefs = getSharedPreferences(getString(R.string.SharedPrefName), 0);
//        if (sharedPrefs.contains(getString(R.string.PrefsEmailString)))
//        {
//            signInCompleted(sharedPrefs.getString(getString(R.string.PrefsEmailString), ""));
//        }
//        else
//        {
            //Set Google login button onclick
            findViewById(R.id.google_sign_in_button).setOnClickListener(this);

            //Google services client initialization
            gClient = new GoogleApiClient.Builder(this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(Plus.API, null)
                    .addScope(Plus.SCOPE_PLUS_LOGIN)
                    .build();
//        }
    }

    protected void onStart()
    {
        super.onStart();

        if (gClient != null)
        {
            //Initialize connection to Google server

        }
    }

    protected void onStop()
    {
        super.onStop();


        if (gClient != null && gClient.isConnected())
        {
            gClient.disconnect();
        }
    }

    /**
     * Onclick method for facebook button
     * @param view
     */
    public void login_facebook(View view)
    {
        signInCompleted("");
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
        signInCompleted("");
    }

    /**
     * Method to be called once some sort of sign in has been completed. Sets the username and moves to the next screen
     * @param email
     */
    protected void signInCompleted(String email)
    {
        //Save current user information
        SharedPreferences.Editor editor = sharedPrefs.edit();
        editor.putString(getString(R.string.PrefsEmailString), email);
        editor.commit();

        Intent intent = new Intent(this, MapActivity.class);
        startActivity(intent);
    }

    @Override
    public void onConnected(Bundle bundle)
    {

        String email = Plus.AccountApi.getAccountName(gClient);

        Toast.makeText(getApplicationContext(), email+" is connected!", Toast.LENGTH_SHORT).show();
        signInCompleted(email);
    }

    @Override
    public void onConnectionSuspended(int i)
    {

    }

    public void onActivityResult(int requestCode, int responseCode, Intent intent)
    {
        if (requestCode == RC_SIGN_IN) {

            mIntentInProgress = false;

            if (!gClient.isConnecting()) {
                gClient.connect();
            }
        }
    }


        @Override
    public void onConnectionFailed(ConnectionResult result)
        {
        if (!mIntentInProgress) {
            // Store the ConnectionResult so that we can use it later when the user clicks
            // 'sign-in'.
            mConnectionResult = result;

            resolveSignInError();
        }
    }

    /* A helper method to resolve the current ConnectionResult error. */
    private void resolveSignInError()
    {
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
    public void onClick(View view)
    {
        if (view.getId() == R.id.google_sign_in_button)
        {
            login_google(view);
        }
    }
}
