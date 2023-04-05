package com.example.client;

import androidx.appcompat.app.AppCompatActivity;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import com.example.aidl.IOptions;

import java.io.File;
import java.io.FileInputStream;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private IOptions options;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            options = IOptions.Stub.asInterface(iBinder);
            transferData();
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {

        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        bindToService();
    }

    private void bindToService() {
        Intent intent = new Intent();
        intent.setAction("com.example.aidl.ServerService");
        intent.setClassName("com.example.server", "com.example.server.ServerService");
        boolean suc = bindService(intent, serviceConnection, BIND_AUTO_CREATE);
        Log.i(TAG, "bindToService: " + suc);
    }

    private void transferData() {
        try {
            // file.iso 是要传输的文件，位于app的缓存目录下，约3.5GB
            ParcelFileDescriptor fileDescriptor = ParcelFileDescriptor.open(new File(getCacheDir(), "file.iso"), ParcelFileDescriptor.MODE_READ_ONLY);
            // 调用AIDL接口，将文件描述符的读端 传递给 接收方
            options.transactFileDescriptor(fileDescriptor);
            fileDescriptor.close();

            /******** 下面的方法也可以实现文件传输，「接收端」不需要任何修改，原理是一样的 ********/
//        createReliablePipe 创建一个管道，返回一个 ParcelFileDescriptor 数组，
//        数组中的第一个元素是管道的读端，
//        第二个元素是管道的写端
//            ParcelFileDescriptor[] pfds = ParcelFileDescriptor.createReliablePipe();
//            ParcelFileDescriptor pfdRead = pfds[0];
//
//            // 调用AIDL接口，将管道的读端传递给 接收端
//            options.transactFileDescriptor(pfdRead);
//
//            ParcelFileDescriptor pfdWrite = pfds[1];
//
//            // 将文件写入到管道中
//            byte[] buffer = new byte[1024];
//            int len;
//            try (
//                    // file.iso 是要传输的文件，位于app的缓存目录下
//                    FileInputStream inputStream = new FileInputStream(new File(getCacheDir(), "file.iso"));
//                    ParcelFileDescriptor.AutoCloseOutputStream autoCloseOutputStream = new ParcelFileDescriptor.AutoCloseOutputStream(pfdWrite);
//            ) {
//                while ((len = inputStream.read(buffer)) != -1) {
//                    autoCloseOutputStream.write(buffer, 0, len);
//                }
//            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        transferData();
    }
}