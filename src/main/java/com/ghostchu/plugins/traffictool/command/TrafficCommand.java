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
        if (args.length < 1) {
            source.sendMessage(Component.text("错误的命令使用方法，可用参数 <view/config/upload/me>"));
            return;
        }
        if (args[0].equalsIgnoreCase("me")) {
            if (!source.hasPermission("traffictool.me")) {
                source.sendMessage(Component.text("权限不足！").color(NamedTextColor.RED));
                return;
            }
            viewMe(invocation);
        } else if (args[0].equalsIgnoreCase("upload")) {
            if (!source.hasPermission("traffictool.upload")) {
                source.sendMessage(Component.text("权限不足！").color(NamedTextColor.RED));
                return;
            }
            source.sendMessage(Component.text("正在手动上传到数据库……").color(NamedTextColor.GREEN));
            plugin.recordMetricsToDatabase();
        } else if (args[0].equalsIgnoreCase("view")) {
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

    private void viewMe(Invocation invocation) {
        if (!(invocation.source() instanceof ConnectedPlayer)) {
            invocation.source().sendMessage(Component.text("仅在线玩家可使用此命令"));
        }
        ConnectedPlayer player = (ConnectedPlayer) invocation.source();
        Optional<ChannelTrafficShapingHandler> cHandlerOptional = plugin.getTrafficControlManager().getPlayerTrafficShapingHandler(player);
        if (cHandlerOptional.isEmpty()) {
            invocation.source().sendMessage(Component.text("Pipeline 未注册 ChannelTrafficShapingHandler，操作失败！").color(NamedTextColor.RED));
            return;
        }
        ChannelTrafficShapingHandler handler = cHandlerOptional.get();
        TrafficCounter counter = handler.trafficCounter();
        player.sendMessage(Component.text("[TrafficTool] 您当前的套接字属性如下："));
        player.sendMessage(Component.text("写速率限制：" + formatBytes(handler.getWriteLimit()) + "/" + handler.getCheckInterval() + "ms"));
        player.sendMessage(Component.text("读速率限制：" + formatBytes(handler.getWriteLimit()) + "/" + handler.getCheckInterval() + "ms"));
        player.sendMessage(Component.text("包队列大小：" + handler.queueSize() +" (长时间或过多的包堆积将导致 Ping 升高)"));
        player.sendMessage(Component.text("---------------"));
        invocation.source().sendMessage(Component.text("累计读取字节数: " + formatBytes(counter.cumulativeReadBytes())));
        invocation.source().sendMessage(Component.text("累计写入字节数: " + formatBytes(counter.cumulativeWrittenBytes())));
        invocation.source().sendMessage(Component.text("当前读取字节数: " + formatBytes(counter.currentReadBytes())));
        invocation.source().sendMessage(Component.text("当前写入字节数: " + formatBytes(counter.currentWrittenBytes())));
        invocation.source().sendMessage(Component.text("实际写入吞吐量: " + formatBytes(counter.getRealWriteThroughput()) + "/" + handler.getCheckInterval() + "ms"));
        invocation.source().sendMessage(Component.text("实际写入字节数: " + formatBytes(counter.getRealWrittenBytes().get())));
        invocation.source().sendMessage(Component.text("最后累计时间: " + counter.lastCumulativeTime()));
        invocation.source().sendMessage(Component.text("最后读取字节数: " + formatBytes(counter.lastReadBytes())));
        invocation.source().sendMessage(Component.text("最后读取吞吐量: " + formatBytes(counter.lastReadThroughput()) + "/" + handler.getCheckInterval() + "ms"));
        invocation.source().sendMessage(Component.text("最后写入吞吐量: " + formatBytes(counter.lastWriteThroughput()) + "/" + handler.getCheckInterval() + "ms"));
        invocation.source().sendMessage(Component.text("最后写入字节数: " + formatBytes(counter.lastWrittenBytes())));
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
        if (!(player instanceof ConnectedPlayer connectedPlayer)) {
            invocation.source().sendMessage(Component.text("无效对象转换，此玩未继承 ConnectedPlayer").color(NamedTextColor.RED));
            return;
        }
        Optional<ChannelTrafficShapingHandler> cHandlerOptional = plugin.getTrafficControlManager().getPlayerTrafficShapingHandler(connectedPlayer);
        if (cHandlerOptional.isEmpty()) {
            invocation.source().sendMessage(Component.text("此玩家 Pipeline 未注册 ChannelTrafficShapingHandler，操作失败！").color(NamedTextColor.RED));
            return;
        }
        ChannelTrafficShapingHandler handler = cHandlerOptional.get();

        invocation.source().sendMessage(Component.text("检查间隔时间: " + handler.getCheckInterval() + "ms"));
        invocation.source().sendMessage(Component.text("最大等待时长: " + handler.getMaxTimeWait()));
        invocation.source().sendMessage(Component.text("最大写入延迟: " + handler.getMaxWriteDelay()));
        invocation.source().sendMessage(Component.text("最大写入大小: " + formatBytes(handler.getMaxWriteSize())));
        invocation.source().sendMessage(Component.text("读速率限制: " + formatBytes(handler.getReadLimit()) + "/" + handler.getCheckInterval() + "ms"));
        invocation.source().sendMessage(Component.text("写速率限制: " + formatBytes(handler.getWriteLimit()) + "/" + handler.getCheckInterval() + "ms"));
        invocation.source().sendMessage(Component.text("队列大小: " + handler.queueSize()+" (长时间或过多的包堆积将导致 Ping 升高)"));
        invocation.source().sendMessage(Component.text("-----"));
        TrafficCounter counter = handler.trafficCounter();
        invocation.source().sendMessage(Component.text("累计读取字节数: " + formatBytes(counter.cumulativeReadBytes())));
        invocation.source().sendMessage(Component.text("累计写入字节数: " + formatBytes(counter.cumulativeWrittenBytes())));
        invocation.source().sendMessage(Component.text("当前读取字节数: " + formatBytes(counter.currentReadBytes())));
        invocation.source().sendMessage(Component.text("当前写入字节数: " + formatBytes(counter.currentWrittenBytes())));
        invocation.source().sendMessage(Component.text("实际写入吞吐量: " + formatBytes(counter.getRealWriteThroughput()) + "/" + handler.getCheckInterval() + "ms"));
        invocation.source().sendMessage(Component.text("实际写入字节数: " + formatBytes(counter.getRealWrittenBytes().get())));
        invocation.source().sendMessage(Component.text("最后累计时间: " + counter.lastCumulativeTime()));
        invocation.source().sendMessage(Component.text("最后读取字节数: " + formatBytes(counter.lastReadBytes())));
        invocation.source().sendMessage(Component.text("最后读取吞吐量: " + formatBytes(counter.lastReadThroughput()) + "/" + handler.getCheckInterval() + "ms"));
        invocation.source().sendMessage(Component.text("最后写入吞吐量: " + formatBytes(counter.lastWriteThroughput()) + "/" + handler.getCheckInterval() + "ms"));
        invocation.source().sendMessage(Component.text("最后写入字节数: " + formatBytes(counter.lastWrittenBytes())));
    }

    private void viewGlobal(Invocation invocation) {
        GlobalTrafficShapingHandler handler = plugin.getTrafficControlManager().getGlobalTrafficHandler();
        invocation.source().sendMessage(Component.text("检查间隔时间: " + handler.getCheckInterval() + "ms"));
        invocation.source().sendMessage(Component.text("最大等待时长: " + handler.getMaxTimeWait()));
        invocation.source().sendMessage(Component.text("最大写入延迟: " + handler.getMaxWriteDelay()));
        invocation.source().sendMessage(Component.text("最大写入大小: " + formatBytes(handler.getMaxWriteSize())));
        invocation.source().sendMessage(Component.text("读速率限制: " + formatBytes(handler.getReadLimit()) + "/" + handler.getCheckInterval() + "ms"));
        invocation.source().sendMessage(Component.text("写速率限制: " + formatBytes(handler.getWriteLimit()) + "/" + handler.getCheckInterval() + "ms"));
        invocation.source().sendMessage(Component.text("队列大小: " + handler.queuesSize()));
        invocation.source().sendMessage(Component.text("-----"));
        TrafficCounter counter = handler.trafficCounter();
        invocation.source().sendMessage(Component.text("累计读取字节数: " + formatBytes(counter.cumulativeReadBytes())));
        invocation.source().sendMessage(Component.text("累计写入字节数: " + formatBytes(counter.cumulativeWrittenBytes())));
        invocation.source().sendMessage(Component.text("当前读取字节数: " + formatBytes(counter.currentReadBytes())));
        invocation.source().sendMessage(Component.text("当前写入字节数: " + formatBytes(counter.currentWrittenBytes())));
        invocation.source().sendMessage(Component.text("实际写入吞吐量: " + formatBytes(counter.getRealWriteThroughput()) + "/" + handler.getCheckInterval() + "ms"));
        invocation.source().sendMessage(Component.text("实际写入字节数: " + formatBytes(counter.getRealWrittenBytes().get())));
        invocation.source().sendMessage(Component.text("最后累计时间: " + counter.lastCumulativeTime()));
        invocation.source().sendMessage(Component.text("最后读取字节数: " + formatBytes(counter.lastReadBytes())));
        invocation.source().sendMessage(Component.text("最后读取吞吐量: " + formatBytes(counter.lastReadThroughput()) + "/" + handler.getCheckInterval() + "ms"));
        invocation.source().sendMessage(Component.text("最后写入吞吐量: " + formatBytes(counter.lastWriteThroughput()) + "/" + handler.getCheckInterval() + "ms"));
        invocation.source().sendMessage(Component.text("最后写入字节数: " + formatBytes(counter.lastWrittenBytes())));
    }

    // This method allows you to control who can execute the command.
    // If the executor does not have the required permission,
    // the execution of the command and the control of its autocompletion
    // will be sent directly to the server on which the sender is located
    @Override
    public boolean hasPermission(final Invocation invocation) {
        return true;
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

    private String formatBytes(long a) {
        return TrafficTool.humanReadableByteCount(a, false);
    }
}
