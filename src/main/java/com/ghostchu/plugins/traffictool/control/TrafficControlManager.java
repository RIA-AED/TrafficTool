package com.ghostchu.plugins.traffictool.control;

import com.ghostchu.plugins.traffictool.TrafficTool;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.Player;
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
    private final ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);

    public TrafficControlManager(TrafficTool plugin) {
        this.plugin = plugin;
        globalTrafficHandler = new GlobalTrafficShapingHandler(Executors.newScheduledThreadPool(plugin.getConfig().getInt("global-traffic-handler.scheduled-thread-pool-core-pool-size")), 1000);
        injectEveryoneAlreadyInServer();
    }


    public void injectEveryoneAlreadyInServer() {
        plugin.getServer().getAllPlayers().forEach(p -> {
            if (p.getCurrentServer().isPresent()) {
                String serverName = p.getCurrentServer().get().getServerInfo().getName();
                injectPlayer(p, serverName);
            }

        });
    }

    public Optional<ChannelTrafficShapingHandler> getPlayerTrafficShapingHandler(ConnectedPlayer connectedPlayer) {
        ChannelHandler handler = connectedPlayer.getConnection().getChannel().pipeline().get(CHANNEL_TRAFFIC_HANDLER_NAME);
        if (handler instanceof ChannelTrafficShapingHandler) {
            return Optional.of((ChannelTrafficShapingHandler) handler);
        }
        return Optional.empty();
    }

    public Map<UUID, TrafficController> getTrafficController() {
        return trafficController;
    }

    public GlobalTrafficShapingHandler getGlobalTrafficHandler() {
        return globalTrafficHandler;
    }

    public TrafficShapingRule getTrafficShapingRule(Player player, String serverName) {
        if (player.hasPermission("traffictool.bypass.shaping")) {
            return new TrafficShapingRule(true, 0, 0, 0, 0);
        }
        if (plugin.getConfig().getStringList("ignored-servers").contains(serverName)) {
            return new TrafficShapingRule(false, 0, 0, 0, 0);
        }
        long avgWriteLimit = plugin.getConfig().getLong("player-traffic-shaping.avg.writeLimit");
        long avgDuration = plugin.getConfig().getLong("player-traffic-shaping.avg.min-duration");
        long burstWriteLimit = plugin.getConfig().getLong("player-traffic-shaping.burst.writeLimit");
        long burstDuration = plugin.getConfig().getLong("player-traffic-shaping.burst.max-duration");
        return new TrafficShapingRule(false, avgWriteLimit, avgDuration, burstWriteLimit, burstDuration);
    }

    @Subscribe(order = PostOrder.LAST)
    public void playerConnected(ServerConnectedEvent event) {
        injectPlayer(event.getPlayer(), event.getServer().getServerInfo().getName());
    }

    @Subscribe(order = PostOrder.LAST)
    public void playerDisconnected(DisconnectEvent event) {
        TrafficController controller = trafficController.get(event.getPlayer().getUniqueId());
        if (controller != null) {
            controller.stop();
        }
    }

    public void injectPlayer(Player player, String serverName) {
        synchronized (this){
        if (player instanceof ConnectedPlayer connectedPlayer) {
            ChannelTrafficShapingHandler handler;
            Optional<ChannelTrafficShapingHandler> handlerOptional = getPlayerTrafficShapingHandler(connectedPlayer);
            handler = handlerOptional.orElseGet(() -> {
                ChannelTrafficShapingHandler injectedHandler = injectConnection(connectedPlayer.getConnection());
                plugin.getLogger().info("Injected ChannelTrafficShapingHandler to player {}", player.getUsername());
                return injectedHandler;
            });
            TrafficShapingRule rule = getTrafficShapingRule(connectedPlayer, serverName);
            Channel channel = connectedPlayer.getConnection().getChannel();
            TrafficController controller = trafficController.get(connectedPlayer.getUniqueId());
            if (controller == null) {
                controller = new TrafficController(this, rule, connectedPlayer, channel, handler);
                plugin.getLogger().info("Installed TrafficController to player {}", player.getUsername());
            } else {
                controller.stop();
                controller = new TrafficController(this, rule, connectedPlayer, channel, handler);
                plugin.getLogger().info("Updated traffic controller for player {}", connectedPlayer.getUsername());
            }
            trafficController.put(connectedPlayer.getUniqueId(), controller);
            controller.start();
        }
        }
    }

    public void uninjectPlayer(Player player) {
        synchronized (this){
            if (player instanceof ConnectedPlayer connectedPlayer) {
                TrafficController controller = trafficController.get(connectedPlayer.getUniqueId());
                if(controller != null){
                    controller.stop();
                }
                uninject(((ConnectedPlayer) player).getConnection());
            }
        }
    }

    public ChannelTrafficShapingHandler injectConnection(MinecraftConnection minecraftConnection) {
        uninject(minecraftConnection);
        minecraftConnection.getChannel().pipeline().addLast(GLOBAL_TRAFFIC_HANDLER_NAME, globalTrafficHandler);
        ChannelTrafficShapingHandler channelTrafficShapingHandler = new ChannelTrafficShapingHandler(1000);
        minecraftConnection.getChannel().pipeline().addLast(CHANNEL_TRAFFIC_HANDLER_NAME, channelTrafficShapingHandler);
        return channelTrafficShapingHandler;
    }

    public void uninject(MinecraftConnection minecraftConnection) {
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
    }

    public static class TrafficController {

        private final TrafficControlManager parent;
        private final Channel channel;
        private final ChannelTrafficShapingHandler shaper;
        private final TrafficShapingRule rule;
        private final AtomicLong currentBurstDuration = new AtomicLong();
        private final AtomicLong currentAvgDuration = new AtomicLong();
        private final ConnectedPlayer player;
        private final Runnable runnable;
        private volatile boolean start = false;
        private final AtomicBoolean inBurstRestriction = new AtomicBoolean();

        public TrafficController(TrafficControlManager parent, TrafficShapingRule rule, ConnectedPlayer player, Channel channel, ChannelTrafficShapingHandler shaper) {
            this.parent = parent;
            this.rule = rule;
            this.player = player;
            this.channel = channel;
            this.shaper = shaper;
            this.runnable = () -> {
                    // 验证 channel 有效
                    if (!player.isActive() || !channel.isActive()) {
                        shutdownController();
                        return;
                    }

                    if(!start){
                        shutdownController();
                        return;
                    }

                    // 检查是否存在手动覆写
                    if (shaper.getWriteLimit() != 0 && shaper.getWriteLimit() != rule.getBurstWriteLimit() && shaper.getWriteLimit() != rule.getAvgWriteLimit()) {
                        // 不允许更改
                        currentAvgDuration.set(-1);
                        currentBurstDuration.set(-1);
                        inBurstRestriction.set(false);
                        return;
                    }

                    if (shaper.trafficCounter().lastWriteThroughput() >= rule.getAvgWriteLimit()) {
                        currentBurstDuration.incrementAndGet();
                        if (currentAvgDuration.decrementAndGet() < 0) {
                            currentAvgDuration.set(0);
                        }
                    } else {
                        currentAvgDuration.incrementAndGet();
                        if (currentBurstDuration.decrementAndGet() < 0) {
                            currentBurstDuration.set(0);
                        }
                    }

                    if (currentBurstDuration.get() >= rule.getMaxBurstDuration()) {
                        inBurstRestriction.set(true);
                        currentAvgDuration.set(0);
                    }

                    if (currentAvgDuration.get() >= rule.getMinAvgDuration()) {
                        inBurstRestriction.set(false);
                        currentBurstDuration.set(0);
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
            };
        }

        public Channel getChannel() {
            return channel;
        }

        private void shutdownController() {
            parent.executor.remove(this.runnable);
            this.start = false;
        }

        public void stop() {
            shutdownController();
            this.start = false;
        }

        public boolean isStarted() {
            return this.start;
        }

        public void start() {
            this.start = true;
            parent.executor.scheduleAtFixedRate(this.runnable, 0, 1000, TimeUnit.MILLISECONDS);
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

        @Override
        public String toString() {
            return "TrafficController{" +
                    "currentBurstDuration=" + currentBurstDuration.get() +
                    ", currentAvgDuration=" + currentAvgDuration.get() +
                    ", inBurstRestriction=" + inBurstRestriction.get() +
                    '}';
        }
    }
}