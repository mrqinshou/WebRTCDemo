package com.qinshou.webrtcdemo;

import android.content.Context;
import android.os.Bundle;
import android.view.SurfaceHolder;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

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
import org.webrtc.RendererCommon;
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

/**
 * Author: MrQinshou
 * Email: cqflqinhao@126.com
 * Date: 2023/3/21 17:22
 * Description: 本地 demo
 */
public class LocalDemoActivity extends AppCompatActivity {
    private static final String TAG = LocalDemoActivity.class.getSimpleName();
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
    private PeerConnection mLocalPeerConnection;
    private PeerConnection mRemotePeerConnection;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_local_demo);
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
        // 1.初始化 PeerConnectionFactory
        initPeerConnectionFactory(LocalDemoActivity.this);
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
        if (mLocalPeerConnection != null) {
            mLocalPeerConnection.close();
            mLocalPeerConnection = null;
        }
        if (mRemotePeerConnection != null) {
            mRemotePeerConnection.close();
            mRemotePeerConnection = null;
        }
        SurfaceViewRenderer svrLocal = findViewById(R.id.svr_local);
        svrLocal.release();
        SurfaceViewRenderer svrRemote = findViewById(R.id.svr_remote);
        svrRemote.release();
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
        CameraEnumerator cameraEnumerator = new Camera2Enumerator(LocalDemoActivity.this);
        for (String deviceName : cameraEnumerator.getDeviceNames()) {
            if (cameraEnumerator.isFrontFacing(deviceName)) {
                videoCapturer = new Camera2Capturer(LocalDemoActivity.this, deviceName, null);
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
        videoCapturer.initialize(surfaceTextureHelper, LocalDemoActivity.this, videoSource.getCapturerObserver());
        // Android end.
        // 开始采集
        videoCapturer.startCapture(WIDTH, HEIGHT, FPS);
        // 添加视频轨道
        VideoTrack videoTrack = peerConnectionFactory.createVideoTrack(VIDEO_TRACK_ID, videoSource);
        videoTrack.setEnabled(true);
        return videoTrack;
    }

    private PeerConnection createLocalPeerConnection(PeerConnectionFactory peerConnectionFactory) {
        // 内部会转成 RTCConfiguration
        List<PeerConnection.IceServer> iceServers = new ArrayList<>();
        PeerConnection peerConnection = peerConnectionFactory.createPeerConnection(iceServers, new PeerConnection.Observer() {
            @Override
            public void onSignalingChange(PeerConnection.SignalingState signalingState) {

            }

            @Override
            public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {

            }

            @Override
            public void onIceConnectionReceivingChange(boolean b) {

            }

            @Override
            public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {

            }

            @Override
            public void onIceCandidate(IceCandidate iceCandidate) {
                ShowLogUtil.verbose("local onIceCandidate--->" + iceCandidate);
                // 传递给 Remote 添加 IceCandidate
                mRemotePeerConnection.addIceCandidate(iceCandidate);
            }

            @Override
            public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {

            }

            @Override
            public void onAddStream(MediaStream mediaStream) {
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

    private PeerConnection createRemoteConnection(PeerConnectionFactory peerConnectionFactory) {
        // 内部会转成 RTCConfiguration
        List<PeerConnection.IceServer> iceServers = new ArrayList<>();
        PeerConnection peerConnection = peerConnectionFactory.createPeerConnection(iceServers, new PeerConnection.Observer() {
            @Override
            public void onSignalingChange(PeerConnection.SignalingState signalingState) {

            }

            @Override
            public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {

            }

            @Override
            public void onIceConnectionReceivingChange(boolean b) {

            }

            @Override
            public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {

            }

            @Override
            public void onIceCandidate(IceCandidate iceCandidate) {
                ShowLogUtil.verbose("remote onIceCandidate--->" + iceCandidate);
                // 传递给 Local 添加 IceCandidate
                mLocalPeerConnection.addIceCandidate(iceCandidate);
            }

            @Override
            public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {

            }

            @Override
            public void onAddStream(MediaStream mediaStream) {
                ShowLogUtil.verbose("remote onAddStream--->" + mediaStream);
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
     * Description: 通过 LocalPeerConnection 创建 offer，发起呼叫
     */
    private void createOffer() {
        MediaConstraints mediaConstraints = new MediaConstraints();
        mLocalPeerConnection.createOffer(new MySdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                ShowLogUtil.verbose("local create offer success.");
                mLocalPeerConnection.setLocalDescription(new MySdpObserver() {
                    @Override
                    public void onCreateSuccess(SessionDescription sessionDescription) {

                    }

                    @Override
                    public void onSetSuccess() {
                        ShowLogUtil.verbose("local set local sdp success.");
                        mRemotePeerConnection.setRemoteDescription(new MySdpObserver() {
                            @Override
                            public void onCreateSuccess(SessionDescription sessionDescription) {

                            }

                            @Override
                            public void onSetSuccess() {
                                ShowLogUtil.verbose("remote set remote sdp success.");
                                createAnswer();
                            }
                        }, sessionDescription);
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
    private void createAnswer() {
        MediaConstraints mediaConstraints = new MediaConstraints();
        mRemotePeerConnection.createAnswer(new MySdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                ShowLogUtil.verbose("remote create answer success.");
                mRemotePeerConnection.setLocalDescription(new MySdpObserver() {
                    @Override
                    public void onCreateSuccess(SessionDescription sessionDescription) {

                    }

                    @Override
                    public void onSetSuccess() {
                        ShowLogUtil.verbose("remote set local sdp success.");
                        mLocalPeerConnection.setRemoteDescription(new MySdpObserver() {
                            @Override
                            public void onCreateSuccess(SessionDescription sessionDescription) {

                            }

                            @Override
                            public void onSetSuccess() {
                                ShowLogUtil.verbose("local set remote sdp success.");
                            }
                        }, sessionDescription);

                    }
                }, sessionDescription);
            }

            @Override
            public void onSetSuccess() {

            }
        }, mediaConstraints);
    }

    private void call() {
        // 创建 localPeerConnection
        mLocalPeerConnection = createLocalPeerConnection(mPeerConnectionFactory);
        mLocalPeerConnection.addTrack(mAudioTrack, STREAM_IDS);
        mLocalPeerConnection.addTrack(mVideoTrack, STREAM_IDS);
        // 创建 remotePeerConnection
        mRemotePeerConnection = createRemoteConnection(mPeerConnectionFactory);
        createOffer();
    }

    private void hangUp() {
        if (mLocalPeerConnection != null) {
            mLocalPeerConnection.close();
            mLocalPeerConnection.dispose();
            mLocalPeerConnection = null;
        }
        if (mRemotePeerConnection != null) {
            mRemotePeerConnection.close();
            mRemotePeerConnection.dispose();
            mRemotePeerConnection = null;
        }
        SurfaceViewRenderer svrRemote = findViewById(R.id.svr_remote);
        svrRemote.clearImage();
    }
}