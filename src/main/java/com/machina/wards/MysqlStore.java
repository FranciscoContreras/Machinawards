package com.machina.wards;

import java.sql.*;

public class MysqlStore extends AbstractStore {

    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;

    public MysqlStore(String host, int port, String database, String username, String password) {
        this.host     = host;
        this.port     = port;
        this.database = database;
        this.username = username;
        this.password = password;
    }

    @Override
    protected Connection openConnection() throws SQLException {
        try { Class.forName("com.mysql.cj.jdbc.Driver"); }
        catch (ClassNotFoundException e) {
            throw new SQLException("MySQL driver not found. Ensure mysql-connector-j is available.", e);
        }
        String url = String.format(
                "jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true" +
                "&autoReconnect=true&characterEncoding=UTF-8",
                host, port, database);
        return DriverManager.getConnection(url, username, password);
    }

    @Override
    protected String idColumn() { return "INT AUTO_INCREMENT PRIMARY KEY"; }

    @Override
    protected String insertOrIgnore() { return "INSERT IGNORE"; }

    @Override
    protected void afterConnect(Statement s) throws SQLException {
        // No PRAGMAs needed for MySQL
    }
}
