//
//  LocalDemoViewController.swift
//  WebRTCDemo-iOS
//
//  Created by 覃浩 on 2023/3/21.
//

import UIKit
import WebRTC
import SnapKit

class MultipleDemoViewController: UIViewController {
    private static let AUDIO_TRACK_ID = "ARDAMSa0"
    private static let VIDEO_TRACK_ID = "ARDAMSv0"
    private static let STREAM_IDS = ["ARDAMS"]
    private static let WIDTH = 1280
    private static let HEIGHT = 720
    private static let FPS = 30
    
    private var localView: RTCEAGLVideoView!
    private var remoteViews: UIScrollView!
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
    private var remoteStreamDict: [String : RTCMediaStream] = [:]
//    private let userId = UUID().uuidString
    private let userId = "iOS"
    private var peerConnectionDict: [String : RTCPeerConnection] = [:]
    private var remoteViewDict: [String : RTCEAGLVideoView] = [:]
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
        tfServerUrl!.text = "ws://192.168.1.104:8888"
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
        btnCall.setTitle("加入房间", for: .normal)
        btnCall.setTitleColor(UIColor.black, for: .normal)
        btnCall.addTarget(self, action: #selector(join), for: .touchUpInside)
        self.view.addSubview(btnCall)
        btnCall.snp.makeConstraints({ make in
            make.left.equalToSuperview().offset(30)
            make.width.equalTo(160)
            make.height.equalTo(40)
            make.top.equalTo(btnDisconnect.snp.bottom).offset(10)
        })
        // 挂断按钮
        let btnHangUp = UIButton()
        btnHangUp.backgroundColor = UIColor.lightGray
        btnHangUp.setTitle("退出房间", for: .normal)
        btnHangUp.setTitleColor(UIColor.black, for: .normal)
        btnHangUp.addTarget(self, action: #selector(quit), for: .touchUpInside)
        self.view.addSubview(btnHangUp)
        btnHangUp.snp.makeConstraints({ make in
            make.left.equalToSuperview().offset(30)
            make.width.equalTo(160)
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
        (videoCapturer as? RTCCameraVideoCapturer)?.startCapture(with: captureDevice!, format: captureDevice!.activeFormat, fps: MultipleDemoViewController.FPS)
        // 初始化远端视频渲染控件容器
        remoteViews = UIScrollView()
        self.view.insertSubview(remoteViews, aboveSubview: localView)
        remoteViews.snp.makeConstraints { maker in
            maker.width.equalTo(90)
            maker.top.equalToSuperview().offset(30)
            maker.right.equalToSuperview().offset(-30)
            maker.bottom.equalToSuperview().offset(-30)
        }
    }
    
    override func viewDidDisappear(_ animated: Bool) {
        (videoCapturer as? RTCCameraVideoCapturer)?.stopCapture()
        videoCapturer = nil
        for peerConnection in peerConnectionDict.values {
            peerConnection.close()
        }
        peerConnectionDict.removeAll(keepingCapacity: false)
        remoteViewDict.removeAll(keepingCapacity: false)
        remoteStreamDict.removeAll(keepingCapacity: false)
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
        let audioTrack = peerConnectionFactory.audioTrack(with: audioSource, trackId: MultipleDemoViewController.AUDIO_TRACK_ID)
        audioTrack.isEnabled = true
        return audioTrack
    }
    
    private func createVideoTrack(peerConnectionFactory: RTCPeerConnectionFactory) -> RTCVideoTrack? {
        let videoSource = peerConnectionFactory.videoSource()
        let videoTrack = peerConnectionFactory.videoTrack(with: videoSource, trackId: MultipleDemoViewController.VIDEO_TRACK_ID)
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
    
    private func createPeerConnection(peerConnectionFactory: RTCPeerConnectionFactory, fromUserId: String) -> RTCPeerConnection {
        let configuration = RTCConfiguration()
        //        configuration.sdpSemantics = .unifiedPlan
        //        configuration.continualGatheringPolicy = .gatherContinually
        //        configuration.iceServers = [RTCIceServer(urlStrings: ["stun:stun.l.google.com:19302"])]
        let mandatoryConstraints : [String : String] = [:]
        //      let mandatoryConstraints = [kRTCMediaConstraintsOfferToReceiveAudio: kRTCMediaConstraintsValueTrue,
        //                                  kRTCMediaConstraintsOfferToReceiveVideo: kRTCMediaConstraintsValueTrue]
        let optionalConstraints : [String : String] = [:]
        //        let optionalConstraints = ["DtlsSrtpKeyAgreement" : kRTCMediaConstraintsValueTrue]
        let mediaConstraints = RTCMediaConstraints(mandatoryConstraints: mandatoryConstraints, optionalConstraints: optionalConstraints)
        return peerConnectionFactory.peerConnection(with: configuration, constraints: mediaConstraints, delegate: self)
    }
    
    @objc private func connect() {
        webSocketHelper.connect(url: tfServerUrl!.text!.trimmingCharacters(in: .whitespacesAndNewlines))
    }
    @objc private func disconnect() {
        webSocketHelper.disconnect()
    }
    
    @objc private func join() {
        var jsonObject = [String : String]()
        jsonObject["msgType"] = "join"
        jsonObject["userId"] = userId
        do {
            let data = try JSONSerialization.data(withJSONObject: jsonObject)
            webSocketHelper.send(message: String(data: data, encoding: .utf8)!)
        } catch {
            ShowLogUtil.verbose("error--->\(error)")
        }
    }
    
    @objc private func quit() {
        var jsonObject = [String : String]()
        jsonObject["msgType"] = "quit"
        jsonObject["userId"] = userId
        do {
            let data = try JSONSerialization.data(withJSONObject: jsonObject)
            webSocketHelper.send(message: String(data: data, encoding: .utf8)!)
        } catch {
            ShowLogUtil.verbose("error--->\(error)")
        }
        for peerConnection in peerConnectionDict.values {
            peerConnection.close()
        }
        peerConnectionDict.removeAll(keepingCapacity: false)
        for (key, value) in remoteViewDict {
            remoteViews.removeSubview(view: value)
        }
        remoteViewDict.removeAll(keepingCapacity: false)
    }

    
    private func sendOffer(offer: RTCSessionDescription, toUserId: String) {
        var jsonObject = [String : String]()
        jsonObject["msgType"] = "sdp"
        jsonObject["fromUserId"] = userId
        jsonObject["toUserId"] = toUserId
        jsonObject["type"] = "offer"
        jsonObject["sdp"] = offer.sdp
        do {
            let data = try JSONSerialization.data(withJSONObject: jsonObject)
            webSocketHelper.send(message: String(data: data, encoding: .utf8)!)
        } catch {
            ShowLogUtil.verbose("error--->\(error)")
        }
    }
    
    private func receivedOffer(jsonObject: [String : Any]) {
        let fromUserId = jsonObject["fromUserId"] as? String ?? ""
        var peerConnection = peerConnectionDict[fromUserId]
        if (peerConnection == nil) {
            // 创建 PeerConnection
            peerConnection = createPeerConnection(peerConnectionFactory: peerConnectionFactory, fromUserId: fromUserId)
            // 为 PeerConnection 添加音轨、视轨
            peerConnection!.add(audioTrack!, streamIds: MultipleDemoViewController.STREAM_IDS)
            peerConnection!.add(videoTrack!, streamIds: MultipleDemoViewController.STREAM_IDS)
            peerConnectionDict[fromUserId] = peerConnection
        }
        var remoteView = remoteViewDict[fromUserId]
        if (remoteView == nil) {
            let x = 0
            var y = 0
            if (remoteViews.subviews.count == 0) {
                y = 0
            } else {
                for i in 0..<remoteViews.subviews.count {
                    y += Int(remoteViews.subviews[i].frame.height)
                }
            }
            let width = 90
            let height = width / 9 * 16
            remoteView = RTCEAGLVideoView(frame: CGRect(x: x, y: y, width: width, height: height))
            remoteViews.appendSubView(view: remoteView!)
            remoteViewDict[fromUserId] = remoteView
        }
        // 将 offer sdp 作为参数 setRemoteDescription
        let type = jsonObject["type"] as? String
        let sdp = jsonObject["sdp"] as? String
        let sessionDescription = RTCSessionDescription(type: .offer, sdp: sdp!)
        peerConnection?.setRemoteDescription(sessionDescription, completionHandler: { _ in
            ShowLogUtil.verbose("\(fromUserId) set remote sdp success.")
            // 通过 PeerConnection 创建 answer，获取 sdp
            let mandatoryConstraints : [String : String] = [:]
            let optionalConstraints : [String : String] = [:]
            let mediaConstraints = RTCMediaConstraints(mandatoryConstraints: mandatoryConstraints, optionalConstraints: optionalConstraints)
            peerConnection?.answer(for: mediaConstraints, completionHandler: { sessionDescription, error in
                ShowLogUtil.verbose("\(fromUserId) create answer success.")
                // 将 answer sdp 作为参数 setLocalDescription
                peerConnection?.setLocalDescription(sessionDescription!, completionHandler: { _ in
                    ShowLogUtil.verbose("\(fromUserId) set local sdp success.")
                    // 发送 answer sdp
                    self.sendAnswer(answer: sessionDescription!, toUserId: fromUserId)
                })
            })
        })
    }
    
    private func sendAnswer(answer: RTCSessionDescription, toUserId: String) {
        var jsonObject = [String : String]()
        jsonObject["msgType"] = "sdp"
        jsonObject["fromUserId"] = userId
        jsonObject["toUserId"] = toUserId
        jsonObject["type"] = "answer"
        jsonObject["sdp"] = answer.sdp
        do {
            let data = try JSONSerialization.data(withJSONObject: jsonObject)
            webSocketHelper.send(message: String(data: data, encoding: .utf8)!)
        } catch {
            ShowLogUtil.verbose("error--->\(error)")
        }
    }
    
    private func receivedAnswer(jsonObject: [String : Any]) {
        let fromUserId = jsonObject["fromUserId"] as? String ?? ""
        var peerConnection = peerConnectionDict[fromUserId]
        if (peerConnection == nil) {
            peerConnection = createPeerConnection(peerConnectionFactory: peerConnectionFactory, fromUserId: fromUserId)
            peerConnection!.add(audioTrack!, streamIds: MultipleDemoViewController.STREAM_IDS)
            peerConnection!.add(videoTrack!, streamIds: MultipleDemoViewController.STREAM_IDS)
            peerConnectionDict[fromUserId] = peerConnection
        }
        DispatchQueue.main.async {
            var remoteView = self.remoteViewDict[fromUserId]
            if (remoteView == nil) {
                let x = 0
                var y = 0
                if (self.remoteViews.subviews.count == 0) {
                    y = 0
                } else {
                    for i in 0..<self.remoteViews.subviews.count {
                        y += Int(self.remoteViews.subviews[i].frame.height)
                    }
                }
                let width = 90
                let height = width / 9 * 16
                remoteView = RTCEAGLVideoView(frame: CGRect(x: x, y: y, width: width, height: height))
                self.remoteViews.appendSubView(view: remoteView!)
                self.remoteViewDict[fromUserId] = remoteView
            }
        }
        // 收到 answer sdp，将 answer sdp 作为参数 setRemoteDescription
        let type = jsonObject["type"] as? String
        let sdp = jsonObject["sdp"] as? String
        let sessionDescription = RTCSessionDescription(type: .answer, sdp: sdp!)
        peerConnection!.setRemoteDescription(sessionDescription, completionHandler: { _ in
            ShowLogUtil.verbose(fromUserId + " set remote sdp success.");
        })
    }
    
    private func sendIceCandidate(iceCandidate: RTCIceCandidate, toUserId: String)  {
        var jsonObject = [String : Any]()
        jsonObject["msgType"] = "iceCandidate"
        jsonObject["fromUserId"] = userId
        jsonObject["toUserId"] = toUserId
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
    
    private func receivedCandidate(jsonObject: [String : Any]) {
        let fromUserId = jsonObject["fromUserId"] as? String ?? ""
        let peerConnection = peerConnectionDict[fromUserId]
        if (peerConnection == nil) {
            return
        }
        let id = jsonObject["id"] as? String
        let label = jsonObject["label"] as? Int32
        let candidate = jsonObject["candidate"] as? String
        let iceCandidate = RTCIceCandidate(sdp: candidate!, sdpMLineIndex: label!, sdpMid: id)
        peerConnection!.add(iceCandidate)
    }
    
    private func receiveOtherJoin(jsonObject: [String : Any]) {
        let userId = jsonObject["userId"] as? String ?? ""
        var peerConnection = peerConnectionDict[userId]
        if (peerConnection == nil) {
            // 创建 PeerConnection
            peerConnection = createPeerConnection(peerConnectionFactory: peerConnectionFactory, fromUserId: userId)
            // 为 PeerConnection 添加音轨、视轨
            peerConnection!.add(audioTrack!, streamIds: MultipleDemoViewController.STREAM_IDS)
            peerConnection!.add(videoTrack!, streamIds: MultipleDemoViewController.STREAM_IDS)
            peerConnectionDict[userId] = peerConnection
        }
        DispatchQueue.main.async {
            var remoteView = self.remoteViewDict[userId]
            if (remoteView == nil) {
                let x = 0
                var y = 0
                if (self.remoteViews.subviews.count == 0) {
                    y = 0
                } else {
                    for i in 0..<self.remoteViews.subviews.count {
                        y += Int(self.remoteViews.subviews[i].frame.height)
                    }
                }
                let width = 90
                let height = width / 9 * 16
                remoteView = RTCEAGLVideoView(frame: CGRect(x: x, y: y, width: width, height: height))
                self.remoteViews.appendSubView(view: remoteView!)
                self.remoteViewDict[userId] = remoteView
            }
        }
        // 通过 PeerConnection 创建 offer，获取 sdp
        let mandatoryConstraints : [String : String] = [:]
        let optionalConstraints : [String : String] = [:]
        let mediaConstraints = RTCMediaConstraints(mandatoryConstraints: mandatoryConstraints, optionalConstraints: optionalConstraints)
        peerConnection?.offer(for: mediaConstraints, completionHandler: { sessionDescription, error in
            ShowLogUtil.verbose("\(userId) create offer success.")
            if (error != nil) {
                return
            }
            // 将 offer sdp 作为参数 setLocalDescription
            peerConnection?.setLocalDescription(sessionDescription!, completionHandler: { _ in
                ShowLogUtil.verbose("\(userId) set local sdp success.")
                // 发送 offer sdp
                self.sendOffer(offer: sessionDescription!, toUserId: userId)
            })
        })
    }
    
    private func receiveOtherQuit(jsonObject: [String : Any]) {
        let userId = jsonObject["userId"] as? String ?? ""
        Thread(block: {
            let peerConnection = self.peerConnectionDict[userId]
            if (peerConnection != nil) {
                peerConnection?.close()
                self.peerConnectionDict.removeValue(forKey: userId)
            }
        }).start()
        let remoteView = remoteViewDict[userId]
        if (remoteView != nil) {
            remoteViews.removeSubview(view: remoteView!)
            remoteViewDict.removeValue(forKey: userId)
        }
        remoteStreamDict.removeValue(forKey: userId)
    }
}

// MARK: - RTCVideoViewDelegate
extension MultipleDemoViewController: RTCVideoViewDelegate {
    func videoView(_ videoView: RTCVideoRenderer, didChangeVideoSize size: CGSize) {
    }
}

// MARK: - RTCPeerConnectionDelegate
extension MultipleDemoViewController: RTCPeerConnectionDelegate {
    func peerConnection(_ peerConnection: RTCPeerConnection, didChange stateChanged: RTCSignalingState) {
    }
    
    func peerConnection(_ peerConnection: RTCPeerConnection, didAdd stream: RTCMediaStream) {
        ShowLogUtil.verbose("peerConnection didAdd stream--->\(stream)")
        var userId: String?
        for (key, value) in peerConnectionDict {
            if (value == peerConnection) {
                userId = key
            }
        }
        if (userId == nil) {
            return
        }
        remoteStreamDict[userId!] = stream
        let remoteView = remoteViewDict[userId!]
        if (remoteView == nil) {
            return
        }
        if let videoTrack = stream.videoTracks.first {
            ShowLogUtil.verbose("video track found.")
            videoTrack.add(remoteView!)
        }
        if let audioTrack = stream.audioTracks.first{
            ShowLogUtil.verbose("audio track found.")
            audioTrack.source.volume = 8
        }
    }
    
    func peerConnection(_ peerConnection: RTCPeerConnection, didRemove stream: RTCMediaStream) {
    }
    
    func peerConnectionShouldNegotiate(_ peerConnection: RTCPeerConnection) {
    }
    
    func peerConnection(_ peerConnection: RTCPeerConnection, didChange newState: RTCIceConnectionState) {
        if (newState == .disconnected) {
            DispatchQueue.main.async {
                var userId: String?
                for (key, value) in self.peerConnectionDict {
                    if (value == peerConnection) {
                        userId = key
                    }
                }
                if (userId == nil) {
                    return
                }
                Thread(block: {
                    let peerConnection = self.peerConnectionDict[userId!]
                    if (peerConnection != nil) {
                        peerConnection?.close()
                        self.peerConnectionDict.removeValue(forKey: userId!)
                    }
                }).start()
                let remoteView = self.remoteViewDict[userId!]
                if (remoteView != nil) {
                    self.remoteViews.removeSubview(view: remoteView!)
                    self.remoteViewDict.removeValue(forKey: userId!)
                }
                self.remoteStreamDict.removeValue(forKey: userId!)
            }
        }
    }
    
    func peerConnection(_ peerConnection: RTCPeerConnection, didChange newState: RTCIceGatheringState) {
    }
    
    func peerConnection(_ peerConnection: RTCPeerConnection, didGenerate candidate: RTCIceCandidate) {
//        ShowLogUtil.verbose("didGenerate candidate--->\(candidate)")
        var userId: String?
        for (key, value) in self.peerConnectionDict {
            if (value == peerConnection) {
                userId = key
            }
        }
        if (userId == nil) {
            return
        }
        self.sendIceCandidate(iceCandidate: candidate, toUserId: userId!)
    }
    
    func peerConnection(_ peerConnection: RTCPeerConnection, didRemove candidates: [RTCIceCandidate]) {
    }
    
    func peerConnection(_ peerConnection: RTCPeerConnection, didOpen dataChannel: RTCDataChannel) {
    }
}

// MARK: - UITextFieldDelegate
extension MultipleDemoViewController: UITextFieldDelegate {
    func textFieldShouldReturn(_ textField: UITextField) -> Bool {
        textField.resignFirstResponder()
        return true
    }
}

// MARK: - WebSocketDelegate
extension MultipleDemoViewController: WebSocketDelegate {
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
                let type = jsonObject["type"] as? String;
                if ("offer" == type) {
                    receivedOffer(jsonObject: jsonObject);
                } else if ("answer" == type) {
                    receivedAnswer(jsonObject: jsonObject);
                }
            } else if ("iceCandidate" == msgType) {
                receivedCandidate(jsonObject: jsonObject);
            } else if ("otherJoin" == msgType) {
                receiveOtherJoin(jsonObject: jsonObject)
            } else if ("otherQuit" == msgType) {
                receiveOtherQuit(jsonObject: jsonObject)
            }
        } catch {
        }
    }
}
