package com.ghostchu.plugins.traffictool.control;

import com.ghostchu.plugins.traffictool.TrafficTool;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.proxy.connection.MinecraftConnection;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.handler.traffic.ChannelTrafficShapingHandler;
import io.netty.handler.traffic.GlobalTrafficShapingHandler;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class TrafficControlManager {
    private static final String GLOBAL_TRAFFIC_HANDLER_NAME = "traffictool-global-traffic-handler";
    private static final String CHANNEL_TRAFFIC_HANDLER_NAME = "traffictool-channel-traffic-handler";
    private static final String GLOBAL_COMPRESSION_METRIC_NAME = "traffictool-global-compression-metric-handler";
    private final TrafficTool plugin;
    private final GlobalTrafficShapingHandler globalTrafficHandler;
    private final Map<UUID, TrafficController> trafficController = new ConcurrentSkipListMap<>();

    public TrafficControlManager(TrafficTool plugin) {
        this.plugin = plugin;
        globalTrafficHandler = new GlobalTrafficShapingHandler(Executors.newScheduledThreadPool(plugin.getConfig().getInt("global-traffic-handler.scheduled-thread-pool-core-pool-size")), 1000);
        injectEveryoneAlreadyInServer();
    }


    public void injectEveryoneAlreadyInServer() {
        plugin.getServer().getAllPlayers().forEach(this::injectPlayer);
    }

    public Optional<ChannelTrafficShapingHandler> getPlayerTrafficShapingHandler(ConnectedPlayer connectedPlayer) {
        ChannelHandler handler = connectedPlayer.getConnection().getChannel().pipeline().get(CHANNEL_TRAFFIC_HANDLER_NAME);
        if (handler instanceof ChannelTrafficShapingHandler) {
            return Optional.of((ChannelTrafficShapingHandler) handler);
        }
        return Optional.empty();
    }

    public GlobalTrafficShapingHandler getGlobalTrafficHandler() {
        return globalTrafficHandler;
    }

    public TrafficShapingRule getTrafficShapingRule(Player player) {
        if (player.hasPermission("traffictool.bypass.shaping")) {
            return new TrafficShapingRule(true, 0, 0, 0,0);
        }
        if (player.getCurrentServer().isPresent()) {
            ServerConnection serverConnection = player.getCurrentServer().get();
            String serverName = serverConnection.getServer().getServerInfo().getName();
            if (plugin.getConfig().getStringList("ignored-servers").contains(serverName)) {
                return new TrafficShapingRule(false, 0, 0, 0,0);
            }
        }
        long avgWriteLimit = plugin.getConfig().getLong("player-traffic-shaping.avg.writeLimit");
        long avgDuration = plugin.getConfig().getLong("player-traffic-shaping.avg.min-duration");
        long burstWriteLimit = plugin.getConfig().getLong("player-traffic-shaping.burst.writeLimit");
        long burstDuration = plugin.getConfig().getLong("player-traffic-shaping.burst.max-duration");
        return new TrafficShapingRule(false, avgWriteLimit, avgDuration, burstWriteLimit, burstDuration);
    }

    @Subscribe(order = PostOrder.LAST)
    public void playerConnected(ServerConnectedEvent event) {
        injectPlayer(event.getPlayer());
    }

    @Subscribe(order = PostOrder.LAST)
    public void playerDisconnected(DisconnectEvent event) {
        TrafficController controller = trafficController.get(event.getPlayer().getUniqueId());
        if (controller != null) {
            controller.stop();
        }
    }

    public void injectPlayer(Player player) {
        if (player instanceof ConnectedPlayer connectedPlayer) {
            ChannelTrafficShapingHandler handler;
            Optional<ChannelTrafficShapingHandler> handlerOptional = getPlayerTrafficShapingHandler(connectedPlayer);
            handler = handlerOptional.orElseGet(() -> {
                ChannelTrafficShapingHandler injectedHandler =  injectConnection(connectedPlayer.getConnection());
                plugin.getLogger().info("Injected ChannelTrafficShapingHandler to player {}", player.getUsername());
                return injectedHandler;
            });
            TrafficShapingRule rule = getTrafficShapingRule(connectedPlayer);
            Channel channel = connectedPlayer.getConnection().getChannel();
            TrafficController controller = trafficController.get(connectedPlayer.getUniqueId());
            if (controller == null) {
                controller = new TrafficController(this, rule, connectedPlayer, channel, handler);
                plugin.getLogger().info("Installed TrafficController to player {}", player.getUsername());
            } else {
                    controller.stop();
                    controller = new TrafficController(this, rule, connectedPlayer, channel, handler);
                    plugin.getLogger().warn("Recreated traffic controller for player {}", connectedPlayer.getUsername());
            }
            trafficController.put(connectedPlayer.getUniqueId(), controller);
            controller.start();
        }
    }

    public ChannelTrafficShapingHandler injectConnection(MinecraftConnection minecraftConnection) {
        if (minecraftConnection.getChannel().pipeline().get(GLOBAL_TRAFFIC_HANDLER_NAME) != null) {
            minecraftConnection.getChannel().pipeline().remove(GLOBAL_TRAFFIC_HANDLER_NAME);
        }
        if (minecraftConnection.getChannel().pipeline().get(CHANNEL_TRAFFIC_HANDLER_NAME) != null) {
            minecraftConnection.getChannel().pipeline().remove(CHANNEL_TRAFFIC_HANDLER_NAME);
        }
        // 生产环境移除遗留模块的 pipeline
        if (minecraftConnection.getChannel().pipeline().get(GLOBAL_COMPRESSION_METRIC_NAME) != null) {
            minecraftConnection.getChannel().pipeline().remove(GLOBAL_COMPRESSION_METRIC_NAME);
        }
        minecraftConnection.getChannel().pipeline().addLast(GLOBAL_TRAFFIC_HANDLER_NAME, globalTrafficHandler);
        ChannelTrafficShapingHandler channelTrafficShapingHandler = new ChannelTrafficShapingHandler(1000);
        minecraftConnection.getChannel().pipeline().addLast(CHANNEL_TRAFFIC_HANDLER_NAME, channelTrafficShapingHandler);
        return channelTrafficShapingHandler;
    }

    static class TrafficController {
        private final ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
        private final TrafficControlManager parent;
        private final Channel channel;
        private final ChannelTrafficShapingHandler shaper;
        private final TrafficShapingRule rule;
        private final AtomicLong currentBurstDuration = new AtomicLong();
        private final AtomicLong currentAvgDuration = new AtomicLong();
        private final ConnectedPlayer player;
        private volatile boolean start = false;
        private final AtomicBoolean inBurstRestriction = new AtomicBoolean();

        public TrafficController(TrafficControlManager parent, TrafficShapingRule rule, ConnectedPlayer player, Channel channel, ChannelTrafficShapingHandler shaper) {
            this.parent = parent;
            this.rule = rule;
            this.player = player;
            this.channel = channel;
            this.shaper = shaper;
        }

        public Channel getChannel() {
            return channel;
        }

        private void shutdownController() {
            executor.shutdownNow();
            this.start = false;
        }

        public void stop() {
            shutdownController();
            this.start = false;
        }

        public boolean isStarted(){
            return this.start;
        }

        public void start() {
            executor.scheduleAtFixedRate(() -> {
                // 验证 channel 有效
                if (!player.isActive() || !channel.isActive()) {
                    shutdownController();
                    return;
                }

                // 检查是否存在手动覆写
                if(shaper.getWriteLimit() != 0 && shaper.getWriteLimit() != rule.getBurstWriteLimit() && shaper.getWriteLimit() != rule.getAvgWriteLimit()){
                    // 不允许更改
                    currentAvgDuration.set(0);
                    currentBurstDuration.set(0);
                    inBurstRestriction.set(false);
                    return;
                }

                if(shaper.trafficCounter().getRealWriteThroughput() > rule.getAvgWriteLimit()){
                    currentBurstDuration.incrementAndGet();
                    currentAvgDuration.decrementAndGet();
                }else{
                    currentAvgDuration.incrementAndGet();
                    currentBurstDuration.decrementAndGet();
                }

                if(currentBurstDuration.get() > rule.getMaxBurstDuration()){
                    // 重置 avg 计数器
                    currentAvgDuration.set(0);
                    // 陷入爆发管控阶段
                    inBurstRestriction.set(true);
                }

                if(currentAvgDuration.get() > rule.getMinAvgDuration()){
                    // 重置爆发计数器
                    currentBurstDuration.set(0);
                    // 解除爆发管控
                    inBurstRestriction.set(false);
                }

                // 应用限速规则
                long writeLimit;
                if (inBurstRestriction.get()) {
                    writeLimit = rule.getAvgWriteLimit();
                } else {
                    writeLimit = rule.getBurstWriteLimit();
                }
                if (configure(writeLimit, shaper)) {
                    parent.plugin.getLogger().info("已更新 {} 的流量整形规则: WriteLimit={}/s, CurrentBurstDuration={}, InBurstRestriction={}",
                            player.getUsername(), TrafficTool.humanReadableByteCount(writeLimit, false),
                            currentBurstDuration.get(), inBurstRestriction.get());
                }
            }, 0, 1000, TimeUnit.MILLISECONDS);
            this.start = true;
        }

        private boolean configure(long writeLimit, ChannelTrafficShapingHandler handler) {
            long appliedLimit = handler.getWriteLimit();
            if (writeLimit != appliedLimit) {
                handler.setWriteLimit(writeLimit);
                return true;
            }
            return false;
        }

        private List<ConnectedPlayer> allConnectedPlayers() {
            return parent.plugin.getServer()
                    .getAllPlayers()
                    .stream()
                    .filter(p -> p instanceof ConnectedPlayer)
                    .map(p -> (ConnectedPlayer) p)
                    .collect(Collectors.toList());
        }
    }

}
