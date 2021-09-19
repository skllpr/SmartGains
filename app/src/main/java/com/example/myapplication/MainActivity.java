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
    private int cleanupCounter = 0;

    private double Z_MIN = -9.81;
    private double Z_THRESH = -4;
    private double Z_MAX = -3;
    private double Z_EXTREME = 0;

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

    public double calculateCosEstError(double[] coefficients, ArrayList<Double> x_values) {
        double error = 0.0;

        for(int i = 0; i < x_values.size(); i++) {
            double pred = coefficients[0] * Math.cos(coefficients[1] * (double)i + coefficients[2]);
            error += Math.pow(x_values.get(i) - pred, 2);
        }

        return error;
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

    public double normalize(double value, double min, double max) {
        return 100 * (value - min)/(max - min);
    }

    public double getAccuracy(ArrayList<Double> values, double min, double max, boolean square_root) {
        double[] coefficients = curveFit(values);
        double error = calculateCosEstError(coefficients, values);

        if(square_root) {
            min = Math.sqrt(min);
            max = Math.sqrt(max);
            error = Math.sqrt(error);
        }

        System.out.print(", Error: " + error);

        error = normalize(error, min, max);

        return Math.max(0, Math.min(100 - error, 100));
    }


    private void getAccelerometer(SensorEvent event) {
        float[] values = event.values;
        // Movement
        float x = values[0];
        float y = values[1];
        float z = values[2];
        System.out.println(x);

        float y_thresh = 0;

        //System.out.println("Curve fit data:");
        x_values.add((double)x);
        y_values.add((double)y);
        z_values.add((double)z);

        if(x_values.size() <= 2) {
            return;
        }

        if(x_values.get(x_values.size() - 1) < 0 &&
                (x_values.get(x_values.size() - 1) > x_values.get(x_values.size() - 2))) {
            xMinPassed = true;
        }

        if(xMinPassed) {

            if(cleanupCounter < 20) {
                cleanupCounter++;
                return;
            }

            if(x_values.get(x_values.size() - 1) > 0 &&
                    (x_values.get(x_values.size() - 1) < x_values.get(x_values.size() - 2))) {
                // REP COMPLETE
                double x_acc = getAccuracy(x_values, 100, 900, false);
                double z_acc = getAccuracy(z_values, 0, 100, true);

                // range of motion checker
                double z_peak = Collections.max(z_values);

                if(z_peak > Z_MAX) {
                    // Check if weight goes behind you
                    double rom_score = 100 - Math.min(100, normalize(z_peak, Z_MAX, Z_EXTREME));
                } else {
                    // Check that ROM is full
                    double rom_score = Math.min(100, normalize(z_peak, Z_MIN, Z_THRESH));
                }





                float sum = 0;
                for(int i = 0; i < y_values.size(); i++) {
                    sum += Math.max(y_thresh, y_values.get(i));
                }



                // System.out.print(", x_acc: " + x_acc);
                System.out.println("x_acc: " + x_acc + ", z_acc: " + z_acc);

                xMinPassed = false;
                x_values.clear();
                z_values.clear();
            }
        }

        float accelerationSquareRoot = (x * x + y * y + z * z)
                / (SensorManager.GRAVITY_EARTH * SensorManager.GRAVITY_EARTH);
        long actualTime = event.timestamp;

        // System.out.println(x + "," + y + "," + z);

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