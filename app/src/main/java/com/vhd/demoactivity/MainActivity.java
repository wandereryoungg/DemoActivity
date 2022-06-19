package com.vhd.demoactivity;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private String TAG = getClass().getSimpleName();

    static {
        System.loadLibrary("native-lib");
    }

    private String[] requestPermissions = new String[]{Manifest.permission.CAMERA,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE};

    private Button btnReboot;
    private Button btnHotSpot;
    private Button btnPreview;
    private Button btnImageReader;
    private Button btnMediaDecoder;
    private Button btnMemLeak;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnReboot = findViewById(R.id.btn_reboot);
        btnHotSpot = findViewById(R.id.btn_hotspot);
        btnPreview = findViewById(R.id.btn_preview);
        btnImageReader = findViewById(R.id.btn_img_reader);
        btnMediaDecoder = findViewById(R.id.btn_media_decoder);
        btnMemLeak = findViewById(R.id.btn_mem_leak);

        btnReboot.setOnClickListener(this);
        btnHotSpot.setOnClickListener(this);
        btnPreview.setOnClickListener(this);
        btnImageReader.setOnClickListener(this);
        btnMediaDecoder.setOnClickListener(this);
        btnMemLeak.setOnClickListener(this);


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            for (int i = 0; i < requestPermissions.length; i++) {
                if (checkSelfPermission(requestPermissions[i]) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{requestPermissions[i]}, i);
                }
            }
        }

    }


    public native String stringFromJNI();

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_reboot:
                reboot("reboot");
                break;
            case R.id.btn_hotspot:
                Intent hotspotIntent = new Intent(this, TestActivity.class);
                hotspotIntent.putExtra("type", "hotspot");
                startActivity(hotspotIntent);
                break;
            case R.id.btn_preview:
                Intent previewIntent = new Intent(this, TestActivity.class);
                previewIntent.putExtra("type", "preview");
                startActivity(previewIntent);
                break;
            case R.id.btn_img_reader:
                Intent imgReaderIntent = new Intent(this, TestActivity.class);
                imgReaderIntent.putExtra("type", "img_reader");
                startActivity(imgReaderIntent);
                break;
            case R.id.btn_media_decoder:
                Intent mediaDecoderIntent = new Intent(this, TestActivity.class);
                mediaDecoderIntent.putExtra("type", "media_decoder");
                startActivity(mediaDecoderIntent);
                break;
            case R.id.btn_mem_leak:
                Intent memIntent = new Intent(this, TestActivity.class);
                memIntent.putExtra("type", "mem_leak");
                startActivity(memIntent);
                break;
        }
    }


    public void reboot(String str) {
        ArrayList arrayList = new ArrayList();
        arrayList.add("sh");
        arrayList.add("-c");
        arrayList.add(str);
        try {
            new ProcessBuilder(arrayList).start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onDestroy() {
        Log.e(TAG, "onDestroy");
        super.onDestroy();
    }
}