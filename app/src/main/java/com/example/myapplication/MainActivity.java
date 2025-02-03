package com.example.myapplication;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.empatica.empalink.EmpaDeviceManager;
import com.empatica.empalink.ConnectionNotAllowedException;
import com.empatica.empalink.EmpaticaDevice;
import com.empatica.empalink.config.EmpaSensorStatus;
import com.empatica.empalink.config.EmpaSensorType;
import com.empatica.empalink.config.EmpaStatus;
import com.empatica.empalink.delegate.EmpaDataDelegate;
import com.empatica.empalink.delegate.EmpaStatusDelegate;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements EmpaDataDelegate, EmpaStatusDelegate {

    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_PERMISSION_ACCESS_COARSE_LOCATION = 1;
    private static final int REQUEST_BLUETOOTH_PERMISSION = 2;
    private static final String EMPATICA_API_KEY = ""; // TODO: Insert your API Key here

    private EmpaDeviceManager deviceManager = null;

    // UI components
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
    private TextView wristStatusLabel;
    private LinearLayout dataCnt;
    private Button downloadButton;

    // List to store sensor data as CSV rows
    private List<String> sensorDataList = new ArrayList<>();

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize UI components
        statusLabel = findViewById(R.id.status);
        dataCnt = findViewById(R.id.dataArea);
        accel_xLabel = findViewById(R.id.accel_x);
        accel_yLabel = findViewById(R.id.accel_y);
        accel_zLabel = findViewById(R.id.accel_z);
        bvpLabel = findViewById(R.id.bvp);
        edaLabel = findViewById(R.id.eda);
        ibiLabel = findViewById(R.id.ibi);
        temperatureLabel = findViewById(R.id.temperature);
        batteryLabel = findViewById(R.id.battery);
        deviceNameLabel = findViewById(R.id.deviceName);
        wristStatusLabel = findViewById(R.id.wrist_status_label);

        // Disconnect button to disconnect from the device
        Button disconnectButton = findViewById(R.id.disconnectButton);
        disconnectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (deviceManager != null) {
                    deviceManager.disconnect();
                }
            }
        });

        // Download button to export CSV file
        downloadButton = findViewById(R.id.downloadButton);
        downloadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                exportCSV(view);
            }
        });

        // Initialize the Empatica device manager.
        initEmpaticaDeviceManager();
    }

    /**
     * onRequestPermissionsResult handles the results for both the location and Bluetooth permission requests.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        switch (requestCode) {
            case REQUEST_PERMISSION_ACCESS_COARSE_LOCATION:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Location permission granted, proceed with initializing the device manager.
                    initEmpaticaDeviceManager();
                } else {
                    // Location permission denied.
                    showPermissionDialog(Manifest.permission.ACCESS_COARSE_LOCATION, REQUEST_PERMISSION_ACCESS_COARSE_LOCATION);
                }
                break;

            case REQUEST_BLUETOOTH_PERMISSION:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Bluetooth permission granted, now prompt the user to enable Bluetooth.
                    didRequestEnableBluetooth();
                } else {
                    // Bluetooth permission denied.
                    showPermissionDialog(Manifest.permission.BLUETOOTH_CONNECT, REQUEST_BLUETOOTH_PERMISSION);
                }
                break;
        }
    }

    /**
     * Helper method to show an alert dialog when a permission is denied.
     */
    private void showPermissionDialog(String permission, int requestCode) {
        final boolean needRationale = ActivityCompat.shouldShowRequestPermissionRationale(this, permission);

        new AlertDialog.Builder(this)
                .setTitle("Permission required")
                .setMessage("This permission is necessary for the app to function properly. Please allow it.")
                .setPositiveButton("Retry", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        if (needRationale) {
                            ActivityCompat.requestPermissions(MainActivity.this, new String[]{permission}, requestCode);
                        } else {
                            // If "Never Ask Again" is selected, direct the user to the app settings.
                            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                            Uri uri = Uri.fromParts("package", getPackageName(), null);
                            intent.setData(uri);
                            startActivity(intent);
                        }
                    }
                })
                .setNegativeButton("Exit application", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        finish();
                    }
                })
                .show();
    }

    /**
     * Initialize the Empatica device manager.
     * This method first checks for the ACCESS_COARSE_LOCATION permission (required for BLE scanning).
     * It also checks for the Bluetooth permission on Android 12+.
     */
    private void initEmpaticaDeviceManager() {
        // Check location permission first.
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                    REQUEST_PERMISSION_ACCESS_COARSE_LOCATION);
            return;
        }

        // For Android 12 (API level 31) and above, check Bluetooth permission.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.BLUETOOTH_CONNECT},
                        REQUEST_BLUETOOTH_PERMISSION);
                return;
            }
        }

        // Check if API key is provided.
        if (TextUtils.isEmpty(EMPATICA_API_KEY)) {
            new AlertDialog.Builder(this)
                    .setTitle("Warning")
                    .setMessage("Please insert your API KEY")
                    .setNegativeButton("Close", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            finish();
                        }
                    })
                    .show();
            return;
        }

        // Create a new EmpaDeviceManager. MainActivity is both its data and status delegate.
        deviceManager = new EmpaDeviceManager(getApplicationContext(), this, this);

        // Initialize the Device Manager using your API key.
        // (Make sure you have Internet access at this point.)
        deviceManager.authenticateWithAPIKey(EMPATICA_API_KEY);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (deviceManager != null) {
            deviceManager.stopScanning();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (deviceManager != null) {
            deviceManager.cleanUp();
        }
    }

    @Override
    public void didDiscoverDevice(EmpaticaDevice bluetoothDevice, String deviceName, int rssi, boolean allowed) {
        if (allowed) {
            // Stop scanning once a permitted device is found.
            deviceManager.stopScanning();
            try {
                // Connect to the device.
                deviceManager.connectDevice(bluetoothDevice);
                updateLabel(deviceNameLabel, "To: " + deviceName);
            } catch (ConnectionNotAllowedException e) {
                Toast.makeText(MainActivity.this, "Sorry, you can't connect to this device", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void didFailedScanning(int errorCode) {
        // Handle scanning failure if needed.
    }

    /**
     * This method is called by the Empatica SDK when it needs the user to enable Bluetooth.
     * It launches an intent to prompt the user.
     */
    @Override
    public void didRequestEnableBluetooth() {
        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
    }

    @Override
    public void bluetoothStateChanged() {
        // Handle any Bluetooth state changes if needed.
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
            // The user chose not to enable Bluetooth.
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
        updateLabel(statusLabel, status.name());

        if (status == EmpaStatus.READY) {
            updateLabel(statusLabel, status.name() + " - Turn on your device");
            deviceManager.startScanning();
            hide();
        } else if (status == EmpaStatus.CONNECTED) {
            show();
        } else if (status == EmpaStatus.DISCONNECTED) {
            updateLabel(deviceNameLabel, "");
            hide();
            // Save CSV data automatically upon disconnection (optional)
            saveDataToCSV();
        }
    }

    @Override
    public void didReceiveAcceleration(int x, int y, int z, double timestamp) {
        // Create a CSV row for acceleration data and store it
        String data = timestamp + ",ACCEL," + x + "," + y + "," + z;
        sensorDataList.add(data);
        updateLabel(accel_xLabel, "" + x);
        updateLabel(accel_yLabel, "" + y);
        updateLabel(accel_zLabel, "" + z);
    }

    @Override
    public void didReceiveBVP(float bvp, double timestamp) {
        String data = timestamp + ",BVP," + bvp;
        sensorDataList.add(data);
        updateLabel(bvpLabel, "" + bvp);
    }

    @Override
    public void didReceiveBatteryLevel(float battery, double timestamp) {
        String batteryText = String.format("%.0f %%", battery * 100);
        updateLabel(batteryLabel, batteryText);
    }

    @Override
    public void didReceiveGSR(float gsr, double timestamp) {
        String data = timestamp + ",EDA," + gsr;
        sensorDataList.add(data);
        updateLabel(edaLabel, "" + gsr);
    }

    @Override
    public void didReceiveIBI(float ibi, double timestamp) {
        String data = timestamp + ",IBI," + ibi;
        sensorDataList.add(data);
        updateLabel(ibiLabel, "" + ibi);
    }

    @Override
    public void didReceiveTemperature(float temp, double timestamp) {
        String data = timestamp + ",TEMP," + temp;
        sensorDataList.add(data);
        updateLabel(temperatureLabel, "" + temp);
    }

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
        // Handle tag reception if needed.
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
                    wristStatusLabel.setText("ON WRIST");
                } else {
                    wristStatusLabel.setText("NOT ON WRIST");
                }
            }
        });
    }

    private void show() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                dataCnt.setVisibility(View.VISIBLE);
            }
        });
    }

    private void hide() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                dataCnt.setVisibility(View.INVISIBLE);
            }
        });
    }

    /**
     * Called when the download button is clicked.
     * This method saves the data to a CSV file and then triggers a share intent so that the user can export the file.
     */
    public void exportCSV(View view) {
        File file = saveDataToCSV();  // Save the data to a CSV file first
        if (file != null) {
            shareCSVFile(file);  // Trigger sharing (or "downloading") of the CSV file
        }
    }

    /**
     * Saves the collected sensor data to a CSV file.
     * The file is stored in the app-specific external files directory.
     *
     * @return The CSV file if saved successfully, or null if there was an error.
     */
    private File saveDataToCSV() {
        File path = getExternalFilesDir(null);  // This returns the app-specific directory on external storage
        File file = new File(path, "EmpaticaData.csv");

        try (FileWriter writer = new FileWriter(file)) {
            // Write the header row
            writer.append("Timestamp,Type,Value1,Value2,Value3\n");

            // Write each row of sensor data
            for (String data : sensorDataList) {
                writer.append(data).append("\n");
            }
            writer.flush();
            Toast.makeText(this, "Data saved to: " + file.getAbsolutePath(), Toast.LENGTH_LONG).show();
            return file;
        } catch (IOException e) {
            Toast.makeText(this, "Error saving file ", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Shares the CSV file using Android's share intent.
     * This allows the user to download, email, or otherwise export the CSV file.
     *
     * @param file The CSV file to share.
     */
    private void shareCSVFile(File file) {
        // Use FileProvider to get a content URI
        Uri fileUri = FileProvider.getUriForFile(this,
                getApplicationContext().getPackageName() + ".provider", file);

        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/csv");
        intent.putExtra(Intent.EXTRA_STREAM, fileUri);
        intent.putExtra(Intent.EXTRA_SUBJECT, "Empatica Sensor Data");
        intent.putExtra(Intent.EXTRA_TEXT, "Attached is the CSV file with Empatica sensor data.");

        // Grant temporary read permission to the content URI
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        startActivity(Intent.createChooser(intent, "Share CSV File"));
    }
}
