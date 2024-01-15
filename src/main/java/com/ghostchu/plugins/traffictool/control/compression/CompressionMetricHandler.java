package com.ghostchu.plugins.traffictool.control.compression;

import com.aayushatharva.brotli4j.Brotli4jLoader;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

public class CompressionMetricHandler  extends MessageToByteEncoder<ByteBuf> {
    private final CompressionManager manager;

    public CompressionMetricHandler(CompressionManager manager){
        this.manager = manager;
    }

    @Override
    protected void encode(ChannelHandlerContext channelHandlerContext, ByteBuf byteBuf, ByteBuf byteBuf2) {
        // do nothing
        ByteBuf copied = byteBuf.copy();
        byteBuf2.writeBytes(byteBuf);
        manager.handleByteBuf(copied);
    }
}
