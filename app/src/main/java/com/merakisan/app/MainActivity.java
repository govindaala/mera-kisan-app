// File Path: app/src/main/java/com/merakisan/app/MainActivity.java
package com.merakisan.app;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private ProgressBar progressBar;
    private TextView statusText;
    private LinearLayout cropsContainer;
    private EditText edtSearch;
    private Button btnRefresh, btnCatAll, btnCatGrain, btnCatVeg, btnCatFruit;

    private static final String BASE_API_URL = "https://mera-kisan-backend.vercel.app/api/crops";
    private static final String PREF_NAME = "MeraKisanGuestPrefs";
    private static final int LOCATION_PERMISSION_CODE = 101;

    // डिफ़ॉल्ट लोकेशन (अगर GPS बंद हो)
    private double currentLat = 24.12;
    private double currentLng = 75.56;

    private final List<JSONObject> allCropsList = new ArrayList<>();
    private String selectedCategory = "all";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        progressBar = findViewById(R.id.progressBar);
        statusText = findViewById(R.id.statusText);
        cropsContainer = findViewById(R.id.cropsContainer);
        edtSearch = findViewById(R.id.edtSearch);
        btnRefresh = findViewById(R.id.btnRefresh);

        btnCatAll = findViewById(R.id.btnCatAll);
        btnCatGrain = findViewById(R.id.btnCatGrain);
        btnCatVeg = findViewById(R.id.btnCatVeg);
        btnCatFruit = findViewById(R.id.btnCatFruit);

        btnRefresh.setOnClickListener(v -> checkLocationAndFetch());

        // सर्च बॉक्स फ़िल्टर
        edtSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterAndDisplay();
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        // कैटेगरी फ़िल्टर
        btnCatAll.setOnClickListener(v -> setCategory("all", btnCatAll));
        btnCatGrain.setOnClickListener(v -> setCategory("grain", btnCatGrain));
        btnCatVeg.setOnClickListener(v -> setCategory("vegetable", btnCatVeg));
        btnCatFruit.setOnClickListener(v -> setCategory("fruit", btnCatFruit));

        // ऐप खुलते ही लोकेशन चेक करें
        checkLocationAndFetch();
    }

    private void checkLocationAndFetch() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            getDeviceLocation();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                    LOCATION_PERMISSION_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getDeviceLocation();
            } else {
                Toast.makeText(this, "लोकेशन परमिशन नहीं मिली, डिफ़ॉल्ट क्षेत्र दिखाया जा रहा है", Toast.LENGTH_SHORT).show();
                fetchCrops(currentLat, currentLng);
            }
        }
    }

    private void getDeviceLocation() {
        progressBar.setVisibility(View.VISIBLE);
        statusText.setText("📍 आपकी लोकेशन खोजी जा रही है...");

        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null) {
            fetchCrops(currentLat, currentLng);
            return;
        }

        Location lastKnown = null;
        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lastKnown = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            }
            if (lastKnown == null && locationManager.लोकेशन को ऑटोमैटिक (GPS) करने के लिए हम **Android का नेटिव `LocationManager`** इस्तेमाल करेंगे।

### नेटिव GPS का सबसे बड़ा फ़ायदा (Zero Bloat):
हम Google Play Services की भारी लाइब्रेरी नहीं जोड़ेंगे, बल्कि Android का अपना इनबिल्ट लोकेशन सिस्टम इस्तेमाल करेंगे। इससे:
1. **ऐप का साइज़ बिल्कुल नहीं बढ़ेगा** (अभी भी 1.5 MB के अंदर रहेगा)।
2. ग्राहक जहाँ भी होगा, ऐप 1 सेकंड में उसके फ़ोन का GPS डिटेक्ट कर लेगा और ठीक उसके 20–25 किमी दायरे की फसलें दिखाएगा।
3. अगर किसी वजह से GPS बंद हो, तो भी ऐप क्रैश नहीं होगा और डिफ़ॉल्ट रूप से काम करता रहेगा।

---

आपको अपनी GitHub रिपॉजिटरी (**`mera-kisan-app`**) में बस **2 फ़ाइलें अपडेट** करनी हैं:

---

### फ़ाइल 1: `app/src/main/AndroidManifest.xml` (लोकेशन परमिशन जोड़ें)

* **GitHub पर रास्ता:** `app/src/main/AndroidManifest.xml` खोलें और पेंसिल (✏️ Edit) दबाएँ।

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- File Path: app/src/main/AndroidManifest.xml -->
<manifest xmlns:android="[http://schemas.android.com/apk/res/android](http://schemas.android.com/apk/res/android)"
    package="com.merakisan.app">

    <!-- इंटरनेट परमिशन -->
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

    <!-- GPS लोकेशन परमिशन (सटीक और नेटवर्क दोनों) -->
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />

    <application
        android:allowBackup="true"
        android:icon="@android:drawable/sym_def_app_icon"
        android:label="Mera Kisan"
        android:roundIcon="@android:drawable/sym_def_app_icon"
        android:supportsRtl="true"
        android:theme="@style/AppTheme"
        android:usesCleartextTraffic="true">
        
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>

</manifest>
