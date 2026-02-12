package com.example.galafunctions;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class Settings extends Fragment {

    private ImageButton btnPersonalInfo, btnArchive, btnAbout, btnLogout;
    private ImageButton imgProfile;
    private TextView userDisplay;

    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private GoogleSignInClient googleSignInClient;

    public Settings() {}

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();

        googleSignInClient = com.google.android.gms.auth.api.signin.GoogleSignIn
                .getClient(requireContext(), gso);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View v = inflater.inflate(R.layout.fragment_settings, container, false);

        imgProfile = v.findViewById(R.id.imgProfile);
        btnPersonalInfo = v.findViewById(R.id.imageButton3);
        btnArchive = v.findViewById(R.id.imageButton4);
        btnAbout = v.findViewById(R.id.imageButton6);
        btnLogout = v.findViewById(R.id.imageButton7);
        userDisplay = v.findViewById(R.id.textView4);

        displayUserInfo();
        loadProfilePhoto();

        // 👇 tap profile -> open Personal Info
        imgProfile.setOnClickListener(view -> {
            if (getActivity() == null) return;
            startActivity(new Intent(getActivity(), PersonalInformation.class));
        });

        btnPersonalInfo.setOnClickListener(view -> {
            if (getActivity() == null) return;
            startActivity(new Intent(getActivity(), PersonalInformation.class));
        });

        btnArchive.setOnClickListener(view -> {
            if (getActivity() == null) return;
            startActivity(new Intent(getActivity(), ArchiveActivity.class));
        });

        btnAbout.setOnClickListener(view -> {
            if (getActivity() == null) return;
            startActivity(new Intent(getActivity(), AboutUs.class));
        });

        btnLogout.setOnClickListener(view -> showLogoutConfirm());

        return v;
    }

    private void displayUserInfo() {
        FirebaseUser user = auth.getCurrentUser();

        if (user == null) {
            userDisplay.setText("Guest");
            return;
        }

        String displayName = user.getDisplayName();
        String email = user.getEmail();

        if (displayName != null && !displayName.isEmpty()) {
            userDisplay.setText(displayName);
        } else if (email != null) {
            userDisplay.setText(email);
        } else {
            userDisplay.setText("User");
        }
    }

    private void loadProfilePhoto() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        db.collection("users")
                .document(user.getUid())
                .get()
                .addOnSuccessListener(doc -> {
                    String url = doc.getString("profile_url");

                    if (url != null && !url.trim().isEmpty()) {
                        Glide.with(requireContext())
                                .load(url)
                                .circleCrop()
                                .into(imgProfile);
                    } else {
                        imgProfile.setImageResource(R.drawable.circle_addpic);
                    }
                })
                .addOnFailureListener(e ->
                        imgProfile.setImageResource(R.drawable.circle_addpic)
                );
    }

    private void showLogoutConfirm() {
        if (!isAdded()) return;

        new AlertDialog.Builder(requireContext())
                .setTitle("Logout?")
                .setMessage("Are you sure you want to logout?")
                .setNegativeButton("Cancel", (d, w) -> d.dismiss())
                .setPositiveButton("Logout", (d, w) -> doLogout())
                .show();
    }

    private void doLogout() {
        if (getActivity() == null) return;

        auth.signOut();

        if (googleSignInClient != null) {
            googleSignInClient.signOut().addOnCompleteListener(task -> goToLogin());
        } else {
            goToLogin();
        }
    }

    private void goToLogin() {
        if (getActivity() == null) return;

        Toast.makeText(getActivity(), "Logged out!", Toast.LENGTH_SHORT).show();

        Intent i = new Intent(getActivity(), Login.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
    }
}
