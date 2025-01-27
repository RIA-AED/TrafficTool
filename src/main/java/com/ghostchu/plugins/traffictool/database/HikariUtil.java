package com.ghostchu.plugins.traffictool.database;


import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;

public class HikariUtil {
    private HikariUtil() {
    }

    public static cc.carm.lib.easysql.hikari.HikariConfig createHikariConfig(ConfigurationSection section) {
        cc.carm.lib.easysql.hikari.HikariConfig config = new cc.carm.lib.easysql.hikari.HikariConfig();
        if (section == null) {
            throw new IllegalArgumentException("database.properties section in configuration not found");
        }
        for (String key : section.getKeys(false)) {
            config.addDataSourceProperty(key, section.getString(key));
        }
        return config;
    }
}
