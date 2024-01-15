package com.ghostchu.plugins.traffictool.control;

import com.ghostchu.plugins.traffictool.TrafficTool;
import com.ghostchu.plugins.traffictool.control.compression.CompressionManager;
import com.ghostchu.plugins.traffictool.control.compression.CompressionMetricHandler;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.InboundConnection;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.proxy.connection.MinecraftConnection;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import com.velocitypowered.proxy.connection.client.InitialInboundConnection;
import com.velocitypowered.proxy.connection.client.LoginInboundConnection;
import io.netty.channel.ChannelHandler;
import io.netty.handler.traffic.ChannelTrafficShapingHandler;
import io.netty.handler.traffic.GlobalTrafficShapingHandler;
import io.netty.handler.traffic.TrafficCounter;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class TrafficControlManager {
    private static final String GLOBAL_TRAFFIC_HANDLER_NAME = "traffictool-global-traffic-handler";
    private static final String CHANNEL_TRAFFIC_HANDLER_NAME = "traffictool-channel-traffic-handler";
    private static final String GLOBAL_COMPRESSION_METRIC_NAME = "traffictool-global-compression-metric-handler";
    private final TrafficTool plugin;
    private final GlobalTrafficShapingHandler globalTrafficHandler;
    private final List<WeakReference<ChannelTrafficShapingHandler>> channelTrafficHandler = new CopyOnWriteArrayList<>();
    private CompressionMetricHandler handler;

    public TrafficControlManager(TrafficTool plugin, CompressionManager compressionManager) {
        this.plugin = plugin;
        this.handler = new CompressionMetricHandler(compressionManager);
        globalTrafficHandler = new GlobalTrafficShapingHandler(Executors.newScheduledThreadPool(plugin.getConfig().getInt("global-traffic-handler.scheduled-thread-pool-core-pool-size")), plugin.getConfig().getLong("global-traffic-handler.check-interval"));
        plugin.getServer().getScheduler().buildTask(plugin, () -> {
            channelTrafficHandler.removeIf(weakRef -> weakRef.get() == null);
        }).repeat(1, TimeUnit.MINUTES).schedule();
        plugin.getServer().getScheduler().buildTask(plugin, () -> {
            channelTrafficHandler.removeIf(weakRef -> weakRef.get() == null);
        }).repeat(1, TimeUnit.SECONDS).schedule();
        plugin.getServer().getAllPlayers().forEach(p -> {
            if (!(p instanceof ConnectedPlayer connectedPlayer)) {
                return;
            }
            injectConnection(connectedPlayer.getConnection());
        });
    }

    public Optional<ChannelTrafficShapingHandler> getPlayerTrafficShapingHandler(Player player) {
        if (!(player instanceof ConnectedPlayer)) {
            return Optional.empty();
        }
        ConnectedPlayer connectedPlayer = (ConnectedPlayer) player;
        ChannelHandler handler = connectedPlayer.getConnection().getChannel().pipeline().get(CHANNEL_TRAFFIC_HANDLER_NAME);
        if (handler instanceof ChannelTrafficShapingHandler) {
            return Optional.of((ChannelTrafficShapingHandler) handler);
        }
        return Optional.empty();
    }

    private void a() {
        TrafficCounter counter;
        ChannelTrafficShapingHandler handler;
    }

    public GlobalTrafficShapingHandler getGlobalTrafficHandler() {
        return globalTrafficHandler;
    }

    @Subscribe(order = PostOrder.LAST)
    public void playerConnected(PreLoginEvent event) {
        if (!event.getResult().isAllowed()) return;
        InboundConnection inbound = event.getConnection();
        MinecraftConnection minecraftConnection = null;
        if (inbound instanceof ConnectedPlayer) {
            minecraftConnection = ((ConnectedPlayer) inbound).getConnection();
        } else if (inbound instanceof InitialInboundConnection) {
            minecraftConnection = ((InitialInboundConnection) inbound).getConnection();
        } else if (inbound instanceof LoginInboundConnection) {
            try {
                LoginInboundConnection loginInboundConnection = (LoginInboundConnection) inbound;
                Field delegate = loginInboundConnection.getClass().getDeclaredField("delegate");
                delegate.setAccessible(true);
                InitialInboundConnection initialInboundConnection = (InitialInboundConnection) delegate.get(loginInboundConnection);
                minecraftConnection = initialInboundConnection.getConnection();
            } catch (NoSuchFieldException | IllegalAccessException e) {
                e.printStackTrace();
            }
        }
        if (minecraftConnection == null) {
            plugin.getLogger().warn("无法为 {} (连接：{}) 初始化 MinecraftConnection 实例以创建 Pipeline 工具，将无法跟踪其流量消耗", event.getUsername(), event.getConnection());
            return;
        }
        injectConnection(minecraftConnection);
    }
    @Subscribe(order = PostOrder.LAST)
    public void playerConnected(ServerConnectedEvent event) {
       Player player = event.getPlayer();
       if(player instanceof ConnectedPlayer connectedPlayer){
           injectConnection(connectedPlayer.getConnection());
       }
    }

    public void injectConnection(MinecraftConnection minecraftConnection) {
        if (minecraftConnection.getChannel().pipeline().get(GLOBAL_TRAFFIC_HANDLER_NAME) != null) {
            minecraftConnection.getChannel().pipeline().remove(GLOBAL_TRAFFIC_HANDLER_NAME);
        }
        if (minecraftConnection.getChannel().pipeline().get(CHANNEL_TRAFFIC_HANDLER_NAME) != null) {
            minecraftConnection.getChannel().pipeline().remove(CHANNEL_TRAFFIC_HANDLER_NAME);
        }
        minecraftConnection.getChannel().pipeline().addLast(GLOBAL_TRAFFIC_HANDLER_NAME, globalTrafficHandler);
        ChannelTrafficShapingHandler channelTrafficShapingHandler = new ChannelTrafficShapingHandler(plugin.getConfig().getLong("channel-traffic-handler.check-interval"));
        minecraftConnection.getChannel().pipeline().addLast(CHANNEL_TRAFFIC_HANDLER_NAME, channelTrafficShapingHandler);
        startMetricForCompression(minecraftConnection);
    }

    private void startMetricForCompression(MinecraftConnection minecraftConnection) {
        if (minecraftConnection.getChannel().pipeline().get(GLOBAL_COMPRESSION_METRIC_NAME) != null) {
            minecraftConnection.getChannel().pipeline().remove(GLOBAL_COMPRESSION_METRIC_NAME);
        }
        if (minecraftConnection.getChannel().pipeline().get("compression-decoder") != null) {
            plugin.getLogger().info("压缩比开始统计！");
            minecraftConnection.getChannel().pipeline().addBefore("compression-decoder", GLOBAL_COMPRESSION_METRIC_NAME, this.handler);
        }
    }

}
