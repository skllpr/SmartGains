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
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.Toast;
import android.widget.TextView;

import androidx.annotation.RequiresApi;

//import com.example.myapplication.databinding.ActivityMainBinding;

public class MainActivity extends Activity implements SensorEventListener {
    private SensorManager sensorManager;
    private Vibrator vibrator;
    private VibrationEffect effect;
    private boolean color = false;
    private View view;
    private ProgressBar progressBar;
    private TextView repCount;
    private TextView repText;
    private boolean setComplete = false;
    private int correctReps = 0;
    private final int MAX_REPS = 14;
    private double sumTotal = 0;
    private int totalCount = 0;
    private long lastUpdate;

    private ArrayList<Double> x_values = new ArrayList();
    private ArrayList<Double> y_values = new ArrayList();
    private ArrayList<Double> z_values = new ArrayList();

    private boolean xMinPassed = false;
    private int cleanupCounter = 0;

    private double Z_MIN = -9.81;
    private double Z_THRESH = -3;
    private double Z_MAX = -0.5;
    private double Z_EXTREME = 0;

    private double X_THRESH_LOWER = -6.5;
    private double X_THRESH_UPPER = 7.5;

    private double Y_MAX = 0;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        view = findViewById(R.id.text);
        progressBar = findViewById(R.id.progressBar);
        repCount = findViewById(R.id.repCount);
        repText = findViewById(R.id.repText);

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        lastUpdate = System.currentTimeMillis();

    }

    @RequiresApi(api = Build.VERSION_CODES.O)
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

        // System.out.print("Error: " + error);

        error = normalize(error, min, max);

        return Math.max(0, Math.min(100 - error, 100));
    }


    @RequiresApi(api = Build.VERSION_CODES.O)
    private void getAccelerometer(SensorEvent event) {
        float[] values = event.values;
        // Movement
        float x = values[0];
        float y = values[1];
        float z = values[2];

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
                double x_acc = 100; // getAccuracy(x_values, 100, 900, false);
                double z_acc = getAccuracy(z_values, 75, 300, false);

                // range of motion checker
                double z_peak = Collections.max(z_values);

                double z_rom_score = 0;
                double x_rom_score_upper = 100;
                double x_rom_score_lower = 100;

                if(z_peak > Z_MAX) {
                    // Check if weight goes behind you
                    z_rom_score = 100 - Math.min(100, normalize(z_peak, Z_MAX, Z_EXTREME));
                } else {
                    // Check that ROM is full
                    z_rom_score = Math.min(100, normalize(z_peak, Z_MIN, Z_THRESH));
                }

                double x_peak = Collections.max(x_values);
                double x_trough = Collections.min(x_values);

                if(x_peak < X_THRESH_UPPER) {
                    x_rom_score_upper = Math.min(100, normalize(x_peak, 0, X_THRESH_UPPER));
                }
                if(x_peak < X_THRESH_LOWER) {
                    x_rom_score_lower = 100 - Math.min(100, normalize(x_trough, X_THRESH_LOWER, 0));
                }

                double rom_score = (z_rom_score + x_rom_score_upper + x_rom_score_lower) / 3;
                // System.out.println("ROM Scores: " + z_rom_score + ", " + x_rom_score_upper + ", " + x_rom_score_lower);

                float avg = 0;
                int counter = 0;
                for(int i = 0; i < y_values.size(); i++) {
                    double add = Math.max(Y_MAX, y_values.get(i));
                    if(add > 0) {
                        avg += add;
                        counter++;
                    }
                }
                double y_score = 100;
                if(counter > 0) {
                    avg = avg / counter;
                    y_score = 100 - Math.min(100, normalize(avg, 0, 1));
                }

                double total_score = y_score * (rom_score + x_acc + z_acc) / 300;
                String text = total_score > 80 ? "Great job!" : "Bruh, please...";

                sumTotal += total_score;
                totalCount++;

                if (total_score > 70) {
                    correctReps++;

                    if (correctReps < MAX_REPS) {
                        progressBar.setProgress((int) ((double) correctReps / MAX_REPS * 100));
                        repCount.setText(String.valueOf(correctReps));
                    } else if(!setComplete) {
                        progressBar.setProgress(100);
                        repCount.setText(String.valueOf(MAX_REPS));

                        effect = VibrationEffect.createOneShot(1000, VibrationEffect.DEFAULT_AMPLITUDE);
                        vibrator.vibrate(effect);

                        progressBar.setVisibility(View.INVISIBLE);
                        repText.setVisibility(View.INVISIBLE);
                        repCount.setText("Score: " + String.format("%.2f", (sumTotal / totalCount)));

                        setComplete = true;
                    }
                }

//                System.out.println("x_acc: " + x_acc);
                System.out.println("z_acc: " + z_acc);
//                System.out.println("tilt: " + y_score);
//                System.out.println("z_rom: " + z_rom_score);
//                System.out.println("x_up: " + x_rom_score_upper);
//                System.out.println("x_low: " + x_rom_score_lower);

                //System.out.println("Your y score was: " + y_score);

                System.out.println(text + " Your score was " + total_score);
                System.out.println();

                // System.out.print(", x_acc: " + x_acc);
                // System.out.println("x_acc: " + x_acc + ", z_acc: " + z_acc);

                xMinPassed = false;
                x_values.clear();
                y_values.clear();
                z_values.clear();
            }
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