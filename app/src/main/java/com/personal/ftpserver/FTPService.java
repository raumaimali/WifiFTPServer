package com.personal.ftpserver;

import android.app.*;
import android.content.*;
import android.os.*;
import android.util.Log;
import androidx.core.app.NotificationCompat;

public class FTPService extends Service {

    private static final String TAG = "FTPService";
    public static final String ACTION_START = "START_FTP";
    public static final String ACTION_STOP  = "STOP_FTP";
    public static final String CHANNEL_ID   = "FTPServiceChannel";
    public static final int    NOTIF_ID     = 101;

    private FTPServer ftpServer;
    private PowerManager.WakeLock wakeLock;

    // Broadcast to update UI
    public static final String BROADCAST_STATUS   = "com.personal.ftpserver.STATUS";
    public static final String BROADCAST_TRANSFER  = "com.personal.ftpserver.TRANSFER";

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();

        // Wake lock: keep CPU running during transfer
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FTPServer:WakeLock");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;

        String action = intent.getAction();

        if (ACTION_START.equals(action)) {
            String rootPath  = intent.getStringExtra("rootPath");
            String username  = intent.getStringExtra("username");
            String password  = intent.getStringExtra("password");
            int    port      = intent.getIntExtra("port", FTPServer.DEFAULT_PORT);

            startFTPServer(rootPath, username, password, port);

        } else if (ACTION_STOP.equals(action)) {
            stopFTPServer();
        }

        return START_NOT_STICKY;
    }

    private void startFTPServer(String rootPath, String username, String password, int port) {
        if (ftpServer != null && ftpServer.isRunning()) return;

        ftpServer = new FTPServer(rootPath, username, password, port);
        ftpServer.setCallback(new FTPServer.ServerCallback() {

            @Override
            public void onClientConnected(String clientIP) {
                sendBroadcast("connected", clientIP, 0, 0);
                updateNotification("متصل: " + clientIP);
            }

            @Override
            public void onClientDisconnected(String clientIP) {
                sendBroadcast("disconnected", clientIP, 0, 0);
                updateNotification("سرور چل رہا ہے");
            }

            @Override
            public void onFileTransfer(String fileName, long bytes, boolean isUpload) {
                sendBroadcast(isUpload ? "upload" : "download", fileName, bytes, 0);
            }

            @Override
            public void onError(String error) {
                sendBroadcast("error", error, 0, 0);
            }

            @Override
            public void onSpeedUpdate(double speedMBps) {
                sendBroadcast("speed", "", 0, speedMBps);
            }
        });

        new Thread(() -> {
            try {
                ftpServer.start();
                if (!wakeLock.isHeld()) wakeLock.acquire(10 * 60 * 60 * 1000L); // max 10 hours
                startForeground(NOTIF_ID, buildNotification("FTP سرور چل رہا ہے — Port " + port));
                sendBroadcast("started", "", 0, 0);
            } catch (Exception e) {
                Log.e(TAG, "Server start failed: " + e.getMessage());
                sendBroadcast("error", e.getMessage(), 0, 0);
            }
        }, "FTP-Start-Thread").start();
    }

    private void stopFTPServer() {
        if (ftpServer != null) {
            ftpServer.stop();
            ftpServer = null;
        }
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        sendBroadcast("stopped", "", 0, 0);
        stopForeground(true);
        stopSelf();
    }

    private void sendBroadcast(String status, String extra, long bytes, double speed) {
        Intent intent = new Intent(BROADCAST_STATUS);
        intent.putExtra("status", status);
        intent.putExtra("extra", extra);
        intent.putExtra("bytes", bytes);
        intent.putExtra("speed", speed);
        sendBroadcast(intent);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "FTP Service", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("FTP Server Background Service");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification(String text) {
        Intent stopIntent = new Intent(this, FTPService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stopPending = PendingIntent.getService(this, 0, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent mainIntent = new Intent(this, MainActivity.class);
        PendingIntent mainPending = PendingIntent.getActivity(this, 0, mainIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Personal FTP Server")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_share)
                .setContentIntent(mainPending)
                .addAction(android.R.drawable.ic_delete, "بند کریں", stopPending)
                .setOngoing(true)
                .build();
    }

    private void updateNotification(String text) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIF_ID, buildNotification(text));
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        if (ftpServer != null && ftpServer.isRunning()) ftpServer.stop();
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        super.onDestroy();
    }
}
