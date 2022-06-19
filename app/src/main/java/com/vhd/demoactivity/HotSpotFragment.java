package com.vhd.demoactivity;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class HotSpotFragment extends Fragment implements View.OnClickListener {

    private String TAG = getClass().getSimpleName();
    private Switch switchBar;
    private EditText etAccount, etPassword;

    private ConnectivityManager connectivityManager;
    private WifiManager wifiManager;

    static class Singleton {
        private static final HotSpotFragment INSTANCE = new HotSpotFragment();
    }

    public static HotSpotFragment instance() {
        return Singleton.INSTANCE;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        Log.e(TAG, "onAttach");
        super.onAttach(context);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        Log.e(TAG, "onCreate");
        super.onCreate(savedInstanceState);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Log.e(TAG, "onCreateView");

        View view = inflater.inflate(R.layout.fragment_hotspot, container, false);
        connectivityManager = (ConnectivityManager) getActivity().getSystemService(Context.CONNECTIVITY_SERVICE);
        wifiManager = (WifiManager) getActivity().getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        switchBar = view.findViewById(R.id.switch_bar);
        etAccount = view.findViewById(R.id.et_account);
        etPassword = view.findViewById(R.id.et_password);

        if (isWifiApEnabled()) {
            switchBar.setChecked(true);
        } else {
            switchBar.setChecked(false);
        }

        switchBar.setOnClickListener(this);

        IntentFilter filter = new IntentFilter("android.net.wifi.WIFI_AP_STATE_CHANGED");
        getActivity().registerReceiver(mReceiver, filter);
        return view;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        Log.e(TAG, "onActivityCreated");
        super.onActivityCreated(savedInstanceState);
    }

    @Override
    public void onStart() {
        Log.e(TAG, "onStart");
        super.onStart();
    }

    @Override
    public void onResume() {
        Log.e(TAG, "onResume");
        super.onResume();
    }

    @Override
    public void onPause() {
        Log.e(TAG, "onPause");
        super.onPause();
    }

    @Override
    public void onStop() {
        Log.e(TAG, "onStop");
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        Log.e(TAG, "onDestroyView");
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        Log.e(TAG, "onDestroy");
        getActivity().unregisterReceiver(mReceiver);
        super.onDestroy();
    }

    @Override
    public void onDetach() {
        Log.e(TAG, "onDetach");
        super.onDetach();
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if ("android.net.wifi.WIFI_AP_STATE_CHANGED".equals(action)) {
                final int state = intent.getIntExtra("wifi_state", 14);
                Log.e(TAG, "receive wifi ap state changed: " + state);
                handleWifiApStateChanged(state);
            }
        }
    };


    private boolean isWifiApEnabled() {
        try {
            Method method = wifiManager.getClass().getMethod("isWifiApEnabled");
            boolean bool = (boolean) method.invoke(wifiManager);
            return bool;
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
        return false;
    }


    private void startTethering(String SSID, String password, int apType, final boolean enable) {
        Log.e(TAG, "startTethering enable: " + enable);
        try {
            if (enable) {
                WifiConfiguration apConfig = new WifiConfiguration();
                apConfig.SSID = SSID;
                apConfig.preSharedKey = password;
                apConfig.allowedKeyManagement.set(4);//设置加密类型，这里4是wpa加密
                int apBand = apType;
                Field fieldId = WifiConfiguration.class.getDeclaredField("apBand");
                fieldId.set(apConfig, apBand);

                Method setWifiConfig = wifiManager.getClass().getMethod("setWifiApConfiguration", WifiConfiguration.class);
                boolean setWifiConfigResult = (boolean) setWifiConfig.invoke(wifiManager, apConfig);
                Log.e(TAG, "setWifiApConfiguration result: " + setWifiConfigResult);

                Method startTethering = ConnectivityManager.class.getMethod("startTethering", int.class, boolean.class);
                startTethering.invoke(connectivityManager, 0, true);

            } else {
                Method stopTethering = ConnectivityManager.class.getMethod("stopTethering", int.class);
                stopTethering.invoke(connectivityManager, 0);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleWifiApStateChanged(int state) {
        switch (state) {
            case 12://WifiManager.WIFI_AP_STATE_ENABLING:
                switchBar.setEnabled(false);
                break;
            case 13://WifiManager.WIFI_AP_STATE_ENABLED:
                switchBar.setChecked(true);
                switchBar.setEnabled(true);
                Toast.makeText(getActivity(), "开启热点成功", Toast.LENGTH_SHORT).show();
                break;
            case 10://WifiManager.WIFI_AP_STATE_DISABLING:
                switchBar.setEnabled(false);
                break;
            case 11://WifiManager.WIFI_AP_STATE_DISABLED:
                switchBar.setChecked(false);
                switchBar.setEnabled(true);
                Toast.makeText(getActivity(), "关闭热点成功", Toast.LENGTH_SHORT).show();
                break;
            default:
                switchBar.setChecked(false);
                switchBar.setEnabled(true);
                break;
        }
    }

    @Override
    public void onClick(View v) {
        switchBar.setEnabled(false);
        if (!isWifiApEnabled()) {
            String account = etAccount.getText().toString();
            String password = etPassword.getText().toString();
            startTethering(TextUtils.isEmpty(account) ? "hello" : account, TextUtils.isEmpty(password) ? "12345678" : password, 1, true);
        } else if (isWifiApEnabled()) {
            startTethering("哈哈哥", "12345678", 1, false);
        }
    }

}
