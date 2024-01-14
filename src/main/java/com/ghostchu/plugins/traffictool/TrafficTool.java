package com.ghostchu.plugins.traffictool;

import cc.carm.lib.easysql.api.SQLManager;
import cc.carm.lib.easysql.api.action.PreparedSQLUpdateBatchAction;
import cc.carm.lib.easysql.hikari.HikariConfig;
import cc.carm.lib.easysql.hikari.HikariDataSource;
import cc.carm.lib.easysql.manager.SQLManagerImpl;
import com.ghostchu.plugins.traffictool.command.TrafficCommand;
import com.ghostchu.plugins.traffictool.control.TrafficControlManager;
import com.ghostchu.plugins.traffictool.database.DataTables;
import com.ghostchu.plugins.traffictool.database.DatabaseManager;
import com.ghostchu.plugins.traffictool.database.HikariUtil;
import com.ghostchu.plugins.traffictool.database.SimpleDatabaseHelper;
import com.google.inject.Inject;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.InboundConnection;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.proxy.connection.MinecraftConnection;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import com.velocitypowered.proxy.connection.client.InitialInboundConnection;
import com.velocitypowered.proxy.connection.client.LoginInboundConnection;
import io.netty.channel.ChannelHandler;
import io.netty.handler.traffic.ChannelTrafficShapingHandler;
import io.netty.handler.traffic.GlobalChannelTrafficShapingHandler;
import io.netty.handler.traffic.GlobalTrafficShapingHandler;
import io.netty.handler.traffic.TrafficCounter;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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
        this.trafficControlManager = new TrafficControlManager(this);
        server.getEventManager().register(this, this.trafficControlManager);
        server.getCommandManager().register("traffic", new TrafficCommand(this));
        setupDatabase();
        server.getScheduler().buildTask(this, this::recordMetricsToDatabase).repeat(1, TimeUnit.MINUTES).schedule();

    }

    public  void recordMetricsToDatabase() {
        // record global
        GlobalTrafficShapingHandler gHandler = trafficControlManager.getGlobalTrafficHandler();
        TrafficCounter gCounter = gHandler.trafficCounter();
        DataTables.TRAFFIC_GLOBAL.createInsert()
                .setColumnNames("logging_at", "lastTime", "cumulativeReadBytes", "cumulativeWrittenBytes", "currentReadBytes",
                        "currentWrittenBytes","getRealWriteThroughput", "getRealWrittenBytes", "lastCumulativeTime", "lastReadBytes",
                        "lastReadThroughput", "lastWriteThroughput", "lastWrittenBytes", "maxGlobalWriteSize", "queuesSize", "maxTimeWait", "maxWriteDelay", "maxWriteSize", "readLimit", "writeLimit")
                .setParams(LocalDateTime.now(), gCounter.lastTime(),
                        gCounter.cumulativeReadBytes(), gCounter.cumulativeWrittenBytes(), gCounter.currentReadBytes(),
                        gCounter.currentWrittenBytes(), gCounter.getRealWriteThroughput(), gCounter.getRealWrittenBytes(),
                        gCounter.lastCumulativeTime(), gCounter.lastReadBytes(),gCounter.lastReadThroughput(),
                        gCounter.lastWriteThroughput(), gCounter.lastWrittenBytes(), gHandler.getMaxGlobalWriteSize(),
                        gHandler.queuesSize(), gHandler.getMaxTimeWait(), gHandler.getMaxWriteDelay(), gHandler.getMaxWriteSize(),
                        gHandler.getReadLimit(), gHandler.getWriteLimit())
                .executeAsync((sql)->logger.info("Global upload success"), (err,sql)->{
                    err.printStackTrace();
                });
       PreparedSQLUpdateBatchAction<Integer> batchAction =  DataTables.TRAFFIC_PLAYER.createInsertBatch()
                        .setColumnNames("uuid", "username", "logging_at", "lastTime", "cumulativeReadBytes", "cumulativeWrittenBytes", "currentReadBytes",
                                "currentWrittenBytes","getRealWriteThroughput", "getRealWrittenBytes", "lastCumulativeTime", "lastReadBytes",
                                "lastReadThroughput", "lastWriteThroughput", "lastWrittenBytes", "queueSize", "maxTimeWait", "maxWriteDelay", "maxWriteSize", "readLimit", "writeLimit");
        server.getAllPlayers().forEach(p->{
            Optional<ChannelTrafficShapingHandler> opt = trafficControlManager.getPlayerTrafficShapingHandler(p);
            if(opt.isEmpty()) return;
            ChannelTrafficShapingHandler cHandler = opt.get();
            TrafficCounter cCounter = cHandler.trafficCounter();
            batchAction.addParamsBatch(p.getUniqueId(), p.getUsername(), LocalDateTime.now(), cCounter.lastTime(),
                    cCounter.cumulativeReadBytes(), cCounter.cumulativeWrittenBytes(), cCounter.currentReadBytes(),
                    cCounter.currentWrittenBytes(), cCounter.getRealWriteThroughput(), cCounter.getRealWrittenBytes(),
                    cCounter.lastCumulativeTime(), cCounter.lastReadBytes(),cCounter.lastReadThroughput(),
                    cCounter.lastWriteThroughput(), cCounter.lastWrittenBytes(),
                    cHandler.queueSize(), cHandler.getMaxTimeWait(), cHandler.getMaxWriteDelay(), cHandler.getMaxWriteSize(),
                    cHandler.getReadLimit(), cHandler.getWriteLimit());
        });
        batchAction.executeAsync((sql)->logger.info("Batch upload success"), (err,sql)->{
            err.printStackTrace();
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
        return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
    }
}
