// File Path: app/src/main/java/com/merakisan/app/MainActivity.java
package com.merakisan.app;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends AppCompatActivity {

    private TextView txtBuyerLocationStatus, txtCropsCountHeading, txtEmptyNotice;
    private EditText edtSearchCrop;
    private Button btnRefresh, btnHelpDispute;

    // दूरी फ़िल्टर चिप्स
    private Button chipDistAll, chipDist10, chipDist20, chipDist30, chipDist50, chipDist100;
    // श्रेणी फ़िल्टर चिप्स
    private Button chipAll, chipOrganic, chipLocalVillage;

    private LinearLayout containerCrops;
    private ProgressBar progressBar;

    private static final int LOCATION_REQ_CODE = 301;
    private double buyerLat = 24.12;
    private double buyerLng = 75.58;
    private String buyerVillageName = "";

    private boolean isPaymentOnline = false;
    private List<JSONObject> fullCropsList = new ArrayList<>();

    // चुने गए फ़िल्टर्स
    private int selectedMaxDistKm = 0; // 0 = सभी दूरी
    private String selectedCategory = "all"; // all, organic, local

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        setupDistanceFilters();
        setupCategoryFilters();
        setupSearch();
        requestBuyerLocation();
        fetchPublicConfig();
        loadLiveCrops();
    }

    private void initViews() {
        txtBuyerLocationStatus = findViewById(R.id.txtBuyerLocationStatus);
        txtCropsCountHeading = findViewById(R.id.txtCropsCountHeading);
        txtEmptyNotice = findViewById(R.id.txtEmptyNotice);
        edtSearchCrop = findViewById(R.id.edtSearchCrop);
        btnRefresh = findViewById(R.id.btnRefresh);
        btnHelpDispute = findViewById(R.id.btnHelpDispute);

        chipDistAll = findViewById(R.id.chipDistAll);
        chipDist10 = findViewById(R.id.chipDist10);
        chipDist20 = findViewById(R.id.chipDist20);
        chipDist30 = findViewById(R.id.chipDist30);
        chipDist50 = findViewById(R.id.chipDist50);
        chipDist100 = findViewById(R.id.chipDist100);

        chipAll = findViewById(R.id.chipAll);
        chipOrganic = findViewById(R.id.chipOrganic);
        chipLocalVillage = findViewById(R.id.chipLocalVillage);

        containerCrops = findViewById(R.id.containerCrops);
        progressBar = findViewById(R.id.progressBar);

        btnRefresh.setOnClickListener(v -> {
            requestBuyerLocation();
            loadLiveCrops();
        });

        btnHelpDispute.setOnClickListener(v -> showDisputeDialog());
    }

    // 1. दूरी फ़िल्टर सेट करना (10, 20, 30, 50, 100 किमी)
    private void setupDistanceFilters() {
        chipDistAll.setOnClickListener(v -> applyDistanceFilter(0, chipDistAll));
        chipDist10.setOnClickListener(v -> applyDistanceFilter(10, chipDist10));
        chipDist20.setOnClickListener(v -> applyDistanceFilter(20, chipDist20));
        chipDist30.setOnClickListener(v -> applyDistanceFilter(30, chipDist30));
        chipDist50.setOnClickListener(v -> applyDistanceFilter(50, chipDist50));
        chipDist100.setOnClickListener(v -> applyDistanceFilter(100, chipDist100));
    }

    private void applyDistanceFilter(int maxKm, Button activeBtn) {
        selectedMaxDistKm = maxKm;
        Button[] dChips = {chipDistAll, chipDist10, chipDist20, chipDist30, chipDist50, chipDist100};
        for (Button b : dChips) {
            b.setBackgroundColor(Color.parseColor("#E2E8F0"));
            b.setTextColor(Color.parseColor("#1E293B"));
        }
        activeBtn.setBackgroundColor(Color.parseColor("#166534"));
        activeBtn.setTextColor(Color.WHITE);
        renderCropsList(edtSearchCrop.getText().toString().trim());
    }

    // 2. श्रेणी फ़िल्टर सेट करना
    private void setupCategoryFilters() {
        chipAll.setOnClickListener(v -> applyCategoryFilter("all", chipAll));
        chipOrganic.setOnClickListener(v -> applyCategoryFilter("organic", chipOrganic));
        chipLocalVillage.setOnClickListener(v -> applyCategoryFilter("local", chipLocalVillage));
    }

    private void applyCategoryFilter(String cat, Button activeBtn) {
        selectedCategory = cat;
        Button[] cChips = {chipAll, chipOrganic, chipLocalVillage};
        for (Button b : cChips) {
            b.setBackgroundColor(Color.parseColor("#E2E8F0"));
            b.setTextColor(Color.parseColor("#1E293B"));
        }
        activeBtn.setBackgroundColor(Color.parseColor("#0284C7"));
        activeBtn.setTextColor(Color.WHITE);
        renderCropsList(edtSearchCrop.getText().toString().trim());
    }

    private void setupSearch() {
        edtSearchCrop.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                renderCropsList(s.toString().trim());
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    // 3. जीपीएस लोकेशन लाना
    private void requestBuyerLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                    LOCATION_REQ_CODE);
        } else {
            fetchAccurateLocation();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_REQ_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            fetchAccurateLocation();
        }
    }

    private void fetchAccurateLocation() {
        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) return;

        try {
            Location loc = null;
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            }
            if (loc == null && lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                loc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            }

            if (loc != null) {
                buyerLat = loc.getLatitude();
                buyerLng = loc.getLongitude();
                resolveBuyerVillage(buyerLat, buyerLng);
            } else {
                txtBuyerLocationStatus.setText("📍 गाँव: बर्दि‍या अमरा (डिफ़ॉल्ट GPS)");
                buyerVillageName = "बर्दि‍या अमरा";
            }
        } catch (SecurityException ignored) {
            txtBuyerLocationStatus.setText("📍 गाँव: बर्दि‍या अमरा");
            buyerVillageName = "बर्दि‍या अमरा";
        }
    }

    private void resolveBuyerVillage(double lat, double lng) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                Geocoder geocoder = new Geocoder(this, new Locale("hi", "IN"));
                List<Address> addresses = geocoder.getFromLocation(lat, lng, 1);
                if (addresses != null && !addresses.isEmpty()) {
                    Address addr = addresses.get(0);
                    String v = addr.getSubLocality();
                    if (v == null || v.isEmpty()) v = addr.getLocality();
                    if (v == null || v.isEmpty()) v = addr.getFeatureName();

                    final String detected = (v != null ? v : "बर्दि‍या अमरा");
                    buyerVillageName = detected;

                    new Handler(Looper.getMainLooper()).post(() -> {
                        txtBuyerLocationStatus.setText("📍 आपकी लोकेशन: " + detected);
                        renderCropsList(edtSearchCrop.getText().toString().trim());
                    });
                }
            } catch (Exception ignored) {}
        });
    }

    private void fetchPublicConfig() {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                URL url = new URL("https://mera-kisan-backend.vercel.app/api/admin?action=get_public_config");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");

                if (conn.getResponseCode() == 200) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                    br.close();

                    JSONObject res = new JSONObject(sb.toString());
                    isPaymentOnline = res.optBoolean("payment_enabled", false);
                }
            } catch (Exception ignored) {}
        });
    }

    private void loadLiveCrops() {
        progressBar.setVisibility(View.VISIBLE);
        txtEmptyNotice.setVisibility(View.GONE);

        Executors.newSingleThreadExecutor().execute(() -> {
            List<JSONObject> tempList = new ArrayList<>();
            try {
                URL url = new URL("https://mera-kisan-backend.vercel.app/api/crops");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");

                if (conn.getResponseCode() == 200) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                    br.close();

                    JSONObject res = new JSONObject(sb.toString());
                    JSONArray arr = res.optJSONArray("crops");
                    if (arr != null) {
                        for (int i = 0; i < arr.length(); i++) {
                            tempList.add(arr.getJSONObject(i));
                        }
                    }
                }
            } catch (Exception ignored) {}

            new Handler(Looper.getMainLooper()).post(() -> {
                progressBar.setVisibility(View.GONE);
                fullCropsList = tempList;
                renderCropsList(edtSearchCrop.getText().toString().trim());
            });
        });
    }

    // 4. किसी फ़सल की सटीक दूरी (किमी में) निकालना
    private float getAccurateDistanceKm(JSONObject crop) {
        String farmerVillage = crop.optString("village", "");
        if (isSameVillage(farmerVillage, buyerVillageName)) {
            return 0.1f; // आपके ही गाँव में
        }

        double fLat = crop.optDouble("lat", 0);
        double fLng = crop.optDouble("lng", 0);

        if (fLat == 0 || fLng == 0) return 0.5f;

        float[] results = new float[1];
        Location.distanceBetween(buyerLat, buyerLng, fLat, fLng, results);
        return results[0] / 1000f; // मीटर को किमी में बदला
    }

    private boolean isSameVillage(String v1, String v2) {
        if (v1 == null || v2 == null || v1.isEmpty() || v2.isEmpty()) return false;
        String a = v1.replaceAll("[^a-zA-Z0-9\u0900-\u097F]", "").toLowerCase();
        String b = v2.replaceAll("[^a-zA-Z0-9\u0900-\u097F]", "").toLowerCase();
        return a.contains("बर्दिया") || a.contains("बर्दि‍या") || a.contains(b) || b.contains(a);
    }

    // 5. फ़िल्टर लगाना व सबसे नज़दीकी फ़सलें प्रदर्शित करना
    private void renderCropsList(String query) {
        containerCrops.removeAllViews();
        List<JSONObject> filtered = new ArrayList<>();

        for (JSONObject c : fullCropsList) {
            String cropName = c.optString("crop_name", "");
            String farmerName = c.optString("farmer_name", "");
            String farmerVillage = c.optString("village", "");
            boolean isOrganic = "organic".equalsIgnoreCase(c.optString("farming_type"));

            // सर्च फ़िल्टर
            if (!query.isEmpty()) {
                String qLower = query.toLowerCase();
                if (!cropName.toLowerCase().contains(qLower) &&
                    !farmerName.toLowerCase().contains(qLower) &&
                    !farmerVillage.toLowerCase().contains(qLower)) {
                    continue;
                }
            }

            // श्रेणी फ़िल्टर
            if ("organic".equals(selectedCategory) && !isOrganic) continue;
            if ("local".equals(selectedCategory) && !isSameVillage(farmerVillage, buyerVillageName)) continue;

            // 📍 दूरी फ़िल्टर (10, 20, 30, 50, 100 किमी)
            float distKm = getAccurateDistanceKm(c);
            if (selectedMaxDistKm > 0 && distKm > selectedMaxDistKm) {
                continue;
            }

            filtered.add(c);
        }

        // सबसे नज़दीक वाली फ़सलें ऊपर दिखाना (प्रमोटेड फ़सलें हमेशा सर्वोच्च)
        Collections.sort(filtered, (o1, o2) -> {
            boolean p1 = o1.optBoolean("is_promoted", false);
            boolean p2 = o2.optBoolean("is_promoted", false);
            if (p1 && !p2) return -1;
            if (!p1 && p2) return 1;
            return Float.compare(getAccurateDistanceKm(o1), getAccurateDistanceKm(o2));
        });

        for (JSONObject c : filtered) {
            addModernCropCard(c);
        }

        String distSuffix = selectedMaxDistKm > 0 ? " (" + selectedMaxDistKm + " किमी के भीतर: " + filtered.size() + ")" : " (" + filtered.size() + ")";
        txtCropsCountHeading.setText("🌾 मंडी में उपलब्ध फ़सलें" + distSuffix);
        txtEmptyNotice.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
    }

    // 6. आधुनिक फ़सल कार्ड बनाना
    private void addModernCropCard(JSONObject crop) {
        String name = crop.optString("crop_name");
        String farmerName = crop.optString("farmer_name", "किसान साथी");
        String farmerVillage = crop.optString("village", "गाँव");
        String phone = crop.optString("farmer_phone", "");
        double price = crop.optDouble("price_per_kg", 0);
        double stock = crop.optDouble("stock_qty_kg", 0);
        boolean isOrganic = "organic".equalsIgnoreCase(crop.optString("farming_type"));
        boolean isPromoted = crop.optBoolean("is_promoted", false);

        float distKm = getAccurateDistanceKm(crop);
        String distanceLabel;
        if (isSameVillage(farmerVillage, buyerVillageName) || distKm < 0.5f) {
            distanceLabel = "📍 आपके ही गाँव में (खेत पर सीधा संपर्क)";
        } else if (distKm < 1.0f) {
            distanceLabel = "📍 1 किमी के भीतर (पड़ोस में)";
        } else {
            distanceLabel = "📍 " + String.format(Locale.ENGLISH, "%.1f", distKm) + " किमी दूर (" + farmerVillage + ")";
        }

        CardView card = new CardView(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, 16);
        card.setLayoutParams(lp);
        card.setRadius(14);
        card.setCardElevation(3);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(18, 18, 18, 18);

        // शीर्ष पंक्ति: फ़सल नाम व बैज
        LinearLayout headRow = new LinearLayout(this);
        headRow.setOrientation(LinearLayout.HORIZONTAL);

        TextView title = new TextView(this);
        title.setText(name);
        title.setTextSize(17);
        title.setTextColor(Color.parseColor("#166534"));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        headRow.addView(title);

        if (isPromoted) {
            TextView badgeP = new TextView(this);
            badgeP.setText("🔥 टॉप डील");
            badgeP.setBackgroundColor(Color.parseColor("#FEF3C7"));
            badgeP.setTextColor(Color.parseColor("#B45309"));
            badgeP.setTextSize(10);
            badgeP.setPadding(8, 4, 8, 4);
            headRow.addView(badgeP);
        }
        box.addView(headRow);

        // किसान विवरण व दूरी
        TextView fDetails = new TextView(this);
        fDetails.setText("👨‍🌾 किसान: " + farmerName + "\n" + distanceLabel);
        fDetails.setTextSize(13);
        fDetails.setTextColor(Color.parseColor("#475569"));
        fDetails.setPadding(0, 6, 0, 6);
        box.addView(fDetails);

        if (isOrganic) {
            TextView orgBadge = new TextView(this);
            orgBadge.setText("🛡️ 100% प्रमाणित जैविक फ़सल");
            orgBadge.setTextColor(Color.parseColor("#15803D"));
            orgBadge.setTextSize(12);
            orgBadge.setTypeface(null, android.graphics.Typeface.BOLD);
            orgBadge.setPadding(0, 0, 0, 6);
            box.addView(orgBadge);
        }

        // भाव व स्टॉक
        TextView priceView = new TextView(this);
        priceView.setText("💰 भाव: ₹" + (int)price + "/kg (₹" + (int)(price * 100) + "/क्विंटल)  |  📦 स्टॉक: " + (int)stock + " किलो");
        priceView.setTextSize(14);
        priceView.setTextColor(Color.parseColor("#0F172A"));
        priceView.setTypeface(null, android.graphics.Typeface.BOLD);
        priceView.setPadding(0, 4, 0, 14);
        box.addView(priceView);

        // एक्शन बटन्स (कॉल व WhatsApp)
        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);

        Button btnCall = new Button(this);
        btnCall.setText("📞 कॉल करें");
        btnCall.setBackgroundColor(Color.parseColor("#0284C7"));
        btnCall.setTextColor(Color.WHITE);
        btnCall.setTextSize(12);
        btnCall.setOnClickListener(v -> {
            Intent callIntent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:+91" + phone));
            startActivity(callIntent);
        });
        btnRow.addView(btnCall);

        Button btnWa = new Button(this);
        btnWa.setText("💬 WhatsApp");
        btnWa.setBackgroundColor(Color.parseColor("#166534"));
        btnWa.setTextColor(Color.WHITE);
        btnWa.setTextSize(12);
        LinearLayout.LayoutParams waLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        waLp.setMargins(8, 0, 0, 0);
        btnWa.setLayoutParams(waLp);
        btnWa.setOnClickListener(v -> openWhatsAppToFarmer(phone, name, farmerName));
        btnRow.addView(btnWa);

        if (isPaymentOnline) {
            Button btnBook = new Button(this);
            btnBook.setText("🛒 10% टोकन");
            btnBook.setBackgroundColor(Color.parseColor("#F59E0B"));
            btnBook.setTextColor(Color.BLACK);
            btnBook.setTextSize(12);
            LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            bLp.setMargins(8, 0, 0, 0);
            btnBook.setLayoutParams(bLp);
            btnBook.setOnClickListener(v -> Toast.makeText(this, "सुरक्षित एस्क्रो बुकिंग जल्द शुरू होगी!", Toast.LENGTH_SHORT).show());
            btnRow.addView(btnBook);
        }

        box.addView(btnRow);
        card.addView(box);
        containerCrops.addView(card);
    }

    private void openWhatsAppToFarmer(String phone, String cropName, String farmerName) {
        String msg = "नमस्ते " + farmerName + " जी, मैंने Mera Kisan ऐप पर आपकी फ़सल '" + cropName + "' देखी है। मुझे यह खरीदनी है, कृपया उपलब्ध स्टॉक और अंतिम भाव बताएं।";
        try {
            Intent waIntent = new Intent(Intent.ACTION_VIEW);
            waIntent.setData(Uri.parse("https://wa.me/91" + phone + "?text=" + URLEncoder.encode(msg, "UTF-8")));
            startActivity(waIntent);
        } catch (Exception e) {
            Toast.makeText(this, "WhatsApp नहीं खुला", Toast.LENGTH_SHORT).show();
        }
    }

    private void showDisputeDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("📢 ग्राहक सहायता व शिकायत केंद्र");

        final EditText input = new EditText(this);
        input.setHint("फ़सल या किसान को लेकर अपनी समस्या लिखें...");
        input.setMinLines(3);
        input.setPadding(20, 20, 20, 20);
        builder.setView(input);

        builder.setPositiveButton("भेजें ✉️", (dialog, which) -> {
            String msg = input.getText().toString().trim();
            if (!msg.isEmpty()) submitDispute(msg);
        });
        builder.setNegativeButton("रद्द करें", null);
        builder.show();
    }

    private void submitDispute(String msg) {
        Toast.makeText(this, "शिकायत भेजी जा रही है...", Toast.LENGTH_SHORT).show();
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                URL url = new URL("https://mera-kisan-backend.vercel.app/api/admin");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);

                JSONObject payload = new JSONObject();
                payload.put("action", "create_dispute");
                payload.put("user_type", "buyer");
                payload.put("name", "ग्राहक साथी (" + buyerVillageName + ")");
                payload.put("phone", "अज्ञात");
                payload.put("issue_type", "ग्राहक सहायता");
                payload.put("message", msg);

                OutputStream os = conn.getOutputStream();
                os.write(payload.toString().getBytes("UTF-8"));
                os.close();
                conn.getResponseCode();
            } catch (Exception ignored) {}

            new Handler(Looper.getMainLooper()).post(() -> {
                Toast.makeText(this, "✅ आपकी बात एडमिन टीम तक पहुँच गई है!", Toast.LENGTH_LONG).show();
            });
        });
    }
}
