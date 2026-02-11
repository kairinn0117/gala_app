package com.example.galafunctions;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.google.firebase.auth.FirebaseAuth;

public class Settings extends Fragment {

    private ImageButton btnPersonalInfo, btnArchive, btnAbout, btnLogout;

    private FirebaseAuth auth;

    public Settings() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        auth = FirebaseAuth.getInstance();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View v = inflater.inflate(R.layout.fragment_settings, container, false);

        // IDs from your XML
        btnPersonalInfo = v.findViewById(R.id.imageButton3);
        btnArchive = v.findViewById(R.id.imageButton4);
        btnAbout = v.findViewById(R.id.imageButton6);
        btnLogout = v.findViewById(R.id.imageButton7);

        // Personal Info
        btnPersonalInfo.setOnClickListener(view -> {
            if (getActivity() == null) return;

            // ✅ change to your actual activity if different
            Intent i = new Intent(getActivity(), PersonalInformation.class);
            startActivity(i);
        });

        // Archive
        btnArchive.setOnClickListener(view -> {
            if (getActivity() == null) return;
            startActivity(new Intent(getActivity(), ArchiveActivity.class));
        });


        // About Us
        btnAbout.setOnClickListener(view -> {
            if (getActivity() == null) return;

            // ✅ change to your actual activity if different
            Intent i = new Intent(getActivity(), AboutUs.class);
            startActivity(i);
        });

        // Logout (with confirm)
        btnLogout.setOnClickListener(view -> showLogoutConfirm());

        return v;
    }

    private void showLogoutConfirm() {
        if (getContext() == null) return;

        new AlertDialog.Builder(requireContext())
                .setTitle("Logout?")
                .setMessage("Are you sure you want to logout?")
                .setNegativeButton("Cancel", (d, w) -> {})
                .setPositiveButton("Logout", (d, w) -> doLogout())
                .show();
    }

    private void doLogout() {
        if (getActivity() == null) return;

        auth.signOut();
        Toast.makeText(getActivity(), "Logged out!", Toast.LENGTH_SHORT).show();

        // ✅ change LoginActivity to your actual login screen
        Intent i = new Intent(getActivity(), Login.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
    }
}