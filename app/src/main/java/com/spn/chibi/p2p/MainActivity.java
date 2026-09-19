package com.spn.chibi.p2p;

import android.Manifest;
import android.content.pm.PackageManager;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Build;
import android.os.Bundle;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int REQ_PERMS = 501;
    private WebView webView;
    private P2PManager p2pManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        webView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setMediaPlaybackRequiresUserGesture(false);
        setContentView(webView);

        WifiP2pManager manager = (WifiP2pManager) getSystemService(WIFI_P2P_SERVICE);
        WifiP2pManager.Channel channel = manager.initialize(this, getMainLooper(), null);
        p2pManager = new P2PManager(this, manager, channel, webView);

        AndroidP2PBridge bridge = new AndroidP2PBridge(p2pManager);
        webView.addJavascriptInterface(bridge, "AndroidP2P");

        // o teu index.html (com o jogo) fica em app/src/main/assets/index.html
        webView.loadUrl("file:///android_asset/index.html");

        requestNeededPermissions();
    }

    private void requestNeededPermissions() {
        List<String> need = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            need.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, "android.permission.NEARBY_WIFI_DEVICES")
                    != PackageManager.PERMISSION_GRANTED) {
                need.add("android.permission.NEARBY_WIFI_DEVICES");
            }
        }
        if (!need.isEmpty()) {
            ActivityCompat.requestPermissions(this, need.toArray(new String[0]), REQ_PERMS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_PERMS) {
            boolean allGranted = true;
            for (int r : grantResults) if (r != PackageManager.PERMISSION_GRANTED) allGranted = false;
            if (!allGranted) {
                Toast.makeText(this, "Sem permissão de localização / Wi-Fi próximo, o Wi-Fi Direto não funciona.", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        p2pManager.registerReceiver();
    }

    @Override
    protected void onPause() {
        super.onPause();
        p2pManager.unregisterReceiver();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        p2pManager.teardown();
    }
}
