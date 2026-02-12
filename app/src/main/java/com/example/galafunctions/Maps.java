package com.example.galafunctions;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

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

public class Maps extends Fragment {

    private static final int REQ_LOC = 2001;

    private GoogleMap mMap;
    private Marker marker;

    private TextView tvPickedAddress;
    private AutoCompleteTextView actSearchPlace;
    private Button btnSearch, btnMyLocation, btnDirections;

    private LatLng pickedLatLng = null;
    private String pickedAddress = "";

    private FusedLocationProviderClient fused;

    private PlacesClient placesClient;

    private final ArrayList<AutocompletePrediction> predictionList = new ArrayList<>();
    private ArrayAdapter<String> predictionsAdapter;

    public Maps() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_maps, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (!isAdded()) return;

        fused = LocationServices.getFusedLocationProviderClient(requireActivity());

        tvPickedAddress = view.findViewById(R.id.tvPickedAddress);
        actSearchPlace = view.findViewById(R.id.actSearchPlace);
        btnSearch = view.findViewById(R.id.btnSearch);
        btnMyLocation = view.findViewById(R.id.btnMyLocation);
        btnDirections = view.findViewById(R.id.btnDirections);

        btnDirections.setEnabled(false);

        // ✅ Init Places (use your google_maps_key)
        String apiKey = getString(R.string.google_maps_key);
        if (!Places.isInitialized()) {
            Places.initialize(requireContext().getApplicationContext(), apiKey);
        }
        placesClient = Places.createClient(requireContext());

        // ✅ Dropdown adapter
        predictionsAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                new ArrayList<>());
        actSearchPlace.setAdapter(predictionsAdapter);
        actSearchPlace.setThreshold(2);

        // typing -> predictions
        actSearchPlace.addTextChangedListener(SimpleTextWatcher.afterChanged(s -> {
            String q = s.toString().trim();
            if (q.length() < 2) return;
            fetchPredictions(q);
        }));

        // click suggestion
        actSearchPlace.setOnItemClickListener((parent, v, position, id) -> {
            if (position < 0 || position >= predictionList.size()) return;
            AutocompletePrediction chosen = predictionList.get(position);
            fetchPlaceAndMove(chosen.getPlaceId(), chosen);
        });

        // Search button -> show dropdown
        btnSearch.setOnClickListener(v -> {
            if (!actSearchPlace.isPopupShowing()) actSearchPlace.showDropDown();
        });

        // keyboard search -> show dropdown
        actSearchPlace.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                if (!actSearchPlace.isPopupShowing()) actSearchPlace.showDropDown();
                return true;
            }
            return false;
        });

        btnMyLocation.setOnClickListener(v -> goToMyLocation());
        btnDirections.setOnClickListener(v -> openDirections());

        // ✅ Map init
        SupportMapFragment mapFragment =
                (SupportMapFragment) getChildFragmentManager().findFragmentById(R.id.map);

        if (mapFragment == null) {
            Toast.makeText(requireContext(),
                    "Map fragment not found. Check fragment_maps.xml (id=@id/map)",
                    Toast.LENGTH_LONG).show();
            return;
        }

        mapFragment.getMapAsync(googleMap -> {
            mMap = googleMap;

            LatLng manila = new LatLng(14.5995, 120.9842);
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(manila, 11f));

            mMap.setOnMapClickListener(latLng -> {
                pickedAddress = "";
                onMapTapped(latLng);
            });

            // Optional: auto go to location on open
            goToMyLocation();
        });
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
                .addOnFailureListener(e -> {
                    // silent
                });
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
                        Toast.makeText(requireContext(), "No coordinates found.", Toast.LENGTH_SHORT).show();
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
                        Toast.makeText(requireContext(), "Place fetch failed: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
    }

    private void goToMyLocation() {
        if (!isAdded()) return;

        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            requestPermissions(
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                    REQ_LOC
            );
            return;
        }

        fused.getLastLocation()
                .addOnSuccessListener(loc -> {
                    if (loc == null || mMap == null) {
                        Toast.makeText(requireContext(), "Can't get current location.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    LatLng me = new LatLng(loc.getLatitude(), loc.getLongitude());
                    pickedAddress = "";
                    onMapTapped(me);
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(me, 16f));
                })
                .addOnFailureListener(e ->
                        Toast.makeText(requireContext(), "Location error: " + e.getMessage(), Toast.LENGTH_SHORT).show()
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

        btnDirections.setEnabled(true);
    }

    private String reverseGeocode(LatLng latLng) {
        try {
            if (!Geocoder.isPresent()) return "";
            Geocoder geocoder = new Geocoder(requireContext(), Locale.getDefault());
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
        if (!isAdded()) return;

        if (pickedLatLng == null) {
            Toast.makeText(requireContext(), "Pick a location first.", Toast.LENGTH_SHORT).show();
            return;
        }

        String uri = "google.navigation:q=" + pickedLatLng.latitude + "," + pickedLatLng.longitude;
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
        intent.setPackage("com.google.android.apps.maps");

        if (intent.resolveActivity(requireActivity().getPackageManager()) != null) {
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
            else if (isAdded()) Toast.makeText(requireContext(), "Location permission denied.", Toast.LENGTH_SHORT).show();
        }
    }
}
