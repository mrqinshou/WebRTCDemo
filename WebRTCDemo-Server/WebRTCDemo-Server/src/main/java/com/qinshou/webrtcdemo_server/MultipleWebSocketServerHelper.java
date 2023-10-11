package com.qinshou.webrtcdemo_server;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Author: MrQinshou
 * Email: cqflqinhao@126.com
 * Date: 2023/2/8 9:33
 * Description: 多人通话 WebSocketServer
 */
public class MultipleWebSocketServerHelper {
    public static class WebSocketBean {
        private String mUserId;
        private WebSocket mWebSocket;

        public WebSocketBean() {
        }

        public WebSocketBean(WebSocket webSocket) {
            mWebSocket = webSocket;
        }

        public String getUserId() {
            return mUserId;
        }

        public void setUserId(String userId) {
            mUserId = userId;
        }

        public WebSocket getWebSocket() {
            return mWebSocket;
        }

        public void setWebSocket(WebSocket webSocket) {
            mWebSocket = webSocket;
        }
    }

    private WebSocketServer mWebSocketServer;
    private final List<WebSocketBean> mWebSocketBeans = new LinkedList<>();
    //    private static final String HOST_NAME = "192.168.1.104";
    private static final String HOST_NAME = "172.16.2.172";
    private static final int PORT = 8888;

    private WebSocketBean getWebSocketBeanByWebSocket(WebSocket webSocket) {
        for (WebSocketBean webSocketBean : mWebSocketBeans) {
            if (webSocket == webSocketBean.getWebSocket()) {
                return webSocketBean;
            }
        }
        return null;
    }

    private WebSocketBean getWebSocketBeanByUserId(String userId) {
        for (WebSocketBean webSocketBean : mWebSocketBeans) {
            if (userId.equals(webSocketBean.getUserId())) {
                return webSocketBean;
            }
        }
        return null;
    }

    private WebSocketBean removeWebSocketBeanByWebSocket(WebSocket webSocket) {
        for (WebSocketBean webSocketBean : mWebSocketBeans) {
            if (webSocket == webSocketBean.getWebSocket()) {
                mWebSocketBeans.remove(webSocketBean);
                return webSocketBean;
            }
        }
        return null;
    }

    public void start() {
        InetSocketAddress inetSocketAddress = new InetSocketAddress(HOST_NAME, PORT);
        mWebSocketServer = new WebSocketServer(inetSocketAddress) {

            @Override
            public void onOpen(WebSocket conn, ClientHandshake handshake) {
                System.out.println("onOpen--->" + conn);
                // 有客户端连接，创建 WebSocketBean，此时仅保存了 WebSocket 连接，但还没有和 userId 绑定
                mWebSocketBeans.add(new WebSocketBean(conn));
            }

            @Override
            public void onClose(WebSocket conn, int code, String reason, boolean remote) {
                System.out.println("onClose--->" + conn);
                WebSocketBean webSocketBean = removeWebSocketBeanByWebSocket(conn);
                if (webSocketBean == null) {
                    return;
                }
                // 通知其他用户有人退出房间
                JsonObject jsonObject = new JsonObject();
                jsonObject.addProperty("msgType", "otherQuit");
                jsonObject.addProperty("userId", webSocketBean.mUserId);
                for (WebSocketBean w : mWebSocketBeans) {
                    if (w != webSocketBean) {
                        w.mWebSocket.send(jsonObject.toString());
                    }
                }
            }

            @Override
            public void onMessage(WebSocket conn, String message) {
                System.out.println("onMessage--->" + message);
                Map<String, String> map = new Gson().fromJson(message, new TypeToken<Map<String, String>>() {
                }.getType());
                String msgType = map.get("msgType");
                if ("join".equals(msgType)) {
                    // 收到加入房间指令
                    String userId = map.get("userId");
                    WebSocketBean webSocketBean = getWebSocketBeanByWebSocket(conn);
                    // WebSocket 连接绑定 userId
                    if (webSocketBean != null) {
                        webSocketBean.setUserId(userId);
                    }
                    // 通知其他用户有其他人加入房间
                    JsonObject jsonObject = new JsonObject();
                    jsonObject.addProperty("msgType", "otherJoin");
                    jsonObject.addProperty("userId", userId);
                    for (WebSocketBean w : mWebSocketBeans) {
                        if (w != webSocketBean && w.getUserId() != null) {
                            w.mWebSocket.send(jsonObject.toString());
                        }
                    }
                    return;
                }
                if ("quit".equals(msgType)) {
                    // 收到退出房间指令
                    String userId = map.get("userId");
                    WebSocketBean webSocketBean = getWebSocketBeanByWebSocket(conn);
                    // WebSocket 连接解绑 userId
                    if (webSocketBean != null) {
                        webSocketBean.setUserId(null);
                    }
                    // 通知其他用户有其他人退出房间
                    JsonObject jsonObject = new JsonObject();
                    jsonObject.addProperty("msgType", "otherQuit");
                    jsonObject.addProperty("userId", userId);
                    for (WebSocketBean w : mWebSocketBeans) {
                        if (w != webSocketBean && w.getUserId() != null) {
                            w.mWebSocket.send(jsonObject.toString());
                        }
                    }
                    return;
                }
                // 其他消息透传
                // 接收方
                String toUserId = map.get("toUserId");
                // 找到接收方对应 WebSocket 连接
                WebSocketBean webSocketBean = getWebSocketBeanByUserId(toUserId);
                if (webSocketBean != null) {
                    webSocketBean.getWebSocket().send(message);
                }
            }

            @Override
            public void onError(WebSocket conn, Exception ex) {
                ex.printStackTrace();
                System.out.println("onError");
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
        new MultipleWebSocketServerHelper().start();
    }
}
