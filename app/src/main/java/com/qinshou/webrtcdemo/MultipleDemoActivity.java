package com.qinshou.webrtcdemo;

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.LinearLayoutCompat;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera2Capturer;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.DataChannel;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpReceiver;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoDecoderFactory;
import org.webrtc.VideoEncoderFactory;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Author: qinhao
 * Email: hqin@viazijing.com
 * Date: 2023/3/21 17:23
 * Description: 多端互通 demo
 */
public class MultipleDemoActivity extends AppCompatActivity {
    private static final String TAG = MultipleDemoActivity.class.getSimpleName();
    private static final int DEFAULT_PORT = 8888;
    private static final String AUDIO_TRACK_ID = "ARDAMSa0";
    private static final String VIDEO_TRACK_ID = "ARDAMSv0";
    private static final List<String> STREAM_IDS = new ArrayList<String>() {{
        add("ARDAMS");
    }};
    private static final String SURFACE_TEXTURE_HELPER_THREAD_NAME = "SurfaceTextureHelperThread";
    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    private static final int FPS = 30;

    private EglBase mEglBase;
    private PeerConnectionFactory mPeerConnectionFactory;
    private VideoCapturer mVideoCapturer;
    private AudioTrack mAudioTrack;
    private VideoTrack mVideoTrack;
    private String mUserId = UUID.randomUUID().toString();
    private final Map<String, PeerConnection> mPeerConnectionMap = new ConcurrentHashMap<>();
    private final Map<String, SurfaceViewRenderer> mRemoteViewMap = new ConcurrentHashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_multiple_demo);
        ((TextView) findViewById(R.id.tv_local_ip)).setText("本机 ip：" + IpUtil.ipAddress2String(IpUtil.getIpAddress(this)));
        ((EditText) findViewById(R.id.et_server_url)).setText("ws://" + IpUtil.ipAddress2String(IpUtil.getIpAddress(this)) + ":" + DEFAULT_PORT);
        findViewById(R.id.btn_start_server).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startWebSocketServer();
            }
        });
        findViewById(R.id.btn_start_client).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startWebSocketClient();
            }
        });
        findViewById(R.id.btn_join).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                join();
            }
        });
        findViewById(R.id.btn_quit).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                quit();
            }
        });
        // 1.初始化 PeerConnectionFactory
        initPeerConnectionFactory(MultipleDemoActivity.this);
        // 2.创建 EglBase
        mEglBase = EglBase.create();
        // 3.创建 PeerConnectionFactory
        mPeerConnectionFactory = createPeerConnectionFactory(mEglBase);
        // 4.创建音轨
        mAudioTrack = createAudioTrack(mPeerConnectionFactory);
        // 5.创建视轨
        mVideoCapturer = createVideoCapturer();
        mVideoTrack = createVideoTrack(mPeerConnectionFactory, mEglBase, mVideoCapturer);
        // 初始化 SurfaceViewRender ，这个方法非常重要，不初始化黑屏
        SurfaceViewRenderer svrLocal = findViewById(R.id.svr_local);
        svrLocal.init(mEglBase.getEglBaseContext(), null);
        // 添加渲染器到视轨中，画面开始呈现
        mVideoTrack.addSink(svrLocal);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mEglBase != null) {
            mEglBase.release();
            mEglBase = null;
        }
        if (mVideoCapturer != null) {
            try {
                mVideoCapturer.stopCapture();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            mVideoCapturer = null;
        }
        for (PeerConnection peerConnection : mPeerConnectionMap.values()) {
            peerConnection.close();
            peerConnection.dispose();
        }
        mPeerConnectionMap.clear();
        SurfaceViewRenderer svrLocal = findViewById(R.id.svr_local);
        svrLocal.release();
        for (SurfaceViewRenderer surfaceViewRenderer : mRemoteViewMap.values()) {
            surfaceViewRenderer.release();
        }
        mRemoteViewMap.clear();
        WebSocketClientHelper.SINGLETON.close();
        MultipleWebSocketServerHelper.SINGLETON.stop();
    }

    private void initPeerConnectionFactory(Context context) {
        PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(context).createInitializationOptions());
    }

    private PeerConnectionFactory createPeerConnectionFactory(EglBase eglBase) {
        VideoEncoderFactory videoEncoderFactory = new DefaultVideoEncoderFactory(eglBase.getEglBaseContext(), true, true);
        VideoDecoderFactory videoDecoderFactory = new DefaultVideoDecoderFactory(eglBase.getEglBaseContext());
        return PeerConnectionFactory.builder().setVideoEncoderFactory(videoEncoderFactory).setVideoDecoderFactory(videoDecoderFactory).createPeerConnectionFactory();
    }

    private AudioTrack createAudioTrack(PeerConnectionFactory peerConnectionFactory) {
        AudioSource audioSource = peerConnectionFactory.createAudioSource(new MediaConstraints());
        AudioTrack audioTrack = peerConnectionFactory.createAudioTrack(AUDIO_TRACK_ID, audioSource);
        audioTrack.setEnabled(true);
        return audioTrack;
    }

    private VideoCapturer createVideoCapturer() {
        // 获取前摄像头
        VideoCapturer videoCapturer = null;
        CameraEnumerator cameraEnumerator = new Camera2Enumerator(MultipleDemoActivity.this);
        for (String deviceName : cameraEnumerator.getDeviceNames()) {
            if (cameraEnumerator.isFrontFacing(deviceName)) {
                videoCapturer = new Camera2Capturer(MultipleDemoActivity.this, deviceName, null);
            }
        }
        return videoCapturer;
    }

    private VideoTrack createVideoTrack(PeerConnectionFactory peerConnectionFactory, EglBase eglBase, VideoCapturer videoCapturer) {
        // 创建视频源
        VideoSource videoSource = peerConnectionFactory.createVideoSource(videoCapturer.isScreencast());
        // Android start.
        // 创建 SurfaceTextureHelper，用来表示 camera 初始化的线程
        SurfaceTextureHelper surfaceTextureHelper = SurfaceTextureHelper.create(SURFACE_TEXTURE_HELPER_THREAD_NAME, eglBase.getEglBaseContext());
        // 初始化视频采集器
        videoCapturer.initialize(surfaceTextureHelper, MultipleDemoActivity.this, videoSource.getCapturerObserver());
        // Android end.
        // 开始采集
        videoCapturer.startCapture(WIDTH, HEIGHT, FPS);
        // 添加视频轨道
        VideoTrack videoTrack = peerConnectionFactory.createVideoTrack(VIDEO_TRACK_ID, videoSource);
        videoTrack.setEnabled(true);
        return videoTrack;
    }

    private PeerConnection createPeerConnection(PeerConnectionFactory peerConnectionFactory, String fromUserId) {
        // 内部会转成 RTCConfiguration
        List<PeerConnection.IceServer> iceServers = new ArrayList<>();
        PeerConnection peerConnection = peerConnectionFactory.createPeerConnection(iceServers, new PeerConnection.Observer() {
            @Override
            public void onSignalingChange(PeerConnection.SignalingState signalingState) {
                ShowLogUtil.debug("onSignalingChange--->" + signalingState);
            }

            @Override
            public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
                ShowLogUtil.debug("onIceConnectionChange--->" + iceConnectionState);
//                if (iceConnectionState == PeerConnection.IceConnectionState.DISCONNECTED) {
//                    PeerConnection peerConnection = mPeerConnectionMap.get(fromUserId);
//                    ShowLogUtil.debug("peerConnection--->" + peerConnection);
//                    if (peerConnection != null) {
//                        peerConnection.close();
//                        mPeerConnectionMap.remove(fromUserId);
//                    }
//                    runOnUiThread(new Runnable() {
//                        @Override
//                        public void run() {
//                            SurfaceViewRenderer surfaceViewRenderer = mRemoteViewMap.get(fromUserId);
//                            if (surfaceViewRenderer != null) {
//                                ((ViewGroup) surfaceViewRenderer.getParent()).removeView(surfaceViewRenderer);
//                                mRemoteViewMap.remove(fromUserId);
//                            }
//                        }
//                    });
//                }
            }

            @Override
            public void onIceConnectionReceivingChange(boolean b) {

            }

            @Override
            public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {

            }

            @Override
            public void onIceCandidate(IceCandidate iceCandidate) {
                ShowLogUtil.verbose("onIceCandidate--->" + iceCandidate);
                // 传递给 Remote 添加 IceCandidate
                try {
                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put("msgType", "iceCandidate");
                    jsonObject.put("fromUserId", mUserId);
                    jsonObject.put("toUserId", fromUserId);
                    jsonObject.put("id", iceCandidate.sdpMid);
                    jsonObject.put("label", iceCandidate.sdpMLineIndex);
                    jsonObject.put("candidate", iceCandidate.sdp);
                    WebSocketClientHelper.SINGLETON.send(jsonObject.toString());
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {

            }

            @Override
            public void onAddStream(MediaStream mediaStream) {
                ShowLogUtil.verbose("onAddStream--->" + mediaStream);
                if (mediaStream == null || mediaStream.videoTracks == null || mediaStream.videoTracks.isEmpty()) {
                    return;
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        SurfaceViewRenderer surfaceViewRenderer = mRemoteViewMap.get(fromUserId);
                        if (surfaceViewRenderer != null) {
                            mediaStream.videoTracks.get(0).addSink(surfaceViewRenderer);
                        }
                    }
                });
            }

            @Override
            public void onRemoveStream(MediaStream mediaStream) {
            }

            @Override
            public void onDataChannel(DataChannel dataChannel) {

            }

            @Override
            public void onRenegotiationNeeded() {

            }

            @Override
            public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {

            }
        });
        return peerConnection;
    }

    /**
     * Author: MrQinshou
     * Email: cqflqinhao@126.com
     * Date: 2023/2/8 15:29
     * Description: 创建 offer，发起呼叫
     */
    private void createOffer(PeerConnection peerConnection, String fromUserId) {
        MediaConstraints mediaConstraints = new MediaConstraints();
        peerConnection.createOffer(new MySdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                ShowLogUtil.verbose(fromUserId + " create offer success.");
                peerConnection.setLocalDescription(new MySdpObserver() {
                    @Override
                    public void onCreateSuccess(SessionDescription sessionDescription) {

                    }

                    @Override
                    public void onSetSuccess() {
                        ShowLogUtil.verbose(fromUserId + " set local sdp success.");
                        try {
                            JSONObject jsonObject = new JSONObject();
                            jsonObject.put("msgType", "sdp");
                            jsonObject.put("fromUserId", mUserId);
                            jsonObject.put("toUserId", fromUserId);
                            jsonObject.put("type", "offer");
                            jsonObject.put("sdp", sessionDescription.description);
                            WebSocketClientHelper.SINGLETON.send(jsonObject.toString());
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                }, sessionDescription);
            }

            @Override
            public void onSetSuccess() {

            }
        }, mediaConstraints);
    }

    /**
     * Author: MrQinshou
     * Email: cqflqinhao@126.com
     * Date: 2023/2/8 15:29
     * Description: 通过 RemotePeerConnection 创建 answer，返回应答
     */
    private void createAnswer(PeerConnection peerConnection, String fromUserId) {
        MediaConstraints mediaConstraints = new MediaConstraints();
        peerConnection.createAnswer(new MySdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                ShowLogUtil.verbose(fromUserId + " create answer success.");
                peerConnection.setLocalDescription(new MySdpObserver() {
                    @Override
                    public void onCreateSuccess(SessionDescription sessionDescription) {

                    }

                    @Override
                    public void onSetSuccess() {
                        ShowLogUtil.verbose(fromUserId + " set local sdp success.");
                        try {
                            JSONObject jsonObject = new JSONObject();
                            jsonObject.put("msgType", "sdp");
                            jsonObject.put("fromUserId", mUserId);
                            jsonObject.put("toUserId", fromUserId);
                            jsonObject.put("type", "answer");
                            jsonObject.put("sdp", sessionDescription.description);
                            WebSocketClientHelper.SINGLETON.send(jsonObject.toString());
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }

                    }
                }, sessionDescription);
            }

            @Override
            public void onSetSuccess() {

            }
        }, mediaConstraints);
    }

    private void join() {
        try {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("msgType", "join");
            jsonObject.put("userId", mUserId);
            WebSocketClientHelper.SINGLETON.send(jsonObject.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void quit() {
        try {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("msgType", "quit");
            jsonObject.put("userId", mUserId);
            WebSocketClientHelper.SINGLETON.send(jsonObject.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                for (PeerConnection peerConnection : mPeerConnectionMap.values()) {
                    peerConnection.close();
                }
                mPeerConnectionMap.clear();
            }
        }).start();
        for (SurfaceViewRenderer surfaceViewRenderer : mRemoteViewMap.values()) {
            ((ViewGroup) surfaceViewRenderer.getParent()).removeView(surfaceViewRenderer);
        }
        mRemoteViewMap.clear();
    }

    private void startWebSocketServer() {
        TextView tvWebSocketState = findViewById(R.id.tv_web_socket_state);
        MultipleWebSocketServerHelper.SINGLETON.setOnWebSocketListener(new MultipleWebSocketServerHelper.OnWebSocketServerListener() {
            @Override
            public void onStart() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        tvWebSocketState.setText("WebSocketServer 已开启");
                    }
                });
            }

            @Override
            public void onOpen(int size) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        tvWebSocketState.setText("WebSocketServer 已开启，当前客户端连接数：" + size);
                    }
                });
            }

            @Override
            public void onClose(int size) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        tvWebSocketState.setText("WebSocketServer 已开启，当前客户端连接数：" + size);
                    }
                });
            }
        });
        String url = ((EditText) findViewById(R.id.et_server_url)).getText().toString().trim();
        MultipleWebSocketServerHelper.SINGLETON.start(url);
    }

    private void startWebSocketClient() {
        TextView tvWebSocketState = findViewById(R.id.tv_web_socket_state);
        WebSocketClientHelper.SINGLETON.setOnWebSocketListener(new WebSocketClientHelper.OnWebSocketClientListener() {
            @Override
            public void onOpen() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        tvWebSocketState.setText("WebSocketClient 已开启");
                    }
                });
            }

            @Override
            public void onMessage(String message) {
                ShowLogUtil.debug("message--->" + message);
                try {
                    JSONObject jsonObject = new JSONObject(message);
                    String msgType = jsonObject.optString("msgType");
                    if (TextUtils.equals("sdp", msgType)) {
                        handleSdp(jsonObject);
                    } else if (TextUtils.equals("iceCandidate", msgType)) {
                        handleIceCandidate(jsonObject);
                    } else if (TextUtils.equals("otherJoin", msgType)) {
                        handleOtherJoin(jsonObject);
                    } else if (TextUtils.equals("otherQuit", msgType)) {
                        handleOtherQuit(jsonObject);
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });
        String url = ((EditText) findViewById(R.id.et_server_url)).getText().toString().trim();
        WebSocketClientHelper.SINGLETON.connect(url);
    }

    private void handleSdp(JSONObject jsonObject) throws JSONException {
        String fromUserId = jsonObject.optString("fromUserId");
        PeerConnection peerConnection = mPeerConnectionMap.get(fromUserId);
        if (peerConnection == null) {
            peerConnection = createPeerConnection(mPeerConnectionFactory, fromUserId);
            peerConnection.addTrack(mAudioTrack, STREAM_IDS);
            peerConnection.addTrack(mVideoTrack, STREAM_IDS);
            mPeerConnectionMap.put(fromUserId, peerConnection);
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                SurfaceViewRenderer surfaceViewRenderer = mRemoteViewMap.get(fromUserId);
                if (surfaceViewRenderer == null) {
                    // 初始化 SurfaceViewRender ，这个方法非常重要，不初始化黑屏
                    surfaceViewRenderer = new SurfaceViewRenderer(MultipleDemoActivity.this);
                    surfaceViewRenderer.init(mEglBase.getEglBaseContext(), null);
                    surfaceViewRenderer.setLayoutParams(new LinearLayout.LayoutParams(dp2px(MultipleDemoActivity.this, 90), dp2px(MultipleDemoActivity.this, 160)));
                    LinearLayoutCompat llRemotes = findViewById(R.id.ll_remotes);
                    llRemotes.addView(surfaceViewRenderer);
                    mRemoteViewMap.put(fromUserId, surfaceViewRenderer);
                }
            }
        });
        String type = jsonObject.optString("type");
        String sdp = jsonObject.optString("sdp");
        SessionDescription sessionDescription = new SessionDescription(SessionDescription.Type.fromCanonicalForm(type), sdp);
        if (TextUtils.equals("offer", type)) {
            PeerConnection finalPeerConnection = peerConnection;
            peerConnection.setRemoteDescription(new MySdpObserver() {
                @Override
                public void onCreateSuccess(SessionDescription sessionDescription) {
                }

                @Override
                public void onSetSuccess() {
                    ShowLogUtil.debug(fromUserId + " set remote sdp success.");
                    createAnswer(finalPeerConnection, fromUserId);
                }
            }, sessionDescription);
        } else if (TextUtils.equals("answer", type)) {
            peerConnection.setRemoteDescription(new MySdpObserver() {
                @Override
                public void onCreateSuccess(SessionDescription sessionDescription) {
                }

                @Override
                public void onSetSuccess() {
                    ShowLogUtil.debug(fromUserId + " set remote sdp success.");
                }
            }, sessionDescription);
        }
    }

    private void handleIceCandidate(JSONObject jsonObject) throws JSONException {
        String fromUserId = jsonObject.optString("fromUserId");
        PeerConnection peerConnection = mPeerConnectionMap.get(fromUserId);
        if (peerConnection == null) {
            return;
        }
        String id = jsonObject.optString("id");
        int label = jsonObject.optInt("label");
        String candidate = jsonObject.optString("candidate");
        IceCandidate iceCandidate = new IceCandidate(id, label, candidate);
        peerConnection.addIceCandidate(iceCandidate);
    }

    private void handleOtherJoin(JSONObject jsonObject) throws JSONException {
        String userId = jsonObject.optString("userId");
        PeerConnection peerConnection = mPeerConnectionMap.get(userId);
        if (peerConnection == null) {
            peerConnection = createPeerConnection(mPeerConnectionFactory, userId);
            peerConnection.addTrack(mAudioTrack, STREAM_IDS);
            peerConnection.addTrack(mVideoTrack, STREAM_IDS);
            mPeerConnectionMap.put(userId, peerConnection);
        }
        PeerConnection finalPeerConnection = peerConnection;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                SurfaceViewRenderer surfaceViewRenderer = mRemoteViewMap.get(userId);
                if (surfaceViewRenderer == null) {
                    // 初始化 SurfaceViewRender ，这个方法非常重要，不初始化黑屏
                    surfaceViewRenderer = new SurfaceViewRenderer(MultipleDemoActivity.this);
                    surfaceViewRenderer.init(mEglBase.getEglBaseContext(), null);
                    surfaceViewRenderer.setLayoutParams(new LinearLayout.LayoutParams(dp2px(MultipleDemoActivity.this, 90), dp2px(MultipleDemoActivity.this, 160)));
                    LinearLayoutCompat llRemotes = findViewById(R.id.ll_remotes);
                    llRemotes.addView(surfaceViewRenderer);
                    mRemoteViewMap.put(userId, surfaceViewRenderer);
                }
                createOffer(finalPeerConnection, userId);
            }
        });
    }

    private void handleOtherQuit(JSONObject jsonObject) throws JSONException {
        String userId = jsonObject.optString("userId");
        PeerConnection peerConnection = mPeerConnectionMap.get(userId);
        if (peerConnection != null) {
            peerConnection.close();
            mPeerConnectionMap.remove(userId);
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                SurfaceViewRenderer surfaceViewRenderer = mRemoteViewMap.get(userId);
                if (surfaceViewRenderer != null) {
                    ((ViewGroup) surfaceViewRenderer.getParent()).removeView(surfaceViewRenderer);
                    mRemoteViewMap.remove(userId);
                }
            }
        });
    }

    /**
     * Author: MrQinshou
     * Email: cqflqinhao@126.com
     * Date: 2020/11/18 17:53
     * Description: dp 转为 px
     */
    public static int dp2px(Context context, float dp) {
        float density = context.getResources().getDisplayMetrics().density;
        return (int) (dp * density + 0.5f);
    }
}