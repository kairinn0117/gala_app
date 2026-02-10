package com.example.galafunctions;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

public class MainActivity extends AppCompatActivity {

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    private MaterialCardView ongoingBar;
    private TextView tvOngoingTitle, tvOngoingSub;

    private ListenerRegistration ongoingListener;
    private String ongoingTripId = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        ongoingBar = findViewById(R.id.ongoingBar);
        tvOngoingTitle = findViewById(R.id.tvOngoingTitle);
        tvOngoingSub = findViewById(R.id.tvOngoingSub);

        ongoingBar.setOnClickListener(v -> {
            if (ongoingTripId == null) return;
            Intent i = new Intent(MainActivity.this, StartGala.class);
            i.putExtra("tripId", ongoingTripId);
            startActivity(i);
        });

        BottomNavigationView bnv1 = findViewById(R.id.bnv1);

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragmentContainerView2, new Home())
                    .commit();
        }

        // ✅ tip: no addToBackStack for bottom nav
        bnv1.setOnItemSelectedListener(item -> {
            FragmentManager fragmentManager = getSupportFragmentManager();

            if (item.getItemId() == R.id.home) {
                fragmentManager.beginTransaction()
                        .replace(R.id.fragmentContainerView2, Home.class, null)
                        .setReorderingAllowed(true)
                        .commit();
                return true;

            } else if (item.getItemId() == R.id.map) {
                fragmentManager.beginTransaction()
                        .replace(R.id.fragmentContainerView2, Maps.class, null)
                        .setReorderingAllowed(true)
                        .commit();
                return true;

            } else if (item.getItemId() == R.id.gallery) {
                fragmentManager.beginTransaction()
                        .replace(R.id.fragmentContainerView2, Gallery.class, null)
                        .setReorderingAllowed(true)
                        .commit();
                return true;

            } else if (item.getItemId() == R.id.settings) {
                fragmentManager.beginTransaction()
                        .replace(R.id.fragmentContainerView2, Settings.class, null)
                        .setReorderingAllowed(true)
                        .commit();
                return true;
            }
            return false;
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        attachOngoingListener();
    }

    @Override
    protected void onStop() {
        super.onStop();
        detachOngoingListener();
    }

    private void attachOngoingListener() {
        detachOngoingListener();

        if (auth.getCurrentUser() == null) {
            hideOngoingBar();
            return;
        }

        String uid = auth.getCurrentUser().getUid();

        // ✅ no orderBy to avoid index issue
        ongoingListener = db.collection("users")
                .document(uid)
                .collection("trips")
                .whereEqualTo("status", "IN_PROGRESS")
                .whereEqualTo("is_archived", false)
                .limit(1)
                .addSnapshotListener((snap, e) -> {
                    if (e != null || snap == null || snap.isEmpty()) {
                        hideOngoingBar();
                        return;
                    }

                    DocumentSnapshot doc = snap.getDocuments().get(0);
                    ongoingTripId = doc.getId();

                    String tripName = doc.getString("trip_name");
                    String location = doc.getString("location");

                    if (tvOngoingTitle != null) {
                        tvOngoingTitle.setText(!isBlank(tripName) ? ("Ongoing Gala: " + tripName) : "Ongoing Gala");
                    }
                    if (tvOngoingSub != null) {
                        String sub = "Tap to resume";
                        if (!isBlank(location)) sub += " • " + location;
                        tvOngoingSub.setText(sub);
                    }

                    showOngoingBar();
                });
    }

    private void detachOngoingListener() {
        if (ongoingListener != null) {
            ongoingListener.remove();
            ongoingListener = null;
        }
    }

    private void showOngoingBar() {
        if (ongoingBar != null) ongoingBar.setVisibility(View.VISIBLE);
    }

    private void hideOngoingBar() {
        ongoingTripId = null;
        if (ongoingBar != null) ongoingBar.setVisibility(View.GONE);
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}