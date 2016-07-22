package com.example.kazuki.blesetting;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

public class BLECentralActivity extends Activity {
    private final static int SDKVER_MARSHMALLOW = 23;
    public class SendDataTimer extends TimerTask {
        @Override
        public void run() {
            if (isBleEnabled) {;
                writeCharacteristic();
            }
        }
    }

    private BluetoothManager bleManager;
    private BluetoothAdapter bleAdapter;
    private boolean isBleEnabled = false;
    private BluetoothLeScanner bleScanner;
    private BluetoothGatt bleGatt;
    private BluetoothGattCharacteristic bleCharacteristic;

    private Timer timer;
    private SendDataTimer sendDataTimer;

    public void onGpsIsEnabled(){
        // 2016.03.07現在GPSを要求するのが6.0以降のみなのでOnになったら新しいAPIでScan開始.
        this.startScanByBleScanner();
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_blecentral);

        isBleEnabled = false;

        // Bluetoothの使用準備.
        bleManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bleAdapter = bleManager.getAdapter();

        // Writeリクエスト用のタイマーをセット.
        timer = new Timer();
        sendDataTimer = new SendDataTimer();
        // 第二引数:最初の処理までのミリ秒 第三引数:以降の処理実行の間隔(ミリ秒).
        timer.schedule(sendDataTimer, 500, 1000);

        Button sendButton = (Button) findViewById(R.id.send_button);
        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                scanNewDevice();
            }
        });
        // BluetoothがOffならインテントを表示する.
        if ((bleAdapter == null)
                || (! bleAdapter.isEnabled())) {
            // Intentでボタンを押すとonActivityResultが実行されるので、第二引数の番号を元に処理を行う.
            startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), R.string.request_ble_on);
        }
        else{
            // Android6.0以降なら権限確認.
            if(Build.VERSION.SDK_INT >= SDKVER_MARSHMALLOW)
            {
                this.requestBlePermission();
            }else {

            }
        }
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Intentでユーザーがボタンを押したら実行.
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case R.string.request_ble_on:
                if ((bleAdapter != null)
                        || (bleAdapter.isEnabled())) {
                }
                break;
            case R.string.request_enable_location:
                if(resultCode == RESULT_OK){
                    onGpsIsEnabled();
                }
                break;
        }
    }
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback(){
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState){
            // 接続状況が変化したら実行.
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                // 接続に成功したらサービスを検索する.
                gatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                // 接続が切れたらGATTを空にする.
                if (bleGatt != null){
                    bleGatt.close();
                    bleGatt = null;
                }
                isBleEnabled = false;
            }
        }
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status){
            // Serviceが見つかったら実行.
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // UUIDが同じかどうかを確認する.
                BluetoothGattService bleService = gatt.getService(UUID.fromString(getString(R.string.uuid_service)));
                if (bleService != null){
                    // 指定したUUIDを持つCharacteristicを確認する.
                    bleCharacteristic = bleService.getCharacteristic(UUID.fromString(getString(R.string.uuid_characteristic)));
                    if (bleCharacteristic != null) {
                        // Service, CharacteristicのUUIDが同じならBluetoothGattを更新する.
                        bleGatt = gatt;
                        // キャラクタリスティックが見つかったら、Notificationをリクエスト.
                        bleGatt.setCharacteristicNotification(bleCharacteristic, true);
                        isBleEnabled = true;
                    }
                }
            }
        }
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic){

        }
    };
    private void scanNewDevice(){
        // OS ver.6.0以上ならGPSがOnになっているかを確認する(GPSがOffだとScanに失敗するため).
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
            startScanByBleScanner();
        }
        // OS ver.5.0以上ならBluetoothLeScannerを使用する.
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP){
            this.startScanByBleScanner();
        }
        else {
        }
    }
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void startScanByBleScanner(){
        bleScanner = bleAdapter.getBluetoothLeScanner();

        // デバイスの検出.
        bleScanner.startScan(new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                super.onScanResult(callbackType, result);
                result.getDevice().connectGatt(getApplicationContext(), false, mGattCallback);
            }

            @Override
            public void onScanFailed(int intErrorCode) {
                super.onScanFailed(intErrorCode);
            }
        });
    }
    @TargetApi(SDKVER_MARSHMALLOW)
    private void requestBlePermission(){
        // 権限が許可されていない場合はリクエスト.
        if(checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION
            },R.string.request_enable_location);
        }
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        // 権限リクエストの結果を取得する.
        if (requestCode == R.string.request_enable_location) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {

            } else {
                Toast.makeText(BLECentralActivity.this, "Failed", Toast.LENGTH_SHORT).show();
            }
        }else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }
    public void writeCharacteristic() {
        BluetoothGattCharacteristic write = getCharacteristic(
                getString(R.string.uuid_service),
                getString(R.string.uuid_characteristic));
        String message = ((EditText)findViewById(R.id.input_area)).getText().toString();
        write.setValue(message);
        bleGatt.writeCharacteristic(write);
    }
    
    public BluetoothGattCharacteristic getCharacteristic(String sid, String cid) {
        BluetoothGattService s = bleGatt.getService(UUID.fromString(sid));
        if (s == null) {
            return null;
            }
        BluetoothGattCharacteristic c = s.getCharacteristic(UUID.fromString(cid));
        if (c == null) {
            return null;
            }
        return c;
    }

}
