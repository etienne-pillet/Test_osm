package com.example.epillet.test_osm;

import org.osmdroid.DefaultResourceProxyImpl;
import org.osmdroid.api.IMapController;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.content.IntentSender.SendIntentException;
import android.location.Location;
import android.location.Address;

import com.google.android.gms.location.LocationListener;

import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import org.osmdroid.bonuspack.location.GeocoderNominatim;
import org.osmdroid.bonuspack.overlays.MapEventsOverlay;
import org.osmdroid.bonuspack.overlays.MapEventsReceiver;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.ItemizedIconOverlay;
import org.osmdroid.views.overlay.OverlayItem;
import org.osmdroid.bonuspack.overlays.Marker;
import org.osmdroid.bonuspack.overlays.Polygon;

import android.view.View;
import android.widget.Toast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener, LocationListener ,MapEventsReceiver{

    private static final String TAG = "OsmLocationActivity";

    private static final String KEY_IN_RESOLUTION = "is_in_resolution";
    public ArrayList<Marker> markers = new ArrayList<>();
    /**
     * Request code for auto Google Play Services error resolution.
     */
    protected static final int REQUEST_CODE_RESOLUTION = 1;
    private ItemizedIconOverlay<OverlayItem> locationOverlay;
    public Polygon polygon;
    /**
     * Google API client.
     */
    private GoogleApiClient googleApiClient;

    /**
     * Determines if the client is in a resolution state, and
     * waiting for resolution intent to return.
     */
    private boolean mIsInResolution;
    private MapView mapView;
    private IMapController mapController;
    private DefaultResourceProxyImpl resourceProxy;
    private List<OverlayItem> items;
    private LocationRequest locationRequest;
    List<Address> listOfAddress = null ;
    /**
     * Called when the activity is starting. Restores the activity state.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            mIsInResolution = savedInstanceState.getBoolean(KEY_IN_RESOLUTION, false);
        }

        setContentView(R.layout.layout_main);

        resourceProxy = new DefaultResourceProxyImpl(getApplicationContext());

        items = new ArrayList<>();

        mapView = (MapView) findViewById(R.id.mapview);
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setBuiltInZoomControls(true);
        mapView.setMultiTouchControls(true);
        mapView.setClickable(true);

        mapController = mapView.getController();
        mapController.setZoom(10);

        MapEventsOverlay mapEventsOverlay = new MapEventsOverlay(this,this);

        locationOverlay = new ItemizedIconOverlay<>(items, new Glistener(), resourceProxy);

        mapView.getOverlays().add(0, mapEventsOverlay);
        mapView.getOverlays().add(locationOverlay);
        mapView.invalidate();

        locationRequest = new LocationRequest();
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    /**
     * Called when the Activity is made visible.
     * A connection to Play Services need to be initiated as
     * soon as the activity is visible. Registers {@code ConnectionCallbacks}
     * and {@code OnConnectionFailedListener} on the
     * activities itself.
     */
    @Override
    protected void onStart() {
        super.onStart();
        if (googleApiClient == null) {
            googleApiClient = new GoogleApiClient.Builder(this)
                    .addApi(LocationServices.API)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .build();
        }
        googleApiClient.connect();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (googleApiClient.isConnected()) {
            LocationServices.FusedLocationApi.requestLocationUpdates(
                    googleApiClient, locationRequest, this);
        }
    }

    @Override
    protected void onPause() {
        LocationServices.FusedLocationApi.removeLocationUpdates(
                googleApiClient, this);
        super.onPause();
    }

    /**
     * Called when activity gets invisible. Connection to Play Services needs to
     * be disconnected as soon as an activity is invisible.
     */
    @Override
    protected void onStop() {
        LocationServices.FusedLocationApi.removeLocationUpdates(
                googleApiClient, this);
        if (googleApiClient != null) {
            googleApiClient.disconnect();
        }
        super.onStop();
    }

    /**
     * Saves the resolution state.
     */
    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(KEY_IN_RESOLUTION, mIsInResolution);
    }

    /**
     * Handles Google Play Services resolution callbacks.
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQUEST_CODE_RESOLUTION:
                retryConnecting();
                break;
        }
    }

    private void retryConnecting() {
        mIsInResolution = false;
        if (!googleApiClient.isConnecting()) {
            googleApiClient.connect();
        }
    }

    /**
     * Called when {@code mGoogleApiClient} is connected.
     */
    @Override
    public void onConnected(Bundle connectionHint) {
        Log.i(TAG, "GoogleApiClient connected");
        Location lastLocation = LocationServices.FusedLocationApi.getLastLocation(
                googleApiClient);
        updateMap(lastLocation);
        LocationServices.FusedLocationApi.requestLocationUpdates(
                googleApiClient, locationRequest, this);
    }

    private void updateMap(Location location) {
        if (location != null) {
            int lat = (int) (location.getLatitude() * 1E6);
            int lng = (int) (location.getLongitude() * 1E6);
            GeoPoint gpt = new GeoPoint(lat, lng);
            mapController.setCenter(gpt);
            locationOverlay.addItem( new OverlayItem(getString(R.string.location), (getString(R.string.location)), gpt));
            mapView.invalidate();
        }
    }
    /**
     * Called when {@code mGoogleApiClient} connection is suspended.
     */
    @Override
    public void onConnectionSuspended(int cause) {
        Log.i(TAG, "GoogleApiClient connection suspended");
        retryConnecting();
    }

    /**
     * Called when {@code mGoogleApiClient} is trying to connect but failed.
     * Handle {@code result.getResolution()} if there is a resolution
     * available.
     */
    @Override
    public void onConnectionFailed(ConnectionResult result) {
        Log.i(TAG, "GoogleApiClient connection failed: " + result.toString());
        if (!result.hasResolution()) {
            // Show a localized error dialog.
            GooglePlayServicesUtil.getErrorDialog(
                    result.getErrorCode(), this, 0, new OnCancelListener() {
                        @Override
                        public void onCancel(DialogInterface dialog) {
                            retryConnecting();
                        }
                    }).show();
            return;
        }
        // If there is an existing resolution error being displayed or a resolution
        // activity has started before, do nothing and wait for resolution
        // progress to be completed.
        if (mIsInResolution) {
            return;
        }
        mIsInResolution = true;
        try {
            result.startResolutionForResult(this, REQUEST_CODE_RESOLUTION);
        } catch (SendIntentException e) {
            Log.e(TAG, "Exception while starting resolution activity", e);
            retryConnecting();
        }
    }

    /**
     * Listener for adding points, gps referenced. Make a polyline with it.
     */
    @Override
    public void onLocationChanged(Location location) {
        updateMap(location);
    }

    @Override
    public boolean singleTapConfirmedHelper(GeoPoint p) {
        Marker newMarker = new Marker(mapView);
        newMarker.setPosition(p);
        newMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        newMarker.setIcon(this.getResources().getDrawable(R.drawable.marker_icon));
        newMarker.setDraggable(true);
        getAddress(new MarkerPos(newMarker,p));
        newMarker.setOnMarkerDragListener(new Marker.OnMarkerDragListener() {
            @Override
            public void onMarkerDrag(Marker marker) {
                markerToArray(markers);
            }

            @Override
            public void onMarkerDragEnd(Marker marker) {
                markerToArray(markers);
            }

            @Override
            public void onMarkerDragStart(Marker marker) {
                marker.closeInfoWindow();
            }
        });
        mapView.getOverlays().add(newMarker);
        markers.add(newMarker);
        markerToArray(markers);
        return true;
    }

    @Override
    public boolean longPressHelper(GeoPoint geoPoint) {
        return true;
    }
    /**
     * Dragging markers listeners.
     * Force the rerending of address and of the polygone
     */
    class Glistener implements ItemizedIconOverlay.OnItemGestureListener<OverlayItem> {
        @Override
        public boolean onItemLongPress(int index, OverlayItem item) {
            return false;
        }

        @Override
        public boolean onItemSingleTapUp(int index, OverlayItem item) {
            return true;
        }
    }
    public void drawPolygon(ArrayList<GeoPoint> points, Boolean refresh){
        if (points.size()>=2) {
            if (polygon!=null && refresh)
                mapView.getOverlays().remove(polygon);
            //Instantiates a new Polygon object and adds points to define a rectangle

            polygon = new Polygon(this);
            polygon.setPoints(points);
            if(polygon!=null && refresh)
                mapView.getOverlays().remove(polygon);
            // Get back the mutable Polygon
            mapView.getOverlays().add(1,polygon);
        }
    }
    /**
     * Take a list of marker and build a polygon from their position
     * @param markers array of markers
     */
    public void markerToArray(ArrayList<Marker> markers){
        ArrayList<GeoPoint> points = new ArrayList<GeoPoint>();
        for (Marker mark:markers){
            if(mark!=null){
                points.add(mark.getPosition());
            }
        }
        drawPolygon(points, true);
        mapView.invalidate();
    }

    private class GetAddressTask extends AsyncTask<MarkerPos, Void, MarkerPos> {
        Context mContext;
        public GetAddressTask(Context context) {
            super();
            mContext = context;
        }


        /**
         * Get a Geocoder instance, get the latitude and longitude
         * look up the address, and return it
         *
         * @params params One or more Latlng objects
         * @return A string containing the address of the current
         * location, or an empty string if no address can be found,
         * or an error message
         */
        @Override
        protected MarkerPos doInBackground(MarkerPos... params) {
            GeocoderNominatim geocoder = new GeocoderNominatim(mContext, Locale.getDefault());
            // Get the current location from the input parameter list
            MarkerPos markpos = new MarkerPos(params[0]);
            GeoPoint loc = markpos.getPosition();
            // Create a list to contain the result address
            List<Address> addresses = null;
            try {
                /*
				* Return 1 address.
				*/
                addresses = geocoder.getFromLocation(loc.getLatitude(),
                        loc.getLongitude(), 1);
            } catch (IOException e1) {
                Log.e("LocationSampleActivity","IO Exception in getFromLocation()");
                e1.printStackTrace();
                markpos.setAdresse("Impossible d'avoir l'adresse. Vérifier connexion réseau");
                return markpos;
            } catch (IllegalArgumentException e2) {
                // Error message to post in the log
                String errorString = "Illegal arguments " +
                        Double.toString(loc.getLatitude()) +
                        " , " +
                        Double.toString(loc.getLongitude()) +
                        " passed to address service";
                Log.e("LocationSampleActivity", errorString);
                e2.printStackTrace();
                markpos.setAdresse(errorString);
                return markpos;
            }
            // If the reverse geocode returned an address
            if (addresses != null && addresses.size() > 0) {
                // Get the first address
                Address address = addresses.get(0);
                 /*
				* Format the first line of address (if available),
				* city, and country name.
				*/
                String addressText = String.format(
                        "%s, %s, %s",
                        // If there's a street address, add it
                        address.getMaxAddressLineIndex() > 0 ?
                                address.getAddressLine(0) : "",
                        // Locality is usually a city
                        address.getLocality(),
                        // The country of the address
                        address.getCountryName());
                // Return the text
                markpos.setAdresse(addressText);
                return markpos;
            } else {
                markpos.setAdresse("No address found");
                return markpos;
            }
        }

        /**
         * A method that's called once doInBackground() completes. Turn
         * off the indeterminate activity indicator and set
         * the text of the UI element that shows the address. If the
         * lookup failed, display the error message.
         */
        @SuppressWarnings("null")
        @Override
        protected void onPostExecute(MarkerPos markpos) {
            // Set activity indicator visibility to "gone"

            // Register the results of the lookup.
            markpos.getMarker().setSnippet(markpos.getAdresse());
            markpos.getMarker().showInfoWindow();

            markerToArray(markers);
        }
    }

    /**
     *
     * @param markpos The view object associated with this method,
     * in this case a Button.
     */
    public void getAddress(MarkerPos markpos) {
        // Ensure that a Geocoder services is available
        if (Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.GINGERBREAD
                &&
                GeocoderNominatim.isPresent()) {
            // Show the activity indicator

             /*
			* Reverse geocoding is long-running and synchronous.
			* Run it on a background thread.
			* Pass the current location to the background task.
			* When the task finishes,
			* onPostExecute() displays the address.
			*/
            (new GetAddressTask(this)).execute(markpos);
        }
    }
}