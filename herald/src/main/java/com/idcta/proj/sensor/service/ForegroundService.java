//  Copyright 2020 VMware, Inc.
//  SPDX-License-Identifier: Apache-2.0
//

package com.idcta.proj.sensor.service;

import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import com.idcta.proj.sensor.data.ConcreteSensorLogger;
import com.idcta.proj.sensor.data.SensorLogger;
import com.idcta.proj.sensor.datatype.Tuple;


/// Foreground service for enabling continuous BLE operation in background
public class ForegroundService extends Service {
    private final SensorLogger logger = new ConcreteSensorLogger("App", "ForegroundService");

    @Override
    public void onCreate() {
        logger.debug("onCreate");
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        logger.debug("onStartCommand");

        final NotificationService notificationService = NotificationService.shared(getApplication());
        final Tuple<Integer, Notification> notification = notificationService.notification("Contact Tracing", "Sensor is working");
        startForeground(notification.a, notification.b);
        super.onStartCommand(intent, flags, startId);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        logger.debug("onDestroy");
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}