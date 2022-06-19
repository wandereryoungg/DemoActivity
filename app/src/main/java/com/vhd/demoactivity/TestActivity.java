package com.vhd.demoactivity;

import android.Manifest;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.FrameLayout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.vhd.demoactivity.media.MediaDecoderFragment;

public class TestActivity extends AppCompatActivity {

    private String TAG = getClass().getSimpleName();
    FrameLayout flMain;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test);

        flMain = findViewById(R.id.fl_main);

        Intent intent = getIntent();
        String type = intent.getStringExtra("type");
        if (TextUtils.isEmpty(type)) {
            return;
        }

        FragmentManager manager = getSupportFragmentManager();
        FragmentTransaction transaction = manager.beginTransaction();
        Log.e(TAG, "type: " + type);
        if (type.equals("hotspot")) {
            transaction.replace(R.id.fl_main, HotSpotFragment.instance());
        } else if (type.equals("preview")) {
            transaction.replace(R.id.fl_main, PreviewFragment.instance());
        } else if (type.equals("img_reader")) {
            transaction.replace(R.id.fl_main, ImageReaderFragment.instance());
        } else if (type.equals("media_decoder")) {
            transaction.replace(R.id.fl_main, MediaDecoderFragment.instance());
        } else if (type.equals("mem_leak")) {
            transaction.replace(R.id.fl_main, MemLeakFragment.instance());
        }
        transaction.commit();
    }


}