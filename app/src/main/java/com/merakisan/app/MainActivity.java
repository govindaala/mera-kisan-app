// File Path: app/src/main/java/com/merakisan/app/MainActivity.java
package com.merakisan.app;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
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
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
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

    private static final String API_URL = "https://mera-kisan-backend.vercel.app/api/crops?lat=24.12&lng=75.56";
    private static final String PREF_NAME = "MeraKisanGuestPrefs";

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

        btnRefresh.setOnClickListener(v -> fetchCrops());

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

        // कैटेगरी क्लिक लिसनर्स
        btnCatAll.setOnClickListener(v -> setCategory("all", btnCatAll));
        btnCatGrain.setOnClickListener(v -> setCategory("grain", btnCatGrain));
        btnCatVeg.setOnClickListener(v -> setCategory("vegetable", btnCatVeg));
        btnCatFruit.setOnClickListener(v -> setCategory("fruit", btnCatFruit));

        fetchCrops();
    }

    private void setCategory(String category, Button activeBtn) {
        selectedCategory = category;
        btnCatAll.setBackgroundColor(Color.parseColor("#757575"));
        btnCatGrain.setBackgroundColor(Color.parseColor("#757575"));
        btnCatVeg.setBackgroundColor(Color.parseColor("#757575"));
        btnCatFruit.setBackgroundColor(Color.parseColor("#757575"));
        activeBtn.setBackgroundColor(Color.parseColor("#2E7D32"));
        filterAndDisplay();
    }

    private void fetchCrops() {
        progressBar.setVisibility(View.VISIBLE);
        statusText.setText("ताज़ा फसलें खोजी जा रही हैं...");
        cropsContainer.removeAllViews();

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                URL url = new URL(API_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);

                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();

                    JSONObject jsonResponse = new JSONObject(response.toString());
                    JSONArray cropsArray = jsonResponse.getJSONArray("crops");

                    allCropsList.clear();
                    for (int i = 0; i < cropsArray.length(); i++) {
                        allCropsList.add(cropsArray.getJSONObject(i));
                    }

                    new Handler(Looper.getMainLooper()).post(this::filterAndDisplay);
                } else {
                    new Handler(Looper.getMainLooper()).post(() -> {
                        progressBar.setVisibility(View.GONE);
                        statusText.setText("सर्वर से संपर्क नहीं हो सका (Error " + responseCode + ")");
                    });
                }
            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    progressBar.setVisibility(View.GONE);
                    statusText.setText("त्रुटि: " + e.getLocalizedMessage());
                });
            }
        });
    }

    private void filterAndDisplay() {
        progressBar.setVisibility(View.GONE);
        cropsContainer.removeAllViews();

        String query = edtSearch.getText().toString().trim().toLowerCase();
        List<JSONObject> filtered = new ArrayList<>();

        for (JSONObject crop : allCropsList) {
            String name = crop.optString("crop_name", "").toLowerCase();
            String cat = crop.optString("category", "").toLowerCase();

            boolean matchCategory = selectedCategory.equals("all") || cat.equalsIgnoreCase(selectedCategory);
            boolean matchQuery = query.isEmpty() || name.contains(query);

            if (matchCategory && matchQuery) {
                filtered.add(crop);
            }
        }

        if (filtered.isEmpty()) {
            statusText.setText("कोई फसल उपलब्ध नहीं है।");
            return;
        }

        statusText.setText("उपलब्ध फसलें (" + filtered.size() + ") - ऑर्डर के लिए फसल पर टैप करें");

        for (JSONObject crop : filtered) {
            cropsContainer.addView(createCropCard(crop));
        }
    }

    private View createCropCard(JSONObject crop) {
        CardView card = new CardView(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, 16);
        card.setLayoutParams(params);
        card.setRadius(12);
        card.setCardElevation(4);
        card.setContentPadding(20, 20, 20, 20);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(this);
        title.setText(crop.optString("crop_name", "अज्ञात फसल"));
        title.setTextSize(18);
        title.setTextColor(Color.parseColor("#1B5E20"));
        title.getPaint().setFakeBoldText(true);

        TextView farmer = new TextView(this);
        farmer.setText("किसान: " + crop.optString("farmer_name", "किसान") + " (" + crop.optString("village", "गाँव") + ")");
        farmer.setTextSize(14);
        farmer.setTextColor(Color.DKGRAY);

        TextView price = new TextView(this);
        double rate = crop.optDouble("price_per_kg", 0);
        double dist = crop.optDouble("distance_km", 0);
        price.setText("भाव: ₹" + rate + "/kg  |  दूरी: " + dist + " km");
        price.setTextSize(14);
        price.setTextColor(Color.parseColor("#D84315"));

        TextView hint = new TextView(this);
        hint.setText("👉 खरीदने के लिए यहाँ टैप करें");
        hint.setTextSize(12);
        hint.setTextColor(Color.parseColor("#2E7D32"));
        hint.setPadding(0, 8, 0, 0);

        layout.addView(title);
        layout.addView(farmer);
        layout.addView(price);
        layout.addView(hint);
        card.addView(layout);

        // कार्ड पर क्लिक होने पर गेस्ट चेकआउट डायलॉग खोलें
        card.setOnClickListener(v -> openCheckoutDialog(crop));
        return card;
    }

    private void openCheckoutDialog(JSONObject crop) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_guest_checkout, null);
        builder.setView(dialogView);
        AlertDialog dialog = builder.create();

        TextView txtCrop = dialogView.findViewById(R.id.dialogCropTitle);
        TextView txtFarmer = dialogView.findViewById(R.id.dialogFarmerDetails);
        EditText edtQty = dialogView.findViewById(R.id.dialogEdtQty);
        TextView txtTotal = dialogView.findViewById(R.id.dialogTxtTotal);
        EditText edtName = dialogView.findViewById(R.id.dialogEdtName);
        EditText edtPhone = dialogView.findViewById(R.id.dialogEdtPhone);
        EditText edtAddress = dialogView.findViewById(R.id.dialogEdtAddress);
        Button btnWhatsapp = dialogView.findViewById(R.id.dialogBtnWhatsapp);
        Button btnCall = dialogView.findViewById(R.id.dialogBtnCall);

        String cropName = crop.optString("crop_name", "फसल");
        String farmerName = crop.optString("farmer_name", "किसान");
        String village = crop.optString("village", "गाँव");
        String farmerPhone = crop.optString("farmer_phone", "9876543210");
        double rate = crop.optDouble("price_per_kg", 0);

        txtCrop.setText(cropName);
        txtFarmer.setText("किसान: " + farmerName + " (" + village + ") | भाव: ₹" + rate + "/kg");

        // पहले से सेव की गई ग्राहक की जानकारी भरें (Auto-memory)
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        edtName.setText(prefs.getString("cust_name", ""));
        edtPhone.setText(prefs.getString("cust_phone", ""));
        edtAddress.setText(prefs.getString("cust_address", ""));

        // लाइव बिल गणना
        edtQty.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                try {
                    double qty = Double.parseDouble(s.toString().trim());
                    txtTotal.setText("कुल राशि: ₹" + Math.round(qty * rate));
                } catch (Exception e) {
                    txtTotal.setText("कुल राशि: ₹0");
                }
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });
        txtTotal.setText("कुल राशि: ₹" + Math.round(5 * rate));

        // WhatsApp पर ऑर्डर भेजें
        btnWhatsapp.setOnClickListener(v -> {
            String name = edtName.getText().toString().trim();
            String phone = edtPhone.getText().toString().trim();
            String address = edtAddress.getText().toString().trim();
            String qty = edtQty.getText().toString().trim();

            if (name.isEmpty() || phone.isEmpty() || qty.isEmpty()) {
                Toast.makeText(this, "कृपया नाम, फ़ोन नंबर और मात्रा भरें!", Toast.LENGTH_SHORT).show();
                return;
            }

            // जानकारी को फ़ोन में सेव रखें
            prefs.edit()
                .putString("cust_name", name)
                .putString("cust_phone", phone)
                .putString("cust_address", address)
                .apply();

            double totalAmt = 0;
            try {
                totalAmt = Double.parseDouble(qty) * rate;
            } catch (Exception ignored) {}

            String message = "नमस्ते " + farmerName + " जी,\n\n"
                    + "मुझे *Mera Kisan* ऐप से आपकी फसल का ऑर्डर देना है:\n"
                    + "🌾 *फसल:* " + cropName + "\n"
                    + "⚖️ *मात्रा:* " + qty + " किलो\n"
                    + "💰 *कुल अनुमानित राशि:* ₹" + Math.round(totalAmt) + "\n\n"
                    + "👤 *ग्राहक:* " + name + "\n"
                    + "📞 *मोबाइल:* " + phone + "\n"
                    + "📍 *डिलीवरी पता:* " + (address.isEmpty() ? "कॉल पर बताएंगे" : address);

            try {
                String url = "https://api.whatsapp.com/send?phone=91" + farmerPhone + "&text=" + URLEncoder.encode(message, "UTF-8");
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                startActivity(intent);
                dialog.dismiss();
            } catch (Exception e) {
                Toast.makeText(this, "WhatsApp नहीं खुल सका: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

        // सीधा कॉल करें
        btnCall.setOnClickListener(v -> {
            Intent callIntent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + farmerPhone));
            startActivity(callIntent);
        });

        dialog.show();
    }
}
