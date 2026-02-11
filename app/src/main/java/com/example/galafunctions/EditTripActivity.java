package com.example.galafunctions;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.*;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class EditTripActivity extends AppCompatActivity {

    private ImageButton imgCover;
    private ImageButton btnTripSearchMap;

    private Spinner spTripCategory;
    private EditText etCustomCategory;

    private Switch swBudgetEnabled;
    private LinearLayout layoutBudgetSection;

    private EditText etTripName, etLocation, etTripBudget, etDescription;
    private ImageButton btnSaveTrip, btnCancelTrip;

    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private FirebaseStorage storage;

    private String tripId;
    private Uri selectedImageUri = null;
    private String existingCoverUrl = "";

    private ActivityResultLauncher<String> pickImageLauncher;
    private ActivityResultLauncher<Intent> mapPickerLauncher;

    private boolean isSaving = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_edit_trip);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();

        tripId = getIntent().getStringExtra("tripId");
        if (TextUtils.isEmpty(tripId)) {
            Toast.makeText(this, "Missing tripId.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Bind
        imgCover = findViewById(R.id.imgCover);
        spTripCategory = findViewById(R.id.spTripCategory);
        etCustomCategory = findViewById(R.id.etCustomCategory);

        etTripName = findViewById(R.id.etTripName);
        etLocation = findViewById(R.id.etLocation);
        etTripBudget = findViewById(R.id.etTripBudget);
        etDescription = findViewById(R.id.etDescription);

        swBudgetEnabled = findViewById(R.id.swBudgetEnabled);
        layoutBudgetSection = findViewById(R.id.layoutBudgetSection);

        btnTripSearchMap = findViewById(R.id.btnTripSearchMap);
        btnSaveTrip = findViewById(R.id.btnSaveTrip);
        btnCancelTrip = findViewById(R.id.btnCancelTrip);

        // ✅ Back handler (replaces onBackPressed)
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (isSaving) {
                    // while saving, allow normal back (or just ignore)
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                    return;
                }
                showCancelConfirm();
            }
        });

        // Lock location to map-only
        lockLocationField();

        // Image picker
        pickImageLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    selectedImageUri = uri;
                    if (uri != null) {
                        Glide.with(EditTripActivity.this)
                                .load(uri)
                                .centerCrop()
                                .into(imgCover);
                    }
                }
        );

        imgCover.setOnClickListener(v -> {
            if (isSaving) return;
            pickImageLauncher.launch("image/*");
        });

        // Budget toggle
        swBudgetEnabled.setOnCheckedChangeListener((b, checked) -> {
            layoutBudgetSection.setVisibility(checked ? View.VISIBLE : View.GONE);
            if (!checked) {
                etTripBudget.setText("");
                etTripBudget.setError(null);
            }
        });

        // Category "Others" handling
        spTripCategory.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selected = (spTripCategory.getSelectedItem() != null)
                        ? spTripCategory.getSelectedItem().toString().trim()
                        : "";

                if (isOtherCategory(selected)) {
                    etCustomCategory.setVisibility(View.VISIBLE);
                } else {
                    etCustomCategory.setVisibility(View.GONE);
                    etCustomCategory.setText("");
                    etCustomCategory.setError(null);
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        // Map picker
        mapPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        String address = result.getData().getStringExtra(MapPickerActivity.EXTRA_RESULT_ADDRESS);
                        if (!TextUtils.isEmpty(address)) {
                            etLocation.setText(address);
                            etLocation.setError(null);
                        }
                    }
                }
        );

        View.OnClickListener openMap = v -> {
            if (isSaving) return;
            Intent intent = new Intent(EditTripActivity.this, MapPickerActivity.class);
            mapPickerLauncher.launch(intent);
        };

        btnTripSearchMap.setOnClickListener(openMap);
        etLocation.setOnClickListener(openMap); // tap field also opens map

        // Cancel confirm
        btnCancelTrip.setOnClickListener(v -> {
            if (isSaving) return;
            showCancelConfirm();
        });

        // Save confirm (validate first)
        btnSaveTrip.setOnClickListener(v -> {
            if (isSaving) return;
            if (!validateInputs()) return;
            showSaveConfirm();
        });

        loadTrip();
    }

    // ---------- UI helpers ----------

    private void lockLocationField() {
        etLocation.setFocusable(false);
        etLocation.setFocusableInTouchMode(false);
        etLocation.setClickable(true);
        etLocation.setCursorVisible(false);
        etLocation.setLongClickable(false);
        etLocation.setTextIsSelectable(false);
        etLocation.setInputType(android.text.InputType.TYPE_NULL);

        // API 26+ only
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            etLocation.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        }
    }

    private boolean isOtherCategory(String value) {
        if (TextUtils.isEmpty(value)) return false;
        String v = value.trim().toLowerCase();
        return v.equals("others") || v.equals("other");
    }

    // ---------- Load trip ----------

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
                    String description = doc.getString("description");

                    Boolean budgetEnabled = doc.getBoolean("budget_enabled");
                    Double tripBudget = doc.getDouble("trip_budget");

                    String coverUrl = doc.getString("cover_url");
                    existingCoverUrl = (coverUrl != null) ? coverUrl : "";

                    etTripName.setText(tripName != null ? tripName : "");
                    etLocation.setText(location != null ? location : "");
                    etDescription.setText(description != null ? description : "");

                    // set spinner selection
                    if (!TextUtils.isEmpty(category) && spTripCategory.getAdapter() != null) {
                        ArrayAdapter adapter = (ArrayAdapter) spTripCategory.getAdapter();
                        int pos = adapter.getPosition(category);

                        if (pos >= 0) {
                            spTripCategory.setSelection(pos);
                            etCustomCategory.setVisibility(View.GONE);
                            etCustomCategory.setText("");
                        } else {
                            // not in list -> set to Others and show custom
                            int otherPos = adapter.getPosition("Others");
                            if (otherPos < 0) otherPos = adapter.getPosition("Other");
                            if (otherPos >= 0) spTripCategory.setSelection(otherPos);

                            etCustomCategory.setVisibility(View.VISIBLE);
                            etCustomCategory.setText(category);
                        }
                    }

                    boolean budgetChecked = (budgetEnabled != null && budgetEnabled);
                    swBudgetEnabled.setChecked(budgetChecked);
                    layoutBudgetSection.setVisibility(budgetChecked ? View.VISIBLE : View.GONE);

                    if (budgetChecked && tripBudget != null) {
                        etTripBudget.setText(String.valueOf(tripBudget));
                    } else {
                        etTripBudget.setText("");
                    }

                    if (!TextUtils.isEmpty(existingCoverUrl)) {
                        Glide.with(EditTripActivity.this)
                                .load(existingCoverUrl)
                                .centerCrop()
                                .into(imgCover);
                    } else {
                        imgCover.setImageResource(R.drawable.addphoto);
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Load failed: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
    }

    // ---------- Validation + confirms ----------

    private boolean validateInputs() {
        String tripName = etTripName.getText().toString().trim();
        String location = etLocation.getText().toString().trim();

        String selectedCategory = (spTripCategory.getSelectedItem() != null)
                ? spTripCategory.getSelectedItem().toString().trim()
                : "";

        boolean budgetEnabled = swBudgetEnabled.isChecked();

        etTripName.setError(null);
        etLocation.setError(null);
        etTripBudget.setError(null);
        etCustomCategory.setError(null);

        if (TextUtils.isEmpty(tripName)) {
            etTripName.setError("Required");
            return false;
        }

        if (TextUtils.isEmpty(location)) {
            etLocation.setError("Required (pick from map)");
            Toast.makeText(this, "Please pick a location using the map.", Toast.LENGTH_SHORT).show();
            return false;
        }

        if (isOtherCategory(selectedCategory)) {
            String custom = etCustomCategory.getText().toString().trim();
            if (TextUtils.isEmpty(custom)) {
                etCustomCategory.setError("Required");
                return false;
            }
        }

        if (budgetEnabled) {
            String budgetStr = etTripBudget.getText().toString().trim();
            if (TextUtils.isEmpty(budgetStr)) {
                etTripBudget.setError("Required");
                return false;
            }
            try {
                double b = Double.parseDouble(budgetStr);
                if (b < 0) {
                    etTripBudget.setError("Must be 0 or more");
                    return false;
                }
            } catch (Exception e) {
                etTripBudget.setError("Invalid number");
                return false;
            }
        }

        return true;
    }

    private void showSaveConfirm() {
        new AlertDialog.Builder(this)
                .setTitle("Save changes?")
                .setMessage("Are you sure you want to update this trip?")
                .setNegativeButton("No", (d, w) -> d.dismiss())
                .setPositiveButton("Yes", (d, w) -> {
                    d.dismiss();
                    saveTripEdits();
                })
                .show();
    }

    private void showCancelConfirm() {
        new AlertDialog.Builder(this)
                .setTitle("Discard changes?")
                .setMessage("Are you sure you want to cancel? Your inputs will be lost.")
                .setNegativeButton("No", (d, w) -> d.dismiss())
                .setPositiveButton("Yes", (d, w) -> {
                    d.dismiss();
                    finish();
                })
                .show();
    }

    // ---------- Save ----------

    private void saveTripEdits() {
        if (auth.getCurrentUser() == null) {
            Toast.makeText(this, "Please login first.", Toast.LENGTH_SHORT).show();
            return;
        }
        String uid = auth.getCurrentUser().getUid();

        isSaving = true;
        btnSaveTrip.setEnabled(false);
        btnCancelTrip.setEnabled(false);

        String tripName = etTripName.getText().toString().trim();
        String location = etLocation.getText().toString().trim();
        String description = etDescription.getText().toString().trim();

        String selectedCategory = (spTripCategory.getSelectedItem() != null)
                ? spTripCategory.getSelectedItem().toString().trim()
                : "";

        String finalCategory = selectedCategory;
        if (isOtherCategory(selectedCategory)) {
            finalCategory = etCustomCategory.getText().toString().trim();
        }

        boolean budgetEnabled = swBudgetEnabled.isChecked();

        Double tripBudget = null;
        if (budgetEnabled) {
            String budgetStr = etTripBudget.getText().toString().trim();
            if (!TextUtils.isEmpty(budgetStr)) {
                tripBudget = Double.parseDouble(budgetStr);
            }
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("trip_name", tripName);
        updates.put("trip_category", finalCategory);
        updates.put("location", location);
        updates.put("budget_enabled", budgetEnabled);
        updates.put("trip_budget", tripBudget);
        updates.put("description", description);

        // Clean old fields (same as your code)
        updates.put("people_count", null);
        updates.put("date", "");
        updates.put("time", "");
        updates.put("is_template", null);

        // No new cover selected
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
                        isSaving = false;
                        btnSaveTrip.setEnabled(true);
                        btnCancelTrip.setEnabled(true);
                        Toast.makeText(this, "Update failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
            return;
        }

        // Upload new cover then update
        String fileName = "cover_" + UUID.randomUUID();
        StorageReference ref = storage.getReference()
                .child("users")
                .child(uid)
                .child("trips")
                .child(tripId)
                .child(fileName);

        ref.putFile(selectedImageUri)
                .continueWithTask(task -> ref.getDownloadUrl())
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
                                isSaving = false;
                                btnSaveTrip.setEnabled(true);
                                btnCancelTrip.setEnabled(true);
                                Toast.makeText(this, "Update failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            });
                })
                .addOnFailureListener(e -> {
                    isSaving = false;
                    btnSaveTrip.setEnabled(true);
                    btnCancelTrip.setEnabled(true);
                    Toast.makeText(this, "Cover upload failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
}