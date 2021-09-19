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

    private ArrayList<Double> x_values = new ArrayList();
    private ArrayList<Double> y_values = new ArrayList();
    private ArrayList<Double> z_values = new ArrayList();

    private boolean xMinPassed = false;


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
    public double[] curveFit(ArrayList<Double> current_sample) {
        //double[] current_sample = {8.6675, 8.636854,8.63494,8.656487,8.639729,8.657924,8.63925,8.543486,8.407501,7.6514444,6.953325,5.887951,4.398822,2.899158,1.6408199,0.3518371,-0.67714655,-1.738211,-2.7777288,-3.8210769};
        double[] sample = new double[current_sample.size()];
        for(int i = 0; i < current_sample.size(); i++) {
            sample[i] = current_sample.get(i);
        }

        StandardDeviation sd = new StandardDeviation(false);
        double amp = 3*sd.evaluate(sample)/Math.sqrt(2);
        //double amp = 3*ArrayUtils.std(half_cycle)/Math.sqrt(2);
        double freq = 0;
        double phase = 0;
        double[] guess = new double[]{amp, freq, phase};
        HarmonicCurveFitter curveFit = HarmonicCurveFitter.create();
        curveFit.withStartPoint(guess);
        List<WeightedObservedPoint> points = new ArrayList<WeightedObservedPoint>();
        for (int i=0; i < sample.length; i++) {
            points.add(new WeightedObservedPoint(1.0, i, sample[i]));
        }
        double[] coefficients = curveFit.fit(points);
        return coefficients;
    }

    private void getAccelerometer(SensorEvent event) {
        float[] values = event.values;
        // Movement
        float x = values[0];
        float y = values[1];
        float z = values[2];

        //System.out.println("Curve fit data:");
        x_values.add((double)x);

        if(x_values.get(x_values.size() - 1) < 0 &&
                (x_values.get(x_values.size() - 1) > x_values.get(x_values.size() - 2))) {
            xMinPassed = true;
        }

        if(xMinPassed) {
            if(x_values.get(x_values.size() - 1) > 0 &&
                    (x_values.get(x_values.size() - 1) < x_values.get(x_values.size() - 2))) {
                // REP COMPLETE
                double[] coefficients = curveFit(x_values);
                System.out.println(coefficients[0]);
                xMinPassed = false;
            }
        }

        float accelerationSquareRoot = (x * x + y * y + z * z)
                / (SensorManager.GRAVITY_EARTH * SensorManager.GRAVITY_EARTH);
        long actualTime = event.timestamp;

        // System.out.println(x + "," + y + "," + z);
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