package com.example.galafunctions;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.bumptech.glide.Glide;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.auth.UserInfo;
import com.google.firebase.firestore.FirebaseFirestore;

public class DeleteAccount extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private GoogleSignInClient googleSignInClient;

    private EditText etPassword;
    private ImageButton btnDelete, btnBack;
    private TextView tvInfo, tvUsername;
    private ImageView imgProfile;

    private boolean isGoogleOnly = false;

    // Google re-auth launcher
    private final ActivityResultLauncher<Intent> googleLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {

                Intent data = result.getData();
                if (data == null) {
                    Toast.makeText(this, "Google re-auth cancelled.", Toast.LENGTH_SHORT).show();
                    return;
                }

                Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
                try {
                    GoogleSignInAccount account = task.getResult(ApiException.class);
                    if (account != null && account.getIdToken() != null) {

                        AuthCredential credential =
                                GoogleAuthProvider.getCredential(account.getIdToken(), null);

                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user == null) return;

                        user.reauthenticate(credential)
                                .addOnSuccessListener(unused -> reallyDeleteUser())
                                .addOnFailureListener(e ->
                                        Toast.makeText(this, "Re-auth failed: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                                );

                    } else {
                        Toast.makeText(this, "Google token missing.", Toast.LENGTH_SHORT).show();
                    }

                } catch (ApiException e) {
                    Toast.makeText(this, "Google re-auth failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_delete_account);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        etPassword = findViewById(R.id.editTextText5);
        btnDelete = findViewById(R.id.imageButton15);
        btnBack = findViewById(R.id.imageButton18);
        tvInfo = findViewById(R.id.textView6);
        tvUsername = findViewById(R.id.textView6); // Re-using for username display
        imgProfile = findViewById(R.id.imageView);

        btnBack.setOnClickListener(v -> finish());

        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            startActivity(new Intent(this, Login.class));
            finish();
            return;
        }

        // Display Username/Email
        tvUsername.setText(user.getEmail());

        // ✅ Load Profile Photo from Firestore
        loadProfilePhoto(user.getUid());

        isGoogleOnly = isGoogleOnly(user);

        // Setup Google client
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();

        googleSignInClient = GoogleSignIn.getClient(this, gso);

        // UI Behavior
        if (isGoogleOnly) {
            etPassword.setVisibility(View.GONE);
            tvInfo.setText("Confirm with Google to delete account.");
        } else {
            etPassword.setVisibility(View.VISIBLE);
            tvInfo.setText("Enter password to confirm deletion.");
        }

        btnDelete.setOnClickListener(v -> {
            if (isGoogleOnly) {
                googleLauncher.launch(googleSignInClient.getSignInIntent());
            } else {
                deleteWithPassword();
            }
        });
    }

    private void loadProfilePhoto(String uid) {
        db.collection("users")
                .document(uid)
                .get()
                .addOnSuccessListener(doc -> {
                    String url = doc.getString("profile_url");
                    if (url != null && !url.trim().isEmpty()) {
                        Glide.with(DeleteAccount.this)
                                .load(url)
                                .circleCrop()
                                .into(imgProfile);
                    }
                });
    }

    private void deleteWithPassword() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null || user.getEmail() == null) return;

        String password = etPassword.getText().toString().trim();

        if (TextUtils.isEmpty(password)) {
            Toast.makeText(this, "Enter your password.", Toast.LENGTH_SHORT).show();
            return;
        }

        user.reauthenticate(
                        EmailAuthProvider.getCredential(user.getEmail(), password))
                .addOnSuccessListener(unused -> reallyDeleteUser())
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Incorrect password.", Toast.LENGTH_SHORT).show()
                );
    }

    private void reallyDeleteUser() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;

        String uid = user.getUid();

        db.collection("users")
                .document(uid)
                .delete()
                .addOnCompleteListener(task -> {
                    user.delete()
                            .addOnSuccessListener(unused -> {
                                Toast.makeText(this, "Account deleted.", Toast.LENGTH_SHORT).show();
                                mAuth.signOut();
                                if (googleSignInClient != null)
                                    googleSignInClient.signOut();

                                Intent intent = new Intent(DeleteAccount.this, Login.class);
                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                startActivity(intent);
                                finish();
                            })
                            .addOnFailureListener(e ->
                                    Toast.makeText(this, "Delete failed: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                            );
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
