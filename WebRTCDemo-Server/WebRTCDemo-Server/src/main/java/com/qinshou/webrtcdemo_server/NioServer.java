package com.qinshou.webrtcdemo_server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Iterator;

public class NioServer {
    private static final int SERVER_PORT = 8888;

    public static void main(String[] args) throws IOException {
        // 创建选择器
        Selector selector = Selector.open();

        // 创建服务器通道并绑定端口
        ServerSocketChannel serverChannel = ServerSocketChannel.open();
        serverChannel.bind(new InetSocketAddress(SERVER_PORT));
        serverChannel.configureBlocking(false);

        // 将服务器通道注册到选择器上并监听连接事件
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);

        System.out.println("Server started. Listening on port " + SERVER_PORT);

        while (true) {
            // 阻塞等待就绪的事件
            selector.select();

            // 获取选择器中所有已就绪的事件
            Iterator<SelectionKey> keyIterator = selector.selectedKeys().iterator();

            while (keyIterator.hasNext()) {
                SelectionKey key = keyIterator.next();
                keyIterator.remove();

                if (key.isAcceptable()) {
                    // 处理新连接事件
                    handleAcceptable(key, selector);
                }

                if (key.isReadable()) {
                    // 处理可读事件
                    handleReadable(key);
                }
            }
        }
    }

    private static void handleAcceptable(SelectionKey key, Selector selector) throws IOException {
        ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
        SocketChannel clientChannel = serverChannel.accept();
        clientChannel.configureBlocking(false);

        // 将客户端通道注册到选择器上并监听可读事件
        clientChannel.register(selector, SelectionKey.OP_READ);
        System.out.println("New client connected: " + clientChannel.getRemoteAddress());

        // 响应客户端
        String response = "Hello Client";
        ByteBuffer responseBuffer = ByteBuffer.wrap(response.getBytes());
        clientChannel.write(responseBuffer);
    }

    private static void handleReadable(SelectionKey key) throws IOException {
        SocketChannel clientChannel = (SocketChannel) key.channel();
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        int bytesRead = clientChannel.read(buffer);

        if (bytesRead == -1) {
            // 客户端关闭连接
            clientChannel.close();
            System.out.println("Client disconnected: " + clientChannel.getRemoteAddress());
            return;
        }

        buffer.flip();
        byte[] data = new byte[buffer.limit()];
        buffer.get(data);
        String message = new String(data);
        System.out.println("Received from client: " + message);

        // 处理客户端发送的消息
    }
}
