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

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
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
import com.android.volley.toolbox.StringRequest;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

public class MainActivity extends Activity
        implements DelayedConfirmationView.DelayedConfirmationListener, SensorEventListener {
    private static final String TAG = "MainActivity";
    final String url = "http://kinect.andrew.cmu.edu:8000/watch/events";
    public static final boolean DEBUG = true;

    private static final int NUM_SECONDS = 5;

    private GestureDetectorCompat mGestureDetector;
    private DismissOverlayView mDismissOverlayView;

    SensorManager mSensorManager;
    Sensor mHeartRateSensor;

    private Boolean fallDetected = false;
    public static float normalThreshold = 15, fallenThreshold = 15;
    private float[] mGravity;
    private float mAccelLast, mAccel, mAccelCurrent, maxAccelSeen;
    public static final String LOG_TAG = "MEMES";

    private String filename = "file";
    private final static int HEART_INTERVAL = 1000 * 30; //2 minutes
    private final static int SLEEP_INTERVAL = 1000 * 30 * 30 * 10; // 10 hours

    private static int counter = 0;
    private String heartRate;

    Handler mHandler = new Handler();

    Runnable mHandlerTask = new Runnable() {
        @Override
        public void run() {
            try {
                postHeartRate();
            } catch (JSONException e) {
                e.printStackTrace();
            }
            mHandler.postDelayed(mHandlerTask, HEART_INTERVAL);
        }
    };

    Runnable mHandlerTask1 = new Runnable() {
        @Override
        public void run() {
            try {
                int quality = getSleepQuality();
                postSleep(quality);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            mHandler.postDelayed(mHandlerTask1, SLEEP_INTERVAL);
        }
    };

    private int getSleepQuality() {

        File file = new File(FileHandler.getStorageDir(), filename);

        String content = FileHandler.readFile(file);

        String[] parts = content.split(";");
        String start = parts[0];

        ArrayList<String> xVals = new ArrayList<>();

        int j = 0;

        int movements = 0;

        // 30 minute intervals
        int[] intervals = new int[(int)Math.ceil(parts.length/300f)];
        int[] lightIntervals = new int[(int)Math.ceil(parts.length/300f)];

        int sleepEvents = 0;
        int movementEvents = 0;
        int snoreEvents = 0;

        int awake = 0;
        int sleep = 0;

        int nightLight = 0;
        int dawnLight = 0;
        int dayLight = 0;

        ArrayList<Integer> lightsIntensities = new ArrayList<>();

        for(int i = 2;i<parts.length;i++) {
            String[] values = parts[i].split(" ");
            j++;
            if(values[1].equals("0")) {
                sleepEvents++;
            }
            if(values[1].equals("1")) {
                snoreEvents++;
            }
            if(values[1].equals("2")) {
                movementEvents++;
                movements++;
            }
            int lightIntensity = Integer.parseInt(values[0]);
            if(lightIntensity <= 20) {
                nightLight++;
            } else if(lightIntensity <= 100) {
                dawnLight++;
            } else {
                dayLight++;
            }

            lightsIntensities.add(lightIntensity);


            if(i % 300 == 0) {
                // Add the movement interval
                if(movements > 1) {
                    intervals[(int) (i / 300f)] = movements;
                    awake++;
                } else {
                    sleep++;
                }
                movements = 0;

                // Add the light interval
                int lightSum = 0;
                for(Integer intensity: lightsIntensities) {
                    lightSum += intensity;
                }
                lightIntervals[(int) (i / 300f)] = lightSum / lightsIntensities.size();
                lightsIntensities.clear();
            }
        }

        int phases = 0;
        boolean isSleeping = false;

        for(int i = 0;i<intervals.length;i++) {
            // Set x value
            long dv = (Long.valueOf(start) + 5 * (i*300)) * 1000;
            Date df = new java.util.Date(dv);
            xVals.add(new SimpleDateFormat("HH:mm").format(df));

            int movementAmount = 0;
            if(intervals[i] > 2) {
                movementAmount = intervals[i];
            }

            //sleepStageEntries.add(new BarEntry(movementAmount,i));
            if(movementAmount > 2) {
                if(isSleeping) {
                    phases++;
                    isSleeping = false;
                }
            } else {
                if(!isSleeping) {
                    isSleeping = true;
                }
            }

            //lightStageEntries.add(new Entry(lightIntervals[i],i));

        }

        int qualityLight = 1;
        // If one hour of the sleep was during daylight consider the light quality bad
        if(dayLight >= 36000) {
            qualityLight = -1;
        } else if(dawnLight+dayLight >= 54000) {
            // If one hour of the sleep was during dawn, consider the light quality medium
            qualityLight = 0;
        }

        int qualityPhases = 1;
        // Too much phases are no good sign
        if(phases > 10 || phases < 4) {
            qualityPhases = 0;
        }

        int qualitySleep = -1;
        if(parts.length >= 0.1 * 60*60*7) {
            // At least 7 hours of sleep
            qualitySleep = 1;
        } else if(parts.length >= 0.1 * 60*60*5.5) {
            // At least 5.5 hours of sleep
            qualitySleep = 0;
        }

        return (int)((qualityPhases + qualitySleep + qualityLight) / 3f);
    }

    void startRepeatingTask() {
        mHandlerTask.run();
    }

    void stopRepeatingTask() {
        mHandler.removeCallbacks(mHandlerTask);
    }

    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.main_activity);

        requestPermissions();
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


        //mSensorManager.registerListener(this, mHeartRateSensor, mSensorManager.SENSOR_DELAY_FASTEST);

        startRepeatingTask();

        Intent trackingIntent = new Intent(MainActivity.this, RecordingService.class);
        MainActivity.this.startService(trackingIntent);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        return mGestureDetector.onTouchEvent(event) || super.dispatchTouchEvent(event);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {

        if (event.sensor.getType() == Sensor.TYPE_HEART_RATE) {

            heartRate = "" + (int) event.values[0];
            //Toast.makeText(this, "heart rate " + heartRate, Toast.LENGTH_SHORT).show();
            Log.d(TAG, "current heart rate is " + heartRate);
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


            //System.out.println(mAccel + " " + threshold + " wtf");
            if (DEBUG)
                // Log.d(LOG_TAG, "Sensor ServiceX: onChange mAccel=" + mAccel + " maxAccelSeen=" + maxAccelSeen + " threshold=" + threshold);
                if (mAccel > threshold) {
                    maxAccelSeen = 0;
                    if ((fallDetected) && (mAccel > fallenThreshold)) {
                        // fall detected
                        if (counter > 15) {
                            try {
                                postFall();
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                            Toast.makeText(this, "Fall detected", Toast.LENGTH_LONG).show();
                            counter = 0;
                        }
                        counter++;
                    } else {
                        if ((!fallDetected) && (mAccel > normalThreshold)) {
                            fallDetected = true;
                            // fall detected
                            if (counter > 15) {
                                try {
                                    postFall();
                                } catch (JSONException e) {
                                    e.printStackTrace();
                                }
                                Toast.makeText(this, "Fall detected", Toast.LENGTH_LONG).show();
                                counter = 0;
                            }
                        }
                    }
                }
        } else
            Log.d(TAG, "Unknown sensor type");
    }

    private String getUTCTime() {
        TimeZone tz = TimeZone.getTimeZone("UTC");
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"); // Quoted "Z" to indicate UTC, no timezone offset
        df.setTimeZone(tz);
        String nowAsISO = df.format(new Date());
        return nowAsISO;
    }

    private void postHeartRate() throws JSONException {

        if (heartRate == null) return;
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("event_id", 1);
        jsonObject.put("event_description", "" + heartRate);
        jsonObject.put("event_category", "heartrate");
        jsonObject.put("timestamp", getUTCTime());

        JSONArray array = new JSONArray();
        array.put(jsonObject);
        final String requestBody = array.toString();
        System.out.println("json is " + requestBody);

        StringRequest jsonRequest = new StringRequest(Request.Method.POST, url, new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                //TODO: handle success
                Log.d(TAG, "onResponse: heart rate detected and posted to server");
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                error.printStackTrace();
                System.err.println("heart rate failed to post");
//                //get status code here
//                final String statusCode = String.valueOf(error.networkResponse.statusCode);
//                //get response body and parse with appropriate encoding
//
//
//                try {
//                    String body = new String(error.networkResponse.data,"UTF-8");
//                    System.err.println(body);
//                } catch (UnsupportedEncodingException e) {
//                    // exception
//                }
//                //TODO: handle failure
            }
        }) {
            @Override
            public byte[] getBody() {
                try {
                    return requestBody == null ? null : requestBody.getBytes("utf-8");
                } catch (UnsupportedEncodingException exception) {
                    exception.printStackTrace();
                    //VolleyLog.wtf("Unsupported Encoding while trying to get the bytes of %s using %s", requestBody, "utf-8");
                    return null;
                }
            }

            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                // add headers <key,value>
                String credentials = "watch_user" + ":" + "rpcs_watch2019";
                String auth = "Basic "
                        + Base64.encodeToString(credentials.getBytes(),
                        Base64.NO_WRAP);
                headers.put("authorization", auth);
                headers.put("accept", "application/json");
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
        jsonObject.put("timestamp", getUTCTime());
        jsonObject.put("event_category", "fall");


        JSONArray array = new JSONArray();
        array.put(jsonObject);

        final String requestBody = array.toString();
        StringRequest jsonRequest = new StringRequest(Request.Method.POST, url, new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                //TODO: handle success
                Log.d(TAG, "onResponse: fall detected and posted to server");
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                error.printStackTrace();
                System.err.println("fall failed to post");
//                String body;
//                //get status code here
//                final String statusCode = String.valueOf(error.networkResponse.statusCode);
//                System.err.println("fall failed to post " +statusCode);
//                //get response body and parse with appropriate encoding
//                try {
//                    body = new String(error.networkResponse.data,"UTF-8");
//                    System.err.println("fall failed to post " + body );
//                } catch (UnsupportedEncodingException e) {
//                    // exception
//                }
                //TODO: handle failure
            }
        }) {
            @Override
            public byte[] getBody() {
                try {
                    return requestBody == null ? null : requestBody.getBytes("utf-8");
                } catch (UnsupportedEncodingException exception) {
                    exception.printStackTrace();
                    //VolleyLog.wtf("Unsupported Encoding while trying to get the bytes of %s using %s", requestBody, "utf-8");
                    return null;
                }
            }

            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                // add headers <key,value>
                String credentials = "watch_user" + ":" + "rpcs_watch2019";
                String auth = "Basic "
                        + Base64.encodeToString(credentials.getBytes(),
                        Base64.NO_WRAP);
                headers.put("authorization", auth);
                headers.put("accept", "application/json");
                headers.put("Content-Type", "application/json");
                return headers;
            }
        };

        jsonRequest.setRetryPolicy(new DefaultRetryPolicy(10 * 1000, 0, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        Setup.getInstance().getRequestQueue().add(jsonRequest);
    }

    private void postSleep(int sleepQuality) throws JSONException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("event_id", 2);
        jsonObject.put("timestamp", getUTCTime());
        jsonObject.put("event_description", sleepQuality);
        jsonObject.put("event_category", "sleep");

        JSONArray array = new JSONArray();
        array.put(jsonObject);
        final String requestBody = array.toString();

        StringRequest jsonRequest = new StringRequest(Request.Method.POST, url, new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                //TODO: handle success
                Log.d(TAG, "onResponse: sleep analyzed and posted to server");
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                error.printStackTrace();
                //TODO: handle failure
            }
        }){
            @Override
            public byte[] getBody() {
                try {
                    return requestBody == null ? null : requestBody.getBytes("utf-8");
                } catch (UnsupportedEncodingException exception) {
                    exception.printStackTrace();
                    //VolleyLog.wtf("Unsupported Encoding while trying to get the bytes of %s using %s", requestBody, "utf-8");
                    return null;
                }
            }

            @Override
            public Map<String, String> getHeaders()  {
                Map<String,String> headers = new HashMap<>();
                // add headers <key,value>
                String credentials = "watch_user"+":"+"rpcs_watch2019";
                String auth = "Basic "
                        + Base64.encodeToString(credentials.getBytes(),
                        Base64.NO_WRAP);
                headers.put("authorization", auth);
                headers.put("accept", "application/json");
                headers.put("Content-Type", "application/json");
                return headers;
            }
        };
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

    public static boolean hasPermissions(Context context, String... permissions) {
        if (context != null && permissions != null) {
            for (String permission : permissions) {
                if (ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
        }
        return true;
    }

    private void requestPermissions() {
        //check API version, do nothing if API version < 23!
        int currentapiVersion = android.os.Build.VERSION.SDK_INT;
        if (currentapiVersion > android.os.Build.VERSION_CODES.LOLLIPOP) {


            int PERMISSION_ALL = 1;
            String[] PERMISSIONS = {
                    Manifest.permission.BODY_SENSORS,
                    Manifest.permission.WAKE_LOCK,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.INTERNET,
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.ACCESS_NETWORK_STATE
            };

            if(!hasPermissions(this, PERMISSIONS)){
                ActivityCompat.requestPermissions(this, PERMISSIONS, PERMISSION_ALL);
            }
        }
    }

}