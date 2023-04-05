package com.example.server;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import com.example.aidl.IOptions;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class ServerService extends Service {

    private static final String TAG = "ServerService";

    private final IOptions.Stub options = new IOptions.Stub() {
        @Override
        public void transactFileDescriptor(ParcelFileDescriptor pfd) {
            Log.i(TAG, "transactFileDescriptor: " + Thread.currentThread().getName());
            Log.i(TAG, "transactFileDescriptor: calling pid:" + Binder.getCallingPid() + " calling uid:" + Binder.getCallingUid());
            File file = new File(getCacheDir(), "file.iso");
            try (
                    ParcelFileDescriptor.AutoCloseInputStream inputStream = new ParcelFileDescriptor.AutoCloseInputStream(pfd);
            ) {
                file.delete();
                file.createNewFile();
                FileOutputStream stream = new FileOutputStream(file);
                byte[] buffer = new byte[1024];
                int len;
                // 将inputStream中的数据写入到file中
                while ((len = inputStream.read(buffer)) != -1) {
                    stream.write(buffer, 0, len);
                }
                stream.close();
                pfd.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    };

    public ServerService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();
        startServiceForeground();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return options;
    }

    private static final String CHANNEL_ID_STRING = "com.example.server.service";
    private static final int CHANNEL_ID = 0x11;

    private void startServiceForeground() {
        NotificationManager notificationManager = (NotificationManager)
                getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel channel;
        channel = new NotificationChannel(CHANNEL_ID_STRING, getString(R.string.app_name),
                NotificationManager.IMPORTANCE_LOW);
        notificationManager.createNotificationChannel(channel);
        Notification notification = new Notification.Builder(getApplicationContext(),
                CHANNEL_ID_STRING).build();
        startForeground(CHANNEL_ID, notification);
    }
}