//package com.example.myapplication;
//
//import android.Manifest;
//import android.annotation.SuppressLint;
//import android.app.Activity;
//import android.app.AlertDialog;
//import android.bluetooth.BluetoothAdapter;
//import android.bluetooth.le.ScanCallback;
//import android.content.Context;
//import android.content.DialogInterface;
//import android.content.Intent;
//import android.content.pm.PackageManager;
//import android.location.Location;
//import android.location.LocationManager;
//import android.net.Uri;
//import android.os.Build;
//import android.os.Bundle;
//import android.os.Environment;
//import android.os.Handler;
//import android.os.Looper;
//import android.provider.Settings;
//import android.text.TextUtils;
//import android.util.Log;
//import android.view.View;
//import android.widget.Button;
//import android.widget.LinearLayout;
//import android.widget.TextView;
//import android.widget.Toast;
//
//import androidx.activity.result.ActivityResultLauncher;
//import androidx.activity.result.contract.ActivityResultContracts;
//import androidx.annotation.NonNull;
//import androidx.appcompat.app.AppCompatActivity;
//import androidx.core.app.ActivityCompat;
//import androidx.core.content.ContextCompat;
//import androidx.core.content.FileProvider;
//
//import com.android.volley.DefaultRetryPolicy;
//import com.android.volley.Request;
//import com.android.volley.RequestQueue;
//import com.android.volley.VolleyError;
//import com.android.volley.toolbox.StringRequest;
//import com.android.volley.toolbox.Volley;
//import com.empatica.empalink.ConfigurationProfileException;
//import com.empatica.empalink.EmpaDeviceManager;
//import com.empatica.empalink.ConnectionNotAllowedException;
//import com.empatica.empalink.EmpaticaDevice;
//import com.empatica.empalink.config.EmpaSensorStatus;
//import com.empatica.empalink.config.EmpaSensorType;
//import com.empatica.empalink.config.EmpaStatus;
//import com.empatica.empalink.delegate.EmpaDataDelegate;
//import com.empatica.empalink.delegate.EmpaStatusDelegate;
//import com.google.android.gms.location.FusedLocationProviderClient;
//import com.google.android.gms.location.LocationCallback;
//import com.google.android.gms.location.LocationResult;
//import com.google.android.gms.location.LocationServices;
//import com.google.android.gms.tasks.OnCompleteListener;
//import com.google.android.gms.tasks.Task;
//
//import java.io.File;
//import java.io.FileWriter;
//import java.io.IOException;
//import java.util.ArrayList;
//import java.util.Calendar;
//import java.util.HashMap;
//import java.util.List;
//import java.util.Map;
//
//public class Main extends AppCompatActivity implements EmpaDataDelegate, EmpaStatusDelegate {
//
//    // Constants and UI components
//    private static final int REQUEST_ENABLE_BT = 1;
//
//    private static final String LOG_TAG = Main.class.getSimpleName();
//    private static final int REQUEST_PERMISSION_ACCESS_COARSE_LOCATION = 1;
//    private static final int REQUEST_BLUETOOTH_PERMISSION = 2;
//    private static final String TAG = "MainActivity";
//    private static final String EMPATICA_API_KEY = "2d7ce21b237741cdb35a1007ec98fc18";
//
//    private ActivityResultLauncher<Intent> bluetoothLauncher;
//    // Sensor data storage
//    private List<String> sensorDataList = new ArrayList<>();
//    private StringBuilder edaData = new StringBuilder();
//    private StringBuilder ppgData = new StringBuilder();
//
//    // Location handling
//    private FusedLocationProviderClient mFusedLocationClient;
//    private String latitude = "28.476976";
//    private String longitude = "77.311178";
//    private static final int PERMISSION_ID = 44;
//
//    // Empatica device manager
//    private EmpaDeviceManager deviceManager = null;
//
//    // UI components
//    private TextView statusLabel, deviceNameLabel, wristStatusLabel;
//    private LinearLayout dataCnt;
//    private Button downloadButton;
//
//    private Button disconnectButton;
//
//    // Periodic data posting
//    private Handler handler = new Handler();
//    private Runnable dataPostRunnable = new Runnable() {
//        @Override
//        public void run() {
//            postDataToServer();
//            handler.postDelayed(this, 260000); // 260 seconds
//        }
//    };
//
//    @SuppressLint("MissingInflatedId")
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_main);
//
//        initializeViews();
//        initializeBluetoothLauncher();
//        setupLocationServices();
//        checkAndRequestPermissions();
//    }
//
//    private void initializeViews() {
//        statusLabel = findViewById(R.id.status);
//        deviceNameLabel = findViewById(R.id.deviceName);
//        wristStatusLabel = findViewById(R.id.wrist_status_label);
//        dataCnt = findViewById(R.id.dataArea);
//
//        // Initialize buttons
//        disconnectButton = findViewById(R.id.disconnectButton);
//        downloadButton = findViewById(R.id.downloadButton);
//
//        // Set initial visibility
//        if (statusLabel != null) statusLabel.setVisibility(View.VISIBLE);
//        if (dataCnt != null) dataCnt.setVisibility(View.VISIBLE);
//
//        // Setup button listeners
//        disconnectButton.setOnClickListener(v -> handleDisconnect());
//        downloadButton.setOnClickListener(this::exportCSV);
//    }
//
//    private void initializeBluetoothLauncher() {
//        bluetoothLauncher = registerForActivityResult(
//                new ActivityResultContracts.StartActivityForResult(),
//                result -> {
//                    if (result.getResultCode() == Activity.RESULT_OK) {
//                        initEmpaticaDeviceManager();
//                    } else {
//                        Toast.makeText(this, "Bluetooth is required for device connection",
//                                Toast.LENGTH_LONG).show();
//                    }
//                }
//        );
//    }
//
//    private void setupLocationServices() {
//        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
//    }
//
//    private void checkAndRequestPermissions() {
//        List<String> permissions = new ArrayList<>();
//
//        // Location permissions
//        if (ContextCompat.checkSelfPermission(this,
//                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
//            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
//        }
//
//        // Bluetooth permissions for Android 12+
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
//            if (ContextCompat.checkSelfPermission(this,
//                    Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
//                permissions.add(Manifest.permission.BLUETOOTH_SCAN);
//            }
//            if (ContextCompat.checkSelfPermission(this,
//                    Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
//                permissions.add(Manifest.permission.BLUETOOTH_CONNECT);
//            }
//        }
//
//        if (!permissions.isEmpty()) {
//            ActivityCompat.requestPermissions(this,
//                    permissions.toArray(new String[0]), PERMISSION_ID);
//        } else {
//            initEmpaticaDeviceManager();
//        }
//    }
//
//
//    private void handleDisconnect() {
//        if (deviceManager != null) {
//            deviceManager.disconnect();
//            updateLabel(deviceNameLabel, "");
//            hide();
//        }
//    }
//
//    // Modified Empatica initialization with location permissions
//    private void initEmpaticaDeviceManager() {
//        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
//                != PackageManager.PERMISSION_GRANTED) {
//            ActivityCompat.requestPermissions(this,
//                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
//                    REQUEST_PERMISSION_ACCESS_COARSE_LOCATION);
//            return;
//        }
//
//        try {
//            if (deviceManager != null) {
//                deviceManager.cleanUp();
//            }
//            deviceManager = new EmpaDeviceManager(getApplicationContext(), this, this);
//            deviceManager.authenticateWithAPIKey(EMPATICA_API_KEY);
//        } catch (Exception e) {
//            showErrorDialog("Initialization Error", "Failed to initialize device manager");
//        }
//    }
//
//    // Sensor data callbacks
//    @Override
//    public void didReceiveGSR(float gsr, double timestamp) {
//        edaData.append(gsr).append(" ");
//        String data = timestamp + ",EDA," + gsr;
//        sensorDataList.add(data);
//        Log.d(TAG, "EDA: " + gsr);
//    }
//
//    @Override
//    public void didReceiveBVP(float bvp, double timestamp) {
//        ppgData.append(bvp).append(" ");
//        String data = timestamp + ",BVP," + bvp;
//        sensorDataList.add(data);
//        Log.d(TAG, "BVP: " + bvp);
//    }
//
//    // Server communication methods
//    private void postDataToServer() {
//        new Thread(() -> {
//            getLastLocation();
//            String time = getCurrentTime();
//
//            RequestQueue queue = Volley.newRequestQueue(this);
//            String url = "https://your-server-endpoint.com/api/data";
//
//            StringRequest request = new StringRequest(Request.Method.POST, url,
//                    this::handleServerResponse,
//                    this::handleErrorResponse) {
//                @Override
//                protected Map<String, String> getParams() {
//                    Map<String, String> params = new HashMap<>();
//                    params.put("time", time);
//                    params.put("location", latitude + " " + longitude);
//                    params.put("eda", edaData.toString());
//                    params.put("ppg", ppgData.toString());
//
//                    edaData.setLength(0);
//                    ppgData.setLength(0);
//
//                    return params;
//                }
//            };
//
//            request.setRetryPolicy(new DefaultRetryPolicy(5000, 3, 1));
//            queue.add(request);
//        }).start();
//    }
//
//    private void handleServerResponse(String response) {
//        Log.i(TAG, "Server response: " + response);
//    }
//
//    private void handleErrorResponse(VolleyError error) {
//        Log.e(TAG, "Server error: " + error.getMessage());
//    }
//
//    // Location handling methods
//    @SuppressLint("MissingPermission")
//    private void getLastLocation() {
//        if (checkPermissions() && isLocationEnabled()) {
//            mFusedLocationClient.getLastLocation().addOnCompleteListener(task -> {
//                Location location = task.getResult();
//                if (location != null) {
//                    latitude = String.valueOf(location.getLatitude());
//                    longitude = String.valueOf(location.getLongitude());
//                }
//            });
//        }
//    }
//
//    private void requestPermissions() {
//        ActivityCompat.requestPermissions(this, new String[]{
//                Manifest.permission.ACCESS_COARSE_LOCATION,
//                Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSION_ID);
//    }
//
//    private boolean isLocationEnabled() {
//        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
//        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
//    }
//
//    private boolean checkPermissions() {
//        return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
//    }
//
//    private String getCurrentTime() {
//        Calendar now = Calendar.getInstance();
//        return String.format("%02d %02d %02d",
//                now.get(Calendar.HOUR_OF_DAY),
//                now.get(Calendar.MINUTE),
//                now.get(Calendar.SECOND));
//    }
//
//    // Permission handling
//    @Override
//    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
//        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
//        if (requestCode == PERMISSION_ID && grantResults.length > 0) {
//            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
//                getLastLocation();
//            }
//        }
//    }
//
//    // CSV export functionality
//    public void exportCSV(View view) {
//        File file = saveSensorData();
//        if (file != null) shareCSVFile(file);
//    }
//
//    private File saveSensorData() {
//        File path = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
//        File file = new File(path, "Empatica_Data.csv");
//
//        try (FileWriter writer = new FileWriter(file)) {
//            writer.append("Timestamp,SensorType,Value\n");
//            for (String data : sensorDataList) {
//                writer.append(data).append("\n");
//            }
//            return file;
//        } catch (IOException e) {
//            Log.e(TAG, "CSV save failed", e);
//            return null;
//        }
//    }
//
//    private void shareCSVFile(File file) {
//        Uri uri = FileProvider.getUriForFile(this,
//                getPackageName() + ".provider", file);
//
//        Intent shareIntent = new Intent(Intent.ACTION_SEND)
//                .setType("text/csv")
//                .putExtra(Intent.EXTRA_STREAM, uri)
//                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
//
//        startActivity(Intent.createChooser(shareIntent, "Share CSV"));
//    }
//
//    // Activity lifecycle management
//    @Override
//    protected void onDestroy() {
//        handler.removeCallbacks(dataPostRunnable);
//        if (deviceManager != null) {
//            deviceManager.cleanUp();
//        }
//        super.onDestroy();
//    }
//
//    // Helper methods
//    private void updateLabel(TextView label, String text) {
//        runOnUiThread(() -> label.setText(text));
//    }
//
//    private void showErrorDialog(String title, String message) {
//        new AlertDialog.Builder(this)
//                .setTitle(title)
//                .setMessage(message)
//                .setPositiveButton("OK", null)
//                .show();
//    }
//
//    @Override
//    public void didDiscoverDevice(EmpaticaDevice bluetoothDevice, String deviceName, int rssi, boolean allowed) {
//        // Check if the discovered device can be used with your API key. If allowed is always false,
//        // the device is not linked with your API key. Please check your developer area at
//        // https://www.empatica.com/connect/developer.php
//
//        Log.i(LOG_TAG, "didDiscoverDevice" + deviceName + "allowed: " + allowed);
//
//        if (allowed) {
//            // Stop scanning. The first allowed device will do.
//            deviceManager.stopScanning();
//            try {
//                // Connect to the device
//                deviceManager.connectDevice(bluetoothDevice);
//                Toast.makeText(Main.this, "Device connected", Toast.LENGTH_SHORT).show();
////                updateLabel(deviceNameLabel, "To: " + deviceName);
//            } catch (ConnectionNotAllowedException e) {
//                // This should happen only if you try to connect when allowed == false.
//                Toast.makeText(Main.this, "Sorry, you can't connect to this device", Toast.LENGTH_SHORT).show();
//                Log.e(LOG_TAG, "didDiscoverDevice" + deviceName + "allowed: " + allowed + " - ConnectionNotAllowedException", e);
//            }
//        }
//    }
//
//    @Override
//    public void didFailedScanning(int errorCode) {
//
//        /*
//         A system error occurred while scanning.
//         @see https://developer.android.com/reference/android/bluetooth/le/ScanCallback
//        */
//        switch (errorCode) {
//            case ScanCallback.SCAN_FAILED_ALREADY_STARTED:
//                Log.e(LOG_TAG,"Scan failed: a BLE scan with the same settings is already started by the app");
//                break;
//            case ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED:
//                Log.e(LOG_TAG,"Scan failed: app cannot be registered");
//                break;
//            case ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED:
//                Log.e(LOG_TAG,"Scan failed: power optimized scan feature is not supported");
//                break;
//            case ScanCallback.SCAN_FAILED_INTERNAL_ERROR:
//                Log.e(LOG_TAG,"Scan failed: internal error");
//                break;
//            default:
//                Log.e(LOG_TAG,"Scan failed with unknown error (errorCode=" + errorCode + ")");
//                break;
//        }
//    }
//
//    @Override
//    public void didRequestEnableBluetooth() {
//        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
//        bluetoothLauncher.launch(enableBtIntent);
//    }
//
//
//    @Override
//    public void bluetoothStateChanged() {
//        // E4link detected a bluetooth adapter change
//        // Check bluetooth adapter and update your UI accordingly.
//        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
//        if (adapter != null) {  // Add null check
//            boolean isBluetoothOn = adapter.isEnabled();
//            Log.i(LOG_TAG, "Bluetooth State Changed: " + isBluetoothOn);
//        }
//    }
//
//
//
//    @Override
//    public void didUpdateSensorStatus(@EmpaSensorStatus int status, EmpaSensorType type) {
//        TextView wristStatusLabel = findViewById(R.id.wrist_status_label);
//        if (status == EmpaSensorStatus.ON_WRIST) {
//            updateLabel(wristStatusLabel, "ON WRIST");
//        } else {
//            updateLabel(wristStatusLabel, "NOT ON WRIST");
//        }
//    }
//
//    @Override
//    public void didUpdateStatus(EmpaStatus status) {
//        TextView statusLabel = findViewById(R.id.status);
//        TextView deviceNameLabel = findViewById(R.id.deviceName);
//        updateLabel(statusLabel, status.name());
//        // ...rest of existing status handling...
//    }
//
//    @Override
//    public void didReceiveAcceleration(int x, int y, int z, double timestamp) {
////        updateLabel(accel_xLabel, "" + x);
////        updateLabel(accel_yLabel, "" + y);
////        updateLabel(accel_zLabel, "" + z);
//    }
//
//
//    @Override
//    public void didReceiveBatteryLevel(float battery, double timestamp) {
////        updateLabel(batteryLabel, String.format("%.0f %%", battery * 100));
//    }
//
//    @Override
//    public void didReceiveIBI(float ibi, double timestamp) {
////        updateLabel(ibiLabel, "" + ibi);
//    }
//
//    @Override
//    public void didReceiveTemperature(float temp, double timestamp) {
////        updateLabel(temperatureLabel, "" + temp);
//    }
//
//    // Update a label with some text, making sure this is run in the UI thread
//
//
//
//
//    @Override
//    public void didEstablishConnection() {
//        Log.d(TAG, "didEstablishConnection: Connection established");
//        show();
//    }
//
//    private void show() {
//        Log.d(TAG, "show: Making sensor data area visible");
//        runOnUiThread(new Runnable() {
//            @Override
//            public void run() {
//                dataCnt.setVisibility(View.VISIBLE);
//            }
//        });
//    }
//    @Override
//    public void didReceiveTag(double timestamp) {
//        Log.d(TAG, "didReceiveTag: Tag received at " + timestamp);
//        // Handle tag reception if needed.
//    }
//
//
//    private void hide() {
//        Log.d(TAG, "hide: Hiding sensor data area");
//        runOnUiThread(new Runnable() {
//            @Override
//            public void run() {
//                dataCnt.setVisibility(View.INVISIBLE);
//            }
//        });
//    }
//
//
//    @Override
//    public void didUpdateOnWristStatus(@EmpaSensorStatus final int status) {
//        Log.d(TAG, "didUpdateOnWristStatus: Wrist status updated to " + (status == EmpaSensorStatus.ON_WRIST ? "ON_WRIST" : "NOT ON WRIST"));
//        runOnUiThread(new Runnable() {
//            @Override
//            public void run() {
//                if (status == EmpaSensorStatus.ON_WRIST) {
//                    wristStatusLabel.setText("ON WRIST");
//                } else {
//                    wristStatusLabel.setText("NOT ON WRIST");
//                }
//            }
//        });
//    }
//}