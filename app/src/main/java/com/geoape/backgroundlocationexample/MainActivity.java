package com.geoape.backgroundlocationexample;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.IBinder;
import android.provider.Settings;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import com.karumi.dexter.Dexter;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.PermissionDeniedResponse;
import com.karumi.dexter.listener.PermissionGrantedResponse;
import com.karumi.dexter.listener.PermissionRequest;
import com.karumi.dexter.listener.single.PermissionListener;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class MainActivity extends AppCompatActivity {

    @BindView(R.id.btn_start_tracking)
    Button btnStartTracking;

    @BindView(R.id.btn_stop_tracking)
    Button btnStopTracking;

    @BindView(R.id.txt_status)
    TextView txtStatus;

    public BackgroundService gpsService;
    public boolean mTracking = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        final Intent intent = new Intent(this.getApplication(), BackgroundService.class);
        this.getApplication().startService(intent);
//        this.getApplication().startForegroundService(intent);
        this.getApplication().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);

    }

    @OnClick(R.id.btn_start_tracking)
    public void startLocationButtonClick() {
        Dexter.withActivity(this)
                .withPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                .withListener(new PermissionListener() {
                    @Override
                    public void onPermissionGranted(PermissionGrantedResponse response) {
                        gpsService.startTracking();
                        mTracking = true;
                        toggleButtons();
                    }

                    @Override
                    public void onPermissionDenied(PermissionDeniedResponse response) {
                        if (response.isPermanentlyDenied()) {
                            openSettings();
                        }
                    }

                    @Override
                    public void onPermissionRationaleShouldBeShown(PermissionRequest permission, PermissionToken token) {
                        token.continuePermissionRequest();
                    }
                }).check();
    }

    @OnClick(R.id.btn_stop_tracking)
    public void stopLocationButtonClick() {
        mTracking = false;
        gpsService.stopTracking();
        toggleButtons();
    }

    private void toggleButtons() {
        btnStartTracking.setEnabled(!mTracking);
        btnStopTracking.setEnabled(mTracking);
        txtStatus.setText( (mTracking) ? "TRACKING" : "GPS Ready" );
    }

    private void openSettings() {
        Intent intent = new Intent();
        intent.setAction( Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", BuildConfig.APPLICATION_ID, null);
        intent.setData(uri);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }


    private ServiceConnection serviceConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            String name = className.getClassName();
            if (name.endsWith("BackgroundService")) {
                gpsService = ((BackgroundService.LocationServiceBinder) service).getService();
                btnStartTracking.setEnabled(true);
                txtStatus.setText("GPS Ready");
            }
        }

        public void onServiceDisconnected(ComponentName className) {
            if (className.getClassName().equals("BackgroundService")) {
                gpsService = null;
            }
        }
    };
}
