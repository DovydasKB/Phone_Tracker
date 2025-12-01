package com.example.myapplication;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;
import java.util.Scanner;

public class LocationService extends Service {

    private static final String FILENAME = "location_log.json";
    private FusedLocationProviderClient fusedClient;
    private LocationCallback callback;
    private Handler mainThreadHandler;
    private PowerManager.WakeLock wakeLock;

    @Override
    public void onCreate() {
        super.onCreate();
        fusedClient = LocationServices.getFusedLocationProviderClient(this);
        mainThreadHandler = new Handler(Looper.getMainLooper());

        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyApp:LocationWakeLockTag");
        wakeLock.acquire();

        createNotificationChannel();
        startForeground(1, buildNotification());
        startLocationUpdates();
    }

    private void startLocationUpdates() {
        LocationRequest request = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)//Pakeisti jeigu norima pakeisti intervalo laiką
                .build();

        callback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult result) {
                for (Location location : result.getLocations()) {
                    if (location != null) {
                        logLocation(location);
                    }
                }
            }
        };

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper());
        }
    }

    private void logLocation(Location loc) {
        try {
            JSONObject newLocationJson = new JSONObject();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            newLocationJson.put("timestamp", sdf.format(new Date()));
            newLocationJson.put("lat", loc.getLatitude());
            newLocationJson.put("lng", loc.getLongitude());

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Uri fileUri = getFileUri(this);
                if (fileUri == null) {
                    mainThreadHandler.post(() -> Toast.makeText(getApplicationContext(), "Error: Could not access file URI.", Toast.LENGTH_SHORT).show());
                    return;
                }

                JSONObject root;
                try {
                    String currentJsonString = readTextFromUri(fileUri);
                    root = currentJsonString.isEmpty() ? new JSONObject() : new JSONObject(currentJsonString);
                } catch (Exception e) {
                    root = new JSONObject();
                }

                JSONArray locations = root.optJSONArray("locations");
                if (locations == null) locations = new JSONArray();
                locations.put(newLocationJson);
                root.put("locations", locations);

                try (OutputStream os = getContentResolver().openOutputStream(fileUri, "w")) { // Overwrite
                    Objects.requireNonNull(os).write(root.toString(2).getBytes(StandardCharsets.UTF_8));
                    mainThreadHandler.post(() -> Toast.makeText(getApplicationContext(), "Location list updated in Documents", Toast.LENGTH_SHORT).show());
                }

            } else {
                File documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
                if (!documentsDir.exists()) documentsDir.mkdirs();
                File file = new File(documentsDir, FILENAME);

                JSONObject root;
                if (file.exists() && file.length() > 0) {
                    try (Scanner scanner = new Scanner(file, StandardCharsets.UTF_8.name())) {
                        String currentJsonString = scanner.useDelimiter("\\A").next();
                        root = new JSONObject(currentJsonString);
                    } catch(Exception e) {
                        root = new JSONObject();
                    }
                } else {
                    root = new JSONObject();
                }

                JSONArray locations = root.optJSONArray("locations");
                if (locations == null) locations = new JSONArray();
                locations.put(newLocationJson);
                root.put("locations", locations);

                try (FileWriter writer = new FileWriter(file, false)) { // Overwrite
                    writer.write(root.toString(2));
                    mainThreadHandler.post(() -> Toast.makeText(getApplicationContext(), "Location list updated in Documents", Toast.LENGTH_SHORT).show());
                }
            }
        } catch (Exception e) {
            Log.e("LocationService", "Error in logLocation (read/modify/write)", e);
            mainThreadHandler.post(() -> Toast.makeText(getApplicationContext(), "A critical error occurred.", Toast.LENGTH_SHORT).show());
        }
    }

    private String readTextFromUri(Uri uri) throws IOException {
        StringBuilder stringBuilder = new StringBuilder();
        try (InputStream inputStream = getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(Objects.requireNonNull(inputStream), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                stringBuilder.append(line);
            }
        }
        return stringBuilder.toString();
    }

    private Uri getFileUri(Context context) {
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, FILENAME);
        values.put(MediaStore.MediaColumns.MIME_TYPE, "application/json");
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS);

        Uri externalContentUri = MediaStore.Files.getContentUri("external");
        String[] projection = {MediaStore.Files.FileColumns._ID};
        String selection = MediaStore.MediaColumns.RELATIVE_PATH + "=? AND " + MediaStore.MediaColumns.DISPLAY_NAME + "=?";
        String[] selectionArgs = {Environment.DIRECTORY_DOCUMENTS + "/", FILENAME};

        try (Cursor cursor = context.getContentResolver().query(externalContentUri, projection, selection, selectionArgs, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                long id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID));
                return Uri.withAppendedPath(externalContentUri, String.valueOf(id));
            } else {
                return context.getContentResolver().insert(externalContentUri, values);
            }
        }
    }

    private Notification buildNotification() {
        return new NotificationCompat.Builder(this, "locChannel")
                .setContentTitle("Location Logger Running")
                .setContentText("Tracking in background...")
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setOngoing(true)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    "locChannel",
                    "Location Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (fusedClient != null && callback != null) {
            fusedClient.removeLocationUpdates(callback);
        }
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }
}