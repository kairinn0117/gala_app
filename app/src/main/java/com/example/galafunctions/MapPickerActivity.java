package com.example.galafunctions;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

// ✅ Places
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.AutocompletePrediction;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.api.net.FetchPlaceRequest;
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest;
import com.google.android.libraries.places.api.net.PlacesClient;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class MapPickerActivity extends AppCompatActivity {

    public static final String EXTRA_RESULT_ADDRESS = "result_address";
    public static final String EXTRA_RESULT_LAT = "result_lat";
    public static final String EXTRA_RESULT_LNG = "result_lng";

    private static final int REQ_LOC = 2001;

    private GoogleMap mMap;
    private Marker marker;

    private TextView tvPickedAddress;
    private AutoCompleteTextView actSearchPlace;
    private ImageButton btnSearch, btnMyLocation, btnDirections, btnCancel, btnUseLocation;

    private LatLng pickedLatLng = null;
    private String pickedAddress = "";

    private FusedLocationProviderClient fused;

    private PlacesClient placesClient;

    private final ArrayList<AutocompletePrediction> predictionList = new ArrayList<>();
    private ArrayAdapter<String> predictionsAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_map_picker);

        if (findViewById(R.id.main) != null) {
            ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
                return insets;
            });
        }

        fused = LocationServices.getFusedLocationProviderClient(this);

        // Bind views
        tvPickedAddress = findViewById(R.id.tvPickedAddress);
        btnCancel = findViewById(R.id.btnCancel);
        btnUseLocation = findViewById(R.id.btnUseLocation);

        actSearchPlace = findViewById(R.id.actSearchPlace);
        btnSearch = findViewById(R.id.btnSearch);
        btnMyLocation = findViewById(R.id.btnMyLocation);
        btnDirections = findViewById(R.id.btnDirections);

        updateDirectionsButton(false);

        btnCancel.setOnClickListener(v -> finish());

        btnUseLocation.setOnClickListener(v -> {
            if (pickedLatLng == null) {
                Toast.makeText(this, "Tap the map to pick a location first.", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent data = new Intent();
            data.putExtra(EXTRA_RESULT_ADDRESS, pickedAddress);
            data.putExtra(EXTRA_RESULT_LAT, pickedLatLng.latitude);
            data.putExtra(EXTRA_RESULT_LNG, pickedLatLng.longitude);
            setResult(RESULT_OK, data);
            finish();
        });

        btnMyLocation.setOnClickListener(v -> goToMyLocation());
        btnDirections.setOnClickListener(v -> openDirections());

        // ✅ Init Places
        String apiKey = getString(R.string.google_maps_key);
        if (!Places.isInitialized()) {
            Places.initialize(getApplicationContext(), apiKey);
        }
        placesClient = Places.createClient(this);

        predictionsAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, new ArrayList<>());
        actSearchPlace.setAdapter(predictionsAdapter);
        actSearchPlace.setThreshold(2);

        actSearchPlace.addTextChangedListener(SimpleTextWatcher.afterChanged(s -> {
            String q = s.toString().trim();
            if (q.length() < 2) return;
            fetchPredictions(q);
        }));

        actSearchPlace.setOnItemClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= predictionList.size()) return;
            AutocompletePrediction chosen = predictionList.get(position);
            fetchPlaceAndMove(chosen.getPlaceId(), chosen);
        });

        btnSearch.setOnClickListener(v -> {
            if (!actSearchPlace.isPopupShowing()) actSearchPlace.showDropDown();
        });

        actSearchPlace.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                if (!actSearchPlace.isPopupShowing()) actSearchPlace.showDropDown();
                return true;
            }
            return false;
        });

        SupportMapFragment mapFragment =
                (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);

        if (mapFragment != null) {
            mapFragment.getMapAsync(googleMap -> {
                mMap = googleMap;
                LatLng manila = new LatLng(14.5995, 120.9842);
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(manila, 11f));
                mMap.setOnMapClickListener(latLng -> {
                    pickedAddress = "";
                    onMapTapped(latLng);
                });
                goToMyLocation();
            });
        }
    }

    private void updateDirectionsButton(boolean enabled) {
        btnDirections.setEnabled(enabled);
        if (enabled) {
            btnDirections.setImageResource(R.drawable.directions);
        } else {
            btnDirections.setImageResource(R.drawable.directions_disabled);
        }
    }

    private void fetchPredictions(String query) {
        FindAutocompletePredictionsRequest request =
                FindAutocompletePredictionsRequest.builder()
                        .setQuery(query)
                        .build();

        placesClient.findAutocompletePredictions(request)
                .addOnSuccessListener(response -> {
                    predictionList.clear();
                    predictionList.addAll(response.getAutocompletePredictions());

                    ArrayList<String> display = new ArrayList<>();
                    for (AutocompletePrediction p : predictionList) {
                        String primary = (p.getPrimaryText(null) != null) ? p.getPrimaryText(null).toString() : "";
                        String secondary = (p.getSecondaryText(null) != null) ? p.getSecondaryText(null).toString() : "";

                        if (!TextUtils.isEmpty(primary) && !TextUtils.isEmpty(secondary)) {
                            display.add(primary + " • " + secondary);
                        } else if (!TextUtils.isEmpty(primary)) {
                            display.add(primary);
                        } else {
                            display.add(secondary);
                        }
                    }

                    predictionsAdapter.clear();
                    predictionsAdapter.addAll(display);
                    predictionsAdapter.notifyDataSetChanged();

                    if (!display.isEmpty()) actSearchPlace.showDropDown();
                })
                .addOnFailureListener(e -> {});
    }

    private void fetchPlaceAndMove(String placeId, AutocompletePrediction chosen) {
        List<Place.Field> fields = Arrays.asList(
                Place.Field.LAT_LNG,
                Place.Field.NAME,
                Place.Field.ADDRESS
        );

        FetchPlaceRequest request = FetchPlaceRequest.builder(placeId, fields).build();

        placesClient.fetchPlace(request)
                .addOnSuccessListener(response -> {
                    Place place = response.getPlace();
                    LatLng latLng = place.getLatLng();
                    if (latLng == null) {
                        Toast.makeText(this, "No coordinates found for that place.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    String name = place.getName();
                    String address = place.getAddress();

                    String finalLabel;
                    if (!TextUtils.isEmpty(name) && !TextUtils.isEmpty(address)) {
                        finalLabel = name + " • " + address;
                    } else if (!TextUtils.isEmpty(name)) {
                        finalLabel = name;
                    } else if (!TextUtils.isEmpty(address)) {
                        finalLabel = address;
                    } else {
                        finalLabel = chosen.getFullText(null) != null ? chosen.getFullText(null).toString() : "";
                    }

                    pickedAddress = finalLabel;
                    onMapTapped(latLng);

                    if (mMap != null) mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16f));
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Place fetch failed: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
    }

    private void goToMyLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                    REQ_LOC
            );
            return;
        }

        fused.getLastLocation()
                .addOnSuccessListener(loc -> {
                    if (loc == null || mMap == null) {
                        Toast.makeText(this, "Can't get current location.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    LatLng me = new LatLng(loc.getLatitude(), loc.getLongitude());
                    pickedAddress = "";
                    onMapTapped(me);
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(me, 16f));
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Location error: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
    }

    private void onMapTapped(LatLng latLng) {
        if (mMap == null) return;

        pickedLatLng = latLng;

        if (marker != null) marker.remove();
        marker = mMap.addMarker(new MarkerOptions().position(latLng).title("Selected Location"));

        if (TextUtils.isEmpty(pickedAddress)) {
            pickedAddress = reverseGeocode(latLng);
        }

        if (TextUtils.isEmpty(pickedAddress)) {
            tvPickedAddress.setText("Lat: " + latLng.latitude + ", Lng: " + latLng.longitude);
        } else {
            tvPickedAddress.setText(pickedAddress);
            if (marker != null) marker.setTitle(pickedAddress);
        }

        updateDirectionsButton(true);
    }

    private String reverseGeocode(LatLng latLng) {
        try {
            if (!Geocoder.isPresent()) return "";
            Geocoder geocoder = new Geocoder(this, Locale.getDefault());
            List<Address> list = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1);
            if (list != null && !list.isEmpty()) {
                String line = list.get(0).getAddressLine(0);
                return (line != null) ? line : "";
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    private void openDirections() {
        if (pickedLatLng == null) {
            Toast.makeText(this, "Pick a location first.", Toast.LENGTH_SHORT).show();
            return;
        }

        String uri = "google.navigation:q=" + pickedLatLng.latitude + "," + pickedLatLng.longitude;
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
        intent.setPackage("com.google.android.apps.maps");

        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivity(intent);
        } else {
            String web = "https://www.google.com/maps/dir/?api=1&destination="
                    + pickedLatLng.latitude + "," + pickedLatLng.longitude;
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(web)));
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQ_LOC) {
            boolean granted = false;
            for (int r : grantResults) {
                if (r == PackageManager.PERMISSION_GRANTED) { granted = true; break; }
            }
            if (granted) goToMyLocation();
            else Toast.makeText(this, "Location permission denied.", Toast.LENGTH_SHORT).show();
        }
    }
}