package com.example.bottle;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;

import com.example.bottle.ui.view.FlowWaterSurfaceView;

public class MainActivity extends AppCompatActivity {

    private FlowWaterSurfaceView mWaterView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mWaterView = (FlowWaterSurfaceView) findViewById(R.id.surfaceview_bottle);
        mWaterView.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                mWaterView.refreshWater((float) Math.random());
            }
        });
        mWaterView.refreshWater(0.7f);
    }

    @Override
    protected void onResume() {
        mWaterView.resume();
        super.onResume();
    }

    @Override
    protected void onPause() {
        mWaterView.pause();
        super.onPause();
    }
}
