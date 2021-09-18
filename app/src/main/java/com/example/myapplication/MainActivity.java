package com.example.myapplication;

import java.io.*;
import java.util.*;
import org.apache.commons.math3.fitting.*;
import org.apache.commons.math3.stat.descriptive.moment.StandardDeviation;
import android.app.Activity;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;
import android.widget.TextView;

//import com.example.myapplication.databinding.ActivityMainBinding;

public class MainActivity extends Activity implements SensorEventListener {
    private SensorManager sensorManager;
    private boolean color = false;
    private View view;
    private long lastUpdate;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        view = findViewById(R.id.text);
        view.setBackgroundColor(Color.GREEN);

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        lastUpdate = System.currentTimeMillis();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            getAccelerometer(event);
        }

    }
    public void curveFit() {
        double[] current_sample = new double[300];
        double[] half_cycle = Arrays.copyOfRange(current_sample, 175, 225);
        StandardDeviation sd = new StandardDeviation(false);
        double amp = 3*sd.evaluate(half_cycle)/Math.sqrt(2);
        //double amp = 3*ArrayUtils.std(half_cycle)/Math.sqrt(2);
        double freq = 0;
        double phase = 0;
        double[] guess = new double[]{amp, freq, phase};
        HarmonicCurveFitter curveFit = HarmonicCurveFitter.create();
        curveFit.withStartPoint(guess);
        List<WeightedObservedPoint> points = new ArrayList<WeightedObservedPoint>();
        for (int i=0; i < half_cycle.length; i++) {
            points.add(new WeightedObservedPoint(1.0, i, half_cycle[i]));
        }
        double[] vals = curveFit.fit(points);
        for (double val: vals){
            System.out.println(val);
        }
    }

    private void getAccelerometer(SensorEvent event) {
        float[] values = event.values;
        // Movement
        float x = values[0];
        float y = values[1];
        float z = values[2];
        float accelerationSquareRoot = (x * x + y * y + z * z)
                / (SensorManager.GRAVITY_EARTH * SensorManager.GRAVITY_EARTH);
        long actualTime = event.timestamp;

        System.out.println(x + "," + y + "," + z);
        if (accelerationSquareRoot >= 2) //
        {
            if (actualTime - lastUpdate < 200) {
                return;
            }
            lastUpdate = actualTime;
            Toast.makeText(this, "Device was shuffed", Toast.LENGTH_SHORT)
                    .show();
            if (color) {
                view.setBackgroundColor(Color.GREEN);
            } else {
                view.setBackgroundColor(Color.RED);
            }
            color = !color;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    @Override
    protected void onResume() {
        super.onResume();
        // register this class as a listener for the orientation and
        // accelerometer sensors
        sensorManager.registerListener(this,
                sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    protected void onPause() {
        // unregister listener
        super.onPause();
        sensorManager.unregisterListener(this);
    }
}