package com.daemonize.daemonengine.consumer;

import android.os.Handler;
import android.os.Looper;

public class AndroidLooperConsumer implements Consumer {

    private Handler handler = new Handler(Looper.getMainLooper());

    public AndroidLooperConsumer(){}

    public AndroidLooperConsumer(Handler handler) {
        this.handler = handler;
    }

    @Override
    public boolean enqueue(Runnable runnable) {
        return handler.post(runnable);
    }
}
