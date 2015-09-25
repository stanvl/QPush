package com.argo.qpush.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.bytes.ByteArrayDecoder;
import io.netty.handler.codec.bytes.ByteArrayEncoder;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

/**
 * Created by yamingd on 9/24/15.
 */
public class ClientConnection {

    protected static Logger logger = LoggerFactory.getLogger(ClientConnection.class);

    private Properties config;
    private NioEventLoopGroup nioEventLoopGroup;
    private String host;
    private Integer port;
    private ChannelFuture connectFuture;

    public ClientConnection(Properties config, NioEventLoopGroup loopGroup) {
        this.config = config;
        this.nioEventLoopGroup = loopGroup;

        port = Integer.parseInt(config.getProperty("port", "8081"));
        host = config.getProperty("host", "127.0.0.1");

    }

    /**
     *
     */
    public synchronized void connect(){

        Bootstrap b = new Bootstrap();
        final ClientConnection clientConnection = this;

        b.group(this.nioEventLoopGroup); // (2)
        b.channel(NioSocketChannel.class); // (3)
        b.option(ChannelOption.SO_KEEPALIVE, true); // (4)
        b.option(ChannelOption.TCP_NODELAY, true);
        b.option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT);
        b.option(ChannelOption.AUTO_CLOSE, false);

        b.handler(new ChannelInitializer<SocketChannel>() {

            @Override
            public void initChannel(SocketChannel ch) throws Exception {

                ChannelPipeline pipeline = ch.pipeline();
                pipeline.addLast("frameDecoder", new LengthFieldBasedFrameDecoder(Integer.MAX_VALUE, 0, 4, 0, 4));
                pipeline.addLast("bytesDecoder", new ByteArrayDecoder());

                pipeline.addLast("frameEncoder", new LengthFieldPrepender(4, false));
                pipeline.addLast("bytesEncoder", new ByteArrayEncoder());

                pipeline.addLast("handler", new ClientConnectHandler(clientConnection));
            }
        });

        logger.info("QPush server. connecting... host=" + host + "/" + port);
        this.connectFuture = b.connect(this.host, this.port);
        this.connectFuture.addListener(new GenericFutureListener<ChannelFuture>() {

            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                if (!future.isSuccess()){
                    logger.error("Connect Error.", future.cause());
                }
            }
        });
    }

    /**
     * 发送消息
     * @param bytes
     */
    public void send(final byte[] bytes, final GenericFutureListener<? extends Future<? super Void>> listener){

        final ClientConnection clientConnection = this;

        this.connectFuture.channel().eventLoop().execute(new Runnable() {

            @Override
            public void run() {

                clientConnection.connectFuture.channel().writeAndFlush(bytes).addListener(listener);

            }
        });
    }

    /**
     * 停止
     */
    public void shutdown(){
        if (connectFuture.isCancellable()){
            connectFuture.cancel(true);
        }else{
            connectFuture.channel().close();
        }
    }
}