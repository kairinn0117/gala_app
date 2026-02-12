package com.example.galafunctions;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserInfo;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.HashMap;
import java.util.Map;

public class PersonalInformation extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private FirebaseStorage storage;

    private ImageButton btnBack, btnChangePassword, btnDeleteAccount;
    private ImageButton btnAddPhoto;
    private EditText usernameField;

    private ActivityResultLauncher<String> imagePicker;
    private Uri selectedImageUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_personal_information);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();

        btnBack = findViewById(R.id.Backbtn);
        btnChangePassword = findViewById(R.id.btnChangePassword);
        btnDeleteAccount = findViewById(R.id.btnDeleteAccount);
        usernameField = findViewById(R.id.editTextText);
        btnAddPhoto = findViewById(R.id.addPhoto);

        btnBack.setOnClickListener(v -> finish());

        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            startActivity(new Intent(this, Login.class));
            finish();
            return;
        }

        String uid = user.getUid();

        // Username = Email (your requirement)
        String email = user.getEmail() != null ? user.getEmail() : "";
        usernameField.setText(email);
        usernameField.setEnabled(false);
        usernameField.setFocusable(false);

        // ✅ Load saved profile photo (if any)
        loadProfilePhoto(uid);

        // ✅ Image Picker
        imagePicker = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri == null) return;
                    selectedImageUri = uri;

                    // preview immediately
                    Glide.with(PersonalInformation.this)
                            .load(uri)
                            .circleCrop()
                            .into(btnAddPhoto);

                    // upload + save
                    uploadProfilePhoto(uid, uri);
                }
        );

        // ✅ Tap imagebutton -> choose photo
        btnAddPhoto.setOnClickListener(v -> imagePicker.launch("image/*"));

        boolean googleOnly = isGoogleOnly(user);

        // ✅ Change Password: block Google-only users from entering Password.java
        if (googleOnly) {
            btnChangePassword.setEnabled(false);
            btnChangePassword.setAlpha(0.4f);
            btnChangePassword.setOnClickListener(v ->
                    Toast.makeText(this, "Google-only account: cannot change password.", Toast.LENGTH_SHORT).show()
            );
        } else {
            btnChangePassword.setEnabled(true);
            btnChangePassword.setAlpha(1.0f);
            btnChangePassword.setOnClickListener(v ->
                    startActivity(new Intent(PersonalInformation.this, Password.class))
            );
        }

        // ✅ Delete Account: always allowed, DeleteAccount handles Google vs password logic
        btnDeleteAccount.setOnClickListener(v ->
                startActivity(new Intent(PersonalInformation.this, DeleteAccount.class))
        );
    }

    private void loadProfilePhoto(String uid) {
        db.collection("users")
                .document(uid)
                .get()
                .addOnSuccessListener(doc -> {
                    String url = doc.getString("profile_url");
                    if (url != null && !url.trim().isEmpty()) {
                        Glide.with(PersonalInformation.this)
                                .load(url)
                                .circleCrop()
                                .into(btnAddPhoto);
                    } else {
                        // default image stays (circle_addpic)
                        btnAddPhoto.setImageResource(R.drawable.circle_addpic);
                    }
                })
                .addOnFailureListener(e -> {
                    btnAddPhoto.setImageResource(R.drawable.circle_addpic);
                });
    }

    private void uploadProfilePhoto(String uid, Uri uri) {
        btnAddPhoto.setEnabled(false);
        btnAddPhoto.setAlpha(0.6f);

        StorageReference ref = storage.getReference()
                .child("users")
                .child(uid)
                .child("profile")
                .child("profile.jpg"); // overwrite ok

        ref.putFile(uri)
                .continueWithTask(task -> ref.getDownloadUrl())
                .addOnSuccessListener(downloadUri -> {
                    String url = downloadUri.toString();

                    Map<String, Object> updates = new HashMap<>();
                    updates.put("profile_url", url);

                    db.collection("users")
                            .document(uid)
                            .set(updates, com.google.firebase.firestore.SetOptions.merge())
                            .addOnSuccessListener(unused -> {
                                Toast.makeText(this, "Profile photo updated!", Toast.LENGTH_SHORT).show();
                                btnAddPhoto.setEnabled(true);
                                btnAddPhoto.setAlpha(1.0f);
                            })
                            .addOnFailureListener(e -> {
                                Toast.makeText(this, "Saved but DB update failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                btnAddPhoto.setEnabled(true);
                                btnAddPhoto.setAlpha(1.0f);
                            });

                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Upload failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    btnAddPhoto.setEnabled(true);
                    btnAddPhoto.setAlpha(1.0f);
                });
    }

    private boolean isGoogleOnly(FirebaseUser user) {
        boolean hasGoogle = false;
        boolean hasPassword = false;

        for (UserInfo info : user.getProviderData()) {
            if ("google.com".equals(info.getProviderId())) hasGoogle = true;
            if ("password".equals(info.getProviderId())) hasPassword = true;
        }
        return hasGoogle && !hasPassword;
    }
}
