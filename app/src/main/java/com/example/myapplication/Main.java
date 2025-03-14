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



//import android.Manifest;
//import android.annotation.SuppressLint;
//import android.app.Activity;
//import android.app.AlertDialog;
//import android.bluetooth.BluetoothAdapter;
//import android.content.DialogInterface;
//import android.content.Intent;
//import android.content.pm.PackageManager;
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
//import androidx.activity.result.ActivityResultLauncher;
//import androidx.activity.result.contract.ActivityResultContracts;
//import androidx.annotation.NonNull;
//import androidx.appcompat.app.AppCompatActivity;
//import androidx.core.app.ActivityCompat;
//import androidx.core.content.ContextCompat;
//import androidx.core.content.FileProvider;
//import com.empatica.empalink.ConfigurationProfileException;
//import com.empatica.empalink.EmpaDeviceManager;
//import com.empatica.empalink.ConnectionNotAllowedException;
//import com.empatica.empalink.EmpaticaDevice;
//import com.empatica.empalink.config.EmpaSensorStatus;
//import com.empatica.empalink.config.EmpaSensorType;
//import com.empatica.empalink.config.EmpaStatus;
//import com.empatica.empalink.delegate.EmpaDataDelegate;
//import com.empatica.empalink.delegate.EmpaStatusDelegate;
//import android.content.Intent;
//import androidx.core.content.ContextCompat;
//import java.io.File;
//import java.io.FileOutputStream;
//import java.io.FileWriter;
//import java.io.IOException;
//import java.util.ArrayList;
//import java.util.HashMap;
//import java.util.List;
//import java.util.zip.ZipEntry;
//import java.util.zip.ZipFile;
//import java.util.zip.ZipOutputStream;
//public class MainActivity extends AppCompatActivity implements EmpaDataDelegate, EmpaStatusDelegate {
//
//    private static final int REQUEST_ENABLE_BT = 1;
//    private static final int REQUEST_PERMISSION_ACCESS_COARSE_LOCATION = 1;
//    private static final int REQUEST_BLUETOOTH_PERMISSION = 2;
//    private static final int REQUEST_BLUETOOTH_SCAN_PERMISSION = 3;
//    private static final String TAG = "MainActivity"; // Tag for logging
//    private static final String EMPATICA_API_KEY = "2fe2f405268349efaf63509c3dec89f5"; // TODO: Insert your API Key here
//
//    private EmpaDeviceManager deviceManager = null;
//
//    private static final int REQUEST_WRITE_STORAGE_PERMISSION = 4;
//
//    // UI components
//    private TextView accel_xLabel;
//    private TextView accel_yLabel;
//    private TextView accel_zLabel;
//    private TextView bvpLabel;
//    private TextView edaLabel;
//    private TextView ibiLabel;
//    private TextView temperatureLabel;
//    private TextView batteryLabel;
//    private TextView statusLabel;
//    private TextView deviceNameLabel;
//    private TextView wristStatusLabel;
//    private LinearLayout dataCnt;
//    private Button downloadButton;
//
//    private boolean isScanning = false;
//    private final Object scanLock = new Object();
//
//    // List to store sensor data as CSV rows
//    HashMap<String,List<String>> sensorDataList = new HashMap<>();
//
//    private final String[] sensorTypes = {"ACC","BVP","IBI","TEMP","EDA"};
//
//    @SuppressLint("MissingInflatedId")
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        Log.d(TAG, "onCreate: Activity is being created");
//        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_main);
//
//        // Initialize UI components
//        statusLabel = findViewById(R.id.status);
//        dataCnt = findViewById(R.id.dataArea);
//        accel_xLabel = findViewById(R.id.accel_x);
//        accel_yLabel = findViewById(R.id.accel_y);
//        accel_zLabel = findViewById(R.id.accel_z);
//        bvpLabel = findViewById(R.id.bvp);
//        edaLabel = findViewById(R.id.eda);
//        ibiLabel = findViewById(R.id.ibi);
//        temperatureLabel = findViewById(R.id.temperature);
//        batteryLabel = findViewById(R.id.battery);
//        deviceNameLabel = findViewById(R.id.deviceName);
//        wristStatusLabel = findViewById(R.id.wrist_status_label);
//        Log.d(TAG, "onCreate: UI components initialized");
//
//        // Disconnect button to disconnect from the device
//        Button disconnectButton = findViewById(R.id.disconnectButton);
//        disconnectButton.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                Log.d(TAG, "Disconnect button clicked");
//                if (deviceManager != null) {
//                    deviceManager.disconnect();
//                    Log.i(TAG, "Device manager requested disconnect");
//                }
//            }
//        });
//
//        // Download button to export CSV file
//        downloadButton = findViewById(R.id.downloadButton);
//        downloadButton.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
//                Log.d(TAG, "Download button clicked");
//                exportCSV(view);
//            }
//        });
//
//        for (String type : sensorTypes) {
//            sensorDataList.put(type, new ArrayList<>());
//        }
//
//        // Initialize the Empatica device manager.
//        initEmpaticaDeviceManager();
//
//
//    }
//
//    /**
//     * Handles the results for permission requests.
//     */
//    @Override
//    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
//        Log.d(TAG, "onRequestPermissionsResult: Received result for request code " + requestCode);
//        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
//
//        switch (requestCode) {
//            case REQUEST_PERMISSION_ACCESS_COARSE_LOCATION:
//                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
//                    Log.i(TAG, "Location permission granted");
//                    initEmpaticaDeviceManager();
//                } else {
//                    Log.w(TAG, "Location permission denied");
//                    showPermissionDialog(Manifest.permission.ACCESS_COARSE_LOCATION, REQUEST_PERMISSION_ACCESS_COARSE_LOCATION);
//                }
//                break;
//
//            case REQUEST_BLUETOOTH_PERMISSION:
//                boolean allPermissionsGranted = true;
//                for (int result : grantResults) {
//                    if (result != PackageManager.PERMISSION_GRANTED) {
//                        allPermissionsGranted = false;
//                        break;
//                    }
//                }
//
//                if (allPermissionsGranted) {
//                    Log.i(TAG, "All Bluetooth permissions granted");
//                    initEmpaticaDeviceManager();
//                } else {
//                    Log.w(TAG, "Bluetooth permissions denied");
//                    new AlertDialog.Builder(this)
//                            .setTitle("Permissions Required")
//                            .setMessage("Bluetooth permissions are required to connect to the device.")
//                            .setPositiveButton("Retry", (dialog, which) -> {
//                                // Collect denied permissions
//                                List<String> deniedPermissions = new ArrayList<>();
//                                for (int i = 0; i < permissions.length; i++) {
//                                    if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
//                                        deniedPermissions.add(permissions[i]);
//                                    }
//                                }
//                                Log.d(TAG, "Retrying for denied Bluetooth permissions: " + deniedPermissions);
//                                ActivityCompat.requestPermissions(MainActivity.this,
//                                        deniedPermissions.toArray(new String[0]), REQUEST_BLUETOOTH_PERMISSION);
//                            })
//                            .setNegativeButton("Exit", (dialog, which) -> {
//                                Log.d(TAG, "Exiting application due to lack of Bluetooth permissions");
//                                finish();
//                            })
//                            .show();
//                }
//                break;
//                case REQUEST_WRITE_STORAGE_PERMISSION:
//                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
//                    // Permission granted, try saving again
//                    exportCSV(null);
//                } else {
//                    Toast.makeText(this, "Storage permission required to save data",
//                            Toast.LENGTH_SHORT).show();
//                }
//        }
//    }
//
//    /**
//     * Helper method to show an alert dialog when a permission is denied.
//     */
//    private void showPermissionDialog(String permission, int requestCode) {
//        Log.d(TAG, "showPermissionDialog: Showing permission dialog for " + permission);
//        final boolean needRationale = ActivityCompat.shouldShowRequestPermissionRationale(this, permission);
//
//        new AlertDialog.Builder(this)
//                .setTitle("Permission required")
//                .setMessage("This permission is necessary for the app to function properly. Please allow it.")
//                .setPositiveButton("Retry", new DialogInterface.OnClickListener() {
//                    public void onClick(DialogInterface dialog, int which) {
//                        Log.d(TAG, "User chose to retry permission request for " + permission);
//                        if (needRationale) {
//                            ActivityCompat.requestPermissions(MainActivity.this, new String[]{permission}, requestCode);
//                        } else {
//                            // If "Never Ask Again" is selected, direct the user to the app settings.
//                            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
//                            Uri uri = Uri.fromParts("package", getPackageName(), null);
//                            intent.setData(uri);
//                            startActivity(intent);
//                        }
//                    }
//                })
//                .setNegativeButton("Exit application", new DialogInterface.OnClickListener() {
//                    public void onClick(DialogInterface dialog, int which) {
//                        Log.d(TAG, "User chose to exit application due to missing permission: " + permission);
//                        finish();
//                    }
//                })
//                .show();
//    }
//
//    /**
//     * Initialize the Empatica device manager.
//     * This method first checks for the ACCESS_COARSE_LOCATION permission (required for BLE scanning)
//     * and then the Bluetooth permissions on Android 12+.
//     */
//    private void initEmpaticaDeviceManager() {
//        Log.d(TAG, "initEmpaticaDeviceManager: Starting device manager initialization");
//
//        // Check location permission first
//        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
//                != PackageManager.PERMISSION_GRANTED) {
//            ActivityCompat.requestPermissions(this,
//                    new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
//                    REQUEST_PERMISSION_ACCESS_COARSE_LOCATION);
//            return;
//        }
//
//        // Check Bluetooth permissions for Android 12+
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
//            List<String> permissions = new ArrayList<>();
//
//            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
//                    != PackageManager.PERMISSION_GRANTED) {
//                permissions.add(Manifest.permission.BLUETOOTH_CONNECT);
//            }
//
//            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
//                    != PackageManager.PERMISSION_GRANTED) {
//                permissions.add(Manifest.permission.BLUETOOTH_SCAN);
//            }
//
//            if (!permissions.isEmpty()) {
//                ActivityCompat.requestPermissions(this,
//                        permissions.toArray(new String[0]),
//                        REQUEST_BLUETOOTH_PERMISSION);
//                return;
//            }
//        }
//
//        // Initialize device manager with proper error handling
//        try {
//            if (deviceManager != null) {
//                deviceManager.cleanUp();
//                deviceManager = null;
//            }
//
//            // Create new device manager instance
//            deviceManager = new EmpaDeviceManager(getApplicationContext(), this, this);
//
//            // Authenticate with API key
//            if (!TextUtils.isEmpty(EMPATICA_API_KEY)) {
//                Log.e(TAG, "Authenticating with the appropriate API key "+EMPATICA_API_KEY);
//                deviceManager.authenticateWithAPIKey(EMPATICA_API_KEY);
//            } else {
//                throw new IllegalStateException("API Key is empty");
//            }
//        } catch (Exception e) {
//            Log.e(TAG, "Error initializing EmpaticaDeviceManager", e);
//            showErrorDialog("Initialization Error",
//                    "Failed to initialize device manager. Please restart the app.");
//        }
//    }
//
//    @Override
//    protected void onPause() {
//        Log.d(TAG, "onPause: Activity paused, stopping scanning if active");
//        super.onPause();
//        if (deviceManager != null) {
//            deviceManager.stopScanning();
//        }
//    }
//
//    @Override
//    protected void onDestroy() {
//        Log.d(TAG, "onDestroy: Activity being destroyed, cleaning up device manager");
//        super.onDestroy();
//        if (isFinishing()) {
//            if (deviceManager != null) {
//                deviceManager.disconnect();
//                deviceManager.cleanUp();
//            }
//        }
//    }
//
//    @Override
//    public void didDiscoverDevice(EmpaticaDevice bluetoothDevice, String deviceName, int rssi, boolean allowed) {
//        Log.i(TAG, String.format("didDiscoverDevice: Device discovered - Name: %s, RSSI: %d, Allowed: %b", deviceName, rssi, allowed));
//
//        if (allowed) {
//            Log.d(TAG, "didDiscoverDevice: Device is allowed, attempting to connect");
//            deviceManager.stopScanning();
//            try {
//                deviceManager.connectDevice(bluetoothDevice);
//                Log.i(TAG, "didDiscoverDevice: Connection request sent for device: " + deviceName);
//                updateLabel(deviceNameLabel, "To: " + deviceName);
//            } catch (ConnectionNotAllowedException e) {
//                Log.e(TAG, "didDiscoverDevice: Connection not allowed to device: " + deviceName, e);
//                Toast.makeText(MainActivity.this, "Sorry, you can't connect to this device", Toast.LENGTH_SHORT).show();
//            }
//        } else {
//            Log.w(TAG, "didDiscoverDevice: Device not allowed: " + deviceName);
//        }
//    }
//
//    @Override
//    public void didFailedScanning(int errorCode) {
//        Log.e(TAG, "didFailedScanning: Scanning failed with error code: " + errorCode);
//        switch(errorCode) {
//            case 1:
//                Log.e(TAG, "Scan failed due to multiple sessions already present. Will retry after delay.");
//                deviceManager.stopScanning();
//                isScanning = false; // Reset flag to ensure proper restart
//                new android.os.Handler(getMainLooper()).postDelayed(() -> {
//                    if (!isScanning) {
//                        deviceManager.startScanning();
//                        isScanning = true;
//                        Log.d(TAG, "Retrying scan after delay");
//                    }
//                }, 1000);  // 1 second delay
//                break;
//            default:
//                Log.e(TAG, "didFailedScanning: Unhandled error code: " + errorCode);
//        }
//    }
//
//
//    private void startDeviceScan() {
//        synchronized (scanLock) {
//            if (isScanning) {
//                Log.d(TAG, "Scan already in progress, stopping current scan");
//                stopDeviceScan();
//                // Add delay before starting new scan
//                new Handler(Looper.getMainLooper()).postDelayed(() -> {
//                    initiateNewScan();
//                }, 1000); // 1 second delay
//            } else {
//                initiateNewScan();
//            }
//        }
//    }
//    private void initiateNewScan() {
//        synchronized (scanLock) {
//            if (deviceManager == null) {
//                Log.e(TAG, "Device manager is null, reinitializing...");
//                initEmpaticaDeviceManager();
//                return;
//            }
//
//            if (isScanning) {
//                Log.d(TAG, "Scan already in progress, skipping new scan");
//                return;
//            }
//
//            try {
//                // Make sure any previous scan is stopped
//                deviceManager.stopScanning();
//                // Small delay to ensure previous scan is fully stopped
//                new Handler(Looper.getMainLooper()).postDelayed(() -> {
//                    synchronized (scanLock) {
//                        try {
//                            deviceManager.startScanning();
//                            isScanning = true;
//                            updateLabel(statusLabel, "Scanning...");
//                            Log.d(TAG, "Scan started successfully");
//                        } catch (Exception e) {
//                            Log.e(TAG, "Failed to start scanning", e);
//                            isScanning = false;
//                            handleScanError(e);
//                        }
//                    }
//                }, 200); // 200ms delay
//            } catch (Exception e) {
//                Log.e(TAG, "Failed to stop previous scanning", e);
//                isScanning = false;
//                handleScanError(e);
//            }
//        }
//    }
//    private void stopDeviceScan() {
//        synchronized (scanLock) {
//            if (isScanning && deviceManager != null) {
//                try {
//                    deviceManager.stopScanning();
//                    Log.d(TAG, "Scan stopped successfully");
//                } catch (Exception e) {
//                    Log.e(TAG, "Error stopping scan", e);
//                } finally {
//                    isScanning = false;
//                }
//            }
//        }
//    }
//
//    private void handleScanError(Exception e) {
//        String errorMessage;
//        if (e instanceof ConfigurationProfileException) {
//            errorMessage = "Invalid device configuration. Please ensure proper setup.";
//            // Reset device manager
//            if (deviceManager != null) {
//                deviceManager.cleanUp();
//                deviceManager = null;
//            }
//            initEmpaticaDeviceManager();
//        } else {
//            errorMessage = "Failed to start scanning. Please try again.";
//        }
//
//        showErrorDialog("Scanning Error", errorMessage);
//    }
//
//
//    private File saveZipToDownloads() {
//        // Get the Downloads directory
//        File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
//        String timestamp = String.format("%d", System.currentTimeMillis());
//        File zipFile = new File(downloadsDir, "empatica_data_" + timestamp + ".zip");
//
//        // Ensure we have write permission
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
//                    != PackageManager.PERMISSION_GRANTED) {
//                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
//                        REQUEST_WRITE_STORAGE_PERMISSION);
//                return null;
//            }
//        }
//
//        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
//            // Create separate files for each sensor type
//            createAccelerationFile(zos, timestamp);
//            createBVPFile(zos, timestamp);
//            createEDAFile(zos, timestamp);
//            createIBIFile(zos, timestamp);
//            createTemperatureFile(zos, timestamp);
//
//            Log.i(TAG, "Successfully saved ZIP file to Downloads: " + zipFile.getAbsolutePath());
//            return zipFile;
//        } catch (IOException e) {
//            Log.e(TAG, "Error creating ZIP file in Downloads", e);
//            return null;
//        }
//    }
//
//    private void createAccelerationFile(ZipOutputStream zos, String timestamp) throws IOException {
//        List<String> accData = sensorDataList.get("ACC");
//        if (accData != null && !accData.isEmpty()) {
//            StringBuilder csvContent = new StringBuilder();
//            csvContent.append("Timestamp,Type,X,Y,Z\n");
//            for (String data : accData) {
//                csvContent.append(data).append("\n");
//            }
//            addToZip(zos, "ACC_" + timestamp + ".csv", csvContent.toString());
//        }
//    }
//
//    private void createBVPFile(ZipOutputStream zos, String timestamp) throws IOException {
//        List<String> bvpData = sensorDataList.get("BVP");
//        if (bvpData != null && !bvpData.isEmpty()) {
//            StringBuilder csvContent = new StringBuilder();
//            csvContent.append("Timestamp,Type,Value\n");
//            for (String data : bvpData) {
//                csvContent.append(data).append("\n");
//            }
//            addToZip(zos, "BVP_" + timestamp + ".csv", csvContent.toString());
//        }
//    }
//
//    private void createEDAFile(ZipOutputStream zos, String timestamp) throws IOException {
//        List<String> edaData = sensorDataList.get("EDA");
//        if (edaData != null && !edaData.isEmpty()) {
//            StringBuilder csvContent = new StringBuilder();
//            csvContent.append("Timestamp,Type,Value\n");
//            for (String data : edaData) {
//                csvContent.append(data).append("\n");
//            }
//            addToZip(zos, "EDA_" + timestamp + ".csv", csvContent.toString());
//        }
//    }
//
//    private void createIBIFile(ZipOutputStream zos, String timestamp) throws IOException {
//        List<String> ibiData = sensorDataList.get("IBI");
//        if (ibiData != null && !ibiData.isEmpty()) {
//            StringBuilder csvContent = new StringBuilder();
//            csvContent.append("Timestamp,Type,Value\n");
//            for (String data : ibiData) {
//                csvContent.append(data).append("\n");
//            }
//            addToZip(zos, "IBI_" + timestamp + ".csv", csvContent.toString());
//        }
//    }
//
//    private void createTemperatureFile(ZipOutputStream zos, String timestamp) throws IOException {
//        List<String> tempData = sensorDataList.get("TEMP");
//        if (tempData != null && !tempData.isEmpty()) {
//            StringBuilder csvContent = new StringBuilder();
//            csvContent.append("Timestamp,Type,Value\n");
//            for (String data : tempData) {
//                csvContent.append(data).append("\n");
//            }
//            addToZip(zos, "TEMP_" + timestamp + ".csv", csvContent.toString());
//        }
//    }
//
//    private void addToZip(ZipOutputStream zos, String fileName, String content) throws IOException {
//        ZipEntry entry = new ZipEntry(fileName);
//        zos.putNextEntry(entry);
//        zos.write(content.getBytes());
//        zos.closeEntry();
//    }
//
//    /**
//     * Called by the Empatica SDK when it needs the user to enable Bluetooth.
//     */
//    @Override
//    public void didRequestEnableBluetooth() {
//        Log.d(TAG, "didRequestEnableBluetooth: Requesting user to enable Bluetooth");
//        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
//        enableBtLauncher.launch(enableBtIntent);
//    }
//
//    private final ActivityResultLauncher<Intent> enableBtLauncher =
//            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
//                Log.d(TAG, "enableBtLauncher: Received result from Bluetooth enable request");
//                if (result.getResultCode() == Activity.RESULT_OK) {
//                    Log.i(TAG, "enableBtLauncher: Bluetooth enabled");
//                    // Don't start scanning here - let bluetoothStateChanged handle it
//                } else {
//                    Log.w(TAG, "enableBtLauncher: Bluetooth not enabled by the user");
//                    Toast.makeText(this, "Bluetooth must be enabled to proceed", Toast.LENGTH_SHORT).show();
//                }
//            });
//
//
//    @Override
//    public void bluetoothStateChanged() {
//        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
//        if (bluetoothAdapter != null) {
//            runOnUiThread(() -> {
//                if (bluetoothAdapter.isEnabled()) {
//                    updateLabel(statusLabel, "Bluetooth Enabled");
//
//                    // Reset scanning state
//                    synchronized (scanLock) {
//                        isScanning = false;
//                    }
//
//                    // Instead of directly starting scan, check if device manager needs initialization
//                    if (deviceManager == null) {
//                        initEmpaticaDeviceManager();
//                    }
//                    // Don't start scanning here - let didUpdateStatus handle it
//                } else {
//                    updateLabel(statusLabel, "Bluetooth Disabled");
//                    stopDeviceScan();
//                    hide();
//                    didRequestEnableBluetooth();
//                }
//            });
//        }
//    }
//
//    private void showErrorDialog(String title, String message) {
//        runOnUiThread(() -> new AlertDialog.Builder(this)
//                .setTitle(title)
//                .setMessage(message)
//                .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
//                .show());
//    }
//
//
//    @Override
//    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
//        Log.d(TAG, "onActivityResult: requestCode=" + requestCode + ", resultCode=" + resultCode);
//        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
//            Log.w(TAG, "onActivityResult: User cancelled Bluetooth enable request");
//            return;
//        }
//        super.onActivityResult(requestCode, resultCode, data);
//    }
//
//    @Override
//    public void didUpdateSensorStatus(@EmpaSensorStatus int status, EmpaSensorType type) {
//        Log.d(TAG, "didUpdateSensorStatus: Sensor " + type + " status updated to " + status);
//        didUpdateOnWristStatus(status);
//    }
//
//    @Override
//    public void didUpdateStatus(EmpaStatus status) {
//        Log.i(TAG, "didUpdateStatus: Empatica status updated: " + status.name());
//        updateLabel(statusLabel, status.name());
//
//        switch (status) {
//            case READY:
//                Log.d(TAG, "didUpdateStatus: Device manager ready");
//                updateLabel(statusLabel, status.name() + " - Turn on your device");
//                // Instead of directly starting scan, use startDeviceScan which handles synchronization
//                startDeviceScan();
//                hide();
//                break;
//
//            case DISCOVERING:
//                Log.d(TAG, "Active discovery started");
//                updateLabel(statusLabel, "Searching for devices...");
//                break;
//
//            case CONNECTING:
//                Log.d(TAG, "Attempting to connect");
//                updateLabel(statusLabel, "Connecting...");
//                break;
//            case CONNECTED:
//                Log.i(TAG, "didUpdateStatus: Device connected successfully");
//                show();
//                break;
//            case DISCONNECTED:
//                Log.i(TAG, "didUpdateStatus: Device disconnected");
//                updateLabel(deviceNameLabel, "");
//                hide();
//                File zipFile = createZip();
//                if (zipFile != null) {
//                    shareZip(zipFile);
//                }
//                // After disconnection, restart scanning
//                startDeviceScan();
//                break;
//            default:
//                Log.d(TAG, "didUpdateStatus: Status changed to: " + status.name());
//                break;
//        }
//    }
//
//    @Override
//    public void didReceiveAcceleration(int x, int y, int z, double timestamp) {
//        String data = String.format("%f,ACC,%d,%d,%d", timestamp, x, y, z);
//        sensorDataList.get("ACC").add(data);
//        Log.d(TAG, "didReceiveAcceleration: " + data);
//        updateLabel(accel_xLabel, "" + x);
//        updateLabel(accel_yLabel, "" + y);
//        updateLabel(accel_zLabel, "" + z);
//    }
//
//    @Override
//    public void didReceiveBVP(float bvp, double timestamp) {
//        String data = String.format("%f,BVP,%f", timestamp, bvp);
//        sensorDataList.get("BVP").add(data);
//        Log.d(TAG, "didReceiveBVP: " + data);
//        updateLabel(bvpLabel, "" + bvp);
//    }
//
//    @Override
//    public void didReceiveBatteryLevel(float battery, double timestamp) {
//        String batteryText = String.format("%.0f %%", battery * 100);
//        Log.d(TAG, "didReceiveBatteryLevel: " + batteryText);
//        updateLabel(batteryLabel, batteryText);
//    }
//
//    @Override
//    public void didReceiveGSR(float gsr, double timestamp) {
//        String data = String.format("%f,EDA,%f", timestamp, gsr);
//        sensorDataList.get("EDA").add(data);
//        Log.d(TAG, "didReceiveGSR: " + data);
//        updateLabel(edaLabel, "" + gsr);
//    }
//
//    @Override
//    public void didReceiveIBI(float ibi, double timestamp) {
//        String data = String.format("%f,IBI,%f", timestamp, ibi);
//        sensorDataList.get("IBI").add(data);
//        Log.d(TAG, "didReceiveIBI: " + data);
//        updateLabel(ibiLabel, "" + ibi);
//    }
//
//    @Override
//    public void didReceiveTemperature(float temp, double timestamp) {
//        String data = String.format("%f,TEMP,%f", timestamp, temp);
//        sensorDataList.get("TEMP").add(data);
//        Log.d(TAG, "didReceiveTemperature: " + data);
//        updateLabel(temperatureLabel, "" + temp);
//    }
//
//    private void updateLabel(final TextView label, final String text) {
//        runOnUiThread(new Runnable() {
//            @Override
//            public void run() {
//                Log.v(TAG, "updateLabel: Updating label text to " + text);
//                label.setText(text);
//            }
//        });
//    }
//
//    @Override
//    public void didReceiveTag(double timestamp) {
//        Log.d(TAG, "didReceiveTag: Tag received at " + timestamp);
//        // Handle tag reception if needed.
//    }
//
//    @Override
//    public void didEstablishConnection() {
//        Log.d(TAG, "didEstablishConnection: Connection established");
//        show();
//    }
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
//    private void saveAllDataToCSV() {
//        for (String sensorType : sensorTypes) {
//            List<String> dataList = sensorDataList.get(sensorType);
//            if (dataList != null && !dataList.isEmpty()) {
//                saveDataToCSV(sensorType);
//            }
//        }
//        // Clear data after saving
//        for (String type : sensorTypes) {
//            sensorDataList.get(type).clear();
//        }
//    }
//
//    /**
//     * Called when the download button is clicked.
//     * Saves the data to a CSV file and triggers a share intent.
//     */
//    public void exportCSV(View view) {
//        Log.d(TAG, "exportCSV: Exporting CSV file to Downloads folder");
//        File zipFile = saveZipToDownloads();
//        if (zipFile != null) {
//            Toast.makeText(this, "Data saved to Downloads: " + zipFile.getName(), Toast.LENGTH_LONG).show();
//            // Clear the data after successful save
//            for (String type : sensorTypes) {
//                sensorDataList.get(type).clear();
//            }
//        } else {
//            Toast.makeText(this, "Failed to save data", Toast.LENGTH_SHORT).show();
//        }
//    }
//
//    private File createZip() {
//        File path = getExternalFilesDir(null);
//        String timestamp = String.format("%d", System.currentTimeMillis());
//        File zipFile = new File(path, "empatica_data_compiled" + timestamp + ".zip");
//
//        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
//            for (String sensorType : sensorTypes) {
//                List<String> dataList = sensorDataList.get(sensorType);
//                if (dataList != null && !dataList.isEmpty()) {
//                    // Create CSV content in memory
//                    StringBuilder csvContent = new StringBuilder();
//
//                    // Add header
//                    if (sensorType.equals("ACC")) {
//                        csvContent.append("Timestamp,Type,X,Y,Z\n");
//                    } else {
//                        csvContent.append("Timestamp,Type,Value\n");
//                    }
//
//                    // Add data
//                    for (String data : dataList) {
//                        csvContent.append(data).append("\n");
//                    }
//
//                    // Add to ZIP
//                    ZipEntry entry = new ZipEntry(sensorType + ".csv");
//                    zos.putNextEntry(entry);
//                    zos.write(csvContent.toString().getBytes());
//                    zos.closeEntry();
//                }
//            }
//            return zipFile;
//        } catch (IOException e) {
//            Log.e(TAG, "Error creating ZIP file", e);
//            return null;
//        }
//    }
//
//    private void shareZip(File file) {
//        Uri fileUri = FileProvider.getUriForFile(this,
//                getApplicationContext().getPackageName() + ".provider", file);
//
//        Intent intent = new Intent(Intent.ACTION_SEND);
//        intent.setType("application/zip");
//        intent.putExtra(Intent.EXTRA_STREAM, fileUri);
//        intent.putExtra(Intent.EXTRA_SUBJECT, "Empatica Sensor Data");
//        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
//
//        startActivity(Intent.createChooser(intent, "Share ZIP File"));
//    }
//
//    /**
//     * Saves the collected sensor data to a CSV file.
//     *
//     * @return The CSV file if saved successfully, or null if there was an error.
//     */
//    private File saveDataToCSV(String sensorType) {
//        File path = getExternalFilesDir(null);
//        String timestamp = String.format("%d", System.currentTimeMillis());
//        File file = new File(path, sensorType + "_" + timestamp + ".csv");
//
//        try (FileWriter writer = new FileWriter(file)) {
//            // Write header based on sensor type
//            switch (sensorType) {
//                case "ACC":
//                    writer.append("Timestamp,Type,X,Y,Z\n");
//                    break;
//                default:
//                    writer.append("Timestamp,Type,Value\n");
//                    break;
//            }
//
//            // Write data
//            for (String data : sensorDataList.get(sensorType)) {
//                writer.append(data).append("\n");
//            }
//            writer.flush();
//            Log.i(TAG, "Saved " + sensorType + " data to " + file.getAbsolutePath());
//            return file;
//        } catch (IOException e) {
//            Log.e(TAG, "Error saving " + sensorType + " file", e);
//            return null;
//        }
//    }
//
//    /**
//     * Shares the CSV file using Android's share intent.
//     *
//     * @param file The CSV file to share.
//     */
//
//    private void shareCSVFile(File file) {
//        Log.d(TAG, "shareCSVFile: Sharing CSV file");
//        // Use FileProvider to get a content URI
//        Uri fileUri = FileProvider.getUriForFile(this,
//                getApplicationContext().getPackageName() + ".provider", file);
//
//        Intent intent = new Intent(Intent.ACTION_SEND);
//        intent.setType("text/csv");
//        intent.putExtra(Intent.EXTRA_STREAM, fileUri);
//        intent.putExtra(Intent.EXTRA_SUBJECT, "Empatica Sensor Data");
//        intent.putExtra(Intent.EXTRA_TEXT, "Attached is the CSV file with Empatica sensor data.");
//        // Grant temporary read permission to the content URI
//        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
//
//        Log.i(TAG, "shareCSVFile: Launching share intent");
//        startActivity(Intent.createChooser(intent, "Share CSV File"));
//    }
//
// }