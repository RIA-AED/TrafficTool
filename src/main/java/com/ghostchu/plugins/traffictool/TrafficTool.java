package com.ghostchu.plugins.traffictool;

import cc.carm.lib.easysql.api.SQLManager;
import cc.carm.lib.easysql.hikari.HikariConfig;
import cc.carm.lib.easysql.hikari.HikariDataSource;
import cc.carm.lib.easysql.manager.SQLManagerImpl;
import com.ghostchu.plugins.traffictool.control.TrafficControlManager;
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
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Plugin(
        id = "trafficsummarytool",
        name = "TrafficSummaryTool",
        version = "1.0-SNAPSHOT"
)
public class TrafficTool {
    @Inject
    private Logger logger;
    @Inject
    private Path dataDirectory;
    @Inject
    public ProxyServer server;
    private YamlConfiguration config;
    private TrafficControlManager trafficControlManager;
    private DatabaseManager databaseManager;

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        File configFile = new File(dataDirectory.toFile(), "config.yml");
        if (!configFile.exists()) {
            try {
                Files.copy(getClass().getResourceAsStream("/config.yml"), configFile.toPath());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        this.config = YamlConfiguration.loadConfiguration(configFile);
        this.trafficControlManager = new TrafficControlManager(this);
        setupDatabase();
    }

    @Subscribe
    public void onProxyInitialization(ProxyShutdownEvent event) {

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
}
