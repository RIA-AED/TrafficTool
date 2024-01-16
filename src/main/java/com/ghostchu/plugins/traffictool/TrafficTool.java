package com.ghostchu.plugins.traffictool;

import cc.carm.lib.easysql.api.action.PreparedSQLUpdateBatchAction;
import com.ghostchu.plugins.traffictool.command.TrafficCommand;
import com.ghostchu.plugins.traffictool.control.TrafficControlManager;
import com.ghostchu.plugins.traffictool.database.DataTables;
import com.ghostchu.plugins.traffictool.database.DatabaseManager;
import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import io.netty.handler.traffic.ChannelTrafficShapingHandler;
import io.netty.handler.traffic.GlobalTrafficShapingHandler;
import io.netty.handler.traffic.TrafficCounter;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Plugin(
        id = "traffictool",
        name = "TrafficTool",
        version = "1.0-SNAPSHOT"
)
public class TrafficTool {
    @Inject
    private Logger logger;
    @Inject
    @DataDirectory
    private Path dataDirectory;
    @Inject
    public ProxyServer server;
    private YamlConfiguration config;
    private TrafficControlManager trafficControlManager;
    private DatabaseManager databaseManager;

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        loadConfig();
        this.trafficControlManager = new TrafficControlManager(this);
        server.getEventManager().register(this, this.trafficControlManager);
        CommandMeta commandMeta = server.getCommandManager().metaBuilder("traffic")
                // This will create a new alias for the command "/test"
                // with the same arguments and functionality
                .plugin(this)
                .build();
        server.getCommandManager().register(commandMeta, new TrafficCommand(this));
        try {
            setupDatabase();
            server.getScheduler().buildTask(this, this::recordMetricsToDatabase).delay(5, TimeUnit.SECONDS).repeat(3, TimeUnit.MINUTES).schedule();
        } catch (Throwable th) {
            logger.warn("无法初始化数据库，停止自动上传");
        }
    }

    public void loadConfig() {
        File configFile = new File(dataDirectory.toFile(), "config.yml");
        dataDirectory.toFile().mkdirs();
        if (!configFile.exists()) {
            try {
                configFile.createNewFile();
                Files.copy(getClass().getResourceAsStream("/config.yml"), configFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        this.config = YamlConfiguration.loadConfiguration(configFile);
    }

    public void recordMetricsToDatabase() {
        // record global
        GlobalTrafficShapingHandler gHandler = trafficControlManager.getGlobalTrafficHandler();
        TrafficCounter gCounter = gHandler.trafficCounter();
        DataTables.TRAFFIC_GLOBAL.createInsert()
                .setColumnNames("logging_at", "lastTime", "cumulativeReadBytes", "cumulativeWrittenBytes", "currentReadBytes",
                        "currentWrittenBytes", "getRealWriteThroughput", "getRealWrittenBytes", "lastCumulativeTime", "lastReadBytes",
                        "lastReadThroughput", "lastWriteThroughput", "lastWrittenBytes", "maxGlobalWriteSize", "queuesSize", "maxTimeWait", "maxWriteDelay", "maxWriteSize", "readLimit", "writeLimit")
                .setParams(LocalDateTime.now(), gCounter.lastTime(),
                        gCounter.cumulativeReadBytes(), gCounter.cumulativeWrittenBytes(), gCounter.currentReadBytes(),
                        gCounter.currentWrittenBytes(), gCounter.getRealWriteThroughput(), gCounter.getRealWrittenBytes(),
                        gCounter.lastCumulativeTime(), gCounter.lastReadBytes(), gCounter.lastReadThroughput(),
                        gCounter.lastWriteThroughput(), gCounter.lastWrittenBytes(), gHandler.getMaxGlobalWriteSize(),
                        gHandler.queuesSize(), gHandler.getMaxTimeWait(), gHandler.getMaxWriteDelay(), gHandler.getMaxWriteSize(),
                        gHandler.getReadLimit(), gHandler.getWriteLimit())
                .executeFuture()
                .thenAccept(sql -> logger.info("上送全局流量和带宽样本成功"))
                .exceptionally(err -> {
                    logger.warn("上送全局流量和带宽样本失败", err);
                    return null;
                });


        PreparedSQLUpdateBatchAction<Integer> batchAction = DataTables.TRAFFIC_PLAYER.createInsertBatch()
                .setColumnNames("uuid", "username", "logging_at", "lastTime",
                        "cumulativeReadBytes", "cumulativeWrittenBytes", "currentReadBytes",
                        "currentWrittenBytes", "getRealWriteThroughput", "getRealWrittenBytes",
                        "lastCumulativeTime", "lastReadBytes", "lastReadThroughput",
                        "lastWriteThroughput", "lastWrittenBytes", "queueSize",
                        "maxTimeWait", "maxWriteDelay", "maxWriteSize",
                        "readLimit", "writeLimit");
        for (Player p : server.getAllPlayers()) {
            if (p instanceof ConnectedPlayer connectedPlayer) {
                Optional<ChannelTrafficShapingHandler> opt = trafficControlManager.getPlayerTrafficShapingHandler(connectedPlayer);
                if (opt.isEmpty()) continue;
                ChannelTrafficShapingHandler cHandler = opt.get();
                TrafficCounter cCounter = cHandler.trafficCounter();
                batchAction.addParamsBatch(p.getUniqueId(), p.getUsername(), LocalDateTime.now(), cCounter.lastTime(),
                        cCounter.cumulativeReadBytes(), cCounter.cumulativeWrittenBytes(), cCounter.currentReadBytes(),
                        cCounter.currentWrittenBytes(), cCounter.getRealWriteThroughput(), cCounter.getRealWrittenBytes(),
                        cCounter.lastCumulativeTime(), cCounter.lastReadBytes(), cCounter.lastReadThroughput(),
                        cCounter.lastWriteThroughput(), cCounter.lastWrittenBytes(), cHandler.queueSize(),
                        cHandler.getMaxTimeWait(), cHandler.getMaxWriteDelay(), cHandler.getMaxWriteSize(),
                        cHandler.getReadLimit(), cHandler.getWriteLimit());
            } else {
                getLogger().warn("{} 不是一个 ConnectedPlayer", p.getUsername());
            }
        }
        batchAction.executeFuture()
                .thenAccept(sql -> logger.info("上送批量个人流量和带宽样本成功"))
                .exceptionally(err -> {
                    logger.warn("上送批量个人流量和带宽数据失败", err);
                    return null;
                });
    }

    public YamlConfiguration getConfig() {
        return config;
    }

    public ProxyServer getServer() {
        return server;
    }

    public Path getDataDirectory() {
        return dataDirectory;
    }

    public Logger getLogger() {
        return logger;
    }

    public TrafficControlManager getTrafficControlManager() {
        return trafficControlManager;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    private void setupDatabase() {
        logger.info("Setting up database...");
        this.databaseManager = new DatabaseManager(this);
    }

    public static String humanReadableByteCount(long bytes, boolean si) {
        int unit = si ? 1000 : 1024;
        if (bytes < unit) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(unit));
        String pre = (si ? "kMGTPE" : "KMGTPE").charAt(exp - 1) + (si ? "" : "i");
        return String.format("%.2f %sB", bytes / Math.pow(unit, exp), pre);
    }
}
