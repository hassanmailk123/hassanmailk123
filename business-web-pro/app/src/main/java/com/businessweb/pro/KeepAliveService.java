package com.businessweb.pro;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

public class KeepAliveService extends Service {
    private static final String CHANNEL = "business_web_connection";
    private static final int NOTIFICATION_ID = 41;

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
        Intent open = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pending = PendingIntent.getActivity(
                this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(com.businessweb.pro.R.drawable.ic_launcher)
                .setContentTitle("Business Web Pro connected")
                .setContentText("Keeping the linked WhatsApp Web session ready for alerts")
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setContentIntent(pending)
                .build();
        startForeground(NOTIFICATION_ID, notification);
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL,
                    "Business Web connection",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Keeps the linked web session available while the app is in the background");
            manager.createNotificationChannel(channel);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
