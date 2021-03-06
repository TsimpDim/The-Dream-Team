package thedreamteam.passbuy;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.widget.ImageButton;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MoreInfo extends PortraitActivity {
    
    private static final String BUNDLE_NAME = "bundle";
    private static final String BASKET_NAME = "basket";
    private MoreInfoAdapter mAdapter;
    private GsonWorker gson = new GsonWorker();
    private List<Store> stores = new ArrayList<>();
    private List<StoreLocation> storeLocations = new ArrayList<>();
    private Coordinates userCoordinates = new Coordinates();
    private LocationManager locationManager;
    private Basket basket;
    private String bestStore;
    private double bestPrice;
    private TextView bestPriceText;
    private TextView bestSupermarket;
    private TextView loadingText;
    private final LocationListener mLocationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            updateUserLocation(location);
        }

        @Override
        public void onStatusChanged(String s, int i, Bundle bundle) {
            // Unneeded
        }

        @Override
        public void onProviderEnabled(String s) {
            // Unneeded
        }

        @Override
        public void onProviderDisabled(String s) {
            // Unneeded
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.more_info);
        Context mContext = this.getBaseContext();

        ImageButton backButton = findViewById(R.id.backButton);
        ImageButton homeScreen = findViewById(R.id.homeButton);
        loadingText = findViewById(R.id.empty_basket_text);

        //get basket from previous activity
        Bundle bundle = getIntent().getBundleExtra(BUNDLE_NAME);
        basket = (Basket) bundle.getSerializable(BASKET_NAME);
        bestPrice = bundle.getDouble("best_price");
        bestStore = (String) bundle.getCharSequence("best_super");
        stores = (List<Store>) bundle.getSerializable("stores");

        List<StorePrice> totalPrices = basket.getTotalPrices();

        new Thread(() -> {
            if (stores == null && isNetworkAvailable(mContext)) {
                stores = gson.getStores();
                bestStore = stores.get(0).getName();
                //Get best store name
                for (Store store : stores) {
                    if (totalPrices.get(0).getStoreId().equals(store.getId())) {
                        bestStore = store.getName();
                        break;
                    }
                }
            }
            Collections.sort(totalPrices, new IdsComparator());

            bestPriceText = findViewById(R.id.best_price);
            bestSupermarket = findViewById(R.id.best_supermarket);

            runOnUiThread(() -> {
                loadingText.setText("Λαμβάνουμε την τοποθεσία σας μέσω GPS.\nΠαρακαλώ περιμένετε..."
                        + "\n\nΣημείωση: Οι ρυθμίσεις σας θα πρέπει να επιτρέπουν την ανάκτηση τοποθεσίας μέσω GPS.");
                bestPriceText.setText(String.format("%.2f €", bestPrice));
                bestSupermarket.setText(bestStore);

                bestSupermarket.setSelected(true);
                bestPriceText.setSelected(true);
                initRecyclerView();

                // Acquire user location
                this.requestPermissions();
            });
        }).start();

        backButton.setOnClickListener(v -> {
            Intent intent = new Intent(v.getContext(), HomeScreen.class);

            Bundle bundle2 = new Bundle();
            bundle2.putSerializable(BASKET_NAME, basket);
            intent.putExtra(BUNDLE_NAME, bundle2);

            startActivity(intent);
        });

        homeScreen.setOnClickListener(v -> {
            Intent intent = new Intent(v.getContext(), HomeScreen.class);

            Bundle bundle12 = new Bundle();
            bundle12.putSerializable(BASKET_NAME, basket);
            intent.putExtra(BUNDLE_NAME, bundle12);

            startActivity(intent);
        });
    }

    public void requestPermissions() {
        if (Build.VERSION.SDK_INT >= 23) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            else
                this.requestLocation();
        } else
            this.requestLocation();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            this.requestLocation();
        }
    }

    public void requestLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        Location location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);

        if (location != null) {
            long timeDelta = System.currentTimeMillis() - location.getTime();

            // Refresh location if our cached one is older than 2 minutes
            if (timeDelta > (1000 * 60 * 2))
                locationManager.requestSingleUpdate(LocationManager.GPS_PROVIDER, mLocationListener, null);
            else
                this.updateUserLocation(location);
        } else
            locationManager.requestSingleUpdate(LocationManager.GPS_PROVIDER, mLocationListener, null);
    }

    private void initRecyclerView() {

        RecyclerView recyclerView = findViewById(R.id.rv);

        // Use an adapter to feed data into the RecyclerView
        mAdapter = new MoreInfoAdapter(this, basket, stores);

        // Layout size remains fixed, improve performance
        recyclerView.setHasFixedSize(true);
        recyclerView.setAdapter(mAdapter);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        // Draw line divider
        DividerItemDecoration lineDivider = new DividerItemDecoration(this, DividerItemDecoration.VERTICAL);
        recyclerView.addItemDecoration(lineDivider);
    }

    public void updateUserLocation(Location location) {
        userCoordinates.setLat(location.getLatitude());
        userCoordinates.setLng(location.getLongitude());
        locationManager.removeUpdates(mLocationListener);

        loadingText.setText("Ψάχνουμε τα κοντινότερα καταστήματα...");

        new Thread(() -> {
            storeLocations = gson.getNearbyStores(stores, userCoordinates);
            mAdapter.replaceUserLocation(userCoordinates);
            mAdapter.replaceLocations(storeLocations);
            runOnUiThread(() -> {
                mAdapter.notifyDataSetChanged();
                loadingText.setText("");
            });
        }).start();
    }

    public boolean isNetworkAvailable(Context context) {
        final ConnectivityManager connectivityManager = ((ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE));
        return connectivityManager.getActiveNetworkInfo() != null && connectivityManager.getActiveNetworkInfo().isConnected();
    }
}

//Comparator that compares prices
class IdsComparator implements Comparator {
    public int compare(Object o1, Object o2) {
        if (((StorePrice) o1).getStoreId() == ((StorePrice) o2).getStoreId())
            return 0;
        else if (((StorePrice) o1).getStoreId() > ((StorePrice) o2).getStoreId())
            return 1;
        else
            return -1;
    }

}
