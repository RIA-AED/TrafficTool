package com.ghostchu.plugins.traffictool.control.compression;

import com.aayushatharva.brotli4j.Brotli4jLoader;
import com.aayushatharva.brotli4j.encoder.Encoder;
import com.ghostchu.plugins.traffictool.TrafficTool;
import com.velocitypowered.natives.compression.VelocityCompressor;
import com.velocitypowered.natives.util.Natives;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.ReferenceCountUtil;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.DataFormatException;

public class CompressionManager {
    private final VelocityCompressor velocityCompressor;
    private final int velocityThreshold;
    public ExecutorService compressPool = Executors.newWorkStealingPool(6);
    public AtomicLong totalLengthHandled = new AtomicLong();
    public AtomicLong velocitySavedBytes = new AtomicLong();
    public AtomicLong velocityWasteCount = new AtomicLong();
    public AtomicLong velocityWasteBytes = new AtomicLong();
    public AtomicLong brotilSavedBytes = new AtomicLong();
    public AtomicLong brotilWasteCount = new AtomicLong();
    public AtomicLong brotilWasteBytes = new AtomicLong();

    public CompressionManager(TrafficTool plugin) {
        Brotli4jLoader.ensureAvailability();
        this.velocityCompressor = Natives.compress.get().create(plugin.getServer().getConfiguration().getCompressionLevel());
        this.velocityThreshold = plugin.getServer().getConfiguration().getCompressionThreshold();
    }

    public void handleByteBuf(ByteBuf clone) {
        try {
            handleVelocity(clone.copy());
            handleBrotil(clone.copy());
            totalLengthHandled.addAndGet(clone.readableBytes());
        } finally {
            ReferenceCountUtil.safeRelease(clone);
        }
    }

    private void handleBrotil(ByteBuf clone) {
        compressPool.submit(() -> {
            try {
                byte[] bytes = new byte[clone.readableBytes()];
                clone.readBytes(bytes);
                byte[] compressed = Encoder.compress(bytes, Encoder.Parameters.DEFAULT.setQuality(11).setMode(Encoder.Mode.GENERIC));
                long saved = bytes.length - compressed.length;
                brotilSavedBytes.addAndGet(saved);
                if (saved <= 0) {
                    brotilWasteCount.incrementAndGet();
                    brotilWasteBytes.addAndGet(Math.abs(saved));
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                ReferenceCountUtil.safeRelease(clone);
            }
        });
    }

    private void handleVelocity(ByteBuf clone) {
        ByteBuf result = Unpooled.buffer((int)(clone.capacity()*1.5));
        compressPool.submit(() -> {
            try {
                long originalLength = clone.readableBytes();
                if(originalLength < this.velocityThreshold){
                    return;
                }
                velocityCompressor.deflate(clone, result);
                long saved = originalLength - result.readableBytes();
                velocitySavedBytes.addAndGet(saved);
                if (saved <= 0) {
                    velocityWasteCount.incrementAndGet();
                    velocityWasteBytes.addAndGet(Math.abs(saved));
                }
            } catch (DataFormatException e) {
                e.printStackTrace();
            } finally {
                ReferenceCountUtil.safeRelease(clone);
                ReferenceCountUtil.safeRelease(result);
            }
        });
    }
}
