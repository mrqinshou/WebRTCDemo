package com.qinshou.webrtcdemo_server;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

public class WebSocketServerHelper {

    private WebSocketServer mWebSocketServer;
    private final List<WebSocket> mWebSockets = new ArrayList<>();
    private static final String HOST_NAME = "192.168.1.105";
    private static final int PORT = 8888;

    public void start() {
        InetSocketAddress inetSocketAddress = new InetSocketAddress(HOST_NAME, PORT);
        mWebSocketServer = new WebSocketServer(inetSocketAddress) {

            @Override
            public void onOpen(WebSocket conn, ClientHandshake handshake) {
                System.out.println("onOpen--->" + conn);
                // 客户端连接时保存到集合中
                mWebSockets.add(conn);
            }

            @Override
            public void onClose(WebSocket conn, int code, String reason, boolean remote) {
                System.out.println("onClose--->" + conn);
                // 客户端断开时从集合中移除
                mWebSockets.remove(conn);
            }

            @Override
            public void onMessage(WebSocket conn, String message) {
//                System.out.println("onMessage--->" + message);
                // 消息直接透传给除发送方以外的连接
                for (WebSocket webSocket : mWebSockets) {
                    if (webSocket != conn) {
                        webSocket.send(message);
                    }
                }
            }

            @Override
            public void onError(WebSocket conn, Exception ex) {
                System.out.println("onError--->" + conn + ", ex--->" + ex);
                // 客户端连接异常时从集合中移除
                mWebSockets.remove(conn);
            }

            @Override
            public void onStart() {
                System.out.println("onStart");
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
        mWebSocketServer = null;
    }

    public static void main(String[] args) {
        new WebSocketServerHelper().start();
    }
}