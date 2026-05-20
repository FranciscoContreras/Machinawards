package com.machina.wards;

import java.io.File;
import java.sql.*;

public class SqliteStore extends AbstractStore {

    private final File dbFile;

    public SqliteStore(File dataFolder) {
        this.dbFile = new File(dataFolder, "MachinaWards.db");
        if (!dataFolder.exists()) dataFolder.mkdirs();
    }

    @Override
    protected Connection openConnection() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
    }

    @Override
    protected String insertOrIgnore() {
        return "INSERT OR IGNORE";
    }

    @Override
    protected void afterConnect(Statement s) throws SQLException {
        s.execute("PRAGMA journal_mode=WAL");
        s.execute("PRAGMA synchronous=NORMAL");
    }
}
