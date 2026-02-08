package com.example.galafunctions;

import android.content.Intent;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.util.List;
import java.util.Locale;

public class MapPickerActivity extends AppCompatActivity {

    public static final String EXTRA_RESULT_ADDRESS = "result_address";
    public static final String EXTRA_RESULT_LAT = "result_lat";
    public static final String EXTRA_RESULT_LNG = "result_lng";

    private GoogleMap mMap;
    private Marker marker;

    private TextView tvPickedAddress;
    private LatLng pickedLatLng = null;
    private String pickedAddress = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Edge-to-edge SAFE setup
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_map_picker);

        // Prevent NPE if root id is missing
        if (findViewById(R.id.main) != null) {
            ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
                return insets;
            });
        }

        // Bind views
        tvPickedAddress = findViewById(R.id.tvPickedAddress);
        Button btnCancel = findViewById(R.id.btnCancel);
        Button btnUseLocation = findViewById(R.id.btnUseLocation);

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

        // ===== MAP INITIALIZATION (SAFE) =====
        SupportMapFragment mapFragment =
                (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);

        if (mapFragment == null) {
            Toast.makeText(
                    this,
                    "Map fragment not found. Check activity_map_picker.xml (id=@id/map)",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        mapFragment.getMapAsync(googleMap -> {
            mMap = googleMap;

            // Default camera (Manila)
            LatLng manila = new LatLng(14.5995, 120.9842);
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(manila, 11f));

            // Tap to drop pin
            mMap.setOnMapClickListener(this::onMapTapped);
        });
    }

    private void onMapTapped(LatLng latLng) {
        if (mMap == null) return;

        pickedLatLng = latLng;

        if (marker != null) marker.remove();
        marker = mMap.addMarker(
                new MarkerOptions()
                        .position(latLng)
                        .title("Selected Location")
        );

        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16f));

        pickedAddress = reverseGeocode(latLng);

        if (pickedAddress == null || pickedAddress.trim().isEmpty()) {
            tvPickedAddress.setText(
                    "Lat: " + latLng.latitude + ", Lng: " + latLng.longitude
            );
        } else {
            tvPickedAddress.setText(pickedAddress);
        }
    }

    private String reverseGeocode(LatLng latLng) {
        try {
            Geocoder geocoder = new Geocoder(this, Locale.getDefault());
            List<Address> list =
                    geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1);

            if (list != null && !list.isEmpty()) {
                return list.get(0).getAddressLine(0);
            }
        } catch (Exception e) {
            e.printStackTrace(); // for debugging
        }
        return "";
    }
}