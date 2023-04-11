package com.qinshou.webrtcdemo;

import android.text.TextUtils;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.InetSocketAddress;
import java.util.LinkedList;
import java.util.List;

/**
 * Author: MrQinshou
 * Email: cqflqinhao@126.com
 * Date: 2023/2/8 9:33
 * Description: 类描述
 */
public enum MultipleWebSocketServerHelper {
    SINGLETON;

    public interface OnWebSocketServerListener {
        void onStart();

        void onOpen(int size);

        void onClose(int size);
    }

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
            if (TextUtils.equals(userId, webSocketBean.getUserId())) {
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
                mWebSocketBeans.add(new WebSocketBean(conn));
                mOnWebSocketServerListener.onOpen(mWebSocketBeans.size());
            }

            @Override
            public void onClose(WebSocket conn, int code, String reason, boolean remote) {
                ShowLogUtil.debug("onClose--->" + conn);
                WebSocketBean webSocketBean = removeWebSocketBeanByWebSocket(conn);
                mOnWebSocketServerListener.onClose(mWebSocketBeans.size());
                if (webSocketBean == null) {
                    return;
                }
                try {
                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put("msgType", "otherQuit");
                    jsonObject.put("userId", webSocketBean.mUserId);
                    for (WebSocketBean w : mWebSocketBeans) {
                        if (w != webSocketBean) {
                            w.mWebSocket.send(jsonObject.toString());
                        }
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onMessage(WebSocket conn, String message) {
                ShowLogUtil.debug("onMessage--->" + message);
                try {
                    JSONObject jsonObject = new JSONObject(message);
                    String msgType = jsonObject.optString("msgType");
                    if (TextUtils.equals("join", msgType)) {
                        String userId = jsonObject.optString("userId");
                        WebSocketBean webSocketBean = getWebSocketBeanByWebSocket(conn);
                        if (webSocketBean != null) {
                            webSocketBean.setUserId(userId);
                        }

                        jsonObject = new JSONObject();
                        jsonObject.put("msgType", "otherJoin");
                        jsonObject.put("userId", userId);
                        for (WebSocketBean w : mWebSocketBeans) {
                            if (w != webSocketBean && w.getUserId() != null) {
                                w.mWebSocket.send(jsonObject.toString());
                            }
                        }
                        return;
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                try {
                    JSONObject jsonObject = new JSONObject(message);
                    String msgType = jsonObject.optString("msgType");
                    if (TextUtils.equals("quit", msgType)) {
                        String userId = jsonObject.optString("userId");
                        WebSocketBean webSocketBean = getWebSocketBeanByWebSocket(conn);
                        if (webSocketBean != null) {
                            webSocketBean.setUserId(null);
                        }
                        jsonObject = new JSONObject();
                        jsonObject.put("msgType", "otherQuit");
                        jsonObject.put("userId", userId);
                        for (WebSocketBean w : mWebSocketBeans) {
                            if (w != webSocketBean && w.getUserId() != null) {
                                w.mWebSocket.send(jsonObject.toString());
                            }
                        }
                        return;
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                try {
                    JSONObject jsonObject = new JSONObject(message);
                    String fromUserId = jsonObject.optString("fromUserId");
                    String toUserId = jsonObject.optString("toUserId");
                    WebSocketBean webSocketBean = getWebSocketBeanByUserId(toUserId);
                    if (webSocketBean != null) {
                        webSocketBean.getWebSocket().send(message);
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
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
