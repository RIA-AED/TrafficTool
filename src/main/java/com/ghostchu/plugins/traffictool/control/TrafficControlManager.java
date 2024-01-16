package com.ghostchu.plugins.traffictool.control;

import com.ghostchu.plugins.traffictool.TrafficTool;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.proxy.connection.MinecraftConnection;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import io.netty.channel.ChannelHandler;
import io.netty.handler.traffic.ChannelTrafficShapingHandler;
import io.netty.handler.traffic.GlobalTrafficShapingHandler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.Optional;
import java.util.concurrent.Executors;

public class TrafficControlManager {
    private static final String GLOBAL_TRAFFIC_HANDLER_NAME = "traffictool-global-traffic-handler";
    private static final String CHANNEL_TRAFFIC_HANDLER_NAME = "traffictool-channel-traffic-handler";
    private static final String GLOBAL_COMPRESSION_METRIC_NAME = "traffictool-global-compression-metric-handler";
    private final TrafficTool plugin;
    private final GlobalTrafficShapingHandler globalTrafficHandler;

    public TrafficControlManager(TrafficTool plugin) {
        this.plugin = plugin;
        globalTrafficHandler = new GlobalTrafficShapingHandler(Executors.newScheduledThreadPool(plugin.getConfig().getInt("global-traffic-handler.scheduled-thread-pool-core-pool-size")), plugin.getConfig().getLong("global-traffic-handler.check-interval"));
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
            return new TrafficShapingRule(true, 0, 0);
        }
        if (player.getCurrentServer().isPresent()) {
            ServerConnection serverConnection = player.getCurrentServer().get();
            String serverName = serverConnection.getServer().getServerInfo().getName();
            if (plugin.getConfig().getStringList("ignored-servers").contains(serverName)) {
                return new TrafficShapingRule(false, 0, 0);
            }
        }
        long writeLimit = plugin.getConfig().getLong("player-traffic-shaping.writeLimit");
        long readLimit = plugin.getConfig().getLong("player-traffic-shaping.readLimit");
        return new TrafficShapingRule(false, writeLimit, readLimit);
    }

    @Subscribe(order = PostOrder.LAST)
    public void playerConnected(ServerConnectedEvent event) {
        injectPlayer(event.getPlayer());
    }

    public void injectPlayer(Player player) {
        if (player instanceof ConnectedPlayer connectedPlayer) {
            ChannelTrafficShapingHandler handler;
            Optional<ChannelTrafficShapingHandler> handlerOptional = getPlayerTrafficShapingHandler(connectedPlayer);
            handler = handlerOptional.orElseGet(() -> injectConnection(connectedPlayer.getConnection()));
            TrafficShapingRule rule = getTrafficShapingRule(connectedPlayer);
            boolean anyUpdate = handler.getWriteLimit() != rule.getWriteLimit();
            if (handler.getReadLimit() != rule.getReadLimit()) anyUpdate = true;
            handler.setWriteLimit(rule.getWriteLimit());
            handler.setReadLimit(rule.getReadLimit());
            String writeLimitString = "无整形";
            String readLimitString = "无整形";
            if (rule.getWriteLimit() != 0) {
                writeLimitString = TrafficTool.humanReadableByteCount(rule.getWriteLimit(), false) + "/" + handler.getCheckInterval() + "ms";
            }
            if (rule.getReadLimit() != 0) {
                readLimitString = TrafficTool.humanReadableByteCount(rule.getReadLimit(), false) + "/" + handler.getCheckInterval() + "ms";
            }
            plugin.getLogger().info("Rule for player {}: W: {} R:{}, Bypass: {}", connectedPlayer.getUsername(), rule.getWriteLimit(), rule.getReadLimit(), rule.isBypass());
            if (plugin.getConfig().getBoolean("send-traffic-rule-update-notification") && anyUpdate) {
                StringBuilder builder = new StringBuilder();
                builder.append("已应用的流量整型规则如下：").append("\n");
                builder.append("\n");
                builder.append("写限制: ").append(writeLimitString).append("\n");
                builder.append("读限制: ").append(readLimitString).append("\n");
                builder.append("\n");
                builder.append("使用 /traffic me 查看您的连接的详细信息");
                String base = "[TrafficTool] 您的连接的流量整形规则已更新";
                if (rule.isBypass()) {
                    base = "[TrafficTool] 您的连接的流量整形规则已更新（管理员豁免）";
                }
                player.sendMessage(
                        Component.text(base)
                                .color(NamedTextColor.DARK_GRAY)
                                .hoverEvent(HoverEvent.showText(Component.text(builder.toString())))
                );
            }
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
        ChannelTrafficShapingHandler channelTrafficShapingHandler = new ChannelTrafficShapingHandler(plugin.getConfig().getLong("channel-traffic-handler.check-interval"));
        minecraftConnection.getChannel().pipeline().addLast(CHANNEL_TRAFFIC_HANDLER_NAME, channelTrafficShapingHandler);
        return channelTrafficShapingHandler;
    }

}
