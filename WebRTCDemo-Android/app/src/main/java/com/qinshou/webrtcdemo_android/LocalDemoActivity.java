package com.qinshou.webrtcdemo_android;

import android.content.Context;
import android.os.Bundle;
import android.view.View;

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
        // 初始化 PeerConnectionFactory
        initPeerConnectionFactory(LocalDemoActivity.this);
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
        // 初始化远端视频渲染控件，这个方法非常重要，不初始化会黑屏
        SurfaceViewRenderer svrRemote = findViewById(R.id.svr_remote);
        svrRemote.init(mEglBase.getEglBaseContext(), null);
        // 开始本地渲染
        // 创建 SurfaceTextureHelper，用来表示 camera 初始化的线程
        SurfaceTextureHelper surfaceTextureHelper = SurfaceTextureHelper.create(SURFACE_TEXTURE_HELPER_THREAD_NAME, mEglBase.getEglBaseContext());
        // 初始化视频采集器
        mVideoCapturer.initialize(surfaceTextureHelper, LocalDemoActivity.this, videoSource.getCapturerObserver());
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
        VideoCapturer videoCapturer = null;
        CameraEnumerator cameraEnumerator = new Camera2Enumerator(LocalDemoActivity.this);
        for (String deviceName : cameraEnumerator.getDeviceNames()) {
            // 前摄像头
            if (cameraEnumerator.isFrontFacing(deviceName)) {
                videoCapturer = new Camera2Capturer(LocalDemoActivity.this, deviceName, null);
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

    private void call() {
        // 创建 PeerConnection
        PeerConnection.RTCConfiguration rtcConfiguration = new PeerConnection.RTCConfiguration(new ArrayList<>());
        mLocalPeerConnection = mPeerConnectionFactory.createPeerConnection(rtcConfiguration, new PeerConnection.Observer() {
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
                ShowLogUtil.verbose("onIceCandidate--->" + iceCandidate);
                sendIceCandidate(mLocalPeerConnection, iceCandidate);
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
        // 为 PeerConnection 添加音轨、视轨
        mLocalPeerConnection.addTrack(mAudioTrack, STREAM_IDS);
        mLocalPeerConnection.addTrack(mVideoTrack, STREAM_IDS);
        // 通过 PeerConnection 创建 offer，获取 sdp
        MediaConstraints mediaConstraints = new MediaConstraints();
        mLocalPeerConnection.createOffer(new MySdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                ShowLogUtil.verbose("create offer success.");
                // 将 offer sdp 作为参数 setLocalDescription
                mLocalPeerConnection.setLocalDescription(new MySdpObserver() {
                    @Override
                    public void onCreateSuccess(SessionDescription sessionDescription) {

                    }

                    @Override
                    public void onSetSuccess() {
                        ShowLogUtil.verbose("set local sdp success.");
                        // 发送 offer sdp
                        sendOffer(sessionDescription);

                    }
                }, sessionDescription);
            }

            @Override
            public void onSetSuccess() {

            }
        }, mediaConstraints);
    }

    private void sendOffer(SessionDescription offer) {
        receivedOffer(offer);
    }

    private void receivedOffer(SessionDescription offer) {
        // 创建 PeerConnection
        PeerConnection.RTCConfiguration rtcConfiguration = new PeerConnection.RTCConfiguration(new ArrayList<>());
        mRemotePeerConnection = mPeerConnectionFactory.createPeerConnection(rtcConfiguration, new PeerConnection.Observer() {
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
                ShowLogUtil.verbose("onIceCandidate--->" + iceCandidate);
                sendIceCandidate(mRemotePeerConnection, iceCandidate);
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
        // 将 offer sdp 作为参数 setRemoteDescription
        mRemotePeerConnection.setRemoteDescription(new MySdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {

            }

            @Override
            public void onSetSuccess() {
                ShowLogUtil.verbose("set remote sdp success.");
                // 通过 PeerConnection 创建 answer，获取 sdp
                MediaConstraints mediaConstraints = new MediaConstraints();
                mRemotePeerConnection.createAnswer(new MySdpObserver() {
                    @Override
                    public void onCreateSuccess(SessionDescription sessionDescription) {
                        ShowLogUtil.verbose("create answer success.");
                        // 将 answer sdp 作为参数 setLocalDescription
                        mRemotePeerConnection.setLocalDescription(new MySdpObserver() {
                            @Override
                            public void onCreateSuccess(SessionDescription sessionDescription) {

                            }

                            @Override
                            public void onSetSuccess() {
                                ShowLogUtil.verbose("set local sdp success.");
                                // 发送 answer sdp
                                sendAnswer(sessionDescription);
                            }
                        }, sessionDescription);
                    }

                    @Override
                    public void onSetSuccess() {

                    }
                }, mediaConstraints);
            }
        }, offer);
    }

    private void sendAnswer(SessionDescription answer) {
        receivedAnswer(answer);
    }

    private void receivedAnswer(SessionDescription answer) {
        // 收到 answer sdp，将 answer sdp 作为参数 setRemoteDescription
        mLocalPeerConnection.setRemoteDescription(new MySdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {

            }

            @Override
            public void onSetSuccess() {
                ShowLogUtil.verbose("set remote sdp success.");
            }
        }, answer);
    }

    private void sendIceCandidate(PeerConnection peerConnection, IceCandidate iceCandidate) {
        receivedCandidate(peerConnection, iceCandidate);
    }

    private void receivedCandidate(PeerConnection peerConnection, IceCandidate iceCandidate) {
        if (peerConnection == mLocalPeerConnection) {
            mRemotePeerConnection.addIceCandidate(iceCandidate);
        } else {
            mLocalPeerConnection.addIceCandidate(iceCandidate);
        }
    }

    private void hangUp() {
        // 关闭 PeerConnection
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
        // 释放远端视频渲染控件
        SurfaceViewRenderer svrRemote = findViewById(R.id.svr_remote);
        svrRemote.clearImage();
    }
}