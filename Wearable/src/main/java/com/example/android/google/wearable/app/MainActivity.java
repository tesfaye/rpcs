/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.google.wearable.app;

import android.app.Activity;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.view.GestureDetectorCompat;
import android.support.wearable.view.DelayedConfirmationView;
import android.support.wearable.view.DismissOverlayView;
import android.util.Base64;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ScrollView;
import android.widget.Toast;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.google.android.gms.common.internal.Constants;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class MainActivity extends Activity
        implements DelayedConfirmationView.DelayedConfirmationListener, SensorEventListener {
    private static final String TAG = "MainActivity";
    final String url = "http://kinect.andrew.cmu.edu:8000/watch/events";
    public static final boolean DEBUG = true;

    private static final int NOTIFICATION_ID = 1;
    private static final int NOTIFICATION_REQUEST_CODE = 1;
    private static final int NUM_SECONDS = 5;

    private GestureDetectorCompat mGestureDetector;
    private DismissOverlayView mDismissOverlayView;

    SensorManager mSensorManager;
    Sensor mHeartRateSensor;
    SensorEventListener sensorEventListener;
    private Boolean fallDetected = false;
    public static float normalThreshold = 10, fallenThreshold = 5;
    private float[] mGravity;
    private float mAccelLast, mAccel, mAccelCurrent, maxAccelSeen;
    public static final String LOG_TAG = "MEMES";

    private static int counter = 0;
    private String heartRate;

    private final static int INTERVAL = 1000 * 30; //2 minutes
    Handler mHandler = new Handler();

    Runnable mHandlerTask = new Runnable()
    {
        @Override
        public void run() {
            try {
                postHeartRate();
            } catch (JSONException e) {
                e.printStackTrace();
            }
            mHandler.postDelayed(mHandlerTask, INTERVAL);
        }
    };

    void startRepeatingTask()
    {
        mHandlerTask.run();
    }

    void stopRepeatingTask()
    {
        mHandler.removeCallbacks(mHandlerTask);
    }

    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.main_activity);

        mDismissOverlayView = (DismissOverlayView) findViewById(R.id.dismiss_overlay);
        mDismissOverlayView.setIntroText(R.string.intro_text);
        mDismissOverlayView.showIntroIfNecessary();
        mGestureDetector = new GestureDetectorCompat(this, new LongPressListener());


        mSensorManager = ((SensorManager) getSystemService(SENSOR_SERVICE));
        mHeartRateSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE);
        mSensorManager.registerListener(this, mHeartRateSensor, SensorManager.SENSOR_DELAY_NORMAL);
        mSensorManager.registerListener(this,
                mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                SensorManager.SENSOR_DELAY_UI);

        Log.i(TAG, "LISTENER REGISTERED.");


        mSensorManager.registerListener(sensorEventListener, mHeartRateSensor, mSensorManager.SENSOR_DELAY_FASTEST);

        startRepeatingTask();



    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        return mGestureDetector.onTouchEvent(event) || super.dispatchTouchEvent(event);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_HEART_RATE) {
            heartRate = "" + (int)event.values[0];
            //Toast.makeText(this, "heart rate " + msg, Toast.LENGTH_LONG).show();
        } else if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            double threshold = (fallDetected) ? fallenThreshold : normalThreshold;
            mGravity = event.values.clone();
            mAccelLast = mAccelCurrent;
            mAccelCurrent = (float) Math.sqrt(mGravity[0] * mGravity[0] + mGravity[1] * mGravity[1] + mGravity[2] * mGravity[2]);
            float delta = mAccelCurrent - mAccelLast;
            mAccel = Math.abs(mAccel * 0.9f) + delta;
            if (mAccel > maxAccelSeen) {
                maxAccelSeen = mAccel;
            }


            System.out.println(mAccel + " " + threshold + " wtf");
            if (DEBUG)
                Log.d(LOG_TAG, "Sensor ServiceX: onChange mAccel=" + mAccel + " maxAccelSeen=" + maxAccelSeen + " threshold=" + threshold);
            if (mAccel > threshold) {
                maxAccelSeen = 0;
                if ((fallDetected) && (mAccel > fallenThreshold)) {
                    // fall detected
                    if(counter > 15) {
                        try {
                            postFall();
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                        Toast.makeText(this, "Fall detected", Toast.LENGTH_SHORT).show();
                        counter = 0;
                    }
                    counter++;
                } else {
                    if ((!fallDetected) && (mAccel > normalThreshold)) {
                        fallDetected = true;
                        // fall detected
                        if(counter > 15) {
                            try {
                                postFall();
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                            Toast.makeText(this, "Fall detected", Toast.LENGTH_SHORT).show();
                            counter = 0;
                        }
                    }
                }
            }
        }
        else
            Log.d(TAG, "Unknown sensor type");
    }

    private void postHeartRate() throws JSONException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("event_id", 1);
        jsonObject.put("event_description", heartRate);
        jsonObject.put("event_category", "heartrate");

        JSONArray array = new JSONArray();
        array.put(jsonObject);
        JsonArrayRequest jsonRequest = new JsonArrayRequest(Request.Method.POST, url, array, new Response.Listener<JSONArray>() {
            @Override
            public void onResponse(JSONArray response) {
                //TODO: handle success
                Log.d(TAG, "onResponse: fall detected and posted to server");
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                error.printStackTrace();
                //TODO: handle failure
            }
        }){
            @Override
            public Map<String, String> getHeaders()  {
                Map<String,String> headers = new HashMap<>();
                // add headers <key,value>
                String credentials = "watch_user"+":"+"rpcs_watch2019";
                String auth = "Basic "
                        + Base64.encodeToString(credentials.getBytes(),
                        Base64.NO_WRAP);
                headers.put("authorization", auth);
                headers.put("Content-Type", "application/json");
                return headers;
            }
        };
        jsonRequest.setRetryPolicy(new DefaultRetryPolicy(10 * 1000, 0, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        Setup.getInstance().getRequestQueue().add(jsonRequest);
    }

    private void postFall() throws JSONException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("event_id", 0);
        jsonObject.put("event_description", Long.toString(System.currentTimeMillis()));
        jsonObject.put("event_category", "fall");


        JSONArray array = new JSONArray();
        array.put(jsonObject);
        JsonArrayRequest jsonRequest = new JsonArrayRequest(Request.Method.POST, url, array, new Response.Listener<JSONArray>() {
            @Override
            public void onResponse(JSONArray response) {
                //TODO: handle success
                Log.d(TAG, "onResponse: fall detected and posted to server");
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                error.printStackTrace();
                //TODO: handle failure
            }
        }){
            @Override
            public Map<String, String> getHeaders()  {
                Map<String,String> headers = new HashMap<>();
                // add headers <key,value>
                String credentials = "watch_user"+":"+"rpcs_watch2019";
                String auth = "Basic "
                        + Base64.encodeToString(credentials.getBytes(),
                        Base64.NO_WRAP);
                headers.put("authorization", auth);
                headers.put("Content-Type", "application/json");
                return headers;
            }
        };

        System.out.println("request is " +"Request body: " + new String(jsonRequest.getBody()));
        jsonRequest.setRetryPolicy(new DefaultRetryPolicy(10 * 1000, 0, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        Setup.getInstance().getRequestQueue().add(jsonRequest);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    private class LongPressListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public void onLongPress(MotionEvent event) {
            mDismissOverlayView.show();
        }
    }


    /**
     * Handles the button press to finish this activity and take the user back to the Home.
     */
    public void onFinishActivity(View view) {
        setResult(RESULT_OK);
        finish();
    }

    /**
     * Handles the button to start a DelayedConfirmationView timer.
     */
    public void onStartTimer(View view) {
        DelayedConfirmationView delayedConfirmationView = (DelayedConfirmationView)
                findViewById(R.id.timer);
        delayedConfirmationView.setTotalTimeMs(NUM_SECONDS * 1000);
        delayedConfirmationView.setListener(this);
        delayedConfirmationView.start();
        scroll(View.FOCUS_DOWN);
    }

    @Override
    public void onTimerFinished(View v) {
        Log.d(TAG, "onTimerFinished is called.");
        scroll(View.FOCUS_UP);
    }

    @Override
    public void onTimerSelected(View v) {
        Log.d(TAG, "onTimerSelected is called.");
        scroll(View.FOCUS_UP);
    }

    private void scroll(final int scrollDirection) {
        final ScrollView scrollView = (ScrollView) findViewById(R.id.scroll);
        scrollView.post(new Runnable() {
            @Override
            public void run() {
                scrollView.fullScroll(scrollDirection);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopRepeatingTask();
    }
}
