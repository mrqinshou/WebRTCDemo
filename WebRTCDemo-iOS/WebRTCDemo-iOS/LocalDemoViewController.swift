//
//  LocalDemoViewController.swift
//  WebRTCDemo-iOS
//
//  Created by 覃浩 on 2023/3/21.
//

import UIKit
import WebRTC
import SnapKit

class LocalDemoViewController: UIViewController {
    private static let AUDIO_TRACK_ID = "ARDAMSa0"
    private static let VIDEO_TRACK_ID = "ARDAMSv0"
    private static let STREAM_IDS = ["ARDAMS"]
    private static let WIDTH = 1280
    private static let HEIGHT = 720
    private static let FPS = 30
    
    private var localView: RTCEAGLVideoView!
    private var remoteView: RTCEAGLVideoView!
    private var peerConnectionFactory: RTCPeerConnectionFactory!
    private var audioTrack: RTCAudioTrack?
    private var videoTrack: RTCVideoTrack?
    /**
     iOS 需要将 Capturer 保存为全局变量，否则无法渲染本地画面
     */
    private var videoCapturer: RTCVideoCapturer?
    /**
     iOS 需要将远端流保存为全局变量，否则无法渲染远端画面
     */
    private var remoteStream: RTCMediaStream?
    private var localPeerConnection: RTCPeerConnection?
    private var remotePeerConnection: RTCPeerConnection?
    
    override func viewDidLoad() {
        super.viewDidLoad()
        // 表明 View 不要扩展到整个屏幕，而是在 NavigationBar 下的区域
        edgesForExtendedLayout = UIRectEdge()
        self.view.backgroundColor = UIColor.black
        // 呼叫按钮
        let btnCall = UIButton()
        btnCall.backgroundColor = UIColor.lightGray
        btnCall.setTitle("呼叫", for: .normal)
        btnCall.setTitleColor(UIColor.black, for: .normal)
        btnCall.addTarget(self, action: #selector(call), for: .touchUpInside)
        self.view.addSubview(btnCall)
        btnCall.snp.makeConstraints({ make in
            make.left.equalToSuperview().offset(30)
            make.width.equalTo(60)
            make.height.equalTo(40)
        })
        // 挂断按钮
        let btnHangUp = UIButton()
        btnHangUp.backgroundColor = UIColor.lightGray
        btnHangUp.setTitle("挂断", for: .normal)
        btnHangUp.setTitleColor(UIColor.black, for: .normal)
        btnHangUp.addTarget(self, action: #selector(hangUp), for: .touchUpInside)
        self.view.addSubview(btnHangUp)
        btnHangUp.snp.makeConstraints({ make in
            make.left.equalToSuperview().offset(30)
            make.width.equalTo(60)
            make.height.equalTo(40)
            make.top.equalTo(btnCall.snp.bottom).offset(10)
        })
        // 初始化 PeerConnectionFactory
        initPeerConnectionFactory()
        // 创建 EglBase
        // 创建 PeerConnectionFactory
        peerConnectionFactory = createPeerConnectionFactory()
        // 创建音轨
        audioTrack = createAudioTrack(peerConnectionFactory: peerConnectionFactory)
        // 创建视轨
        videoTrack = createVideoTrack(peerConnectionFactory: peerConnectionFactory)
        let tuple = createVideoCapturer(videoSource: videoTrack!.source)
        let captureDevice = tuple.captureDevice
        videoCapturer = tuple.videoCapture
        // 初始化本地视频渲染控件
        localView = RTCEAGLVideoView()
        localView.delegate = self
        self.view.insertSubview(localView,at: 0)
        localView.snp.makeConstraints({ maker in
            maker.width.equalToSuperview()
            maker.height.equalTo(localView.snp.width).multipliedBy(16.0/9.0)
            maker.centerY.equalToSuperview()
        })
        videoTrack?.add(localView!)
        // 初始化远端视频渲染控件
        remoteView = RTCEAGLVideoView()
        remoteView.delegate = self
        self.view.insertSubview(remoteView, aboveSubview: localView)
        remoteView.snp.makeConstraints({ maker in
            maker.width.equalTo(90)
            maker.height.equalTo(160)
            maker.top.equalToSuperview().offset(30)
            maker.right.equalToSuperview().offset(-30)
        })
        // 开始本地渲染
        (videoCapturer as? RTCCameraVideoCapturer)?.startCapture(with: captureDevice!, format: captureDevice!.activeFormat, fps: LocalDemoViewController.FPS)
    }
    
    override func viewDidDisappear(_ animated: Bool) {
        (videoCapturer as? RTCCameraVideoCapturer)?.stopCapture()
        videoCapturer = nil
        localPeerConnection?.close()
        localPeerConnection = nil
        remotePeerConnection?.close()
        remotePeerConnection = nil
    }
    
    private func initPeerConnectionFactory() {
        RTCPeerConnectionFactory.initialize()
    }
    
    private func createPeerConnectionFactory() -> RTCPeerConnectionFactory {
        var videoEncoderFactory = RTCDefaultVideoEncoderFactory()
        var videoDecoderFactory = RTCDefaultVideoDecoderFactory()
        if TARGET_OS_SIMULATOR != 0 {
            videoEncoderFactory = RTCSimluatorVideoEncoderFactory()
            videoDecoderFactory = RTCSimulatorVideoDecoderFactory()
        }
        return RTCPeerConnectionFactory(encoderFactory: videoEncoderFactory, decoderFactory: videoDecoderFactory)
    }
    
    private func createAudioTrack(peerConnectionFactory: RTCPeerConnectionFactory) -> RTCAudioTrack {
        let mandatoryConstraints : [String : String] = [:]
        let optionalConstraints : [String : String] = [:]
        let audioSource = peerConnectionFactory.audioSource(with: RTCMediaConstraints(mandatoryConstraints: mandatoryConstraints, optionalConstraints: optionalConstraints))
        let audioTrack = peerConnectionFactory.audioTrack(with: audioSource, trackId: LocalDemoViewController.AUDIO_TRACK_ID)
        audioTrack.isEnabled = true
        return audioTrack
    }
    
    private func createVideoTrack(peerConnectionFactory: RTCPeerConnectionFactory) -> RTCVideoTrack? {
        let videoSource = peerConnectionFactory.videoSource()
        let videoTrack = peerConnectionFactory.videoTrack(with: videoSource, trackId: LocalDemoViewController.VIDEO_TRACK_ID)
        videoTrack.isEnabled = true
        return videoTrack
    }
    
    private func createVideoCapturer(videoSource: RTCVideoSource) -> (captureDevice: AVCaptureDevice?, videoCapture: RTCVideoCapturer?) {
        let videoCapturer = RTCCameraVideoCapturer(delegate: videoSource)
        let captureDevices = RTCCameraVideoCapturer.captureDevices()
        if (captureDevices.count == 0) {
            return (nil, nil)
        }
        var captureDevice: AVCaptureDevice?
        for c in captureDevices {
            // 前摄像头
            // if (c.position == .front) {
            // 后摄像头
            if (c.position == .back) {
                captureDevice = c
                break
            }
        }
        if (captureDevice == nil) {
            return (nil, nil)
        }
        return (captureDevice, videoCapturer)
    }
    
    @objc private func call() {
        // 创建 PeerConnection
        let rtcConfiguration = RTCConfiguration()
        var mandatoryConstraints : [String : String] = [:]
        var optionalConstraints : [String : String] = [:]
        var mediaConstraints = RTCMediaConstraints(mandatoryConstraints: mandatoryConstraints, optionalConstraints: optionalConstraints)
        localPeerConnection = peerConnectionFactory.peerConnection(with: rtcConfiguration, constraints: mediaConstraints, delegate: self)
        // 为 PeerConnection 添加音轨、视轨
        localPeerConnection?.add(audioTrack!, streamIds: LocalDemoViewController.STREAM_IDS)
        localPeerConnection?.add(videoTrack!, streamIds: LocalDemoViewController.STREAM_IDS)
        // 通过 PeerConnection 创建 offer，获取 sdp
        mandatoryConstraints = [:]
        optionalConstraints = [:]
        mediaConstraints = RTCMediaConstraints(mandatoryConstraints: mandatoryConstraints, optionalConstraints: optionalConstraints)
        localPeerConnection?.offer(for: mediaConstraints, completionHandler: { sessionDescription, error in
            ShowLogUtil.verbose("create offer success.")
            // 将 offer sdp 作为参数 setLocalDescription
            self.localPeerConnection?.setLocalDescription(sessionDescription!, completionHandler: { _ in
                ShowLogUtil.verbose("set local sdp success.")
                // 发送 offer sdp
                self.sendOffer(offer: sessionDescription!)
            })
        })
    }
    
    private func sendOffer(offer: RTCSessionDescription) {
        receivedOffer(offer: offer)
    }
    
    private func receivedOffer(offer: RTCSessionDescription) {
        // 创建 PeerConnection
        let rtcConfiguration = RTCConfiguration()
        let mandatoryConstraints : [String : String] = [:]
        let optionalConstraints : [String : String] = [:]
        var mediaConstraints = RTCMediaConstraints(mandatoryConstraints: mandatoryConstraints, optionalConstraints: optionalConstraints)
        remotePeerConnection = peerConnectionFactory.peerConnection(with: rtcConfiguration, constraints: mediaConstraints, delegate: self)
        // 将 offer sdp 作为参数 setRemoteDescription
        remotePeerConnection?.setRemoteDescription(offer, completionHandler: { _ in
            ShowLogUtil.verbose("set remote sdp success.")
            // 通过 PeerConnection 创建 answer，获取 sdp
            let mandatoryConstraints : [String : String] = [:]
            let optionalConstraints : [String : String] = [:]
            let mediaConstraints = RTCMediaConstraints(mandatoryConstraints: mandatoryConstraints, optionalConstraints: optionalConstraints)
            self.remotePeerConnection?.answer(for: mediaConstraints, completionHandler: { sessionDescription, error in
                ShowLogUtil.verbose("create answer success.")
                // 将 answer sdp 作为参数 setLocalDescription
                self.remotePeerConnection?.setLocalDescription(sessionDescription!, completionHandler: { _ in
                    ShowLogUtil.verbose("set local sdp success.")
                    // 发送 answer sdp
                    self.sendAnswer(answer: sessionDescription!)
                })
            })
        })
    }
    
    private func sendAnswer(answer: RTCSessionDescription) {
        receivedAnswer(answer: answer)
    }
    
    private func receivedAnswer(answer: RTCSessionDescription) {
        // 收到 answer sdp，将 answer sdp 作为参数 setRemoteDescription
        localPeerConnection?.setRemoteDescription(answer, completionHandler: { _ in   ShowLogUtil.verbose("set remote sdp success.")
        })
    }
    
    private func sendIceCandidate(peerConnection: RTCPeerConnection, iceCandidate: RTCIceCandidate)  {
        receivedCandidate(peerConnection: peerConnection,iceCandidate: iceCandidate)
    }
    
    private func receivedCandidate(peerConnection: RTCPeerConnection, iceCandidate: RTCIceCandidate) {
        if (peerConnection == localPeerConnection) {
            remotePeerConnection?.add(iceCandidate)
        } else {
            localPeerConnection?.add(iceCandidate)
        }
    }
    
    @objc private func hangUp() {
        // 关闭 PeerConnection
        localPeerConnection?.close()
        localPeerConnection = nil
        
        remotePeerConnection?.close()
        remotePeerConnection = nil
        // 释放远端视频渲染控件
        if let track = remoteStream?.videoTracks.first {
            track.remove(remoteView!)
        }
    }
}

// MARK: - RTCVideoViewDelegate
extension LocalDemoViewController: RTCVideoViewDelegate {
    func videoView(_ videoView: RTCVideoRenderer, didChangeVideoSize size: CGSize) {
    }
}

// MARK: - RTCPeerConnectionDelegate
extension LocalDemoViewController: RTCPeerConnectionDelegate {
    func peerConnection(_ peerConnection: RTCPeerConnection, didChange stateChanged: RTCSignalingState) {
    }
    
    func peerConnection(_ peerConnection: RTCPeerConnection, didAdd stream: RTCMediaStream) {
        ShowLogUtil.verbose("peerConnection didAdd stream--->\(stream)")
        if (peerConnection == self.localPeerConnection) {
        } else if (peerConnection == self.remotePeerConnection) {
            self.remoteStream = stream
            if let track = stream.videoTracks.first {
                track.add(remoteView!)
            }
            if let audioTrack = stream.audioTracks.first{
                audioTrack.source.volume = 8
            }
        }
    }
    
    func peerConnection(_ peerConnection: RTCPeerConnection, didRemove stream: RTCMediaStream) {
    }
    
    func peerConnectionShouldNegotiate(_ peerConnection: RTCPeerConnection) {
    }
    
    func peerConnection(_ peerConnection: RTCPeerConnection, didChange newState: RTCIceConnectionState) {
        if (newState == .disconnected) {
            DispatchQueue.main.async {
                self.hangUp()
            }
        }
    }
    
    func peerConnection(_ peerConnection: RTCPeerConnection, didChange newState: RTCIceGatheringState) {
    }
    
    func peerConnection(_ peerConnection: RTCPeerConnection, didGenerate candidate: RTCIceCandidate) {
        ShowLogUtil.verbose("didGenerate candidate--->\(candidate)")
        self.sendIceCandidate(peerConnection: peerConnection, iceCandidate: candidate)
    }
    
    func peerConnection(_ peerConnection: RTCPeerConnection, didRemove candidates: [RTCIceCandidate]) {
    }
    
    func peerConnection(_ peerConnection: RTCPeerConnection, didOpen dataChannel: RTCDataChannel) {
    }
}
