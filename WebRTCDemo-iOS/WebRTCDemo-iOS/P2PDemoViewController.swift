//
//  LocalDemoViewController.swift
//  WebRTCDemo-iOS
//
//  Created by 覃浩 on 2023/3/21.
//

import UIKit
import WebRTC
import SnapKit

class P2PDemoViewController: UIViewController {
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
    private var peerConnection: RTCPeerConnection?
    private var lbWebSocketState: UILabel? = nil
    private var tfServerUrl: UITextField? = nil
    private let webSocketHelper = WebSocketClientHelper()
    
    override func viewDidLoad() {
        super.viewDidLoad()
        // 表明 View 不要扩展到整个屏幕，而是在 NavigationBar 下的区域
        edgesForExtendedLayout = UIRectEdge()
        self.view.backgroundColor = UIColor.black
        // WebSocket 状态文本框
        lbWebSocketState = UILabel()
        lbWebSocketState!.textColor = UIColor.white
        lbWebSocketState!.text = "WebSocket 已断开"
        self.view.addSubview(lbWebSocketState!)
        lbWebSocketState!.snp.makeConstraints({ make in
            make.left.equalToSuperview().offset(30)
            make.right.equalToSuperview().offset(-30)
            make.height.equalTo(40)
        })
        // 服务器地址输入框
        tfServerUrl = UITextField()
        tfServerUrl!.textColor = UIColor.white
//        tfServerUrl!.text = "ws://192.168.1.104:8888"
        tfServerUrl!.text = "ws://172.16.2.57:8888"
        tfServerUrl!.placeholder = "请输入服务器地址"
        tfServerUrl!.delegate = self
        self.view.addSubview(tfServerUrl!)
        tfServerUrl!.snp.makeConstraints({ make in
            make.left.equalToSuperview().offset(30)
            make.right.equalToSuperview().offset(-30)
            make.height.equalTo(20)
            make.top.equalTo(lbWebSocketState!.snp.bottom).offset(10)
        })
        // 连接 WebSocket 按钮
        let btnConnect = UIButton()
        btnConnect.backgroundColor = UIColor.lightGray
        btnConnect.setTitle("连接 WebSocket", for: .normal)
        btnConnect.setTitleColor(UIColor.black, for: .normal)
        btnConnect.addTarget(self, action: #selector(connect), for: .touchUpInside)
        self.view.addSubview(btnConnect)
        btnConnect.snp.makeConstraints({ make in
            make.left.equalToSuperview().offset(30)
            make.width.equalTo(140)
            make.height.equalTo(40)
            make.top.equalTo(tfServerUrl!.snp.bottom).offset(10)
        })
        // 断开 WebSocket 按钮
        let btnDisconnect = UIButton()
        btnDisconnect.backgroundColor = UIColor.lightGray
        btnDisconnect.setTitle("断开 WebSocket", for: .normal)
        btnDisconnect.setTitleColor(UIColor.black, for: .normal)
        btnDisconnect.addTarget(self, action: #selector(disconnect), for: .touchUpInside)
        self.view.addSubview(btnDisconnect)
        btnDisconnect.snp.makeConstraints({ make in
            make.left.equalToSuperview().offset(30)
            make.width.equalTo(140)
            make.height.equalTo(40)
            make.top.equalTo(btnConnect.snp.bottom).offset(10)
        })
        // 呼叫按钮
        let btnCall = UIButton()
        btnCall.backgroundColor = UIColor.lightGray
        btnCall.setTitle("呼叫", for: .normal)
        btnCall.setTitleColor(UIColor.black, for: .normal)
        btnCall.addTarget(self, action: #selector(call), for: .touchUpInside)
        self.view.addSubview(btnCall)
        btnCall.snp.makeConstraints({ make in
            make.left.equalToSuperview().offset(30)
            make.width.equalTo(80)
            make.height.equalTo(40)
            make.top.equalTo(btnDisconnect.snp.bottom).offset(10)
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
            make.width.equalTo(80)
            make.height.equalTo(40)
            make.top.equalTo(btnCall.snp.bottom).offset(10)
        })
        webSocketHelper.setDelegate(delegate: self)
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
        localView.snp.makeConstraints({ make in
            make.width.equalToSuperview()
            make.height.equalTo(localView.snp.width).multipliedBy(16.0/9.0)
            make.centerY.equalToSuperview()
        })
        videoTrack?.add(localView!)
        // 开始本地渲染
        (videoCapturer as? RTCCameraVideoCapturer)?.startCapture(with: captureDevice!, format: captureDevice!.activeFormat, fps: P2PDemoViewController.FPS)
        // 初始化远端视频渲染控件
        remoteView = RTCEAGLVideoView()
        remoteView.delegate = self
        self.view.insertSubview(remoteView, aboveSubview: localView)
        remoteView.snp.makeConstraints({ make in
            make.width.equalTo(90)
            make.height.equalTo(160)
            make.top.equalToSuperview().offset(30)
            make.right.equalToSuperview().offset(-30)
        })
    }
    
    override func viewDidDisappear(_ animated: Bool) {
        (videoCapturer as? RTCCameraVideoCapturer)?.stopCapture()
        videoCapturer = nil
        peerConnection?.close()
        peerConnection = nil
        webSocketHelper.disconnect()
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
        let audioTrack = peerConnectionFactory.audioTrack(with: audioSource, trackId: P2PDemoViewController.AUDIO_TRACK_ID)
        audioTrack.isEnabled = true
        return audioTrack
    }
    
    private func createVideoTrack(peerConnectionFactory: RTCPeerConnectionFactory) -> RTCVideoTrack? {
        let videoSource = peerConnectionFactory.videoSource()
        let videoTrack = peerConnectionFactory.videoTrack(with: videoSource, trackId: P2PDemoViewController.VIDEO_TRACK_ID)
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
             if (c.position == .front) {
                captureDevice = c
                break
            }
        }
        if (captureDevice == nil) {
            return (nil, nil)
        }
        return (captureDevice, videoCapturer)
    }
    
    private func createPeerConnection() -> RTCPeerConnection {
        let rtcConfiguration = RTCConfiguration()
        let mandatoryConstraints : [String : String] = [:]
        let optionalConstraints : [String : String] = [:]
        let mediaConstraints = RTCMediaConstraints(mandatoryConstraints: mandatoryConstraints, optionalConstraints: optionalConstraints)
        let peerConnection = peerConnectionFactory.peerConnection(with: rtcConfiguration, constraints: mediaConstraints, delegate: self)
        return peerConnection
    }
    
    @objc private func connect() {
        webSocketHelper.connect(url: tfServerUrl!.text!.trimmingCharacters(in: .whitespacesAndNewlines))
    }
    
    @objc private func disconnect() {
        webSocketHelper.disconnect()
    }
    
    @objc private func call() {
        // 创建 PeerConnection
        peerConnection = createPeerConnection()
        // 为 PeerConnection 添加音轨、视轨
        peerConnection?.add(audioTrack!, streamIds: P2PDemoViewController.STREAM_IDS)
        peerConnection?.add(videoTrack!, streamIds: P2PDemoViewController.STREAM_IDS)
        // 通过 PeerConnection 创建 offer，获取 sdp
        let mandatoryConstraints: [String : String] = [:]
        let optionalConstraints: [String : String] = [:]
        let mediaConstraints = RTCMediaConstraints(mandatoryConstraints: mandatoryConstraints, optionalConstraints: optionalConstraints)
        peerConnection?.offer(for: mediaConstraints, completionHandler: { sessionDescription, error in
            ShowLogUtil.verbose("create offer success.")
            // 将 offer sdp 作为参数 setLocalDescription
            self.peerConnection?.setLocalDescription(sessionDescription!, completionHandler: { _ in
                ShowLogUtil.verbose("set local sdp success.")
                // 发送 offer sdp
                self.sendOffer(offer: sessionDescription!)
            })
        })
    }
    
    @objc private func hangUp() {
        // 关闭 PeerConnection
        peerConnection?.close()
        peerConnection = nil
        // 释放远端视频渲染控件
        if let track = remoteStream?.videoTracks.first {
            track.remove(remoteView!)
        }
    }
    
    private func sendOffer(offer: RTCSessionDescription) {
        var jsonObject = [String : String]()
        jsonObject["msgType"] = "sdp"
        jsonObject["type"] = "offer"
        jsonObject["sdp"] = offer.sdp
        do {
            let data = try JSONSerialization.data(withJSONObject: jsonObject)
            webSocketHelper.send(message: String(data: data, encoding: .utf8)!)
        } catch {
            ShowLogUtil.verbose("error--->\(error)")
        }
    }
    
    private func receivedOffer(offer: RTCSessionDescription) {
        // 创建 PeerConnection
        peerConnection = createPeerConnection()
        // 为 PeerConnection 添加音轨、视轨
        peerConnection?.add(audioTrack!, streamIds: P2PDemoViewController.STREAM_IDS)
        peerConnection?.add(videoTrack!, streamIds: P2PDemoViewController.STREAM_IDS)
        // 将 offer sdp 作为参数 setRemoteDescription
        peerConnection?.setRemoteDescription(offer, completionHandler: { _ in
            ShowLogUtil.verbose("set remote sdp success.")
            // 通过 PeerConnection 创建 answer，获取 sdp
            let mandatoryConstraints : [String : String] = [:]
            let optionalConstraints : [String : String] = [:]
            let mediaConstraints = RTCMediaConstraints(mandatoryConstraints: mandatoryConstraints, optionalConstraints: optionalConstraints)
            self.peerConnection?.answer(for: mediaConstraints, completionHandler: { sessionDescription, error in
                ShowLogUtil.verbose("create answer success.")
                // 将 answer sdp 作为参数 setLocalDescription
                self.peerConnection?.setLocalDescription(sessionDescription!, completionHandler: { _ in
                    ShowLogUtil.verbose("set local sdp success.")
                    // 发送 answer sdp
                    self.sendAnswer(answer: sessionDescription!)
                })
            })
        })
    }
    
    private func sendAnswer(answer: RTCSessionDescription) {
        var jsonObject = [String : String]()
        jsonObject["msgType"] = "sdp"
        jsonObject["type"] = "answer"
        jsonObject["sdp"] = answer.sdp
        do {
            let data = try JSONSerialization.data(withJSONObject: jsonObject)
            webSocketHelper.send(message: String(data: data, encoding: .utf8)!)
        } catch {
            ShowLogUtil.verbose("error--->\(error)")
        }
    }
    
    private func receivedAnswer(answer: RTCSessionDescription) {
        // 收到 answer sdp，将 answer sdp 作为参数 setRemoteDescription
        peerConnection?.setRemoteDescription(answer, completionHandler: { _ in   ShowLogUtil.verbose("set remote sdp success.")
        })
    }
    
    private func sendIceCandidate(iceCandidate: RTCIceCandidate)  {
        var jsonObject = [String : Any]()
        jsonObject["msgType"] = "iceCandidate"
        jsonObject["id"] = iceCandidate.sdpMid
        jsonObject["label"] = iceCandidate.sdpMLineIndex
        jsonObject["candidate"] = iceCandidate.sdp
        do {
            let data = try JSONSerialization.data(withJSONObject: jsonObject)
            webSocketHelper.send(message: String(data: data, encoding: .utf8)!)
        } catch {
            ShowLogUtil.verbose("error--->\(error)")
        }
    }
    
    private func receivedCandidate(iceCandidate: RTCIceCandidate) {
        peerConnection?.add(iceCandidate)
    }
}

// MARK: - RTCVideoViewDelegate
extension P2PDemoViewController: RTCVideoViewDelegate {
    func videoView(_ videoView: RTCVideoRenderer, didChangeVideoSize size: CGSize) {
    }
}

// MARK: - RTCPeerConnectionDelegate
extension P2PDemoViewController: RTCPeerConnectionDelegate {
    func peerConnection(_ peerConnection: RTCPeerConnection, didChange stateChanged: RTCSignalingState) {
    }
    
    func peerConnection(_ peerConnection: RTCPeerConnection, didAdd stream: RTCMediaStream) {
        ShowLogUtil.verbose("peerConnection didAdd stream--->\(stream)")
        DispatchQueue.main.async {
            self.remoteStream = stream
            if let track = stream.videoTracks.first {
                track.add(self.remoteView!)
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
        self.sendIceCandidate(iceCandidate: candidate)
    }
    
    func peerConnection(_ peerConnection: RTCPeerConnection, didRemove candidates: [RTCIceCandidate]) {
    }
    
    func peerConnection(_ peerConnection: RTCPeerConnection, didOpen dataChannel: RTCDataChannel) {
    }
}

// MARK: - UITextFieldDelegate
extension P2PDemoViewController: UITextFieldDelegate {
    func textFieldShouldReturn(_ textField: UITextField) -> Bool {
        textField.resignFirstResponder()
        return true
    }
}

// MARK: - WebSocketDelegate
extension P2PDemoViewController: WebSocketDelegate {
    func onOpen() {
        lbWebSocketState?.text = "WebSocket 已连接"
    }
    
    func onClose() {
        lbWebSocketState?.text = "WebSocket 已断开"
    }
    
    func onMessage(message: String) {
        do {
            let data = message.data(using: .utf8)
            let jsonObject: [String : Any] = try JSONSerialization.jsonObject(with: data!) as! [String : Any]
            let msgType = jsonObject["msgType"] as? String
            if ("sdp" == msgType) {
                let type = jsonObject["type"] as? String
                if ("offer" == type) {
                    let sdp = jsonObject["sdp"] as! String
                    let offer = RTCSessionDescription(type: .offer, sdp: sdp)
                    receivedOffer(offer: offer)
                } else if ("answer" == type) {
                    let sdp = jsonObject["sdp"] as! String
                    let answer = RTCSessionDescription(type: .answer, sdp: sdp)
                    receivedAnswer(answer: answer)
                }
            } else if ("iceCandidate" == msgType) {
                let id = jsonObject["id"] as? String
                let label = jsonObject["label"] as? Int32
                let candidate = jsonObject["candidate"] as? String
                let iceCandidate = RTCIceCandidate(sdp: candidate!, sdpMLineIndex: label!, sdpMid: id)
                receivedCandidate(iceCandidate: iceCandidate)
            }
        } catch {
            
        }
    }
}
