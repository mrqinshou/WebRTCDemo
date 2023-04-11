package com.qinshou.webrtcdemo_android;

import org.webrtc.SdpObserver;

/**
 * Author: MrQinshou
 * Email: cqflqinhao@126.com
 * Date: 2023/3/20 18:46
 * Description: 类描述
 */
public interface MySdpObserver extends SdpObserver {
    @Override
    default void onCreateFailure(String s) {

    }

    @Override
    default void onSetFailure(String s) {

    }
}
