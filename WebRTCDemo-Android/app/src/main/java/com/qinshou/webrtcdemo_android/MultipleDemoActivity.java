package com.qinshou.webrtcdemo_android;

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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Author: MrQinshou
 * Email: cqflqinhao@126.com
 * Date: 2023/3/21 17:22
 * Description: P2P demo
 */
public class MultipleDemoActivity extends AppCompatActivity {
    private static final String TAG = MultipleDemoActivity.class.getSimpleName();
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
    private WebSocketClientHelper mWebSocketClientHelper = new WebSocketClientHelper();
//    private String mUserId = UUID.randomUUID().toString();
    private String mUserId = "Android";
    private final Map<String, PeerConnection> mPeerConnectionMap = new ConcurrentHashMap<>();
    private final Map<String, SurfaceViewRenderer> mRemoteViewMap = new ConcurrentHashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_multiple_demo);
//        ((EditText) findViewById(R.id.et_server_url)).setText("ws://192.168.1.104:8888");
        ((EditText) findViewById(R.id.et_server_url)).setText("ws://172.16.2.57:8888");
        findViewById(R.id.btn_connect).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String url = ((EditText) findViewById(R.id.et_server_url)).getText().toString().trim();
                mWebSocketClientHelper.connect(url);
            }
        });
        findViewById(R.id.btn_disconnect).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mWebSocketClientHelper.disconnect();
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
        mWebSocketClientHelper.setOnWebSocketListener(new WebSocketClientHelper.OnWebSocketClientListener() {
            @Override
            public void onOpen() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        ((TextView) findViewById(R.id.tv_websocket_state)).setText("WebSocket 已连接");
                    }
                });
            }

            @Override
            public void onClose() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        ((TextView) findViewById(R.id.tv_websocket_state)).setText("WebSocket 已断开");
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
                        String type = jsonObject.optString("type");
                        if (TextUtils.equals("offer", type)) {
                            receivedOffer(jsonObject);
                        } else if (TextUtils.equals("answer", type)) {
                            receivedAnswer(jsonObject);
                        }
                    } else if (TextUtils.equals("iceCandidate", msgType)) {
                        receivedCandidate(jsonObject);
                    } else if (TextUtils.equals("otherJoin", msgType)) {
                        receivedOtherJoin(jsonObject);
                    } else if (TextUtils.equals("otherQuit", msgType)) {
                        receivedOtherQuit(jsonObject);
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });
        // 初始化 PeerConnectionFactory
        initPeerConnectionFactory(MultipleDemoActivity.this);
        // 创建 EglBase
        mEglBase = EglBase.create();
        // 创建 PeerConnectionFactory
        mPeerConnectionFactory = createPeerConnectionFactory(mEglBase);
        // 创建音轨
        mAudioTrack = createAudioTrack(mPeerConnectionFactory);
        // 创建视轨
        mVideoCapturer = createVideoCapturer();
        VideoSource videoSource = createVideoSource(mPeerConnectionFactory, mVideoCapturer);
        mVideoTrack = createVideoTrack(mPeerConnectionFactory, videoSource);
        // 初始化本地视频渲染控件，这个方法非常重要，不初始化会黑屏
        SurfaceViewRenderer svrLocal = findViewById(R.id.svr_local);
        svrLocal.init(mEglBase.getEglBaseContext(), null);
        mVideoTrack.addSink(svrLocal);
        // 开始本地渲染
        // 创建 SurfaceTextureHelper，用来表示 camera 初始化的线程
        SurfaceTextureHelper surfaceTextureHelper = SurfaceTextureHelper.create(SURFACE_TEXTURE_HELPER_THREAD_NAME, mEglBase.getEglBaseContext());
        // 初始化视频采集器
        mVideoCapturer.initialize(surfaceTextureHelper, MultipleDemoActivity.this, videoSource.getCapturerObserver());
        mVideoCapturer.startCapture(WIDTH, HEIGHT, FPS);
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
            mVideoCapturer.dispose();
            mVideoCapturer = null;
        }
        if (mAudioTrack != null) {
            mAudioTrack.dispose();
            mAudioTrack = null;
        }
        if (mVideoTrack != null) {
            mVideoTrack.dispose();
            mVideoTrack = null;
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
        mWebSocketClientHelper.disconnect();
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
        VideoCapturer videoCapturer = null;
        CameraEnumerator cameraEnumerator = new Camera2Enumerator(MultipleDemoActivity.this);
        for (String deviceName : cameraEnumerator.getDeviceNames()) {
            // 前摄像头
            if (cameraEnumerator.isFrontFacing(deviceName)) {
                videoCapturer = new Camera2Capturer(MultipleDemoActivity.this, deviceName, null);
            }
        }
        return videoCapturer;
    }

    private VideoSource createVideoSource(PeerConnectionFactory peerConnectionFactory, VideoCapturer videoCapturer) {
        // 创建视频源
        VideoSource videoSource = peerConnectionFactory.createVideoSource(videoCapturer.isScreencast());
        return videoSource;
    }

    private VideoTrack createVideoTrack(PeerConnectionFactory peerConnectionFactory, VideoSource videoSource) {
        // 创建视轨
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
            }

            @Override
            public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
                ShowLogUtil.debug("onIceConnectionChange--->" + iceConnectionState);
                if (iceConnectionState == PeerConnection.IceConnectionState.DISCONNECTED) {
                    PeerConnection peerConnection = mPeerConnectionMap.get(fromUserId);
                    ShowLogUtil.debug("peerConnection--->" + peerConnection);
                    if (peerConnection != null) {
                        peerConnection.close();
                        mPeerConnectionMap.remove(fromUserId);
                    }
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            SurfaceViewRenderer surfaceViewRenderer = mRemoteViewMap.get(fromUserId);
                            if (surfaceViewRenderer != null) {
                                ((ViewGroup) surfaceViewRenderer.getParent()).removeView(surfaceViewRenderer);
                                mRemoteViewMap.remove(fromUserId);
                            }
                        }
                    });
                }
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
                sendIceCandidate(iceCandidate, fromUserId);
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

    private void join() {
        try {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("msgType", "join");
            jsonObject.put("userId", mUserId);
            mWebSocketClientHelper.send(jsonObject.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void quit() {
        try {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("msgType", "quit");
            jsonObject.put("userId", mUserId);
            mWebSocketClientHelper.send(jsonObject.toString());
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

    private void sendOffer(SessionDescription offer, String toUserId) {
        try {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("msgType", "sdp");
            jsonObject.put("fromUserId", mUserId);
            jsonObject.put("toUserId", toUserId);
            jsonObject.put("type", "offer");
            jsonObject.put("sdp", offer.description);
            mWebSocketClientHelper.send(jsonObject.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void receivedOffer(JSONObject jsonObject) {
        String fromUserId = jsonObject.optString("fromUserId");
        PeerConnection peerConnection = mPeerConnectionMap.get(fromUserId);
        if (peerConnection == null) {
            // 创建 PeerConnection
            peerConnection = createPeerConnection(mPeerConnectionFactory, fromUserId);
            // 为 PeerConnection 添加音轨、视轨
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
        PeerConnection finalPeerConnection = peerConnection;
        // 将 offer sdp 作为参数 setRemoteDescription
        SessionDescription sessionDescription = new SessionDescription(SessionDescription.Type.fromCanonicalForm(type), sdp);
        peerConnection.setRemoteDescription(new MySdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
            }

            @Override
            public void onSetSuccess() {
                ShowLogUtil.debug(fromUserId + " set remote sdp success.");
                // 通过 PeerConnection 创建 answer，获取 sdp
                MediaConstraints mediaConstraints = new MediaConstraints();
                finalPeerConnection.createAnswer(new MySdpObserver() {
                    @Override
                    public void onCreateSuccess(SessionDescription sessionDescription) {
                        ShowLogUtil.verbose(fromUserId + "create answer success.");
                        // 将 answer sdp 作为参数 setLocalDescription
                        finalPeerConnection.setLocalDescription(new MySdpObserver() {
                            @Override
                            public void onCreateSuccess(SessionDescription sessionDescription) {

                            }

                            @Override
                            public void onSetSuccess() {
                                ShowLogUtil.verbose(fromUserId + " set local sdp success.");
                                // 发送 answer sdp
                                sendAnswer(sessionDescription, fromUserId);
                            }
                        }, sessionDescription);
                    }

                    @Override
                    public void onSetSuccess() {

                    }
                }, mediaConstraints);
            }
        }, sessionDescription);
    }

    private void sendAnswer(SessionDescription answer, String toUserId) {
        try {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("msgType", "sdp");
            jsonObject.put("fromUserId", mUserId);
            jsonObject.put("toUserId", toUserId);
            jsonObject.put("type", "answer");
            jsonObject.put("sdp", answer.description);
            mWebSocketClientHelper.send(jsonObject.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void receivedAnswer(JSONObject jsonObject) {
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
        // 收到 answer sdp，将 answer sdp 作为参数 setRemoteDescription
        SessionDescription sessionDescription = new SessionDescription(SessionDescription.Type.fromCanonicalForm(type), sdp);
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

    private void sendIceCandidate(IceCandidate iceCandidate, String toUserId) {
        try {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("msgType", "iceCandidate");
            jsonObject.put("fromUserId", mUserId);
            jsonObject.put("toUserId", toUserId);
            jsonObject.put("id", iceCandidate.sdpMid);
            jsonObject.put("label", iceCandidate.sdpMLineIndex);
            jsonObject.put("candidate", iceCandidate.sdp);
            mWebSocketClientHelper.send(jsonObject.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void receivedCandidate(JSONObject jsonObject) {
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

    private void receivedOtherJoin(JSONObject jsonObject) throws JSONException {
        String userId = jsonObject.optString("userId");
        PeerConnection peerConnection = mPeerConnectionMap.get(userId);
        if (peerConnection == null) {
            // 创建 PeerConnection
            peerConnection = createPeerConnection(mPeerConnectionFactory, userId);
            // 为 PeerConnection 添加音轨、视轨
            peerConnection.addTrack(mAudioTrack, STREAM_IDS);
            peerConnection.addTrack(mVideoTrack, STREAM_IDS);
            mPeerConnectionMap.put(userId, peerConnection);
        }
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
            }
        });
        PeerConnection finalPeerConnection = peerConnection;
        // 通过 PeerConnection 创建 offer，获取 sdp
        MediaConstraints mediaConstraints = new MediaConstraints();
        peerConnection.createOffer(new MySdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                ShowLogUtil.verbose(userId + " create offer success.");
                // 将 offer sdp 作为参数 setLocalDescription
                finalPeerConnection.setLocalDescription(new MySdpObserver() {
                    @Override
                    public void onCreateSuccess(SessionDescription sessionDescription) {

                    }

                    @Override
                    public void onSetSuccess() {
                        ShowLogUtil.verbose(userId + " set local sdp success.");
                        // 发送 offer sdp
                        sendOffer(sessionDescription, userId);
                    }
                }, sessionDescription);
            }

            @Override
            public void onSetSuccess() {

            }
        }, mediaConstraints);
    }

    private void receivedOtherQuit(JSONObject jsonObject) throws JSONException {
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

    public static int dp2px(Context context, float dp) {
        float density = context.getResources().getDisplayMetrics().density;
        return (int) (dp * density + 0.5f);
    }
}