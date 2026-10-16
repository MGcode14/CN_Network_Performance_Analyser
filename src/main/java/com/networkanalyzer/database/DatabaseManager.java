package com.networkanalyzer.database;

import com.google.gson.Gson;
import com.networkanalyzer.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.sql.*;
import java.util.*;

public class DatabaseManager {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseManager.class);
    private final String dbUrl;
    private final Gson gson = new Gson();

    public DatabaseManager(String dbUrl) {
        this.dbUrl = dbUrl;
        initDatabase();
    }

    private Connection getConnection() throws SQLException {
        Connection conn = DriverManager.getConnection(dbUrl);
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA journal_mode=WAL;");
            stmt.execute("PRAGMA synchronous=NORMAL;");
            stmt.execute("PRAGMA foreign_keys=ON;");
        }
        return conn;
    }

    private void initDatabase() {
        try {
            // Ensure data directory exists
            if (dbUrl.startsWith("jdbc:sqlite:")) {
                String filePath = dbUrl.substring("jdbc:sqlite:".length());
                File file = new File(filePath);
                File parent = file.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }
            }

            try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS experiments (
                        id TEXT PRIMARY KEY,
                        test_id TEXT NOT NULL,
                        name TEXT NOT NULL,
                        protocol TEXT NOT NULL,
                        test_mode TEXT NOT NULL,
                        status TEXT NOT NULL,
                        target_host TEXT NOT NULL,
                        target_port INTEGER NOT NULL,
                        payload_size INTEGER NOT NULL,
                        conn_setup_time_ms REAL,
                        start_time_ms INTEGER,
                        end_time_ms INTEGER,
                        duration_ms REAL,
                        packets_sent INTEGER,
                        packets_received INTEGER,
                        bytes_sent INTEGER,
                        bytes_received INTEGER,
                        throughput_mbps REAL,
                        data_rate_mb_s REAL,
                        avg_rtt_ms REAL,
                        min_rtt_ms REAL,
                        max_rtt_ms REAL,
                        median_rtt_ms REAL,
                        p95_rtt_ms REAL,
                        jitter_ms REAL,
                        packet_loss_pct REAL,
                        delivery_ratio_pct REAL,
                        bandwidth_util_pct REAL,
                        link_capacity_mbps REAL,
                        error_message TEXT,
                        config_json TEXT,
                        created_at INTEGER NOT NULL
                    );
                """);

                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS measurement_samples (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        experiment_id TEXT NOT NULL,
                        sample_seq INTEGER NOT NULL,
                        rtt_ms REAL,
                        instant_throughput_mbps REAL,
                        timestamp_ms INTEGER NOT NULL,
                        FOREIGN KEY (experiment_id) REFERENCES experiments(id) ON DELETE CASCADE
                    );
                """);

                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS server_events (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        event_type TEXT NOT NULL,
                        message TEXT NOT NULL,
                        details TEXT,
                        timestamp_ms INTEGER NOT NULL
                    );
                """);

                // Indexes
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_exp_created_at ON experiments(created_at DESC);");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_exp_protocol ON experiments(protocol);");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_exp_mode ON experiments(test_mode);");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_samples_exp ON measurement_samples(experiment_id, sample_seq);");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_events_time ON server_events(timestamp_ms DESC);");

                logger.info("Database initialized successfully at {}", dbUrl);
            }
        } catch (SQLException e) {
            logger.error("Failed to initialize SQLite database: {}", e.getMessage(), e);
            throw new RuntimeException("Database initialization failure", e);
        }
    }

    public synchronized void saveExperiment(Experiment exp) {
        String sql = """
            INSERT INTO experiments (
                id, test_id, name, protocol, test_mode, status, target_host, target_port, payload_size,
                conn_setup_time_ms, start_time_ms, end_time_ms, duration_ms,
                packets_sent, packets_received, bytes_sent, bytes_received,
                throughput_mbps, data_rate_mb_s, avg_rtt_ms, min_rtt_ms, max_rtt_ms,
                median_rtt_ms, p95_rtt_ms, jitter_ms, packet_loss_pct, delivery_ratio_pct,
                bandwidth_util_pct, link_capacity_mbps, error_message, config_json, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                status = excluded.status,
                conn_setup_time_ms = excluded.conn_setup_time_ms,
                end_time_ms = excluded.end_time_ms,
                duration_ms = excluded.duration_ms,
                packets_sent = excluded.packets_sent,
                packets_received = excluded.packets_received,
                bytes_sent = excluded.bytes_sent,
                bytes_received = excluded.bytes_received,
                throughput_mbps = excluded.throughput_mbps,
                data_rate_mb_s = excluded.data_rate_mb_s,
                avg_rtt_ms = excluded.avg_rtt_ms,
                min_rtt_ms = excluded.min_rtt_ms,
                max_rtt_ms = excluded.max_rtt_ms,
                median_rtt_ms = excluded.median_rtt_ms,
                p95_rtt_ms = excluded.p95_rtt_ms,
                jitter_ms = excluded.jitter_ms,
                packet_loss_pct = excluded.packet_loss_pct,
                delivery_ratio_pct = excluded.delivery_ratio_pct,
                bandwidth_util_pct = excluded.bandwidth_util_pct,
                error_message = excluded.error_message;
        """;

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, exp.getId());
                ps.setString(2, exp.getTestId());
                ps.setString(3, exp.getName());
                ps.setString(4, exp.getProtocol().name());
                ps.setString(5, exp.getTestMode().name());
                ps.setString(6, exp.getStatus().name());
                ps.setString(7, exp.getTargetHost());
                ps.setInt(8, exp.getTargetPort());
                ps.setInt(9, exp.getPayloadSize());

                setNullableDouble(ps, 10, exp.getConnectionSetupTimeMs());
                setNullableLong(ps, 11, exp.getStartTimeMs());
                setNullableLong(ps, 12, exp.getEndTimeMs());
                setNullableDouble(ps, 13, exp.getDurationMs());

                setNullableLong(ps, 14, exp.getPacketsSent());
                setNullableLong(ps, 15, exp.getPacketsReceived());
                setNullableLong(ps, 16, exp.getBytesSent());
                setNullableLong(ps, 17, exp.getBytesReceived());

                setNullableDouble(ps, 18, exp.getThroughputMbps());
                setNullableDouble(ps, 19, exp.getDataRateMBs());
                setNullableDouble(ps, 20, exp.getAvgRttMs());
                setNullableDouble(ps, 21, exp.getMinRttMs());
                setNullableDouble(ps, 22, exp.getMaxRttMs());
                setNullableDouble(ps, 23, exp.getMedianRttMs());
                setNullableDouble(ps, 24, exp.getP95RttMs());
                setNullableDouble(ps, 25, exp.getJitterMs());
                setNullableDouble(ps, 26, exp.getPacketLossPct());
                setNullableDouble(ps, 27, exp.getDeliveryRatioPct());
                setNullableDouble(ps, 28, exp.getBandwidthUtilPct());
                setNullableDouble(ps, 29, exp.getLinkCapacityMbps());

                ps.setString(30, exp.getErrorMessage());
                ps.setString(31, exp.getConfig() != null ? gson.toJson(exp.getConfig()) : null);
                ps.setLong(32, exp.getCreatedAt());

                ps.executeUpdate();
            }

            // Save samples
            if (exp.getSamples() != null && !exp.getSamples().isEmpty()) {
                String sampleSql = "INSERT INTO measurement_samples (experiment_id, sample_seq, rtt_ms, instant_throughput_mbps, timestamp_ms) VALUES (?, ?, ?, ?, ?)";
                try (PreparedStatement psSample = conn.prepareStatement(sampleSql)) {
                    for (MeasurementSample s : exp.getSamples()) {
                        psSample.setString(1, exp.getId());
                        psSample.setLong(2, s.getSampleSeq());
                        setNullableDouble(psSample, 3, s.getRttMs());
                        setNullableDouble(psSample, 4, s.getInstantThroughputMbps());
                        psSample.setLong(5, s.getTimestampMs());
                        psSample.addBatch();
                    }
                    psSample.executeBatch();
                }
            }

            conn.commit();
            logger.debug("Saved experiment {} ({}) to database", exp.getId(), exp.getName());
        } catch (SQLException e) {
            logger.error("Failed to save experiment {}: {}", exp.getId(), e.getMessage(), e);
            throw new RuntimeException("Database error saving experiment", e);
        }
    }

    public Experiment getExperiment(String id) {
        String sql = "SELECT * FROM experiments WHERE id = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Experiment exp = mapResultSetToExperiment(rs);
                    exp.setSamples(getSamplesForExperiment(id, 2000));
                    return exp;
                }
            }
        } catch (SQLException e) {
            logger.error("Error retrieving experiment {}: {}", id, e.getMessage(), e);
        }
        return null;
    }

    public List<Experiment> getAllExperiments(String protocol, String testMode, String search, int limit, int offset) {
        StringBuilder sql = new StringBuilder("SELECT * FROM experiments WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        if (protocol != null && !protocol.equalsIgnoreCase("ALL") && !protocol.trim().isEmpty()) {
            sql.append("AND protocol = ? ");
            params.add(protocol.toUpperCase());
        }

        if (testMode != null && !testMode.equalsIgnoreCase("ALL") && !testMode.trim().isEmpty()) {
            sql.append("AND test_mode = ? ");
            params.add(testMode.toUpperCase());
        }

        if (search != null && !search.trim().isEmpty()) {
            sql.append("AND (name LIKE ? OR target_host LIKE ? OR id LIKE ?) ");
            String wildcard = "%" + search.trim() + "%";
            params.add(wildcard);
            params.add(wildcard);
            params.add(wildcard);
        }

        sql.append("ORDER BY created_at DESC LIMIT ? OFFSET ?");
        params.add(limit > 0 ? limit : 50);
        params.add(offset >= 0 ? offset : 0);

        List<Experiment> list = new ArrayList<>();
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSetToExperiment(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Error listing experiments: {}", e.getMessage(), e);
        }
        return list;
    }

    public int countExperiments(String protocol, String testMode, String search) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM experiments WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        if (protocol != null && !protocol.equalsIgnoreCase("ALL") && !protocol.trim().isEmpty()) {
            sql.append("AND protocol = ? ");
            params.add(protocol.toUpperCase());
        }

        if (testMode != null && !testMode.equalsIgnoreCase("ALL") && !testMode.trim().isEmpty()) {
            sql.append("AND test_mode = ? ");
            params.add(testMode.toUpperCase());
        }

        if (search != null && !search.trim().isEmpty()) {
            sql.append("AND (name LIKE ? OR target_host LIKE ? OR id LIKE ?) ");
            String wildcard = "%" + search.trim() + "%";
            params.add(wildcard);
            params.add(wildcard);
            params.add(wildcard);
        }

        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            logger.error("Error counting experiments: {}", e.getMessage(), e);
        }
        return 0;
    }

    public List<MeasurementSample> getSamplesForExperiment(String experimentId, int maxLimit) {
        String sql = "SELECT * FROM measurement_samples WHERE experiment_id = ? ORDER BY sample_seq ASC LIMIT ?";
        List<MeasurementSample> samples = new ArrayList<>();
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, experimentId);
            ps.setInt(2, maxLimit > 0 ? maxLimit : 1000);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    MeasurementSample s = new MeasurementSample();
                    s.setId(rs.getLong("id"));
                    s.setExperimentId(rs.getString("experiment_id"));
                    s.setSampleSeq(rs.getLong("sample_seq"));
                    s.setRttMs(getNullableDouble(rs, "rtt_ms"));
                    s.setInstantThroughputMbps(getNullableDouble(rs, "instant_throughput_mbps"));
                    s.setTimestampMs(rs.getLong("timestamp_ms"));
                    samples.add(s);
                }
            }
        } catch (SQLException e) {
            logger.error("Error retrieving samples for experiment {}: {}", experimentId, e.getMessage(), e);
        }
        return samples;
    }

    public void logServerEvent(String eventType, String message, String details) {
        String sql = "INSERT INTO server_events (event_type, message, details, timestamp_ms) VALUES (?, ?, ?, ?)";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, eventType);
            ps.setString(2, message);
            ps.setString(3, details);
            ps.setLong(4, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.error("Error logging server event: {}", e.getMessage(), e);
        }
    }

    public List<Map<String, Object>> getRecentServerEvents(int limit) {
        String sql = "SELECT * FROM server_events ORDER BY timestamp_ms DESC LIMIT ?";
        List<Map<String, Object>> events = new ArrayList<>();
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit > 0 ? limit : 50);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("id", rs.getInt("id"));
                    map.put("eventType", rs.getString("event_type"));
                    map.put("message", rs.getString("message"));
                    map.put("details", rs.getString("details"));
                    map.put("timestampMs", rs.getLong("timestamp_ms"));
                    events.add(map);
                }
            }
        } catch (SQLException e) {
            logger.error("Error retrieving server events: {}", e.getMessage(), e);
        }
        return events;
    }

    public Map<String, Object> getMetricsSummary() {
        String sql = """
            SELECT
                COUNT(*) as total_count,
                SUM(CASE WHEN status = 'COMPLETED' THEN 1 ELSE 0 END) as completed_count,
                SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END) as failed_count,
                AVG(CASE WHEN status = 'COMPLETED' AND avg_rtt_ms IS NOT NULL THEN avg_rtt_ms END) as overall_avg_rtt,
                AVG(CASE WHEN status = 'COMPLETED' AND throughput_mbps IS NOT NULL THEN throughput_mbps END) as overall_avg_throughput,
                AVG(CASE WHEN status = 'COMPLETED' AND packet_loss_pct IS NOT NULL THEN packet_loss_pct END) as overall_avg_loss
            FROM experiments
        """;

        Map<String, Object> map = new LinkedHashMap<>();
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                map.put("totalExperiments", rs.getInt("total_count"));
                map.put("completedExperiments", rs.getInt("completed_count"));
                map.put("failedExperiments", rs.getInt("failed_count"));
                map.put("overallAvgRttMs", getNullableDouble(rs, "overall_avg_rtt"));
                map.put("overallAvgThroughputMbps", getNullableDouble(rs, "overall_avg_throughput"));
                map.put("overallAvgLossPct", getNullableDouble(rs, "overall_avg_loss"));
            }
        } catch (SQLException e) {
            logger.error("Error computing metrics summary: {}", e.getMessage(), e);
        }
        return map;
    }

    private Experiment mapResultSetToExperiment(ResultSet rs) throws SQLException {
        Experiment exp = new Experiment();
        exp.setId(rs.getString("id"));
        exp.setTestId(rs.getString("test_id"));
        exp.setName(rs.getString("name"));
        exp.setProtocol(Protocol.valueOf(rs.getString("protocol")));
        exp.setTestMode(TestMode.valueOf(rs.getString("test_mode")));
        exp.setStatus(TestStatus.valueOf(rs.getString("status")));
        exp.setTargetHost(rs.getString("target_host"));
        exp.setTargetPort(rs.getInt("target_port"));
        exp.setPayloadSize(rs.getInt("payload_size"));

        exp.setConnectionSetupTimeMs(getNullableDouble(rs, "conn_setup_time_ms"));
        exp.setStartTimeMs(getNullableLong(rs, "start_time_ms"));
        exp.setEndTimeMs(getNullableLong(rs, "end_time_ms"));
        exp.setDurationMs(getNullableDouble(rs, "duration_ms"));

        exp.setPacketsSent(getNullableLong(rs, "packets_sent"));
        exp.setPacketsReceived(getNullableLong(rs, "packets_received"));
        exp.setBytesSent(getNullableLong(rs, "bytes_sent"));
        exp.setBytesReceived(getNullableLong(rs, "bytes_received"));

        exp.setThroughputMbps(getNullableDouble(rs, "throughput_mbps"));
        exp.setDataRateMBs(getNullableDouble(rs, "data_rate_mb_s"));
        exp.setAvgRttMs(getNullableDouble(rs, "avg_rtt_ms"));
        exp.setMinRttMs(getNullableDouble(rs, "min_rtt_ms"));
        exp.setMaxRttMs(getNullableDouble(rs, "max_rtt_ms"));
        exp.setMedianRttMs(getNullableDouble(rs, "median_rtt_ms"));
        exp.setP95RttMs(getNullableDouble(rs, "p95_rtt_ms"));
        exp.setJitterMs(getNullableDouble(rs, "jitter_ms"));
        exp.setPacketLossPct(getNullableDouble(rs, "packet_loss_pct"));
        exp.setDeliveryRatioPct(getNullableDouble(rs, "delivery_ratio_pct"));
        exp.setBandwidthUtilPct(getNullableDouble(rs, "bandwidth_util_pct"));
        exp.setLinkCapacityMbps(getNullableDouble(rs, "link_capacity_mbps"));

        exp.setErrorMessage(rs.getString("error_message"));
        exp.setCreatedAt(rs.getLong("created_at"));

        String configJson = rs.getString("config_json");
        if (configJson != null && !configJson.isEmpty()) {
            exp.setConfig(gson.fromJson(configJson, TestConfig.class));
        }
        return exp;
    }

    private void setNullableDouble(PreparedStatement ps, int index, Double val) throws SQLException {
        if (val != null) ps.setDouble(index, val); else ps.setNull(index, Types.REAL);
    }

    private void setNullableLong(PreparedStatement ps, int index, Long val) throws SQLException {
        if (val != null) ps.setLong(index, val); else ps.setNull(index, Types.INTEGER);
    }

    private Double getNullableDouble(ResultSet rs, String col) throws SQLException {
        double val = rs.getDouble(col);
        return rs.wasNull() ? null : val;
    }

    private Long getNullableLong(ResultSet rs, String col) throws SQLException {
        long val = rs.getLong(col);
        return rs.wasNull() ? null : val;
    }
}
