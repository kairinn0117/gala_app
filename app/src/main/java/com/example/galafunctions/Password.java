package com.example.galafunctions;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserInfo;

public class Password extends AppCompatActivity {

    private FirebaseAuth mAuth;

    private EditText etCurrent, etNew, etConfirm;
    private ImageButton btnCancel, btnUpdate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_password);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        mAuth = FirebaseAuth.getInstance();

        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "No user session.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // ✅ HARD BLOCK: if Google-only, do not allow this screen
        if (isGoogleOnly(user)) {
            Toast.makeText(this, "Google-only account cannot change password.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Views
        etCurrent = findViewById(R.id.editTextText2);
        etNew = findViewById(R.id.editTextText3);
        etConfirm = findViewById(R.id.editTextText4);

        btnCancel = findViewById(R.id.imageButton11);
        btnUpdate = findViewById(R.id.imageButton13);

        btnCancel.setOnClickListener(v -> finish());
        btnUpdate.setOnClickListener(v -> updatePassword());
    }

    private void updatePassword() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null || user.getEmail() == null) {
            Toast.makeText(this, "No user session.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        String currentPass = etCurrent.getText().toString().trim();
        String newPass = etNew.getText().toString().trim();
        String confirm = etConfirm.getText().toString().trim();

        if (TextUtils.isEmpty(currentPass) || TextUtils.isEmpty(newPass) || TextUtils.isEmpty(confirm)) {
            Toast.makeText(this, "Fill all fields.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!newPass.equals(confirm)) {
            Toast.makeText(this, "New passwords do not match.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (newPass.length() < 6) {
            Toast.makeText(this, "Password must be at least 6 characters.", Toast.LENGTH_SHORT).show();
            return;
        }

        // ✅ Re-auth first with current password, then update
        user.reauthenticate(EmailAuthProvider.getCredential(user.getEmail(), currentPass))
                .addOnSuccessListener(unused ->
                        user.updatePassword(newPass)
                                .addOnSuccessListener(unused2 -> {
                                    Toast.makeText(this, "Password updated!", Toast.LENGTH_SHORT).show();
                                    finish();
                                })
                                .addOnFailureListener(e ->
                                        Toast.makeText(this, "Update failed: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                                )
                )
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Current password incorrect.", Toast.LENGTH_SHORT).show()
                );
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
