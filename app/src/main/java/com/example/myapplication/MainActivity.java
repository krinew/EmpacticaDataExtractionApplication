package com.example.myapplication;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.ScanCallback;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.empatica.empalink.ConnectionNotAllowedException;
import com.empatica.empalink.EmpaDeviceManager;
import com.empatica.empalink.EmpaticaDevice;
import com.empatica.empalink.config.EmpaSensorStatus;
import com.empatica.empalink.config.EmpaSensorType;
import com.empatica.empalink.config.EmpaStatus;
import com.empatica.empalink.delegate.EmpaDataDelegate;
import com.empatica.empalink.delegate.EmpaStatusDelegate;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements EmpaDataDelegate, EmpaStatusDelegate {

    private static final String TAG = "MainActivity";

    private List<SensorData> sensorDataList;
    private static final int REQUEST_WRITE_STORAGE = 2;

    private static final int REQUEST_ENABLE_BT = 1;

    private static final int REQUEST_PERMISSION_ACCESS_COARSE_LOCATION = 1;

    private static final String EMPATICA_API_KEY = "2fe2f405268349efaf63509c3dec89f5"; // TODO insert your API Key here
    private static final String EMPATICA_API_KEY_2 = "";

    private EmpaDeviceManager deviceManager = null;

    private TextView accel_xLabel;

    private TextView accel_yLabel;

    private TextView accel_zLabel;

    private TextView bvpLabel;

    private TextView edaLabel;

    private TextView ibiLabel;

    private TextView temperatureLabel;

    private TextView batteryLabel;

    private TextView statusLabel;

    private TextView deviceNameLabel;

    private LinearLayout dataCnt;

    private static class SensorData {
        double timestamp;
        float bvp;
        float gsr;
        float temperature;
        float ibi;
        int accel_x;
        int accel_y;
        int accel_z;
        SensorData(double timestamp) {
            this.timestamp = timestamp;
        }
    }

    private SensorData currentReading = null;
    private static final long READING_TIMEOUT = 1000; // 1 second in milliseconds

    private void addSensorReading(double timestamp, Runnable dataUpdater) {
        long currentTime = System.currentTimeMillis();

        // If we don't have a current reading or if too much time has passed, create a new one
        if (currentReading == null ||
                (currentTime - (currentReading.timestamp * 1000) > READING_TIMEOUT)) {
            if (currentReading != null) {
                sensorDataList.add(currentReading);
            }
            currentReading = new SensorData(timestamp);
        }

        // Update the current reading with new data
        dataUpdater.run();
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        // Initialize vars that reference UI components
        statusLabel = (TextView) findViewById(R.id.status);

        sensorDataList = new ArrayList<>();

        dataCnt = (LinearLayout) findViewById(R.id.dataArea);

        accel_xLabel = (TextView) findViewById(R.id.accel_x);

        accel_yLabel = (TextView) findViewById(R.id.accel_y);

        accel_zLabel = (TextView) findViewById(R.id.accel_z);

        bvpLabel = (TextView) findViewById(R.id.bvp);

        edaLabel = (TextView) findViewById(R.id.eda);

        ibiLabel = (TextView) findViewById(R.id.ibi);

        temperatureLabel = (TextView) findViewById(R.id.temperature);

        batteryLabel = (TextView) findViewById(R.id.battery);

        deviceNameLabel = (TextView) findViewById(R.id.deviceName);


        final Button disconnectButton = findViewById(R.id.disconnectButton);


        disconnectButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {

                if (deviceManager != null) {

                    deviceManager.disconnect();
                }
            }
        });

        Button downloadButton = findViewById(R.id.downloadButton);
        downloadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkStoragePermissionAndExport();
            }
        });



        initEmpaticaDeviceManager();
        Intent intent = new Intent(this,EmpaticaService.class);
        ContextCompat.startForegroundService(this,intent);

    }

    private void checkStoragePermissionAndExport() {
        Log.d(TAG, "Checking storage permissions for CSV export");

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            // Android 11+ (API 30+): Use the new storage access framework
            if (Environment.isExternalStorageManager()) {
                // Permission granted
                exportToCSV();
            } else {
                // Request permission
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                Uri uri = Uri.fromParts("package", getPackageName(), null);
                intent.setData(uri);
                try {
                    startActivity(intent);
                    Toast.makeText(this, "Please grant all files access permission", Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    Log.e(TAG, "Error opening permission settings", e);
                    // Fallback to regular permission request if settings intent fails
                    Toast.makeText(this, "Unable to open permission settings", Toast.LENGTH_LONG).show();
                }
            }
        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            // Android 10 (API 29): Use scoped storage with app-specific directories
            exportToCSV(); // Use getExternalFilesDir() in the exportToCSV method
        } else {
            // Android 9 (API 28) and below: Use the old permission model
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {

                if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE)) {

                    new AlertDialog.Builder(this)
                            .setTitle("Storage Permission Required")
                            .setMessage("This app needs storage access to save your Empatica sensor data as CSV files.")
                            .setPositiveButton("Grant Permission", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    ActivityCompat.requestPermissions(MainActivity.this,
                                            new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                                            REQUEST_WRITE_STORAGE);
                                }
                            })
                            .setNegativeButton("Cancel", null)
                            .create()
                            .show();
                } else {
                    ActivityCompat.requestPermissions(this,
                            new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                            REQUEST_WRITE_STORAGE);
                }
            } else {
                exportToCSV();
            }
        }
    }

    private void exportToCSV() {
        Log.d(TAG, "Starting exportToCSV process");

        // Add the current reading to the list if it exists
        if (currentReading != null) {
            Log.d(TAG, "Adding current reading to sensorDataList before export");
            sensorDataList.add(currentReading);
            currentReading = null;
        }

        // Check if we have data to export
        if (sensorDataList.isEmpty()) {
            Log.w(TAG, "No sensor data available to export");
            runOnUiThread(() -> Toast.makeText(MainActivity.this,
                    "No data available to export", Toast.LENGTH_SHORT).show());
            return;
        }

        Log.i(TAG, "Preparing to export " + sensorDataList.size() + " data points");

        // Get the appropriate directory based on Android version
        File baseDir;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            // For Android 10+ use app-specific directory
            baseDir = new File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "EmpaticaData");
        } else {
            // For older versions use public directory
            baseDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "EmpaticaData");
        }

        if (!baseDir.exists()) {
            if (!baseDir.mkdirs()) {
                Log.e(TAG, "Failed to create directory: " + baseDir.getAbsolutePath());
                runOnUiThread(() -> Toast.makeText(MainActivity.this,
                        "Error: Could not create download directory", Toast.LENGTH_SHORT).show());
                return;
            } else {
                Log.d(TAG, "Created directory: " + baseDir.getAbsolutePath());
            }
        }

        // Create a unique filename with current timestamp
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String fileName = "empatica_data_" + timeStamp + ".csv";
        File file = new File(baseDir, fileName);
        String filePath = file.getAbsolutePath();

        Log.i(TAG, "Writing CSV file to: " + filePath);

        try {
            FileWriter fw = new FileWriter(file);

            // Write header
            fw.append("Timestamp,BVP,GSR,Temperature,IBI,Accel_X,Accel_Y,Accel_Z,Human_Readable_Time\n");
            Log.d(TAG, "CSV header written");

            int successfulWrites = 0;

            // Write data rows with human-readable timestamp
            for (SensorData data : sensorDataList) {
                // Convert timestamp to human-readable date format
                String humanReadableTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
                        .format(new Date((long)(data.timestamp * 1000)));

                fw.append(String.format(Locale.US, "%f,%f,%f,%f,%f,%d,%d,%d,%s\n",
                        data.timestamp, data.bvp, data.gsr, data.temperature, data.ibi,
                        data.accel_x, data.accel_y, data.accel_z, humanReadableTime));
                successfulWrites++;
            }

            fw.flush();
            fw.close();

            Log.i(TAG, "Successfully wrote " + successfulWrites + " data points to CSV file");

            final int finalSuccessfulWrites = successfulWrites;
            runOnUiThread(() -> {
                Toast.makeText(MainActivity.this,
                        "CSV file saved: " + fileName + " (" + finalSuccessfulWrites + " records)",
                        Toast.LENGTH_LONG).show();
            });

        } catch (IOException e) {
            Log.e(TAG, "Error writing CSV file: " + e.getMessage(), e);
            runOnUiThread(() -> Toast.makeText(MainActivity.this,
                    "Error saving CSV file: " + e.getMessage(), Toast.LENGTH_SHORT).show());
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_WRITE_STORAGE) {
            Log.d(TAG, "Received storage permission result");

            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.i(TAG, "Storage permission granted, proceeding with export");
                exportToCSV();
            } else {
                Log.w(TAG, "Storage permission denied by user");

                boolean showRationale = ActivityCompat.shouldShowRequestPermissionRationale(this,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE);

                if (!showRationale) {
                    // User selected "Don't ask again" option
                    Log.w(TAG, "User selected 'Don't ask again' for storage permission");

                    new AlertDialog.Builder(this)
                            .setTitle("Permission Required")
                            .setMessage("Storage permission is required to export data. Please enable it in app settings.")
                            .setPositiveButton("Open Settings", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    // Open app settings
                                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                    Uri uri = Uri.fromParts("package", getPackageName(), null);
                                    intent.setData(uri);
                                    startActivity(intent);
                                }
                            })
                            .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    Toast.makeText(MainActivity.this,
                                            "Cannot export data without storage permission",
                                            Toast.LENGTH_LONG).show();
                                }
                            })
                            .setCancelable(false)
                            .create()
                            .show();
                } else {
                    // User just denied permission this time
                    Toast.makeText(this,
                            "Storage permission is required to export data",
                            Toast.LENGTH_LONG).show();
                }
            }
        } else if (requestCode == REQUEST_PERMISSION_ACCESS_COARSE_LOCATION) {
            // Keep existing location permission handling
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission was granted, yay!
                initEmpaticaDeviceManager();
            } else {
                // Permission denied, boo!
                final boolean needRationale = ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_COARSE_LOCATION);
                new AlertDialog.Builder(this)
                        .setTitle("Permission required")
                        .setMessage("Without this permission bluetooth low energy devices cannot be found, allow it in order to connect to the device.")
                        .setPositiveButton("Retry", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                // try again
                                if (needRationale) {
                                    // the "never ask again" flash is not set, try again with permission request
                                    initEmpaticaDeviceManager();
                                } else {
                                    // the "never ask again" flag is set so the permission requests is disabled, try open app settings to enable the permission
                                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                    Uri uri = Uri.fromParts("package", getPackageName(), null);
                                    intent.setData(uri);
                                    startActivity(intent);
                                }
                            }
                        })
                        .setNegativeButton("Exit application", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                // without permission exit is the only way
                                finish();
                            }
                        })
                        .show();
            }
        }
    }

    private void initEmpaticaDeviceManager() {
        // Android 6 (API level 23) now require ACCESS_COARSE_LOCATION permission to use BLE
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[] { Manifest.permission.ACCESS_FINE_LOCATION }, REQUEST_PERMISSION_ACCESS_COARSE_LOCATION);
        } else {

            if (TextUtils.isEmpty(EMPATICA_API_KEY)) {
                new AlertDialog.Builder(this)
                        .setTitle("Warning")
                        .setMessage("Please insert your API KEY")
                        .setNegativeButton("Close", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                // without permission exit is the only way
                                finish();
                            }
                        })
                        .show();
                return;
            }

            // Create a new EmpaDeviceManager. MainActivity is both its data and status delegate.
            deviceManager = new EmpaDeviceManager(getApplicationContext(), this, this);

            // Initialize the Device Manager using your API key. You need to have Internet access at this point.
            deviceManager.authenticateWithAPIKey(EMPATICA_API_KEY);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (deviceManager != null) {
            deviceManager.cleanUp();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (deviceManager != null) {
            deviceManager.stopScanning();
        }
    }

    @Override
    public void didDiscoverDevice(EmpaticaDevice bluetoothDevice, String deviceName, int rssi, boolean allowed) {
        // Check if the discovered device can be used with your API key. If allowed is always false,
        // the device is not linked with your API key. Please check your developer area at
        // https://www.empatica.com/connect/developer.php

        Log.i(TAG, "didDiscoverDevice" + deviceName + "allowed: " + allowed);

        if (allowed) {
            // Stop scanning. The first allowed device will do.
            deviceManager.stopScanning();
            try {
                // Connect to the device
                deviceManager.connectDevice(bluetoothDevice);
                updateLabel(deviceNameLabel, "To: " + deviceName);
            } catch (ConnectionNotAllowedException e) {
                // This should happen only if you try to connect when allowed == false.
                Toast.makeText(MainActivity.this, "Sorry, you can't connect to this device", Toast.LENGTH_SHORT).show();
                Log.e(TAG, "didDiscoverDevice" + deviceName + "allowed: " + allowed + " - ConnectionNotAllowedException", e);
            }
        }
    }

    @Override
    public void didFailedScanning(int errorCode) {

        /*
         A system error occurred while scanning.
         @see https://developer.android.com/reference/android/bluetooth/le/ScanCallback
        */
        switch (errorCode) {
            case ScanCallback.SCAN_FAILED_ALREADY_STARTED:
                Log.e(TAG,"Scan failed: a BLE scan with the same settings is already started by the app");
                break;
            case ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED:
                Log.e(TAG,"Scan failed: app cannot be registered");
                break;
            case ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED:
                Log.e(TAG,"Scan failed: power optimized scan feature is not supported");
                break;
            case ScanCallback.SCAN_FAILED_INTERNAL_ERROR:
                Log.e(TAG,"Scan failed: internal error");
                break;
            default:
                Log.e(TAG,"Scan failed with unknown error (errorCode=" + errorCode + ")");
                break;
        }
    }

        @Override
    public void didRequestEnableBluetooth() {
        Log.d(TAG, "didRequestEnableBluetooth: Requesting user to enable Bluetooth");
        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        enableBtLauncher.launch(enableBtIntent);
    }

    private final ActivityResultLauncher<Intent> enableBtLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                Log.d(TAG, "enableBtLauncher: Received result from Bluetooth enable request");
                if (result.getResultCode() == Activity.RESULT_OK) {
                    Log.i(TAG, "enableBtLauncher: Bluetooth enabled");
                    // Don't start scanning here - let bluetoothStateChanged handle it
                } else {
                    Log.w(TAG, "enableBtLauncher: Bluetooth not enabled by the user");
                    Toast.makeText(this, "Bluetooth must be enabled to proceed", Toast.LENGTH_SHORT).show();
                }
            });



    @Override
    public void bluetoothStateChanged() {
        // E4link detected a bluetooth adapter change
        // Check bluetooth adapter and update your UI accordingly.
        boolean isBluetoothOn = BluetoothAdapter.getDefaultAdapter().isEnabled();
        Log.i(TAG, "Bluetooth State Changed: " + isBluetoothOn);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // The user chose not to enable Bluetooth
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
            // You should deal with this

            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void didUpdateSensorStatus(@EmpaSensorStatus int status, EmpaSensorType type) {

        didUpdateOnWristStatus(status);
    }

    @Override
    public void didUpdateStatus(EmpaStatus status) {
        // Update the UI
        updateLabel(statusLabel, status.name());

        // The device manager is ready for use
        if (status == EmpaStatus.READY) {
            updateLabel(statusLabel, status.name() + " - Turn on your device");
            // Start scanning
            deviceManager.startScanning();
            // The device manager has established a connection

            hide();

        } else if (status == EmpaStatus.CONNECTED) {

            show();
            // The device manager disconnected from a device
        } else if (status == EmpaStatus.DISCONNECTED) {

            updateLabel(deviceNameLabel, "");

            hide();
        }
    }

    @Override
    public void didReceiveAcceleration(int x, int y, int z, double timestamp) {
        updateLabel(accel_xLabel, "" + x);
        updateLabel(accel_yLabel, "" + y);
        updateLabel(accel_zLabel, "" + z);

        addSensorReading(timestamp, () -> {
            currentReading.accel_x = x;
            currentReading.accel_y = y;
            currentReading.accel_z = z;
        });
    }

    @Override
    public void didReceiveBVP(float bvp, double timestamp) {
        updateLabel(bvpLabel, "" + bvp);

        addSensorReading(timestamp, () -> {
            currentReading.bvp = bvp;
        });
    }

    @Override
    public void didReceiveBatteryLevel(float battery, double timestamp) {
        updateLabel(batteryLabel, String.format("%.0f %%", battery * 100));
    }

    @Override
    public void didReceiveGSR(float g, double timestamp) {
        updateLabel(edaLabel, "" + g);
        addSensorReading(timestamp, () -> {
            currentReading.gsr = g;
        });
    }

    @Override
    public void didReceiveIBI(float ibi, double timestamp) {
        updateLabel(ibiLabel, "" + ibi);
        addSensorReading(timestamp, () -> {
            currentReading.ibi = ibi;
        });
    }

    @Override
    public void didReceiveTemperature(float temp, double timestamp) {
        updateLabel(temperatureLabel, "" + temp);
        addSensorReading(timestamp, () -> {
            currentReading.temperature = temp;
        });
    }

    // Update a label with some text, making sure this is run in the UI thread
    private void updateLabel(final TextView label, final String text) {

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                label.setText(text);
            }
        });
    }

    @Override
    public void didReceiveTag(double timestamp) {

    }

    @Override
    public void didEstablishConnection() {

        show();
    }

    @Override
    public void didUpdateOnWristStatus(@EmpaSensorStatus final int status) {

        runOnUiThread(new Runnable() {
            @Override
            public void run() {

                if (status == EmpaSensorStatus.ON_WRIST) {

                    ((TextView) findViewById(R.id.wrist_status_label)).setText("ON WRIST");
                }
                else {

                    ((TextView) findViewById(R.id.wrist_status_label)).setText("NOT ON WRIST");
                }
            }
        });
    }
    void show() {

        runOnUiThread(new Runnable() {

            @Override
            public void run() {

                dataCnt.setVisibility(View.VISIBLE);
            }
        });
    }

    void hide() {

        runOnUiThread(new Runnable() {

            @Override
            public void run() {
                dataCnt.setVisibility(View.INVISIBLE);
            }
        });
    }
}
