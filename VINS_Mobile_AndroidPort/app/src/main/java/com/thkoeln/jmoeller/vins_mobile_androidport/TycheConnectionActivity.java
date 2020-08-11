package com.thkoeln.jmoeller.vins_mobile_androidport;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;

import com.aibrain.tyche.bluetoothle.TycheControlHelper;
import com.aibrain.tyche.bluetoothle.packet.receive.StatusData;

public class TycheConnectionActivity extends AppCompatActivity implements View.OnClickListener {

    private Button btConnection;

    TycheControlHelper tycheControlHelper = new TycheControlHelper(this, new TycheControlHelper.OnChangeStatusListener() {
        @Override
        public void onConnectionStatusChange(boolean isConnect) {
            final String connect = isConnect? "Connected" : "Disconnected";
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    btConnection.setText(connect);
                }
            });
        }

        @Override
        public void onStatusChange(StatusData status) {

        }

        @Override
        public void onObstacleDetected(int distance) {

        }

        @Override
        public void onNotEnoughBattery() {

        }
    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tyche_connection);

        btConnection = (Button)findViewById(R.id.connection);
        btConnection.setOnClickListener(this);
        findViewById(R.id.slam).setOnClickListener(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        tycheControlHelper.close(false);
        tycheControlHelper.releaseBleManager();
    }


    @Override
    public void onClick(View view) {
        switch (view.getId())
        {
            case R.id.connection:
                tycheControlHelper.open();
                tycheControlHelper.enableObstacleDetector(true);
                break;
            case R.id.slam:
                Intent intent = new Intent(this, MainActivity.class);
                startActivity(intent);
                break;
        }
    }
}