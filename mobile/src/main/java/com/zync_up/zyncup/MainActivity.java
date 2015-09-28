package com.zync_up.zyncup;

import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.getbase.floatingactionbutton.FloatingActionButton;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.model.LatLng;

public class  MainActivity extends FragmentActivity
        implements ConnectionCallbacks,OnConnectionFailedListener {

    private static final float DEFAULT_ZOOM_LEVEL = 15;

    private GoogleMap map; // Might be null if Google Play services APK is not available.
    private GoogleApiClient googleApiClient;
    private FrameLayout tintLayout;
    private DrawerLayout drawerLayout;
    private ObjectAnimator tintAnimation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (!isLoggedIn()) {
            Intent intent = new Intent(MainActivity.this, LoginActivity.class);
            startActivity(intent);
            finish();
            return;
        }

        buildGoogleApiClient();

        setupFab();

        tintLayout = (FrameLayout) findViewById(R.id.tint_layout);
        drawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawerLayout.setFitsSystemWindows(true);

        SearchBar searchBar = (SearchBar) findViewById(R.id.searchbox);
        searchBar.enableVoiceRecognition(this);

        searchBar.setMenuListener(new SearchBar.MenuListener() {
            @Override
            public void onMenuClick() {
                drawerLayout.openDrawer(GravityCompat.START);
            }
        });

        searchBar.setSearchListener(new SearchBar.SearchListener() {
            int tintColor = Color.parseColor("#B0000000");

            @Override
            public void onSearchOpened() {
                tintAnimation = ObjectAnimator.ofInt(
                        tintLayout,
                        "backgroundColor",
                        Color.TRANSPARENT,
                        tintColor)
                        .setDuration(300);
                tintAnimation.setEvaluator(new ArgbEvaluator());
                tintAnimation.start();
                //TODO: close view when outside the searchbar is clicked.

            }

            @Override
            public void onSearchClosed() {
                tintAnimation.reverse();
            }

            @Override
            public void onSearchTermChanged(String term) {

            }

            @Override
            public void onSearch(String searchTerm) {
                Toast.makeText(MainActivity.this, searchTerm + " Searched", Toast.LENGTH_LONG).show();
            }

            @Override
            public void onSearchCleared() {
                //Called when the clear button is clicked
            }

        });

        map = ((MapFragment) getFragmentManager()
                .findFragmentById(R.id.fragment_map))
                .getMap();

    }

    @Override
    protected void onStart() {
        super.onStart();
        googleApiClient.connect();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    protected synchronized void buildGoogleApiClient() {
        googleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
    }

    @Override
    public void onConnected(Bundle connectionHint) {
        Location lastLocation = LocationServices.FusedLocationApi.getLastLocation(
                googleApiClient);
        if (lastLocation != null) {
            LatLng latLng = new LatLng(lastLocation.getLatitude(), lastLocation.getLongitude());
            //Move the camera instantly to hamburg with a zoom of 15.
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, DEFAULT_ZOOM_LEVEL));

            // Zoom in, animating the camera.
            map.animateCamera(CameraUpdateFactory.zoomTo(DEFAULT_ZOOM_LEVEL), 2000, null);
        }
    }

    @Override
    public void onConnectionSuspended(int cause) {
        // The connection has been interrupted.
        // Disable any UI components that depend on Google APIs
        // until onConnected() is called.
    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        // This callback is important for handling errors that
        // may occur while attempting to connect with Google.
        //
        // More about this in the 'Handle Connection Failures' section.
    }

    private void setupFab() {
        FloatingActionButton messageButton = (FloatingActionButton) findViewById(R.id.fab_message);
        messageButton.setTitle("Message");
        messageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SharedPreferences sharedPreferences = getSharedPreferences("PREFS", MODE_PRIVATE);
                sharedPreferences.edit().clear().apply();
            }
        });

        FloatingActionButton zyncButton = (FloatingActionButton) findViewById(R.id.fab_zyncup);
        zyncButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

            }
        });

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onStop() {
        googleApiClient.disconnect();
        super.onStop();
    }

    public boolean isLoggedIn(){
        SharedPreferences sharedPreferences = getSharedPreferences("PREFS", MODE_PRIVATE);
        return sharedPreferences != null && sharedPreferences.getBoolean("logged_in", false);
    }
}
