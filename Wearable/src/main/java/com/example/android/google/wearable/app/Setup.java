package com.example.android.google.wearable.app;

import android.app.Application;

import com.android.volley.RequestQueue;
import com.android.volley.toolbox.Volley;

public class Setup extends Application {

    private static Setup sInstance;

    private RequestQueue mRequestQueue;

    @Override
    public void onCreate() {
        super.onCreate();

        mRequestQueue = Volley.newRequestQueue(this);
        sInstance = this;
    }

    public synchronized static Setup getInstance() {
        return sInstance;
    }

    public RequestQueue getRequestQueue() {
        return mRequestQueue;
    }

}
