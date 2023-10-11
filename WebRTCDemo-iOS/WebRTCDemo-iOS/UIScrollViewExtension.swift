//
//  UIScrollViewExtension.swift
//  WebRTCDemo
//
//  Created by zst on 2023/3/22.
//

import UIKit

extension UIScrollView {
    func appendSubView(view: UIView) {
        let oldShowsHorizontalScrollIndicator = showsHorizontalScrollIndicator
        let oldShowsVerticalScrollIndicator = showsVerticalScrollIndicator
        showsHorizontalScrollIndicator = false
        showsVerticalScrollIndicator = false
        var y = 0.0
        if (subviews.count == 0) {
            y = 0
        } else {
            for i in 0..<subviews.count {
                if ("_UIScrollViewScrollIndicator" == String(reflecting: type(of: subviews[i]))){
                    continue
                }
                y += subviews[i].frame.height
            }
        }
        view.frame.origin.y = y
        addSubview(view)
        let contentSizeWidth = contentSize.width
        // 重新计算 UIScrollView 内容高度
        var contentSizeHeight = 0.0
        for i in 0..<subviews.count {
            if ("_UIScrollViewScrollIndicator" == String(reflecting: type(of: subviews[i]))){
                continue
            }
            contentSizeHeight += subviews[i].frame.height
        }
        contentSize = CGSize(width: contentSizeWidth, height: contentSizeHeight)
        showsHorizontalScrollIndicator = oldShowsHorizontalScrollIndicator
        showsVerticalScrollIndicator = oldShowsVerticalScrollIndicator
    }
    
    func removeSubview(view: UIView) {
        let oldShowsHorizontalScrollIndicator = showsHorizontalScrollIndicator
        let oldShowsVerticalScrollIndicator = showsVerticalScrollIndicator
        showsHorizontalScrollIndicator = false
        showsVerticalScrollIndicator = false
        var index = -1
        for i in 0..<subviews.count {
            if (subviews[i] == view) {
                index = i
                break
            }
        }
        if (index == -1) {
            return
        }
        for i in index+1..<subviews.count {
            subviews[i].frame.origin.y = subviews[i].frame.origin.y-view.frame.height
        }
        view.removeFromSuperview()
        let contentSizeWidth = contentSize.width
        // 重新计算 UIScrollView 内容高度
        var contentSizeHeight = 0.0
        for i in 0..<subviews.count {
            if ("_UIScrollViewScrollIndicator" == String(reflecting: type(of: subviews[i]))){
                continue
            }
            contentSizeHeight += subviews[i].frame.height
        }
        contentSize = CGSize(width: contentSizeWidth, height: contentSizeHeight)
        showsHorizontalScrollIndicator = oldShowsHorizontalScrollIndicator
        showsVerticalScrollIndicator = oldShowsVerticalScrollIndicator
    }
}
