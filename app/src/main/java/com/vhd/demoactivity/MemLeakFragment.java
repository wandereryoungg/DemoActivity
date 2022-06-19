package com.vhd.demoactivity;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.fragment.app.Fragment;

public class MemLeakFragment extends Fragment {

    private String TAG = getClass().getSimpleName();

    private Thread testThread;
    private boolean exitThread;
    private Handler handler;

    private static class SingleTon {
        private static final MemLeakFragment INSTANCE = new MemLeakFragment();
    }

    public static MemLeakFragment instance() {
        return SingleTon.INSTANCE;
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_mem_leak, container, false);
        return view;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        testThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (!exitThread) {
                    try {
                        Log.i(TAG, "---------------------------------------");
                        Thread.sleep(1);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }, "test_thread");
        testThread.start();

        handler = new Handler(Looper.myLooper());
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                Log.e(TAG, "handler delayed msg after 5 min");
            }
        }, 1000 * 60 * 5);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        exitThread = true;
    }
}
