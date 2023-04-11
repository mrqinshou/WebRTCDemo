package com.qinshou.webrtcdemo;

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera2Capturer;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.DataChannel;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.FileVideoCapturer;
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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Author: MrQinshou
 * Email: cqflqinhao@126.com
 * Date: 2023/3/21 17:23
 * Description: 点对点互通 demo
 */
public class CustomDemoActivity extends AppCompatActivity {
    private static final String TAG = CustomDemoActivity.class.getSimpleName();
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
    private PeerConnection mPeerConnection;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_custom_demo);
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
        findViewById(R.id.btn_call).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                call();
            }
        });
        findViewById(R.id.btn_hang_up).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                hangUp();
            }
        });
        findViewById(R.id.btn_on_off_mic).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mAudioTrack.enabled()) {
                    mAudioTrack.setEnabled(false);
                    ((Button) findViewById(R.id.btn_on_off_mic)).setText("开启 mic");
                } else {
                    mAudioTrack.setEnabled(true);
                    ((Button) findViewById(R.id.btn_on_off_mic)).setText("关闭 mic");
                }
            }
        });
        findViewById(R.id.btn_on_off_camera).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mVideoTrack.enabled()) {
                    mVideoTrack.setEnabled(false);
                    ((Button) findViewById(R.id.btn_on_off_camera)).setText("开启 camera");
                } else {
                    mVideoTrack.setEnabled(true);
                    ((Button) findViewById(R.id.btn_on_off_camera)).setText("关闭 camera");
                }
            }
        });
        findViewById(R.id.btn_switch_camera).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!(mVideoCapturer instanceof Camera2Capturer)) {
                    return;
                }
                ((Camera2Capturer) mVideoCapturer).switchCamera(new CameraVideoCapturer.CameraSwitchHandler() {
                    @Override
                    public void onCameraSwitchDone(boolean b) {
                        ShowLogUtil.info("onCameraSwitchDone--->" + b);
                    }

                    @Override
                    public void onCameraSwitchError(String s) {
                        ShowLogUtil.error("onCameraSwitchError--->" + s);
                    }
                });
            }
        });
        // 1.初始化 PeerConnectionFactory
        initPeerConnectionFactory(CustomDemoActivity.this);
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
        // 初始化 SurfaceViewRender ，这个方法非常重要，不初始化黑屏
        SurfaceViewRenderer svrRemote = findViewById(R.id.svr_remote);
        svrRemote.init(mEglBase.getEglBaseContext(), null);
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
        if (mPeerConnection != null) {
            mPeerConnection.close();
            mPeerConnection.dispose();
            mPeerConnection = null;
        }
        SurfaceViewRenderer svrLocal = findViewById(R.id.svr_local);
        svrLocal.release();
        SurfaceViewRenderer svrRemote = findViewById(R.id.svr_remote);
        svrRemote.release();
        WebSocketClientHelper.SINGLETON.close();
        WebSocketServerHelper.SINGLETON.stop();
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
//        // 获取前摄像头
//        CameraEnumerator cameraEnumerator = new Camera2Enumerator(CustomDemoActivity.this);
//        for (String deviceName : cameraEnumerator.getDeviceNames()) {
//            if (cameraEnumerator.isFrontFacing(deviceName)) {
//                videoCapturer = new Camera2Capturer(CustomDemoActivity.this, deviceName, null);
//            }
//        }
        File file = new File(getCacheDir(), "sample.yuv");
        try {
            videoCapturer = new FileVideoCapturer(file.getAbsolutePath());
        } catch (IOException e) {
            ShowLogUtil.error(e.getMessage());
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
        videoCapturer.initialize(surfaceTextureHelper, CustomDemoActivity.this, videoSource.getCapturerObserver());
        // Android end.
        // 开始采集
        videoCapturer.startCapture(WIDTH, HEIGHT, FPS);
        // 添加视频轨道
        VideoTrack videoTrack = peerConnectionFactory.createVideoTrack(VIDEO_TRACK_ID, videoSource);
        videoTrack.setEnabled(true);
        return videoTrack;
    }

    private PeerConnection createPeerConnection(PeerConnectionFactory peerConnectionFactory) {
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
                if (iceConnectionState == PeerConnection.IceConnectionState.DISCONNECTED) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            hangUp();
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
                // 传递给 Remote 添加 IceCandidate
                try {
                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put("msgType", "iceCandidate");
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
                        SurfaceViewRenderer svrRemote = findViewById(R.id.svr_remote);
                        mediaStream.videoTracks.get(0).addSink(svrRemote);
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
    private void createOffer() {
        MediaConstraints mediaConstraints = new MediaConstraints();
        mPeerConnection.createOffer(new MySdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                ShowLogUtil.verbose("create offer success.");
                mPeerConnection.setLocalDescription(new MySdpObserver() {
                    @Override
                    public void onCreateSuccess(SessionDescription sessionDescription) {

                    }

                    @Override
                    public void onSetSuccess() {
                        ShowLogUtil.verbose("set local sdp success.");
                        try {
                            JSONObject jsonObject = new JSONObject();
                            jsonObject.put("msgType", "sdp");
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
     * Description: 创建 answer，返回应答
     */
    private void createAnswer() {
        MediaConstraints mediaConstraints = new MediaConstraints();
        mPeerConnection.createAnswer(new MySdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                ShowLogUtil.verbose("create answer success.");
                mPeerConnection.setLocalDescription(new MySdpObserver() {
                    @Override
                    public void onCreateSuccess(SessionDescription sessionDescription) {

                    }

                    @Override
                    public void onSetSuccess() {
                        ShowLogUtil.verbose("set local sdp success.");
                        try {
                            JSONObject jsonObject = new JSONObject();
                            jsonObject.put("msgType", "sdp");
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

    private void call() {
        // 创建 PeerConnection
        mPeerConnection = createPeerConnection(mPeerConnectionFactory);
        mPeerConnection.addTrack(mAudioTrack, STREAM_IDS);
        mPeerConnection.addTrack(mVideoTrack, STREAM_IDS);
        createOffer();
    }

    private void hangUp() {
        if (mPeerConnection != null) {
            mPeerConnection.close();
            mPeerConnection = null;
        }
        SurfaceViewRenderer svrRemote = findViewById(R.id.svr_remote);
        svrRemote.clearImage();
    }

    private void startWebSocketServer() {
        TextView tvWebSocketState = findViewById(R.id.tv_web_socket_state);
        WebSocketServerHelper.SINGLETON.setOnWebSocketListener(new WebSocketServerHelper.OnWebSocketServerListener() {
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
        WebSocketServerHelper.SINGLETON.start(url);
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
                if (mPeerConnection == null) {
                    mPeerConnection = createPeerConnection(mPeerConnectionFactory);
                    mPeerConnection.addTrack(mAudioTrack, STREAM_IDS);
                    mPeerConnection.addTrack(mVideoTrack, STREAM_IDS);
                }
                try {
                    JSONObject jsonObject = new JSONObject(message);
                    String msgType = jsonObject.optString("msgType");
                    if (TextUtils.equals("sdp", msgType)) {
                        handleSdp(jsonObject);
                    } else if (TextUtils.equals("iceCandidate", msgType)) {
                        handleIceCandidate(jsonObject);
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
        String type = jsonObject.optString("type");
        String sdp = jsonObject.optString("sdp");
        SessionDescription sessionDescription = new SessionDescription(SessionDescription.Type.fromCanonicalForm(type), sdp);
        if (TextUtils.equals("offer", type)) {
            mPeerConnection.setRemoteDescription(new MySdpObserver() {
                @Override
                public void onCreateSuccess(SessionDescription sessionDescription) {
                }

                @Override
                public void onSetSuccess() {
                    ShowLogUtil.debug("set remote sdp success.");
                    createAnswer();
                }
            }, sessionDescription);
        } else if (TextUtils.equals("answer", type)) {
            mPeerConnection.setRemoteDescription(new MySdpObserver() {
                @Override
                public void onCreateSuccess(SessionDescription sessionDescription) {
                }

                @Override
                public void onSetSuccess() {
                    ShowLogUtil.debug("set remote sdp success.");
                }
            }, sessionDescription);
        }
    }

    private void handleIceCandidate(JSONObject jsonObject) throws JSONException {
        String id = jsonObject.optString("id");
        int label = jsonObject.optInt("label");
        String candidate = jsonObject.optString("candidate");
        IceCandidate iceCandidate = new IceCandidate(id, label, candidate);
        mPeerConnection.addIceCandidate(iceCandidate);
    }

}