// File Path: app/src/main/java/com/merakisan/app/MainActivity.java
package com.merakisan.app;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private ProgressBar progressBar;
    private TextView statusText;
    private LinearLayout cropsContainer;

    // Vercel लाइव API URL
    private static final String API_URL = "https://mera-kisan-backend.vercel.app/api/crops?lat=24.12&lng=75.56";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        progressBar = findViewById(R.id.progressBar);
        statusText = findViewById(R.id.statusText);
        cropsContainer = findViewById(R.id.cropsContainer);

        // API से डेटा फेच करें
        fetchCrops();
    }

    private void fetchCrops() {
        progressBar.setVisibility(View.VISIBLE);
        statusText.setText("ताज़ा फसलें लोड हो रही हैं...");

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

                    new Handler(Looper.getMainLooper()).post(() -> displayCrops(cropsArray));
                } else {
                    new Handler(Looper.getMainLooper()).post(() -> {
                        progressBar.setVisibility(View.GONE);
                        statusText.setText("सर्वर से कनेक्ट नहीं हो सका (Error " + responseCode + ")");
                    });
                }
            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    progressBar.setVisibility(View.GONE);
                    statusText.setText("एरर: " + e.getLocalizedMessage());
                });
            }
        });
    }

    private void displayCrops(JSONArray crops) {
        progressBar.setVisibility(View.GONE);
        cropsContainer.removeAllViews();

        if (crops.length() == 0) {
            statusText.setText("आपके 20 किमी के दायरे में अभी कोई फसल उपलब्ध नहीं है।");
            return;
        }

        statusText.setText("उपलब्ध फसलें (" + crops.length() + ")");

        for (int i = 0; i < crops.length(); i++) {
            try {
                JSONObject crop = crops.getJSONObject(i);

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
                farmer.setText("किसान: " + crop.optString("farmer_name", "-") + " (" + crop.optString("village", "-") + ")");
                farmer.setTextSize(14);
                farmer.setTextColor(Color.DKGRAY);

                TextView price = new TextView(this);
                price.setText("कीमत: ₹" + crop.optDouble("price_per_kg", 0) + "/kg  |  दूरी: " + crop.optDouble("distance_km", 0) + " km");
                price.setTextSize(14);
                price.setTextColor(Color.parseColor("#D84315"));

                layout.addView(title);
                layout.addView(farmer);
                layout.addView(price);
                card.addView(layout);

                cropsContainer.addView(card);
            } catch (Exception ignored) {}
        }
    }
}
