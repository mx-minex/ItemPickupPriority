package kr.minex.itempickuppriority.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import kr.minex.itempickuppriority.model.PlayerPriority;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * MySQL 데이터베이스 제공자
 * PROXY_MYSQL 모드에서 사용
 * 다중 서버 환경에서 중앙 집중식 데이터 관리
 */
public class MySQLProvider implements DatabaseProvider {

    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;
    private final int poolSize;
    private final Logger logger;
    private HikariDataSource dataSource;

    // SQL 쿼리 상수 (MySQL 전용)
    // MySQL 5.7 호환성: TIMESTAMP NOT NULL 컬럼에는 DEFAULT 값 필요
    // CURRENT_TIMESTAMP(3)는 밀리초 정밀도 지원
    private static final String CREATE_TABLE = """
            CREATE TABLE IF NOT EXISTS player_priority (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                uuid VARCHAR(36) NOT NULL,
                name VARCHAR(16) NOT NULL,
                priority INT NOT NULL,
                registered_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                last_seen TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
                expires_at TIMESTAMP(3) NULL DEFAULT NULL,
                remaining_seconds INT DEFAULT NULL,
                UNIQUE KEY uk_uuid (uuid),
                INDEX idx_priority (priority),
                INDEX idx_expires_at (expires_at),
                INDEX idx_remaining_seconds (remaining_seconds)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            """;

    public MySQLProvider(String host, int port, String database,
                         String username, String password, int poolSize, Logger logger) {
        this.host = host;
        this.port = port;
        this.database = database;
        this.username = username;
        this.password = password;
        this.poolSize = poolSize;
        this.logger = logger;
    }

    @Override
    public void initialize() throws SQLException {
        // HikariCP 설정
        HikariConfig config = new HikariConfig();
        // characterEncoding은 Java charset 이름 사용 (utf8mb4는 MySQL 전용)
        // UTF-8로 설정하면 MySQL Connector/J가 자동으로 utf8mb4로 매핑
        config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database +
                "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8&useUnicode=true");
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");

        // 커넥션 풀 설정
        config.setMaximumPoolSize(poolSize);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        config.setPoolName("ItemPickupPriority-MySQL");

        // MySQL 전용 최적화 설정
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");
        config.addDataSourceProperty("useLocalSessionState", "true");
        config.addDataSourceProperty("rewriteBatchedStatements", "true");
        config.addDataSourceProperty("cacheResultSetMetadata", "true");
        config.addDataSourceProperty("cacheServerConfiguration", "true");
        config.addDataSourceProperty("elideSetAutoCommits", "true");
        config.addDataSourceProperty("maintainTimeStats", "false");

        dataSource = new HikariDataSource(config);

        // 테이블 생성
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(CREATE_TABLE);
        }

        logger.info("MySQL 데이터베이스 연결 완료: " + host + ":" + port + "/" + database);
    }

    @Override
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            logger.info("MySQL 데이터베이스 연결 종료");
        }
    }

    @Override
    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    @Override
    public boolean isMySQL() {
        return true;
    }

    // ==================== 조회 ====================

    @Override
    public Optional<PlayerPriority> findByUuid(UUID uuid) {
        String sql = "SELECT * FROM player_priority WHERE uuid = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSet(rs));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "UUID로 조회 실패: " + uuid, e);
        }
        return Optional.empty();
    }

    @Override
    public Optional<PlayerPriority> findByName(String name) {
        // MySQL에서 대소문자 무시 검색 (LOWER 함수 사용)
        String sql = "SELECT * FROM player_priority WHERE LOWER(name) = LOWER(?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSet(rs));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "이름으로 조회 실패: " + name, e);
        }
        return Optional.empty();
    }

    @Override
    public Optional<PlayerPriority> findByPriority(int priority) {
        String sql = "SELECT * FROM player_priority WHERE priority = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, priority);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSet(rs));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "순위로 조회 실패: " + priority, e);
        }
        return Optional.empty();
    }

    @Override
    public List<PlayerPriority> findAll() {
        String sql = "SELECT * FROM player_priority ORDER BY priority ASC";
        List<PlayerPriority> result = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(mapResultSet(rs));
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "전체 조회 실패", e);
        }
        return result;
    }

    @Override
    public List<PlayerPriority> findAllPaged(int page, int pageSize) {
        String sql = "SELECT * FROM player_priority ORDER BY priority ASC LIMIT ? OFFSET ?";
        List<PlayerPriority> result = new ArrayList<>();
        int offset = (page - 1) * pageSize;

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, pageSize);
            ps.setInt(2, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapResultSet(rs));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "페이징 조회 실패", e);
        }
        return result;
    }

    @Override
    public int count() {
        String sql = "SELECT COUNT(*) FROM player_priority";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "카운트 조회 실패", e);
        }
        return 0;
    }

    @Override
    public int getMaxPriority() {
        String sql = "SELECT COALESCE(MAX(priority), 0) FROM player_priority";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "최대 순위 조회 실패", e);
        }
        return 0;
    }

    // ==================== 저장/수정/삭제 ====================

    @Override
    public void insert(PlayerPriority priority) {
        String sql = """
                INSERT INTO player_priority
                (uuid, name, priority, registered_at, last_seen, expires_at, remaining_seconds)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            setInsertParameters(ps, priority);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "INSERT 실패: " + priority.getUuid(), e);
        }
    }

    @Override
    public void update(PlayerPriority priority) {
        String sql = """
                UPDATE player_priority SET
                    name = ?,
                    priority = ?,
                    last_seen = ?,
                    expires_at = ?,
                    remaining_seconds = ?
                WHERE uuid = ?
                """;
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, priority.getName());
            ps.setInt(2, priority.getPriority());
            ps.setTimestamp(3, Timestamp.from(priority.getLastSeen()));
            ps.setTimestamp(4, priority.getExpiresAt() != null ? Timestamp.from(priority.getExpiresAt()) : null);
            ps.setObject(5, priority.getRemainingSeconds());
            ps.setString(6, priority.getUuid().toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "UPDATE 실패: " + priority.getUuid(), e);
        }
    }

    @Override
    public void saveOrUpdate(PlayerPriority priority) {
        // MySQL의 ON DUPLICATE KEY UPDATE 사용
        String sql = """
                INSERT INTO player_priority
                (uuid, name, priority, registered_at, last_seen, expires_at, remaining_seconds)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    name = VALUES(name),
                    priority = VALUES(priority),
                    last_seen = VALUES(last_seen),
                    expires_at = VALUES(expires_at),
                    remaining_seconds = VALUES(remaining_seconds)
                """;
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            setInsertParameters(ps, priority);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "UPSERT 실패: " + priority.getUuid(), e);
        }
    }

    @Override
    public void delete(UUID uuid) {
        String sql = "DELETE FROM player_priority WHERE uuid = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "DELETE 실패: " + uuid, e);
        }
    }

    @Override
    public void deleteAll() {
        String sql = "DELETE FROM player_priority";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "전체 삭제 실패", e);
        }
    }

    // ==================== 순위 조작 ====================

    @Override
    public void incrementPriorities(int fromPriority, int toPriority) {
        String sql = "UPDATE player_priority SET priority = priority + 1 WHERE priority >= ? AND priority <= ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, fromPriority);
            ps.setInt(2, toPriority);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "순위 증가 실패", e);
        }
    }

    @Override
    public void decrementPrioritiesAbove(int fromPriority) {
        String sql = "UPDATE player_priority SET priority = priority - 1 WHERE priority > ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, fromPriority);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "순위 감소 실패", e);
        }
    }

    @Override
    public void deleteAndReorderInTransaction(UUID uuid, int removedRank) {
        try {
            executeInTransaction(conn -> {
                try {
                    // 1. 플레이어 삭제
                    String deleteSql = "DELETE FROM player_priority WHERE uuid = ?";
                    try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
                        ps.setString(1, uuid.toString());
                        ps.executeUpdate();
                    }

                    // 2. 순위 재정렬 (삭제된 순위보다 높은 순위 -1)
                    String reorderSql = "UPDATE player_priority SET priority = priority - 1 WHERE priority > ?";
                    try (PreparedStatement ps = conn.prepareStatement(reorderSql)) {
                        ps.setInt(1, removedRank);
                        ps.executeUpdate();
                    }
                } catch (SQLException e) {
                    throw new RuntimeException("삭제 및 순위 재정렬 SQL 오류", e);
                }

                return null;
            });
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "삭제 및 순위 재정렬 트랜잭션 실패: " + uuid, e);
        }
    }

    @Override
    public void updatePriority(UUID uuid, int newPriority) {
        String sql = "UPDATE player_priority SET priority = ? WHERE uuid = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, newPriority);
            ps.setString(2, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "순위 업데이트 실패: " + uuid, e);
        }
    }

    // ==================== 만료 관련 ====================

    @Override
    public List<PlayerPriority> findExpiredByRealTime(Instant now) {
        // MySQL TIMESTAMP 비교
        String sql = "SELECT * FROM player_priority WHERE expires_at IS NOT NULL AND expires_at <= ?";
        List<PlayerPriority> result = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.from(now));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapResultSet(rs));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "만료 조회 실패 (REAL_TIME)", e);
        }
        return result;
    }

    @Override
    public List<PlayerPriority> findExpiredByServerTime() {
        return findExpiredByServerTime(0);
    }

    @Override
    public List<PlayerPriority> findExpiredByServerTime(int threshold) {
        String sql = "SELECT * FROM player_priority WHERE remaining_seconds IS NOT NULL AND remaining_seconds <= ?";
        List<PlayerPriority> result = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, threshold);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapResultSet(rs));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "만료 조회 실패 (SERVER_TIME)", e);
        }
        return result;
    }

    @Override
    public void decrementRemainingSeconds(int decrementBy) {
        String sql = "UPDATE player_priority SET remaining_seconds = remaining_seconds - ? " +
                "WHERE remaining_seconds IS NOT NULL";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, decrementBy);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "remaining_seconds 감소 실패", e);
        }
    }

    @Override
    public int clearExpirationBatch(Collection<UUID> uuids) {
        if (uuids == null || uuids.isEmpty()) {
            return 0;
        }

        // IN 절 대신 단일 쿼리로 여러 UUID 처리 (성능 최적화)
        // MySQL에서 expires_at 또는 remaining_seconds가 NOT NULL인 것만 업데이트
        String placeholders = String.join(",", uuids.stream()
                .map(u -> "?")
                .toArray(String[]::new));

        String sql = "UPDATE player_priority SET expires_at = NULL, remaining_seconds = NULL, last_seen = ? " +
                "WHERE uuid IN (" + placeholders + ") " +
                "AND (expires_at IS NOT NULL OR remaining_seconds IS NOT NULL)";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.from(Instant.now()));

            int idx = 2;
            for (UUID uuid : uuids) {
                ps.setString(idx++, uuid.toString());
            }

            return ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "일괄 만료시간 클리어 실패", e);
            return 0;
        }
    }

    @Override
    public int recoverGhostUsers(Collection<UUID> excludeUuids, Instant expiresAt, Integer remainingSeconds) {
        // 유령 유저: expires_at = NULL AND remaining_seconds = NULL (온라인 상태로 보이지만 실제로는 아님)
        // excludeUuids에 있는 플레이어는 실제 온라인이므로 제외

        StringBuilder sql = new StringBuilder();
        sql.append("UPDATE player_priority SET expires_at = ?, remaining_seconds = ? ");
        sql.append("WHERE expires_at IS NULL AND remaining_seconds IS NULL");

        // 제외할 UUID가 있으면 NOT IN 절 추가
        if (!excludeUuids.isEmpty()) {
            String placeholders = String.join(",", excludeUuids.stream()
                    .map(u -> "?")
                    .toArray(String[]::new));
            sql.append(" AND uuid NOT IN (").append(placeholders).append(")");
        }

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {

            // MySQL TIMESTAMP 형식으로 만료 시간 설정
            ps.setTimestamp(1, expiresAt != null ? Timestamp.from(expiresAt) : null);
            ps.setObject(2, remainingSeconds);

            // 제외할 UUID 설정
            int idx = 3;
            for (UUID uuid : excludeUuids) {
                ps.setString(idx++, uuid.toString());
            }

            int updated = ps.executeUpdate();
            if (updated > 0) {
                logger.info("유령 유저 복구 완료: " + updated + "명에게 만료시간 설정");
            }
            return updated;
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "유령 유저 복구 실패", e);
            return 0;
        }
    }

    // ==================== 트랜잭션 ====================

    @Override
    public <T> T executeInTransaction(Function<Connection, T> operation) throws SQLException {
        Connection conn = null;
        try {
            conn = getConnection();
            conn.setAutoCommit(false);
            // MySQL 트랜잭션 격리 수준 설정 (READ COMMITTED 권장)
            conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

            T result = operation.apply(conn);

            conn.commit();
            return result;
        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    logger.log(Level.SEVERE, "트랜잭션 롤백 실패", rollbackEx);
                }
            }
            throw e;
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException closeEx) {
                    logger.log(Level.WARNING, "커넥션 닫기 실패", closeEx);
                }
            }
        }
    }

    // ==================== 헬퍼 메서드 ====================

    /**
     * ResultSet을 PlayerPriority 객체로 매핑 (MySQL TIMESTAMP 처리)
     */
    private PlayerPriority mapResultSet(ResultSet rs) throws SQLException {
        // MySQL TIMESTAMP → Instant 변환
        Timestamp registeredAtTs = rs.getTimestamp("registered_at");
        Timestamp lastSeenTs = rs.getTimestamp("last_seen");
        Timestamp expiresAtTs = rs.getTimestamp("expires_at");

        // MySQL에서 NULL Integer 처리
        int remainingSecondsRaw = rs.getInt("remaining_seconds");
        Integer remainingSeconds = rs.wasNull() ? null : remainingSecondsRaw;

        return PlayerPriority.builder()
                .uuid(UUID.fromString(rs.getString("uuid")))
                .name(rs.getString("name"))
                .priority(rs.getInt("priority"))
                .registeredAt(registeredAtTs != null ? registeredAtTs.toInstant() : Instant.now())
                .lastSeen(lastSeenTs != null ? lastSeenTs.toInstant() : Instant.now())
                .expiresAt(expiresAtTs != null ? expiresAtTs.toInstant() : null)
                .remainingSeconds(remainingSeconds)
                .build();
    }

    /**
     * INSERT용 파라미터 설정 (MySQL TIMESTAMP 사용)
     */
    private void setInsertParameters(PreparedStatement ps, PlayerPriority priority) throws SQLException {
        ps.setString(1, priority.getUuid().toString());
        ps.setString(2, priority.getName());
        ps.setInt(3, priority.getPriority());
        ps.setTimestamp(4, Timestamp.from(priority.getRegisteredAt()));
        ps.setTimestamp(5, Timestamp.from(priority.getLastSeen()));
        ps.setTimestamp(6, priority.getExpiresAt() != null ? Timestamp.from(priority.getExpiresAt()) : null);
        ps.setObject(7, priority.getRemainingSeconds());
    }
}
