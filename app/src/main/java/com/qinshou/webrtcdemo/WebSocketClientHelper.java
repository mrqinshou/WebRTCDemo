package com.qinshou.webrtcdemo;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;

/**
 * Author: MrQinshou
 * Email: cqflqinhao@126.com
 * Date: 2023/2/8 9:33
 * Description: 类描述
 */
public enum WebSocketClientHelper {
    SINGLETON;

    public interface OnWebSocketClientListener {
        void onOpen();

        void onMessage(String message);
    }

    private WebSocketClient mWebSocketClient;
    private OnWebSocketClientListener mOnWebSocketClientListener = new OnWebSocketClientListener() {
        @Override
        public void onOpen() {

        }

        @Override
        public void onMessage(String message) {

        }
    };

    public void setOnWebSocketListener(OnWebSocketClientListener onWebSocketClientListener) {
        if (onWebSocketClientListener == null) {
            return;
        }
        mOnWebSocketClientListener = onWebSocketClientListener;
    }

    public void connect(String url) {
        mWebSocketClient = new WebSocketClient(URI.create(url)) {
            @Override
            public void onOpen(ServerHandshake handshakedata) {
                ShowLogUtil.debug("onOpen");
                mOnWebSocketClientListener.onOpen();
            }

            @Override
            public void onMessage(String message) {
//                ShowLogUtil.debug("onMessage--->" + message);
                mOnWebSocketClientListener.onMessage(message);
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                ShowLogUtil.debug("onClose--->" + code);
            }

            @Override
            public void onError(Exception ex) {
                ShowLogUtil.debug("onError");
            }
        };
        mWebSocketClient.connect();
    }

    public void send(String message) {
        if (mWebSocketClient == null) {
            return;
        }
        mWebSocketClient.send(message);
    }

    public void close() {
        if (mWebSocketClient == null) {
            return;
        }
        mWebSocketClient.close();
    }
}
