package com.qinshou.webrtcdemo_android;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;

/**
 * Author: MrQinshou
 * Email: cqflqinhao@126.com
 * Date: 2023/2/8 9:33
 * Description: 类描述
 */
public class WebSocketClientHelper {

    public interface OnWebSocketClientListener {
        void onOpen();

        void onClose();

        void onMessage(String message);
    }

    private WebSocketClient mWebSocketClient;
    private OnWebSocketClientListener mOnWebSocketClientListener = new OnWebSocketClientListener() {
        @Override
        public void onOpen() {

        }

        @Override
        public void onClose() {

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
                mOnWebSocketClientListener.onClose();
            }

            @Override
            public void onError(Exception ex) {
                ShowLogUtil.debug("onError");
            }
        };
        mWebSocketClient.connect();
    }

    public void disconnect() {
        if (mWebSocketClient == null) {
            return;
        }
        mWebSocketClient.close();
    }

    public void send(String message) {
        if (mWebSocketClient == null) {
            return;
        }
        mWebSocketClient.send(message);
    }
}
