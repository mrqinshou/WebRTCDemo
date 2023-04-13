//
//  MainViewController.swift
//  WebRTCDemo-iOS
//
//  Created by zst on 2023/3/21.
//

import UIKit
import AVFoundation
import SnapKit

class MainViewController: UIViewController {
    
    override func viewDidLoad() {
        super.viewDidLoad()
        // 表明 View 不要扩展到整个屏幕，而是在 NavigationBar 下的区域
        edgesForExtendedLayout = UIRectEdge()
        
        AVCaptureDevice.requestAccess(for: .audio, completionHandler: { result in
        })
        AVCaptureDevice.requestAccess(for: .video, completionHandler: { result in
        })
        
        let btnLocalDemo = UIButton()
        btnLocalDemo.backgroundColor = UIColor.lightGray
        btnLocalDemo.setTitle("本地 demo", for: .normal)
        btnLocalDemo.setTitleColor(UIColor.black, for: .normal)
        btnLocalDemo.addTarget(self, action: #selector(localDemo), for: .touchUpInside)
        self.view.addSubview(btnLocalDemo)
        btnLocalDemo.snp.makeConstraints({ maker in
            maker.left.equalToSuperview().offset(30)
            maker.right.equalToSuperview().offset(-30)
            maker.height.equalTo(40)
        })
        
        let btnP2PDemo = UIButton()
        btnP2PDemo.backgroundColor = UIColor.lightGray
        btnP2PDemo.setTitle("点对点 demo", for: .normal)
        btnP2PDemo.setTitleColor(UIColor.black, for: .normal)
        btnP2PDemo.addTarget(self, action: #selector(p2pDemo), for: .touchUpInside)
        self.view.addSubview(btnP2PDemo)
        btnP2PDemo.snp.makeConstraints({ maker in
            maker.left.equalToSuperview().offset(30)
            maker.right.equalToSuperview().offset(-30)
            maker.height.equalTo(40)
            maker.top.equalTo(btnLocalDemo.snp.bottom).offset(10)
        })
        
        let btnMultipleDemo = UIButton()
        btnMultipleDemo.backgroundColor = UIColor.lightGray
        btnMultipleDemo.setTitle("多端 demo", for: .normal)
        btnMultipleDemo.setTitleColor(UIColor.black, for: .normal)
        btnMultipleDemo.addTarget(self, action: #selector(multipleDemo), for: .touchUpInside)
        self.view.addSubview(btnMultipleDemo)
        btnMultipleDemo.snp.makeConstraints({ maker in
            maker.left.equalToSuperview().offset(30)
            maker.right.equalToSuperview().offset(-30)
            maker.height.equalTo(40)
            maker.top.equalTo(btnP2PDemo.snp.bottom).offset(10)
        })
        
        let btnCustomDemo = UIButton()
        btnCustomDemo.backgroundColor = UIColor.lightGray
        btnCustomDemo.setTitle("自定义 demo", for: .normal)
        btnCustomDemo.setTitleColor(UIColor.black, for: .normal)
        btnCustomDemo.addTarget(self, action: #selector(customDemo), for: .touchUpInside)
        self.view.addSubview(btnCustomDemo)
        btnCustomDemo.snp.makeConstraints({ maker in
            maker.left.equalToSuperview().offset(30)
            maker.right.equalToSuperview().offset(-30)
            maker.height.equalTo(40)
            maker.top.equalTo(btnMultipleDemo.snp.bottom).offset(10)
        })
    }
    
    @objc private func localDemo() {
        self.navigationController?.pushViewController(LocalDemoViewController(), animated: true)
    }
    
    @objc private func p2pDemo() {
        self.navigationController?.pushViewController(P2PDemoViewController(), animated: true)
    }
    
    @objc private func multipleDemo() {
//        self.navigationController?.pushViewController(MultipleDemoViewController(), animated: true)
    }
    
    @objc private func customDemo() {
//        self.navigationController?.pushViewController(CustomDemoViewController(), animated: true)
    }
}
