package com.ghostchu.plugins.traffictool.database;

import cc.carm.lib.easysql.api.SQLManager;
import cc.carm.lib.easysql.api.SQLQuery;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class SimpleDatabaseHelper {
    private final SQLManager sqlManager;
    private final String prefix;
    public SimpleDatabaseHelper(SQLManager sqlManager, String prefix) throws SQLException {
        this.sqlManager = sqlManager;
        this.prefix = prefix;
        checkTables();
    }

    private void checkTables() throws SQLException {
        DataTables.initializeTables(sqlManager, prefix);
    }


}
