package com.alexander.skinutils.storage;

import com.alexander.skinutils.SkinUtils;
import com.alexander.skinutils.skin.SkinData;
import org.bukkit.configuration.file.FileConfiguration;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class MySQLStorage implements StorageProvider {

    private final SkinUtils plugin;
    private Connection connection;

    public MySQLStorage(SkinUtils plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {
        try {
            FileConfiguration cfg = plugin.getConfig();
            String url = String.format("jdbc:mysql://%s:%d/%s?useSSL=false&autoReconnect=true",
                    cfg.getString("storage.mysql.host"),
                    cfg.getInt("storage.mysql.port"),
                    cfg.getString("storage.mysql.database"));
            connection = DriverManager.getConnection(url,
                    cfg.getString("storage.mysql.username"),
                    cfg.getString("storage.mysql.password"));
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS skins (" +
                        "uuid VARCHAR(36) PRIMARY KEY, " +
                        "value TEXT NOT NULL, " +
                        "signature TEXT NOT NULL, " +
                        "timestamp BIGINT NOT NULL) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
                stmt.execute("CREATE TABLE IF NOT EXISTS skin_history (" +
                        "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                        "uuid VARCHAR(36) NOT NULL, " +
                        "value TEXT NOT NULL, " +
                        "signature TEXT NOT NULL, " +
                        "source VARCHAR(64) NOT NULL, " +
                        "timestamp BIGINT NOT NULL, " +
                        "INDEX idx_uuid_time (uuid, timestamp DESC)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("MySQL init failed: " + e.getMessage());
        }
    }

    @Override
    public void shutdown() {
        try {
            if (connection != null && !connection.isClosed()) connection.close();
        } catch (SQLException ignored) {
        }
    }

    @Override
    public void save(UUID uuid, SkinData skin) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO skins (uuid, value, signature, timestamp) VALUES (?, ?, ?, ?) " +
                        "ON DUPLICATE KEY UPDATE value=VALUES(value), signature=VALUES(signature), timestamp=VALUES(timestamp)")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, skin.value());
            ps.setString(3, skin.signature());
            ps.setLong(4, skin.timestamp());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("MySQL save failed: " + e.getMessage());
        }
    }

    @Override
    public Optional<SkinData> load(UUID uuid) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT value, signature, timestamp FROM skins WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return Optional.of(new SkinData(rs.getString("value"), rs.getString("signature"), rs.getLong("timestamp")));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("MySQL load failed: " + e.getMessage());
        }
        return Optional.empty();
    }

    @Override
    public void remove(UUID uuid) {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM skins WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("MySQL remove failed: " + e.getMessage());
        }
    }

    @Override
    public void saveHistory(UUID uuid, SkinData skin, String source) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO skin_history (uuid, value, signature, source, timestamp) VALUES (?, ?, ?, ?, ?)")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, skin.value());
            ps.setString(3, skin.signature());
            ps.setString(4, source);
            ps.setLong(5, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("MySQL saveHistory failed: " + e.getMessage());
        }

        int max = plugin.getConfig().getInt("settings.history-max-size", 10);
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM skin_history WHERE uuid = ? AND id NOT IN " +
                        "(SELECT id FROM (SELECT id FROM skin_history WHERE uuid = ? ORDER BY timestamp DESC LIMIT ?) sub)")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, uuid.toString());
            ps.setInt(3, max);
            ps.executeUpdate();
        } catch (SQLException ignored) {
        }
    }

    @Override
    public List<SkinHistory> getHistory(UUID uuid, int limit) {
        List<SkinHistory> history = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT value, signature, source, timestamp FROM skin_history WHERE uuid = ? ORDER BY timestamp DESC LIMIT ?")) {
            ps.setString(1, uuid.toString());
            ps.setInt(2, limit);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                history.add(new SkinHistory(
                        new SkinData(rs.getString("value"), rs.getString("signature"), rs.getLong("timestamp")),
                        rs.getString("source"),
                        rs.getLong("timestamp")
                ));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("MySQL getHistory failed: " + e.getMessage());
        }
        return history;
    }
}
