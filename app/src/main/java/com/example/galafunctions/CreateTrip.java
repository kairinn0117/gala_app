package com.example.galafunctions;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.DialogInterface;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class CreateTrip extends AppCompatActivity {

    private ImageButton imgCover;
    private ImageButton btnCreate, btnCancel;

    private EditText etTripName, etLocation, etBudget, etDescription;

    private Spinner spCategory;
    private EditText etCustomCategory;

    private Switch swBudget;
    private LinearLayout layoutBudget;

    private Uri selectedImageUri;

    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private FirebaseStorage storage;

    private ActivityResultLauncher<String> imagePicker;
    private ActivityResultLauncher<Intent> mapPickerLauncher;

    private ImageButton btnTripSearchMap;

    // states
    private boolean isSaving = false;

    private static final String CATEGORY_OTHERS = "Others"; // adjust if your spinner uses "Other"

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_create_trip);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();

        // Bind views
        imgCover = findViewById(R.id.imgCover);
        btnCreate = findViewById(R.id.btnCreateTrip);
        btnCancel = findViewById(R.id.btnCancelTrip);

        etTripName = findViewById(R.id.etTripName);
        etLocation = findViewById(R.id.etLocation);
        etBudget = findViewById(R.id.etTripBudget);
        etDescription = findViewById(R.id.etDescription);

        spCategory = findViewById(R.id.spTripCategory);
        swBudget = findViewById(R.id.swBudgetEnabled);
        layoutBudget = findViewById(R.id.layoutBudgetSection);

        btnTripSearchMap = findViewById(R.id.btnTripSearchMap);
        etCustomCategory = findViewById(R.id.etCustomCategory);

        // ✅ Make location NOT typeable (map only)
        lockLocationField();

        // ✅ Back press (gesture + button) confirm
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (isSaving) return;
                showCancelConfirm();
            }
        });

        // ✅ Image picker (Glide preview)
        imagePicker = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    selectedImageUri = uri;
                    if (uri != null) {
                        Glide.with(CreateTrip.this)
                                .load(uri)
                                .centerCrop()
                                .into(imgCover);
                    } else {
                        imgCover.setImageResource(R.drawable.addphoto);
                    }
                }
        );

        imgCover.setOnClickListener(v -> {
            if (isSaving) return;
            imagePicker.launch("image/*");
        });

        // Budget toggle
        swBudget.setOnCheckedChangeListener((b, checked) ->
                layoutBudget.setVisibility(checked ? View.VISIBLE : View.GONE)
        );

        // ✅ Category "Others" -> show custom input
        if (etCustomCategory != null) {
            etCustomCategory.setVisibility(View.GONE);
        }
        spCategory.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                String selected = (spCategory.getSelectedItem() != null)
                        ? spCategory.getSelectedItem().toString().trim()
                        : "";

                boolean isOthers = selected.equalsIgnoreCase(CATEGORY_OTHERS)
                        || selected.equalsIgnoreCase("Other")
                        || selected.equalsIgnoreCase("Others");

                if (etCustomCategory != null) {
                    etCustomCategory.setVisibility(isOthers ? View.VISIBLE : View.GONE);
                    if (!isOthers) {
                        etCustomCategory.setText("");
                        etCustomCategory.setError(null);
                    }
                }
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        // ✅ Map picker
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
            Intent intent = new Intent(CreateTrip.this, MapPickerActivity.class);
            mapPickerLauncher.launch(intent);
        };

        btnTripSearchMap.setOnClickListener(openMap);

        // optional: tap on location field also opens map
        etLocation.setOnClickListener(openMap);

        // Cancel confirm
        btnCancel.setOnClickListener(v -> {
            if (isSaving) return;
            showCancelConfirm();
        });

        // Create confirm (with validation first)
        btnCreate.setOnClickListener(v -> {
            if (isSaving) return;
            if (!validateInputs()) return;
            showCreateConfirm();
        });
    }

    private void lockLocationField() {

        etLocation.setFocusable(false);
        etLocation.setFocusableInTouchMode(false);
        etLocation.setClickable(true);
        etLocation.setCursorVisible(false);
        etLocation.setLongClickable(false);
        etLocation.setTextIsSelectable(false);
        etLocation.setInputType(android.text.InputType.TYPE_NULL);

        // Only apply for Android O and above
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            etLocation.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        }
    }

    private boolean validateInputs() {
        String name = safeText(etTripName);
        String location = safeText(etLocation);

        String selectedCategory = (spCategory.getSelectedItem() != null)
                ? spCategory.getSelectedItem().toString().trim()
                : "";

        boolean budgetEnabled = swBudget.isChecked();

        etTripName.setError(null);
        etLocation.setError(null);
        if (etCustomCategory != null) etCustomCategory.setError(null);
        if (etBudget != null) etBudget.setError(null);

        if (TextUtils.isEmpty(name)) {
            etTripName.setError("Required");
            return false;
        }

        if (TextUtils.isEmpty(location)) {
            etLocation.setError("Pick location using map");
            return false;
        }

        // Others category -> custom required
        boolean isOthers = selectedCategory.equalsIgnoreCase(CATEGORY_OTHERS)
                || selectedCategory.equalsIgnoreCase("Other")
                || selectedCategory.equalsIgnoreCase("Others");

        if (isOthers) {
            String custom = (etCustomCategory != null) ? safeText(etCustomCategory) : "";
            if (TextUtils.isEmpty(custom)) {
                if (etCustomCategory != null) etCustomCategory.setError("Please specify");
                return false;
            }
        }

        if (budgetEnabled) {
            String budgetStr = safeText(etBudget);
            if (TextUtils.isEmpty(budgetStr)) {
                etBudget.setError("Required");
                return false;
            }
            try {
                double b = Double.parseDouble(budgetStr);
                if (b < 0) {
                    etBudget.setError("Must be 0 or more");
                    return false;
                }
            } catch (Exception e) {
                etBudget.setError("Invalid number");
                return false;
            }
        }

        return true;
    }

    private void showCreateConfirm() {
        new AlertDialog.Builder(this)
                .setTitle("Create trip?")
                .setMessage("Are you sure you want to create this trip?")
                .setNegativeButton("No", (d, w) -> d.dismiss())
                .setPositiveButton("Yes", (d, w) -> {
                    d.dismiss();
                    createTrip();
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

    private void setUiEnabled(boolean enabled) {
        btnCreate.setEnabled(enabled);
        btnCancel.setEnabled(enabled);
        imgCover.setEnabled(enabled);
        btnTripSearchMap.setEnabled(enabled);

        etTripName.setEnabled(enabled);
        etDescription.setEnabled(enabled);

        // keep location non-typeable but clickable when enabled
        etLocation.setEnabled(enabled);
        etLocation.setClickable(enabled);

        spCategory.setEnabled(enabled);
        swBudget.setEnabled(enabled);

        if (etCustomCategory != null) etCustomCategory.setEnabled(enabled);
        if (etBudget != null) etBudget.setEnabled(enabled);
    }

    private void createTrip() {
        if (auth.getCurrentUser() == null) {
            Toast.makeText(this, "Please login first.", Toast.LENGTH_SHORT).show();
            return;
        }

        String uid = auth.getCurrentUser().getUid();

        String name = safeText(etTripName);
        String location = safeText(etLocation);

        String selectedCategory = (spCategory.getSelectedItem() != null)
                ? spCategory.getSelectedItem().toString().trim()
                : "";

        boolean budgetEnabled = swBudget.isChecked();

        // category final
        boolean isOthers = selectedCategory.equalsIgnoreCase(CATEGORY_OTHERS)
                || selectedCategory.equalsIgnoreCase("Other")
                || selectedCategory.equalsIgnoreCase("Others");

        String categoryFinal = selectedCategory;
        if (isOthers && etCustomCategory != null) {
            String custom = safeText(etCustomCategory);
            if (!TextUtils.isEmpty(custom)) categoryFinal = custom;
        }

        Double budget = null;
        if (budgetEnabled) {
            String budgetStr = safeText(etBudget);
            if (!TextUtils.isEmpty(budgetStr)) {
                try {
                    budget = Double.parseDouble(budgetStr);
                } catch (Exception ignored) {
                    // validateInputs already handles this
                    budget = null;
                }
            }
        }

        isSaving = true;
        setUiEnabled(false);

        String tripId = db.collection("tmp").document().getId();

        Map<String, Object> trip = new HashMap<>();
        trip.put("trip_name", name);
        trip.put("trip_category", categoryFinal);
        trip.put("location", location);

        trip.put("budget_enabled", budgetEnabled);
        trip.put("trip_budget", budget);
        trip.put("description", safeText(etDescription));

        trip.put("status", "PLANNED");
        trip.put("is_archived", false);
        trip.put("created_at", Timestamp.now());

        // defaults
        trip.put("scheduled_date", "");
        trip.put("scheduled_time", "");
        trip.put("scheduled_sort_millis", 0L);

        if (selectedImageUri == null) {
            saveTrip(uid, tripId, trip);
            return;
        }

        StorageReference ref = storage.getReference()
                .child("users").child(uid).child("trips").child(tripId)
                .child("cover_" + UUID.randomUUID());

        ref.putFile(selectedImageUri)
                .continueWithTask(t -> ref.getDownloadUrl())
                .addOnSuccessListener(uri -> {
                    trip.put("cover_url", uri.toString());
                    saveTrip(uid, tripId, trip);
                })
                .addOnFailureListener(e -> {
                    isSaving = false;
                    setUiEnabled(true);
                    Toast.makeText(this, "Upload failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void saveTrip(String uid, String tripId, Map<String, Object> trip) {
        db.collection("users")
                .document(uid)
                .collection("trips")
                .document(tripId)
                .set(trip)
                .addOnSuccessListener(v -> {
                    Intent i = new Intent(this, TripActivity.class);
                    i.putExtra("tripId", tripId);
                    startActivity(i);
                    finish();
                })
                .addOnFailureListener(e -> {
                    isSaving = false;
                    setUiEnabled(true);
                    Toast.makeText(this, "Save failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private String safeText(EditText et) {
        if (et == null || et.getText() == null) return "";
        return et.getText().toString().trim();
    }
}
