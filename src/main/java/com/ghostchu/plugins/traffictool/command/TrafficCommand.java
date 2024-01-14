package com.ghostchu.plugins.traffictool.command;

import com.ghostchu.plugins.traffictool.TrafficTool;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import io.netty.handler.traffic.ChannelTrafficShapingHandler;
import io.netty.handler.traffic.GlobalTrafficShapingHandler;
import io.netty.handler.traffic.TrafficCounter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class TrafficCommand implements SimpleCommand {

    private final TrafficTool plugin;

    public TrafficCommand(TrafficTool plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(final Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();
        if (args.length < 2) {
            source.sendMessage(Component.text("错误的命令使用方法，请传递参数 view 或 config"));
            return;
        }
        if (args[0].equalsIgnoreCase("view")) {
            if (!source.hasPermission("traffictool.view")) {
                source.sendMessage(Component.text("权限不足！").color(NamedTextColor.RED));
                return;
            }
            if (args[1].equalsIgnoreCase("global")) {
                viewGlobal(invocation);
            }
            if (args[1].equalsIgnoreCase("player")) {
                viewPlayer(invocation);
            }
        } else if (args[0].equalsIgnoreCase("config")) {
            if (!source.hasPermission("traffictool.config")) {
                source.sendMessage(Component.text("权限不足！").color(NamedTextColor.RED));
                return;
            }
            if (args.length < 3) {
                source.sendMessage(Component.text("错误的命令使用方法 /traffic config <global/player> [player:name] <writeLimit> <readLimit>"));
                return;
            }
            if (args[1].equalsIgnoreCase("global")) {
                configGlobal(invocation);
            }
            if (args[1].equalsIgnoreCase("player")) {
                configPlayer(invocation);
            }
        }
    }

    private void configPlayer(Invocation invocation) {
        String playerS = invocation.arguments()[2];
        long writeLimit = Long.parseLong(invocation.arguments()[3]);
        long readLimit = Long.parseLong(invocation.arguments()[4]);
        Player player = plugin.getServer().getPlayer(playerS).orElse(null);
        if (player == null) {
            invocation.source().sendMessage(Component.text("玩家不存在！").color(NamedTextColor.RED));
            return;
        }
        if (!(player instanceof ConnectedPlayer)) {
            invocation.source().sendMessage(Component.text("无效对象转换，此玩未继承 ConnectedPlayer").color(NamedTextColor.RED));
            return;
        }
        ConnectedPlayer connectedPlayer = (ConnectedPlayer) player;
        Optional<ChannelTrafficShapingHandler> cHandlerOptional = plugin.getTrafficControlManager().getPlayerTrafficShapingHandler(connectedPlayer);
        if (cHandlerOptional.isEmpty()) {
            invocation.source().sendMessage(Component.text("此玩家 Pipeline 未注册 ChannelTrafficShapingHandler，操作失败！").color(NamedTextColor.RED));
            return;
        }
        ChannelTrafficShapingHandler handler = cHandlerOptional.get();
        handler.configure(writeLimit, readLimit);
        invocation.source().sendMessage(Component.text("OK!"));
    }

    private void configGlobal(Invocation invocation) {
        long writeLimit = Long.parseLong(invocation.arguments()[2]);
        long readLimit = Long.parseLong(invocation.arguments()[3]);
        GlobalTrafficShapingHandler gHandler = plugin.getTrafficControlManager().getGlobalTrafficHandler();
        gHandler.configure(writeLimit, readLimit);
        invocation.source().sendMessage(Component.text("OK!"));
    }

    private void viewPlayer(Invocation invocation) {
        String playerS = invocation.arguments()[2];
        Player player = plugin.getServer().getPlayer(playerS).orElse(null);
        if (player == null) {
            invocation.source().sendMessage(Component.text("玩家不存在！").color(NamedTextColor.RED));
            return;
        }
        if (!(player instanceof ConnectedPlayer)) {
            invocation.source().sendMessage(Component.text("无效对象转换，此玩未继承 ConnectedPlayer").color(NamedTextColor.RED));
            return;
        }
        ConnectedPlayer connectedPlayer = (ConnectedPlayer) player;
        Optional<ChannelTrafficShapingHandler> cHandlerOptional = plugin.getTrafficControlManager().getPlayerTrafficShapingHandler(connectedPlayer);
        if (cHandlerOptional.isEmpty()) {
            invocation.source().sendMessage(Component.text("此玩家 Pipeline 未注册 ChannelTrafficShapingHandler，操作失败！").color(NamedTextColor.RED));
            return;
        }
        ChannelTrafficShapingHandler handler = cHandlerOptional.get();

        invocation.source().sendMessage(Component.text("CheckInterval: " + handler.getCheckInterval()));
        invocation.source().sendMessage(Component.text("MaxTimeWait: " + handler.getMaxTimeWait()));
        invocation.source().sendMessage(Component.text("MaxWriteDelay: " + handler.getMaxWriteDelay()));
        invocation.source().sendMessage(Component.text("MaxWriteSize: " + handler.getMaxWriteSize()));
        invocation.source().sendMessage(Component.text("ReadLimit: " + handler.getReadLimit()));
        invocation.source().sendMessage(Component.text("WriteLimit: " + handler.getWriteLimit()));
        invocation.source().sendMessage(Component.text("Queue Size: " + handler.queueSize()));
        invocation.source().sendMessage(Component.text("-----"));
        TrafficCounter counter = handler.trafficCounter();
        invocation.source().sendMessage(Component.text("cumulativeReadBytes" + counter.cumulativeReadBytes()));
        invocation.source().sendMessage(Component.text("cumulativeWrittenBytes" + counter.cumulativeWrittenBytes()));
        invocation.source().sendMessage(Component.text("currentReadBytes" + counter.currentReadBytes()));
        invocation.source().sendMessage(Component.text("currentWrittenBytes" + counter.currentWrittenBytes()));
        invocation.source().sendMessage(Component.text("getRealWriteThroughput" + counter.getRealWriteThroughput()));
        invocation.source().sendMessage(Component.text("getRealWrittenBytes" + counter.getRealWrittenBytes()));
        invocation.source().sendMessage(Component.text("lastCumulativeTime" + counter.lastCumulativeTime()));
        invocation.source().sendMessage(Component.text("lastReadBytes" + counter.lastReadBytes()));
        invocation.source().sendMessage(Component.text("lastReadThroughput" + counter.lastReadThroughput()));
        invocation.source().sendMessage(Component.text("lastWriteThroughput" + counter.lastWriteThroughput()));
        invocation.source().sendMessage(Component.text("lastWrittenBytes" + counter.lastWrittenBytes()));
    }

    private void viewGlobal(Invocation invocation) {
        GlobalTrafficShapingHandler gHandler = plugin.getTrafficControlManager().getGlobalTrafficHandler();
        invocation.source().sendMessage(Component.text("CheckInterval: " + gHandler.getCheckInterval()));
        invocation.source().sendMessage(Component.text("MaxGlobalWriteSize：" + gHandler.getMaxGlobalWriteSize()));
        invocation.source().sendMessage(Component.text("MaxTimeWait: " + gHandler.getMaxTimeWait()));
        invocation.source().sendMessage(Component.text("MaxWriteDelay: " + gHandler.getMaxWriteDelay()));
        invocation.source().sendMessage(Component.text("MaxWriteSize: " + gHandler.getMaxWriteSize()));
        invocation.source().sendMessage(Component.text("ReadLimit: " + gHandler.getReadLimit()));
        invocation.source().sendMessage(Component.text("WriteLimit: " + gHandler.getWriteLimit()));
        invocation.source().sendMessage(Component.text("Queue Size: " + gHandler.queuesSize()));
        invocation.source().sendMessage(Component.text("-----"));
        TrafficCounter counter = gHandler.trafficCounter();
        invocation.source().sendMessage(Component.text("cumulativeReadBytes" + counter.cumulativeReadBytes()));
        invocation.source().sendMessage(Component.text("cumulativeWrittenBytes" + counter.cumulativeWrittenBytes()));
        invocation.source().sendMessage(Component.text("currentReadBytes" + counter.currentReadBytes()));
        invocation.source().sendMessage(Component.text("currentWrittenBytes" + counter.currentWrittenBytes()));
        invocation.source().sendMessage(Component.text("getRealWriteThroughput" + counter.getRealWriteThroughput()));
        invocation.source().sendMessage(Component.text("getRealWrittenBytes" + counter.getRealWrittenBytes()));
        invocation.source().sendMessage(Component.text("lastCumulativeTime" + counter.lastCumulativeTime()));
        invocation.source().sendMessage(Component.text("lastReadBytes" + counter.lastReadBytes()));
        invocation.source().sendMessage(Component.text("lastReadThroughput" + counter.lastReadThroughput()));
        invocation.source().sendMessage(Component.text("lastWriteThroughput" + counter.lastWriteThroughput()));
        invocation.source().sendMessage(Component.text("lastWrittenBytes" + counter.lastWrittenBytes()));
    }

    // This method allows you to control who can execute the command.
    // If the executor does not have the required permission,
    // the execution of the command and the control of its autocompletion
    // will be sent directly to the server on which the sender is located
    @Override
    public boolean hasPermission(final Invocation invocation) {
        if (invocation.arguments().length < 1) {
            return invocation.source().hasPermission("traffictool.help");
        }
        if (invocation.arguments()[0].equalsIgnoreCase("view")) {
            return invocation.source().hasPermission("traffictool.view");
        }
        if (invocation.arguments()[0].equalsIgnoreCase("config")) {
            return invocation.source().hasPermission("traffictool.config");
        }
        return invocation.source().hasPermission("traffictool.help");
    }

    // With this method you can control the suggestions to send
    // to the CommandSource according to the arguments
    // it has already written or other requirements you need
    @Override
    public List<String> suggest(final Invocation invocation) {
        return null;
    }

    // Here you can offer argument suggestions in the same way as the previous method,
    // but asynchronously. It is recommended to use this method instead of the previous one
    // especially in cases where you make a more extensive logic to provide the suggestions
    @Override
    public CompletableFuture<List<String>> suggestAsync(final Invocation invocation) {
        return CompletableFuture.completedFuture(List.of());
    }
}
