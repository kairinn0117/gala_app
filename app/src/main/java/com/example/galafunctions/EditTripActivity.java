package com.example.galafunctions;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class EditTripActivity extends AppCompatActivity {

    private ImageView imgCoverPick;
    private Button btnChangeCover, btnTripSearchMap;
    private ActivityResultLauncher<Intent> mapPickerLauncher;

    private Spinner spTripCategory;
    private Switch swIsTemplate, swBudgetEnabled;
    private LinearLayout layoutBudgetSection;

    private EditText etTripName, etLocation, etPeopleCount, etTripDate, etTripTime, etTripDescription, etTripBudget;
    private Button btnSaveTrip, btnCancelTrip;

    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private FirebaseStorage storage;

    private String tripId;
    private Uri selectedImageUri = null;
    private String existingCoverUrl = ""; // if user doesn’t change cover

    private ActivityResultLauncher<String> pickImageLauncher;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_edit_trip);

        View main = findViewById(R.id.main);
        if (main != null) {
            ViewCompat.setOnApplyWindowInsetsListener(main, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
                return insets;
            });
        }

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();

        tripId = getIntent().getStringExtra("tripId");
        if (tripId == null || tripId.trim().isEmpty()) {
            Toast.makeText(this, "Missing tripId.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Bind views
        imgCoverPick = findViewById(R.id.imgCover);
        btnChangeCover = findViewById(R.id.btnChangeCover);
        btnTripSearchMap = findViewById(R.id.btnTripSearchMap);

        spTripCategory = findViewById(R.id.spTripCategory);
        swIsTemplate = findViewById(R.id.swIsTemplate);
        swBudgetEnabled = findViewById(R.id.swBudgetEnabled);
        layoutBudgetSection = findViewById(R.id.layoutBudgetSection);

        etTripName = findViewById(R.id.etTripName);
        etLocation = findViewById(R.id.etLocation);
        etPeopleCount = findViewById(R.id.etPeopleCount);
        etTripDate = findViewById(R.id.etDate);
        etTripTime = findViewById(R.id.etTime);
        etTripDescription = findViewById(R.id.etDescription);
        etTripBudget = findViewById(R.id.etTripBudget);

        btnSaveTrip = findViewById(R.id.btnSaveTrip);
        btnCancelTrip = findViewById(R.id.btnCancelTrip);

        // Category options (customize list if you want)
        String[] categories = {"GALA", "AGENDA", "ERRANDS", "WORK", "OTHER"};
        ArrayAdapter<String> catAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                categories
        );
        spTripCategory.setAdapter(catAdapter);

        // Image picker
        pickImageLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        selectedImageUri = uri;
                        imgCoverPick.setImageURI(uri);
                    }
                }
        );

        imgCoverPick.setOnClickListener(v -> pickImageLauncher.launch("image/*"));
        btnChangeCover.setOnClickListener(v -> pickImageLauncher.launch("image/*"));

        // Template switch behavior
        swIsTemplate.setOnCheckedChangeListener((buttonView, isChecked) -> {
            etTripDate.setEnabled(!isChecked);
            etTripTime.setEnabled(!isChecked);

            if (isChecked) {
                etTripDate.setError(null);
                etTripTime.setError(null);
            }
        });

        // Budget switch behavior
        swBudgetEnabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                layoutBudgetSection.setVisibility(View.VISIBLE);
            } else {
                layoutBudgetSection.setVisibility(View.GONE);
                etTripBudget.setText("");
                etTripBudget.setError(null);
            }
        });

        // Cancel
        btnCancelTrip.setOnClickListener(v -> finish());

        // Save
        btnSaveTrip.setOnClickListener(v -> saveTripEdits());

        btnTripSearchMap = findViewById(R.id.btnTripSearchMap);

        mapPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        String address = result.getData().getStringExtra(MapPickerActivity.EXTRA_RESULT_ADDRESS);
                        if (!TextUtils.isEmpty(address)) {
                            etLocation.setText(address);
                        }
                    }
                }
        );

        btnTripSearchMap.setOnClickListener(v -> {
            Intent intent = new Intent(this, MapPickerActivity.class);
            mapPickerLauncher.launch(intent);
        });

        // Load existing trip data
        loadTrip();
    }

    private void loadTrip() {
        if (auth.getCurrentUser() == null) {
            Toast.makeText(this, "Please login first.", Toast.LENGTH_SHORT).show();
            finish();
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
                        finish();
                        return;
                    }

                    String tripName = doc.getString("trip_name");
                    String category = doc.getString("trip_category");
                    String location = doc.getString("location");

                    Long people = doc.getLong("people_count");
                    String date = doc.getString("date");
                    String time = doc.getString("time");
                    String description = doc.getString("description");

                    Boolean isTemplate = doc.getBoolean("is_template");
                    Boolean budgetEnabled = doc.getBoolean("budget_enabled");
                    Double tripBudget = doc.getDouble("trip_budget");

                    String coverUrl = doc.getString("cover_url");
                    existingCoverUrl = (coverUrl != null) ? coverUrl : "";

                    etTripName.setText(tripName != null ? tripName : "");
                    etLocation.setText(location != null ? location : "");
                    if (people != null) etPeopleCount.setText(String.valueOf(people));
                    etTripDate.setText(date != null ? date : "");
                    etTripTime.setText(time != null ? time : "");
                    etTripDescription.setText(description != null ? description : "");

                    // set spinner selection
                    if (!TextUtils.isEmpty(category)) {
                        ArrayAdapter adapter = (ArrayAdapter) spTripCategory.getAdapter();
                        int pos = adapter.getPosition(category);
                        if (pos >= 0) spTripCategory.setSelection(pos);
                    }

                    boolean templateChecked = (isTemplate != null && isTemplate);
                    swIsTemplate.setChecked(templateChecked);
                    etTripDate.setEnabled(!templateChecked);
                    etTripTime.setEnabled(!templateChecked);

                    boolean budgetChecked = (budgetEnabled != null && budgetEnabled);
                    swBudgetEnabled.setChecked(budgetChecked);
                    layoutBudgetSection.setVisibility(budgetChecked ? View.VISIBLE : View.GONE);
                    if (budgetChecked && tripBudget != null) {
                        etTripBudget.setText(String.valueOf(tripBudget));
                    }

                    if (!TextUtils.isEmpty(existingCoverUrl)) {
                        loadImageFromUrl(existingCoverUrl);
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Load failed: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
    }

    private void saveTripEdits() {
        if (auth.getCurrentUser() == null) {
            Toast.makeText(this, "Please login first.", Toast.LENGTH_SHORT).show();
            return;
        }
        String uid = auth.getCurrentUser().getUid();

        // Values
        String tripName = etTripName.getText().toString().trim();
        String location = etLocation.getText().toString().trim();
        String peopleStr = etPeopleCount.getText().toString().trim();
        String date = etTripDate.getText().toString().trim();
        String time = etTripTime.getText().toString().trim();
        String description = etTripDescription.getText().toString().trim();

        String category = (spTripCategory.getSelectedItem() != null)
                ? spTripCategory.getSelectedItem().toString().trim()
                : "OTHER";

        boolean isTemplate = swIsTemplate.isChecked();
        boolean budgetEnabled = swBudgetEnabled.isChecked();
        String budgetStr = etTripBudget.getText().toString().trim();

        // Validation
        if (TextUtils.isEmpty(tripName)) { etTripName.setError("Required"); return; }
        if (TextUtils.isEmpty(location)) { etLocation.setError("Required"); return; }
        if (TextUtils.isEmpty(peopleStr)) { etPeopleCount.setError("Required"); return; }

        int peopleCount;
        try {
            peopleCount = Integer.parseInt(peopleStr);
            if (peopleCount <= 0) { etPeopleCount.setError("Must be at least 1"); return; }
        } catch (Exception e) {
            etPeopleCount.setError("Number only");
            return;
        }

        if (!isTemplate) {
            if (TextUtils.isEmpty(date)) { etTripDate.setError("Required"); return; }
            if (TextUtils.isEmpty(time)) { etTripTime.setError("Required"); return; }
        }

        Double tripBudget = null;
        if (budgetEnabled) {
            if (TextUtils.isEmpty(budgetStr)) { etTripBudget.setError("Required"); return; }
            try {
                tripBudget = Double.parseDouble(budgetStr);
                if (tripBudget < 0) { etTripBudget.setError("Must be 0 or more"); return; }
            } catch (Exception e) {
                etTripBudget.setError("Number only");
                return;
            }
        }

        btnSaveTrip.setEnabled(false);

        // Build updates
        Map<String, Object> updates = new HashMap<>();
        updates.put("trip_name", tripName);
        updates.put("trip_category", category);
        updates.put("location", location);
        updates.put("people_count", peopleCount);
        updates.put("is_template", isTemplate);
        updates.put("budget_enabled", budgetEnabled);
        updates.put("date", date);
        updates.put("time", time);
        updates.put("description", description);

        if (budgetEnabled) {
            updates.put("trip_budget", (tripBudget != null) ? tripBudget : 0.0);
        } else {
            // optional: remove old budget field if user disables budgeting
            updates.put("trip_budget", null);
            updates.put("total_spent", null);
        }

        // If no new image, just update Firestore
        if (selectedImageUri == null) {
            db.collection("users")
                    .document(uid)
                    .collection("trips")
                    .document(tripId)
                    .update(updates)
                    .addOnSuccessListener(unused -> {
                        Toast.makeText(this, "Trip updated!", Toast.LENGTH_SHORT).show();
                        finish();
                    })
                    .addOnFailureListener(e -> {
                        btnSaveTrip.setEnabled(true);
                        Toast.makeText(this, "Update failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
            return;
        }

        // If new image selected: upload then update cover_url too
        String fileName = "cover_" + UUID.randomUUID();
        StorageReference ref = storage.getReference()
                .child("users")
                .child(uid)
                .child("trips")
                .child(tripId)
                .child(fileName);

        ref.putFile(selectedImageUri)
                .continueWithTask(task -> {
                    if (!task.isSuccessful()) throw task.getException();
                    return ref.getDownloadUrl();
                })
                .addOnSuccessListener(downloadUri -> {
                    updates.put("cover_url", downloadUri.toString());

                    db.collection("users")
                            .document(uid)
                            .collection("trips")
                            .document(tripId)
                            .update(updates)
                            .addOnSuccessListener(unused -> {
                                Toast.makeText(this, "Trip updated!", Toast.LENGTH_SHORT).show();
                                finish();
                            })
                            .addOnFailureListener(e -> {
                                btnSaveTrip.setEnabled(true);
                                Toast.makeText(this, "Update failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            });
                })
                .addOnFailureListener(e -> {
                    btnSaveTrip.setEnabled(true);
                    Toast.makeText(this, "Cover upload failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void loadImageFromUrl(String imageUrl) {
        new Thread(() -> {
            try {
                URL url = new URL(imageUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setDoInput(true);
                connection.connect();
                InputStream input = connection.getInputStream();
                Bitmap bitmap = BitmapFactory.decodeStream(input);
                runOnUiThread(() -> imgCoverPick.setImageBitmap(bitmap));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
}
