package com.spn.chibi.p2p;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pWpsInfo;
import android.util.Log;
import android.webkit.WebView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collection;

/**
 * Ponte nativa para Wi-Fi Direct (P2P) usada pelo jogo via window.AndroidP2P (ver AndroidP2PBridge).
 * Fluxo:
 *  1) discoverPeers() -> evento "peers" com a lista de aparelhos por perto
 *  2) connect(address) -> negocia ligação P2P com esse aparelho
 *  3) quando a ligação é formada, cria-se automaticamente um socket TCP entre os dois
 *     (o "group owner" abre um ServerSocket, o outro liga-se como cliente)
 *  4) send(json) manda uma mensagem; o outro lado recebe via evento "message"
 */
public class P2PManager {

    private static final String TAG = "SPN_P2P";
    private static final int PORT = 8988;

    private final Context context;
    private final WifiP2pManager manager;
    private final WifiP2pManager.Channel channel;
    private final WebView webView;

    private BroadcastReceiver receiver;
    private IntentFilter intentFilter;
    private boolean receiverRegistered = false;

    private Socket socket;
    private ServerSocket serverSocket;
    private DataOutputStream out;
    private volatile boolean running = false;

    public P2PManager(Context context, WifiP2pManager manager, WifiP2pManager.Channel channel, WebView webView) {
        this.context = context;
        this.manager = manager;
        this.channel = channel;
        this.webView = webView;
        buildReceiver();
    }

    private void buildReceiver() {
        intentFilter = new IntentFilter();
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                String action = intent.getAction();
                if (action == null) return;
                if (action.equals(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)) {
                    requestPeers();
                } else if (action.equals(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)) {
                    requestConnectionInfo();
                } else if (action.equals(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)) {
                    int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);
                    boolean enabled = state == WifiP2pManager.WIFI_P2P_STATE_ENABLED;
                    sendStatus(enabled ? "wifiOn" : "wifiOff", null);
                }
            }
        };
    }

    public void registerReceiver() {
        if (!receiverRegistered) {
            context.registerReceiver(receiver, intentFilter);
            receiverRegistered = true;
        }
    }

    public void unregisterReceiver() {
        if (receiverRegistered) {
            try { context.unregisterReceiver(receiver); } catch (Exception ignored) {}
            receiverRegistered = false;
        }
    }

    // ---- ações chamadas a partir do JS (via AndroidP2PBridge) ----

    public void discoverPeers() {
        manager.discoverPeers(channel, new WifiP2pManager.ActionListener() {
            @Override public void onSuccess() { sendStatus("discovering", null); }
            @Override public void onFailure(int reason) { sendStatus("error", "discover:" + reason); }
        });
    }

    public void connect(String address) {
        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = address;
        config.wps.setup = WifiP2pWpsInfo.PBC;
        manager.connect(channel, config, new WifiP2pManager.ActionListener() {
            @Override public void onSuccess() { sendStatus("connecting", address); }
            @Override public void onFailure(int reason) { sendStatus("error", "connect:" + reason); }
        });
    }

    public void createGroup() {
        manager.createGroup(channel, new WifiP2pManager.ActionListener() {
            @Override public void onSuccess() { sendStatus("groupCreated", null); }
            @Override public void onFailure(int reason) { sendStatus("error", "createGroup:" + reason); }
        });
    }

    public void disconnect() {
        closeSocket();
        manager.removeGroup(channel, new WifiP2pManager.ActionListener() {
            @Override public void onSuccess() { sendStatus("disconnected", null); }
            @Override public void onFailure(int reason) { sendStatus("disconnected", null); }
        });
    }

    public void requestConnectionInfo() {
        manager.requestConnectionInfo(channel, new WifiP2pManager.ConnectionInfoListener() {
            @Override
            public void onConnectionInfoAvailable(WifiP2pInfo info) {
                try {
                    JSONObject o = new JSONObject();
                    o.put("type", "connectionInfo");
                    o.put("groupFormed", info.groupFormed);
                    o.put("isGroupOwner", info.isGroupOwner);
                    o.put("groupOwnerAddress", info.groupOwnerAddress != null ? info.groupOwnerAddress.getHostAddress() : null);
                    emit(o);
                    if (info.groupFormed) {
                        startSocket(info.isGroupOwner, info.groupOwnerAddress != null ? info.groupOwnerAddress.getHostAddress() : null);
                    }
                } catch (Exception e) { Log.e(TAG, "connInfo", e); }
            }
        });
    }

    private void requestPeers() {
        manager.requestPeers(channel, new WifiP2pManager.PeerListListener() {
            @Override
            public void onPeersAvailable(WifiP2pDeviceList peers) {
                try {
                    JSONArray arr = new JSONArray();
                    Collection<WifiP2pDevice> devices = peers.getDeviceList();
                    for (WifiP2pDevice d : devices) {
                        JSONObject jd = new JSONObject();
                        jd.put("name", d.deviceName);
                        jd.put("address", d.deviceAddress);
                        jd.put("status", statusName(d.status));
                        arr.put(jd);
                    }
                    JSONObject o = new JSONObject();
                    o.put("type", "peers");
                    o.put("devices", arr);
                    emit(o);
                } catch (Exception e) { Log.e(TAG, "peers", e); }
            }
        });
    }

    private String statusName(int status) {
        switch (status) {
            case WifiP2pDevice.CONNECTED: return "ligado";
            case WifiP2pDevice.INVITED: return "convidado";
            case WifiP2pDevice.FAILED: return "falhou";
            case WifiP2pDevice.AVAILABLE: return "disponível";
            case WifiP2pDevice.UNAVAILABLE: return "indisponível";
            default: return "";
        }
    }

    // ---- canal de dados (socket TCP sobre a ligação P2P) ----

    private void startSocket(final boolean isOwner, final String ownerAddress) {
        if (running) return;
        running = true;
        if (isOwner) {
            new Thread(new Runnable() {
                @Override public void run() {
                    try {
                        serverSocket = new ServerSocket(PORT);
                        Socket s = serverSocket.accept();
                        attachSocket(s);
                    } catch (IOException e) {
                        sendStatus("error", "server:" + e.getMessage());
                        running = false;
                    }
                }
            }).start();
        } else {
            new Thread(new Runnable() {
                @Override public void run() {
                    try {
                        Socket s = new Socket();
                        s.bind(null);
                        s.connect(new InetSocketAddress(ownerAddress, PORT), 15000);
                        attachSocket(s);
                    } catch (IOException e) {
                        sendStatus("error", "client:" + e.getMessage());
                        running = false;
                    }
                }
            }).start();
        }
    }

    private void attachSocket(Socket s) {
        try {
            this.socket = s;
            out = new DataOutputStream(s.getOutputStream());
            sendStatus("socketReady", s.getInetAddress().getHostAddress());
            final BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));
            new Thread(new Runnable() {
                @Override public void run() {
                    try {
                        String line;
                        while (running && (line = in.readLine()) != null) {
                            try {
                                JSONObject data = new JSONObject(line);
                                JSONObject o = new JSONObject();
                                o.put("type", "message");
                                o.put("data", data);
                                emit(o);
                            } catch (Exception ignored) {}
                        }
                    } catch (IOException e) {
                        Log.d(TAG, "socket fechado: " + e.getMessage());
                    } finally {
                        sendStatus("disconnected", null);
                        running = false;
                    }
                }
            }).start();
        } catch (IOException e) {
            sendStatus("error", "attach:" + e.getMessage());
            running = false;
        }
    }

    public synchronized void send(String json) {
        if (out == null) return;
        try {
            out.writeBytes(json.replace("\n", " ") + "\n");
            out.flush();
        } catch (IOException e) {
            Log.e(TAG, "send falhou", e);
        }
    }

    private void closeSocket() {
        running = false;
        try { if (out != null) out.close(); } catch (Exception ignored) {}
        try { if (socket != null) socket.close(); } catch (Exception ignored) {}
        try { if (serverSocket != null) serverSocket.close(); } catch (Exception ignored) {}
        out = null; socket = null; serverSocket = null;
    }

    public void teardown() {
        closeSocket();
        unregisterReceiver();
    }

    // ---- utilitários ----

    private void sendStatus(String state, String detail) {
        try {
            JSONObject o = new JSONObject();
            o.put("type", "status");
            o.put("state", state);
            if (detail != null) o.put("detail", detail);
            emit(o);
        } catch (Exception ignored) {}
    }

    private void emit(final JSONObject o) {
        webView.post(new Runnable() {
            @Override
            public void run() {
                String js = "window.onP2PNativeEvent(" + JSONObject.quote(o.toString()) + ");";
                webView.evaluateJavascript(js, null);
            }
        });
    }
}
