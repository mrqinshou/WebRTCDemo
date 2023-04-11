package com.qinshou.webrtcdemo;

import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

/**
 * Author: MrQinshou
 * Email: cqflqinhao@126.com
 * Date: 2023/2/8 9:33
 * Description: 类描述
 */
public enum WebSocketServerHelper {
    SINGLETON;

    public interface OnWebSocketServerListener {
        void onStart();

        void onOpen(int size);

        void onClose(int size);
    }

    private WebSocketServer mWebSocketServer;
    private final List<WebSocket> mWebSockets = new ArrayList<>();
    private OnWebSocketServerListener mOnWebSocketServerListener = new OnWebSocketServerListener() {
        @Override
        public void onStart() {

        }

        @Override
        public void onOpen(int size) {

        }

        @Override
        public void onClose(int size) {

        }
    };

    public void setOnWebSocketListener(OnWebSocketServerListener onWebSocketServerListener) {
        if (onWebSocketServerListener == null) {
            return;
        }
        mOnWebSocketServerListener = onWebSocketServerListener;
    }

    public void start(String url) {
        url = url.replace("ws://", "").replace("wss://", "");
        String[] split = url.split(":");
        if (split.length <= 1) {
            return;
        }
        InetSocketAddress inetSocketAddress = new InetSocketAddress(split[0], Integer.parseInt(split[1]));
        mWebSocketServer = new WebSocketServer(inetSocketAddress) {

            @Override
            public void onOpen(WebSocket conn, ClientHandshake handshake) {
                ShowLogUtil.debug("onOpen--->" + conn);
                mWebSockets.add(conn);
                mOnWebSocketServerListener.onOpen(mWebSockets.size());
            }

            @Override
            public void onClose(WebSocket conn, int code, String reason, boolean remote) {
                ShowLogUtil.debug("onClose--->" + conn);
                mWebSockets.remove(conn);
                mOnWebSocketServerListener.onClose(mWebSockets.size());
            }

            @Override
            public void onMessage(WebSocket conn, String message) {
//                ShowLogUtil.debug( "onMessage--->" + message);
                for (WebSocket webSocket : mWebSockets) {
                    if (webSocket != conn) {
                        webSocket.send(message);
                    }
                }
            }

            @Override
            public void onError(WebSocket conn, Exception ex) {
                ShowLogUtil.debug("onError");
            }

            @Override
            public void onStart() {
                ShowLogUtil.debug("onStart");
                mOnWebSocketServerListener.onStart();
            }
        };
        mWebSocketServer.start();
    }

    public void stop() {
        if (mWebSocketServer == null) {
            return;
        }
        for (WebSocket webSocket : mWebSocketServer.getConnections()) {
            webSocket.close();
        }
        try {
            mWebSocketServer.stop();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
