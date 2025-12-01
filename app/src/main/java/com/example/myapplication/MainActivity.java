package com.example.myapplication;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                if (Boolean.TRUE.equals(result.get(Manifest.permission.POST_NOTIFICATIONS))) {
                    checkBackgroundLocationPermission();
                } else {
                    showPermissionRationale();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button startBtn = findViewById(R.id.startBtn);
        Button stopBtn = findViewById(R.id.stopBtn);

        startBtn.setOnClickListener(v -> checkAndRequestPermissions());
        stopBtn.setOnClickListener(v -> stopLocationService());
    }

    private void checkAndRequestPermissions() {
        String[] permissions;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions = new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.POST_NOTIFICATIONS
            };
        } else {
            permissions = new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            };
        }

        boolean allPermissionsGranted = true;
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                allPermissionsGranted = false;
                break;
            }
        }

        if (!allPermissionsGranted) {
            permissionLauncher.launch(permissions);
        } else {
            checkBackgroundLocationPermission();
        }
    }

    private void checkBackgroundLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                new AlertDialog.Builder(this)
                        .setTitle("Background Location Access")
                        .setMessage("This app requires background location access to track your location even when the app is closed. Please grant this permission in the app settings.")
                        .setPositiveButton("Go to Settings", (dialog, which) -> {
                            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                            Uri uri = Uri.fromParts("package", getPackageName(), null);
                            intent.setData(uri);
                            startActivity(intent);
                        })
                        .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                        .create()
                        .show();
            } else {
                startLocationService();
            }
        } else {
            startLocationService();
        }
    }

    private void showPermissionRationale() {
        new AlertDialog.Builder(this)
                .setTitle("Permissions Required")
                .setMessage("This app requires notification and location permissions to function properly. Please grant these permissions.")
                .setPositiveButton("OK", (dialog, which) -> checkAndRequestPermissions())
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .create()
                .show();
    }

    private void startLocationService() {
        Intent i = new Intent(this, LocationService.class);
        ContextCompat.startForegroundService(this, i);
        Toast.makeText(this, "Starting location tracking...", Toast.LENGTH_SHORT).show();
    }

    private void stopLocationService() {
        Intent i = new Intent(this, LocationService.class);
        stopService(i);
        Toast.makeText(this, "Location tracking stopped.", Toast.LENGTH_SHORT).show();
    }
}
