package com.example.android.google.wearable.app;


import android.content.Context;
import android.os.PowerManager;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;


/**
 * This class is the interface for starting and stopping the tracker.
 * Just call start to start tracking and stop when you want to stop tracking.
 *
 */
public class Recorder {

    private static String TAG = "SleepMinderRecorder";

    private LightRecorder lightRecorder = null;
    private AudioRecorder audioRecorder = null;
    private StringBuilder data = new StringBuilder();
    private String startTime = "";
    private PowerManager.WakeLock wakeLock;
    private boolean running = false;
    private NoiseModel noiseModel = new NoiseModel();

    /**
     * Start tracking
     */
    public void start(Context context) {

        this.running = true;

        // Acquire a wake lock so we don't get interrupted
        PowerManager mgr = (PowerManager)context.getSystemService(Context.POWER_SERVICE);
        wakeLock = mgr.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SleepMinderLock:f");
        wakeLock.acquire();

        // Set the current timestamp to the data string and set start time
        this.startTime = String.valueOf(System.currentTimeMillis() / 1000L);
        this.data.append(this.startTime);
        this.data.append(";");

        // Start the light recorder
        lightRecorder = new LightRecorder();
        lightRecorder.start(context);

        // Start the audio recorder
        audioRecorder = new AudioRecorder(noiseModel,null);
        audioRecorder.start();

        // Get the current data every 5 seconds
        final android.os.Handler customHandler = new android.os.Handler();

        Runnable updateTimerThread = new Runnable()
        {
            public void run() {
                synchronized (Recorder.this) {

                    if(lightRecorder == null || audioRecorder == null) {
                        // Recording already stopped. Do nothing here.
                        return;
                    }
                    // We have to check if we already have a "current" lux. In the first call we might not have gotten a sensor change event.
                    if (lightRecorder.getCurrentLux() != null) {
                        data.append(String.valueOf(lightRecorder.getCurrentLux().intValue()));
                    } else {
                        data.append("-1");
                        Log.d(TAG, "current lux null");
                    }

                    data.append(" ");
                    /*data.append(String.valueOf(noiseModel.getNormalizedRMS()));
                    data.append(" ");
                    data.append(String.valueOf(noiseModel.getNormalizedRLH()));
                    data.append(" ");
                    data.append(String.valueOf(noiseModel.getNormalizedVAR()));*/
                    data.append(String.valueOf(noiseModel.getEvent()));
                    data.append(" ");
                    data.append(String.valueOf(noiseModel.getIntensity()));

                    data.append(";");
                    // Dump the data to the text file if we accumulated "enough" Approximately every 15 minutes
                    if (data.length() > 600) {
                        dumpData();
                    }
                    //System.err.println(data.length() + " " + data + "LMAO ");
                    noiseModel.resetEvents();

                    // Restart in 5 seconds
                    customHandler.postDelayed(this, 5000);
                }
            }
        };

        customHandler.postDelayed(updateTimerThread, 0);
    }

    /**
     * Stop tracking
     */
    public void stop(Context context) {
        synchronized (this) {
            if (lightRecorder != null) {
                // Stop the light recorder
                lightRecorder.stop(context);
                // Cleanup
                lightRecorder = null;
            }

            if(audioRecorder != null) {
                // Stop the audio recorder
                audioRecorder.close();
                // Cleanup
                audioRecorder = null;
            }

            // Release the lock
            wakeLock.release();

            // Write the data to a file
            dumpData();

            // Cleanup
            startTime = "";
            running = false;
        }
    }

    public boolean isRunning() {
        return running;
    }

    /**
     * Outputs the accumulated data
     */
    private void dumpData() {

        // Save the data

        FileOutputStream outputStream;

        try {
            outputStream = new FileOutputStream(new File(getStorageDir(), startTime),true);
            outputStream.write(data.toString().getBytes());
            outputStream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        //Hooks.call(Hooks.RECORDING_LIST_UPDATE); IDK ABOUT THIS

        // Clear the data
        data.setLength(0);
    }

    private static File getStorageDir() {
        // Get the directory for the app's private pictures directory.
        File file = Setup.getInstance().getExternalFilesDir("recordings");
        if (file == null || !file.mkdirs()) {
            Log.d("FileHandler", "Directory not created");
        }
        return file;
    }

}
