package com.example.oxymeter;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import android.util.Log;

public class MainActivity<LeDeviceListAdapter> extends AppCompatActivity {

    private final static boolean TEMP_BOARD = false;

    private final static String TAG = "BLE";
    private String ble_device_address;
    private String mBluetoothDeviceAddress;

    private Button btn_scan, btn_connect;
    private TextView ble_name, ble_address;
    private TextView battery, manufacturer;
    private TextView data_1, data_2, data_3, data_4;

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothDevice device;
    private BluetoothGatt mBluetoothGatt;


    private int mConnectionState                = STATE_DISCONNECTED;
    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING   = 1;
    private static final int STATE_CONNECTED    = 2;

    public final static String ACTION_GATT_CONNECTED           = "android.kaviles.bletutorial.Service_BTLE_GATT.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED        = "android.kaviles.bletutorial.Service_BTLE_GATT.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED = "android.kaviles.bletutorial.Service_BTLE_GATT.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE           = "android.kaviles.bletutorial.Service_BTLE_GATT.ACTION_DATA_AVAILABLE";
    public final static String EXTRA_UUID                      = "android.kaviles.bletutorial.Service_BTLE_GATT.EXTRA_UUID";
    public final static String EXTRA_DATA                      = "android.kaviles.bletutorial.Service_BTLE_GATT.EXTRA_DATA";
    private Object Utils;

    UUID BATTERY_SERVICE_UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb");
    UUID BATTERY_LEVEL_CHARACTERISTIC_UUID =  UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb");

    UUID BLOOD_OXYGEN_SERVICE_UUID = UUID.fromString("00001234-b38d-4985-720e-0f993a68ee41");
    UUID BLOOD_OXYGEN_CHARACTERISTIC_UUID =  UUID.fromString("00001235-0000-1000-8000-00805f9b34fb");
    UUID R_DATA_CHARACTERISTIC_UUID =  UUID.fromString("00001236-0000-1000-8000-00805f9b34fb");
    UUID IR_DATA_CHARACTERISTIC_UUID =  UUID.fromString("00001237-0000-1000-8000-00805f9b34fb");

    UUID HEART_RATE_SERVICE_UUID = UUID.fromString("00002234-b38d-4985-720e-0f993a68ee41");
    UUID HEART_RATE_CHARACTERISTIC_UUID =  UUID.fromString("00002235-0000-1000-8000-00805f9b34fb");

    UUID BODY_TEMPERATURE_SERVICE_UUID = UUID.fromString("00003234-b38d-4985-720e-0f993a68ee41");
    UUID BODY_TEMPERATURE_CHARACTERISTIC_UUID =  UUID.fromString("00003235-0000-1000-8000-00805f9b34fb");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btn_scan = (Button) (findViewById(R.id.btn_scan));
        btn_connect = (Button) (findViewById(R.id.btn_connect));
        ble_name = (TextView) (findViewById(R.id.ble_name));
        ble_address = (TextView) (findViewById(R.id.ble_address));
        battery = (TextView) (findViewById(R.id.battery));
        data_1 = (TextView) (findViewById(R.id.data_1));
        data_2 = (TextView) (findViewById(R.id.data_2));
        data_3 = (TextView) (findViewById(R.id.data_3));
        data_4 = (TextView) (findViewById(R.id.data_4));

        if (false == TEMP_BOARD)
        {
            data_1.setText("Blood Oxygen:");
            data_2.setText("Heart Rate: ");
            data_3.setText("R data: ");
            data_4.setText("IR data: ");
        }
        else
        {
            data_1.setText("Body Temperature: ");
            data_2.setText("");
            data_3.setText("");
            data_4.setText("");
        }

        btn_scan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                scanLeDevice();
                btn_scan.setText("Scanning...");
                ble_name.setText("Device: ");
                ble_address.setText("Address: ");
            }
        });

        btn_connect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                connect(ble_device_address);
            }
        });

        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }

        // Initializes Bluetooth adapter.
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
    }

    private BluetoothLeScanner bluetoothLeScanner =
            BluetoothAdapter.getDefaultAdapter().getBluetoothLeScanner();
    private boolean mScanning;
    private Handler handler = new Handler();

    // Stops scanning after 10 seconds.
    private static final long SCAN_PERIOD = 10000;

    private void scanLeDevice() {
        if (!mScanning) {
            // Stops scanning after a pre-defined scan period.
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mScanning = false;
                    bluetoothLeScanner.stopScan(leScanCallback);
                }
            }, SCAN_PERIOD);

            mScanning = true;
            bluetoothLeScanner.startScan(leScanCallback);
        } else {
            mScanning = false;
            bluetoothLeScanner.stopScan(leScanCallback);
        }
    }

    // Device scan callback.
    private ScanCallback leScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            device = bluetoothAdapter.getRemoteDevice(result.getDevice().getAddress());
            Log.d(TAG, device.getName() + " " + device.getAddress());
            String device_1 = "Blood Oxygen";
            String device_2 = "Human Body Temperature";
            if (device.getName() != null) {

                // Get name of Blood Oxygen project
                if (device.getName().equals(device_1)) {
                    ble_name.setText("Device: " + device.getName());
                    ble_address.setText("Address: " +  device.getAddress());
                    btn_scan.setText("Scan");
                    ble_device_address = device.getAddress();
                    bluetoothLeScanner.stopScan(leScanCallback);
                }

                // Get name of Temperature project
                if (device.getName().equals(device_2)) {
                    ble_name.setText("Device: " + device.getName());
                    ble_address.setText("Address: " +  device.getAddress());
                    btn_scan.setText("Scan");
                    ble_device_address = device.getAddress();
                    bluetoothLeScanner.stopScan(leScanCallback);
                }
            }
        }
    };

    @Override
    public void onResume() {
        super.onResume();
        checkPermission();
    }

     void checkPermission() {
        // First check to see if I have permissions (marshmallow) if I don't then ask, otherwise start up the demo.
        if ((ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) ||
                (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) )   {
            //I'm on not explaining why, just asking for permission.
            Log.d(TAG, "Asking for permissions");
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION},
                   1);
            Log.d(TAG, "We don't have permission to course location");
        } else {
            Log.d(TAG, "We have permission to course location");
        }
    }

    public boolean connect(final String address) {

        if (bluetoothAdapter == null || address == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }

        // Previously connected device.  Try to reconnect.
        if (mBluetoothDeviceAddress != null && address.equals(mBluetoothDeviceAddress) && mBluetoothGatt != null) {
            Log.d(TAG, "Trying to use an existing mBluetoothGatt for connection.");

            if (mBluetoothGatt.connect()) {
                mConnectionState = STATE_CONNECTING;
                return true;
            }
            else {
                return false;
            }
        }

        final BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);

        if (device == null) {
            Log.w(TAG, "Device not found.  Unable to connect.");
            return false;
        }

        // We want to directly connect to the device, so we are setting the autoConnect
        // parameter to false.
        mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
        Log.d(TAG, "Trying to create a new connection.");
        mBluetoothDeviceAddress = address;
        mConnectionState = STATE_CONNECTING;

        return true;
    }

    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            String intentAction;
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                intentAction = ACTION_GATT_CONNECTED;

                mConnectionState = STATE_CONNECTED;

                broadcastUpdate(intentAction);

                Log.i(TAG, "Connected to GATT server.");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        btn_connect.setText("Connected");
                    }
                });
                // Attempts to discover services after successful connection.
                Log.i(TAG, "Attempting to start service discovery:" + mBluetoothGatt.discoverServices());

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                intentAction = ACTION_GATT_DISCONNECTED;

                mConnectionState = STATE_DISCONNECTED;

                Log.i(TAG, "Disconnected from GATT server.");

                broadcastUpdate(intentAction);
            }
        }

        BluetoothGattCharacteristic battery_characteristic;
        BluetoothGattCharacteristic heart_rate_characteristic;
        BluetoothGattCharacteristic blood_oxygen_characteristic;
        BluetoothGattCharacteristic r_data_characteristic;
        BluetoothGattCharacteristic ir_data_characteristic;
        BluetoothGattCharacteristic body_temperature_characteristic;
        int index = 0;

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {


            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);

                // Get the counter characteristic
                Log.d(TAG, "Services Discovered");

                // Battery Service
                BluetoothGattService battery_service = mBluetoothGatt.getService(BATTERY_SERVICE_UUID);
                if(battery_service == null) {
                    Log.i(TAG, "Battery service not found!");
                    return;
                }
                Log.i(TAG, "Battery service found!");

                battery_characteristic = battery_service.getCharacteristic(BATTERY_LEVEL_CHARACTERISTIC_UUID);
                if(battery_characteristic == null) {
                    Log.i(TAG, "Battery characteristic not found!");
                    return;
                }
                Log.i(TAG, "Battery characteristic found!");
                setCharacteristicNotification(battery_characteristic, true);

                if (false == TEMP_BOARD) {
                    // Blood oxygen Service
                    BluetoothGattService blood_oxygen_service = mBluetoothGatt.getService(BLOOD_OXYGEN_SERVICE_UUID);
                    if (blood_oxygen_service == null) {
                        Log.i(TAG, "Blood oxygen service not found!");
                        return;
                    }
                    Log.i(TAG, "Blood oxygen service found!");

                    // Blood oxygen Charicterictic
                    blood_oxygen_characteristic = blood_oxygen_service.getCharacteristic(BLOOD_OXYGEN_CHARACTERISTIC_UUID);
                    if (blood_oxygen_characteristic == null) {
                        Log.i(TAG, "Blood oxygen characteristic not found!");
                        return;
                    }
                    Log.i(TAG, String.format("Blood oxygen characteristic found: %s", blood_oxygen_characteristic.getUuid().toString()));

                    // r data Charicterictic
                    r_data_characteristic = blood_oxygen_service.getCharacteristic(R_DATA_CHARACTERISTIC_UUID);
                    if (r_data_characteristic == null) {
                        Log.i(TAG, "r data characteristic not found!");
                        return;
                    }
                    Log.i(TAG, String.format("r data characteristic found: %s", blood_oxygen_characteristic.getUuid().toString()));

                    // ir data Charicterictic
                    ir_data_characteristic = blood_oxygen_service.getCharacteristic(IR_DATA_CHARACTERISTIC_UUID);
                    if (ir_data_characteristic == null) {
                        Log.i(TAG, "ir data characteristic not found!");
                        return;
                    }
                    Log.i(TAG, String.format("ir data characteristic found: %s", blood_oxygen_characteristic.getUuid().toString()));

                    // Heart Rate Service
                    BluetoothGattService heart_rate_service = mBluetoothGatt.getService(HEART_RATE_SERVICE_UUID);
                    if (heart_rate_service == null) {
                        Log.i(TAG, "Heart Rate service not found!");
                        return;
                    }
                    Log.i(TAG, "Heart Rate service found!");

                    heart_rate_characteristic = heart_rate_service.getCharacteristic(HEART_RATE_CHARACTERISTIC_UUID);
                    if (heart_rate_characteristic == null) {
                        Log.i(TAG, "Heart Rate characteristic not found!");
                        return;
                    }
                    Log.i(TAG, String.format("Heart Rate characteristic found: %s", heart_rate_characteristic.getUuid().toString()));
                }
                else {
                    // Body temperature Service
                    BluetoothGattService temperature_service = mBluetoothGatt.getService(BODY_TEMPERATURE_SERVICE_UUID);
                    if (temperature_service == null) {
                        Log.i(TAG, "Body temperature service not found!");
                        return;
                    }
                    Log.i(TAG, "Body temperature service found!");

                    body_temperature_characteristic = temperature_service.getCharacteristic(BODY_TEMPERATURE_CHARACTERISTIC_UUID);
                    if (body_temperature_characteristic == null) {
                        Log.i(TAG, "Body temperature characteristic not found!");
                        return;
                    }
                    Log.i(TAG, String.format("Body temperature characteristic found: %s", body_temperature_characteristic.getUuid().toString()));
                    setCharacteristicNotification(body_temperature_characteristic, true);
                }

//                mBluetoothGatt.readCharacteristic(battery_characteristic);
//                mBluetoothGatt.readCharacteristic(heart_rate_characteristic);
//                mBluetoothGatt.readCharacteristic(temperature_characteristic);

            } else {
                Log.w(TAG, "Services Discovered received: " + status);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {

            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            Log.d(TAG, "Receive notify");
            broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            switch(index){
                case 0:
                {
                    if (false == TEMP_BOARD)
                    {
                        setCharacteristicNotification(heart_rate_characteristic, true);
                        index = 1;
                    }
                    else
                    {
                        setCharacteristicNotification(body_temperature_characteristic, true);
                    }
                    break;
                }
                case 1:
                {
                    setCharacteristicNotification(blood_oxygen_characteristic, true);
                    index = 2;
                    break;
                }
                case 2:
                {
                    setCharacteristicNotification(r_data_characteristic, true);
                    index = 3;
                    break;
                }
                case 3:
                {
                    setCharacteristicNotification(ir_data_characteristic, true);
                    index = 4;
                    break;
                }
                default:
                    break;
            }
        }
    };

    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
    }

    private void broadcastUpdate(final String action, final BluetoothGattCharacteristic characteristic) {

        final Intent intent = new Intent(action);
        Log.i(TAG, String.format("Notify UUID: %s", characteristic.getUuid().toString()));

        intent.putExtra(EXTRA_UUID, characteristic.getUuid().toString());

        // For all other profiles, writes the data formatted in HEX.
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (characteristic.getUuid().equals(BATTERY_LEVEL_CHARACTERISTIC_UUID))
                {
                    Log.d(TAG, "BATTERY_LEVEL_CHARACTERISTIC");
                    final int data = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
                    battery.setText("Battery: " + data);
                    Log.d(TAG, String.format("Received value: %d", data));
                }
                else if (characteristic.getUuid().equals(BLOOD_OXYGEN_CHARACTERISTIC_UUID))
                {
                    Log.d(TAG, "BLOOD_OXYGEN_CHARACTERISTIC");
                    final int data = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
                    data_1.setText("Blood Oxygen: " + data);
                }
                else if (characteristic.getUuid().equals(R_DATA_CHARACTERISTIC_UUID))
                {
                    Log.d(TAG, "R_DATA_CHARACTERISTIC");
                    final int data = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT32, 0);
                    data_3.setText("R data: " + data);
                }
                else if (characteristic.getUuid().equals(IR_DATA_CHARACTERISTIC_UUID))
                {
                    Log.d(TAG, "IR_DATA_CHARACTERISTIC");
                    final int data = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT32, 0);
                    data_4.setText("IR data: " + data);
                }
                else if (characteristic.getUuid().equals(HEART_RATE_CHARACTERISTIC_UUID))
                {
                    Log.d(TAG, "HEART_RATE_CHARACTERISTIC");
                    final int data = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
                    data_2.setText("Heart Rate: " + data);
                }
                else if (characteristic.getUuid().equals(BODY_TEMPERATURE_CHARACTERISTIC_UUID))
                {
                    final byte[] value = characteristic.getValue();
                    float data = ByteBuffer.wrap(value).order(ByteOrder.LITTLE_ENDIAN).getFloat();
                    Log.d(TAG, "TEMPERATURE_CHARACTERISTIC");
                    data_1.setText("Body Temperature: " + data);
                    data_2.setText("");
                    Log.d(TAG, String.format("Received value: %f", data));
                }
            }
        });

        sendBroadcast(intent);
    }

    private void displayData(String data) {
        if (data != null) {
            battery.setText("Battery: " + data);
        }
    }

    public void setCharacteristicNotification(BluetoothGattCharacteristic characteristic, boolean enabled) {

        if (bluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }

        mBluetoothGatt.setCharacteristicNotification(characteristic, enabled);

        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(
                UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));

        if (enabled) {
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        }
        else {
            descriptor.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
        }

        mBluetoothGatt.writeDescriptor(descriptor);
    }
}
