package com.ghostchu.plugins.traffictool.database;

import cc.carm.lib.easysql.api.SQLManager;
import cc.carm.lib.easysql.hikari.HikariConfig;
import cc.carm.lib.easysql.hikari.HikariDataSource;
import cc.carm.lib.easysql.manager.SQLManagerImpl;
import com.ghostchu.plugins.traffictool.TrafficTool;
import com.mysql.cj.jdbc.Driver;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;

public class DatabaseManager {
    private final TrafficTool plugin;
    private SQLManager sqlManager;
    private DatabaseDriverType databaseDriverType = null;
    private String prefix;
    private SimpleDatabaseHelper databaseHelper;

    public DatabaseManager(TrafficTool plugin) {
        this.plugin = plugin;
        init();
    }

    private void init() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
        ConfigurationSection databaseSection = plugin.getConfig().getConfigurationSection("database");
        if (databaseSection == null) throw new IllegalArgumentException("Database section 不能为空");
        HikariConfig config = HikariUtil.createHikariConfig(databaseSection.getConfigurationSection("properties"));
        try {
            this.prefix = databaseSection.getString("prefix");
            if (this.prefix == null || this.prefix.isBlank() || "none".equalsIgnoreCase(this.prefix)) {
                this.prefix = "";
            }
            if (databaseSection.getBoolean("mysql")) {
                databaseDriverType = DatabaseDriverType.MYSQL;
                this.sqlManager = connectMySQL(config, databaseSection);
            } else {
                throw new IllegalArgumentException("不支持的数据库类型");
            }
            databaseHelper = new SimpleDatabaseHelper(this.sqlManager, this.prefix);
        } catch (Exception e) {
            throw new IllegalStateException("无法初始化数据库连接，请检查数据库配置", e);
        }
    }

    private SQLManager connectMySQL(HikariConfig config, ConfigurationSection dbCfg) {
        databaseDriverType = DatabaseDriverType.MYSQL;
        // MySQL database - Required database be created first.
        String user = dbCfg.getString("user");
        String pass = dbCfg.getString("password");
        String host = dbCfg.getString("host");
        String port = dbCfg.getString("port");
        String database = dbCfg.getString("database");
        boolean useSSL = dbCfg.getBoolean("usessl");
        config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=" + useSSL);
        config.setUsername(user);
        config.setPassword(pass);
        return new SQLManagerImpl(new HikariDataSource(config), "DoDoSRV-SQLManager");
    }

    public DatabaseDriverType getDatabaseDriverType() {
        return databaseDriverType;
    }

    public SimpleDatabaseHelper getDatabaseHelper() {
        return databaseHelper;
    }

    public SQLManager getSqlManager() {
        return sqlManager;
    }

    public String getPrefix() {
        return prefix;
    }
}
