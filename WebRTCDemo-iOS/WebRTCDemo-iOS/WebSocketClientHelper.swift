//
//  WebClientHelper.swift
//  WebRTCDemo-iOS
//
//  Created by 覃浩 on 2023/3/1.
//

import Starscream

public protocol WebSocketDelegate {
    func onOpen()
    
    func onClose()
    
    func onMessage(message: String)
}

class WebSocketClientHelper {
    private var webSocket: WebSocket?
    private var delegate: WebSocketDelegate?
    
    func setDelegate(delegate: WebSocketDelegate) {
        self.delegate = delegate
    }
    
    func connect(url: String) {
        let request = URLRequest(url: URL(string: url)!)
        webSocket = WebSocket(request: request)
        webSocket?.onEvent = { event in
            switch event {
            case .connected(let headers):
                self.delegate?.onOpen()
                break
            case .disconnected(let reason, let code):
                self.delegate?.onClose()
                break
            case .text(let string):
                self.delegate?.onMessage(message: string)
                break
            case .binary(let data):
                break
            case .ping(_):
                break
            case .pong(_):
                break
            case .viabilityChanged(_):
                break
            case .reconnectSuggested(_):
                break
            case .cancelled:
                self.delegate?.onClose()
                break
            case .error(let error):
                self.delegate?.onClose()
                break
            }
        }
        webSocket?.connect()
    }
    
    func disconnect() {
        webSocket?.disconnect()
    }
    
    func send(message: String) {
        webSocket?.write(string: message)
    }
}
