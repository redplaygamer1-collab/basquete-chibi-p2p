package com.spn.chibi.p2p;

import android.webkit.JavascriptInterface;

/**
 * Exposta ao JS do jogo como window.AndroidP2P (ver bloco SPNUnaP2P no index.html).
 * Cada método aqui é chamável diretamente do JavaScript.
 */
public class AndroidP2PBridge {

    private final P2PManager manager;

    public AndroidP2PBridge(P2PManager manager) {
        this.manager = manager;
    }

    @JavascriptInterface
    public void discoverPeers() {
        manager.discoverPeers();
    }

    @JavascriptInterface
    public void connect(String address) {
        manager.connect(address);
    }

    @JavascriptInterface
    public void createGroup() {
        manager.createGroup();
    }

    @JavascriptInterface
    public void requestConnectionInfo() {
        manager.requestConnectionInfo();
    }

    @JavascriptInterface
    public void send(String json) {
        manager.send(json);
    }

    @JavascriptInterface
    public void disconnect() {
        manager.disconnect();
    }
}
