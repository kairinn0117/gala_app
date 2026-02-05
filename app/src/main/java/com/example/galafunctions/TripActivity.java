package com.example.galafunctions;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class TripActivity extends AppCompatActivity {

    private ImageView imgCover;
    private TextView tvTripName, tvTripDateTime, tvTripLocation;

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_trip);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Firebase
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Bind to CLASS FIELDS (wag mag redeclare ng local variables)
        imgCover = findViewById(R.id.imgCover);
        tvTripName = findViewById(R.id.tvTripName);
        tvTripDateTime = findViewById(R.id.tvTripDateTime);
        tvTripLocation = findViewById(R.id.tvTripLocation);

        Button btnEditTrip = findViewById(R.id.btnEditTrip);
        RecyclerView rvDestinations = findViewById(R.id.rvDestinations);

        Button btnAddDestination = findViewById(R.id.btnAddDestination);
        Button btnStartTrip = findViewById(R.id.btnStartTrip);
        Button btnBackTrip = findViewById(R.id.btnBackTrip);

        // ✅ Load trip details from Firestore (STEP 4)
        loadTripDetails();

        btnStartTrip.setOnClickListener(v -> {
            Intent intent = new Intent(TripActivity.this, StartGala.class);
            startActivity(intent);
            finish();
        });

        btnBackTrip.setOnClickListener(v -> {
            finish(); // returns to HomeFragment
        });

        btnAddDestination.setOnClickListener(v -> {
            Intent intent = new Intent(TripActivity.this, CreateDestination.class);
            startActivity(intent);
        });
    }

    private void loadTripDetails() {
        String tripId = getIntent().getStringExtra("tripId");

        if (tripId == null || tripId.trim().isEmpty()) {
            Toast.makeText(this, "Trip not found (missing tripId).", Toast.LENGTH_SHORT).show();
            return;
        }

        if (auth.getCurrentUser() == null) {
            Toast.makeText(this, "Please login first.", Toast.LENGTH_SHORT).show();
            return;
        }

        String uid = auth.getCurrentUser().getUid();

        db.collection("users")
                .document(uid)
                .collection("trips")
                .document(tripId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        Toast.makeText(this, "Trip not found.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    String name = doc.getString("trip_name");
                    String location = doc.getString("location");
                    String date = doc.getString("date");
                    String time = doc.getString("time");
                    String coverUrl = doc.getString("cover_url");

                    tvTripName.setText(name != null ? name : "");
                    tvTripLocation.setText(location != null ? location : "");
                    tvTripDateTime.setText((date != null ? date : "") + " • " + (time != null ? time : ""));

                    if (coverUrl != null && !coverUrl.trim().isEmpty()) {
                        loadImageFromUrl(coverUrl);
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Load failed: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
    }

    private void loadImageFromUrl(String imageUrl) {
        new Thread(() -> {
            try {
                java.net.URL url = new java.net.URL(imageUrl);
                java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
                connection.setDoInput(true);
                connection.connect();

                java.io.InputStream input = connection.getInputStream();
                android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeStream(input);

                runOnUiThread(() -> imgCover.setImageBitmap(bitmap));

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
}