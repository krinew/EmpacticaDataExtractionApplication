# E4link Extended Data Collection

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

## Introduction

This project extends the Empatica E4 functionality beyond its sunset period with enhanced features for researchers and healthcare professionals. Unlike the basic sample, this application provides comprehensive data collection and export capabilities across multiple Android versions.

## Features

- **Multi-sensor data collection** - BVP, GSR/EDA, temperature, acceleration, IBI
- **Real-time visualization** of all sensor readings
- **CSV export** with human-readable timestamps
- **Cross-version compatibility** (Android 9-11+)
- **Background data collection** via foreground service
- **Adaptive storage handling** for different Android versions
- **On-wrist detection** status monitoring

## Setup

1. Clone this repository
   ```bash
   git clone https://github.com/yourusername/e4link-extended.git
   ```

2. Open the project in Android Studio

3. Obtain a valid Empatica API key from your Connect account

4. Insert your API key in `MainActivity.java`
   ```java
   private static final String EMPATICA_API_KEY = "YOUR_API_KEY_HERE";
   ```

5. Download the Empatica Android SDK (1.0 or newer) from the Developer Area

6. Copy the `.aar` file from the SDK into the project's `libs` folder

7. Build and run the project on your Android device

## Storage Permission Handling

The app implements different storage access methods based on Android version:

- **Android 11+**: Uses Storage Access Framework (requires All Files Access permission)
- **Android 10**: Uses scoped storage with app-specific directories
- **Android 9 and below**: Uses legacy storage permissions

## Troubleshooting

- If the device is visible (blinking green) but won't connect, verify the device is linked to your API key
- Check that Bluetooth is enabled and location permissions are granted
- For CSV export issues, verify proper storage permissions are granted
- If data collection stops unexpectedly, check battery levels and wrist placement

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Acknowledgments

- Empatica for the E4 device and original SDK
- Contributors to the Android BLE libraries

For additional information about the Empatica API implementation, refer to the [official documentation.](https://www.empatica.com/research/e4-sunset/) please attach the empatica sdk in the libs folder for complete working of the applciation
