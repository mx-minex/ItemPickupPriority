package kr.minex.itempickuppriority.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import kr.minex.itempickuppriority.model.PlayerPriority;

import java.io.File;
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
 * SQLite 데이터베이스 제공자
 * STANDALONE 모드에서 사용
 */
public class SQLiteProvider implements DatabaseProvider {

    private final File dataFolder;
    private final String fileName;
    private final Logger logger;
    private HikariDataSource dataSource;

    // SQL 쿼리 상수
    private static final String CREATE_TABLE = """
            CREATE TABLE IF NOT EXISTS player_priority (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                uuid TEXT NOT NULL UNIQUE,
                name TEXT NOT NULL,
                priority INTEGER NOT NULL,
                registered_at TEXT NOT NULL,
                last_seen TEXT NOT NULL,
                expires_at TEXT DEFAULT NULL,
                remaining_seconds INTEGER DEFAULT NULL
            )
            """;

    private static final String CREATE_INDEX_PRIORITY =
            "CREATE INDEX IF NOT EXISTS idx_priority ON player_priority(priority)";
    private static final String CREATE_INDEX_EXPIRES =
            "CREATE INDEX IF NOT EXISTS idx_expires ON player_priority(expires_at)";
    private static final String CREATE_INDEX_REMAINING =
            "CREATE INDEX IF NOT EXISTS idx_remaining ON player_priority(remaining_seconds)";

    public SQLiteProvider(File dataFolder, String fileName, Logger logger) {
        this.dataFolder = dataFolder;
        this.fileName = fileName;
        this.logger = logger;
    }

    @Override
    public void initialize() throws SQLException {
        // 데이터 폴더 생성
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        // HikariCP 설정
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + new File(dataFolder, fileName).getAbsolutePath());
        config.setDriverClassName("org.sqlite.JDBC");
        config.setMaximumPoolSize(1); // SQLite는 단일 연결만 지원
        config.setConnectionTestQuery("SELECT 1");
        config.setPoolName("ItemPickupPriority-SQLite");

        // SQLite 전용 설정
        config.addDataSourceProperty("journal_mode", "WAL");
        config.addDataSourceProperty("synchronous", "NORMAL");
        config.addDataSourceProperty("busy_timeout", "30000");

        dataSource = new HikariDataSource(config);

        // 테이블 생성
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(CREATE_TABLE);
            stmt.execute(CREATE_INDEX_PRIORITY);
            stmt.execute(CREATE_INDEX_EXPIRES);
            stmt.execute(CREATE_INDEX_REMAINING);
        }

        logger.info("SQLite 데이터베이스 초기화 완료: " + fileName);
    }

    @Override
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            logger.info("SQLite 데이터베이스 연결 종료");
        }
    }

    @Override
    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
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
        String sql = "SELECT * FROM player_priority WHERE name = ? COLLATE NOCASE";
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
        String sql = "SELECT MAX(priority) FROM player_priority";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1); // NULL이면 0 반환
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
            ps.setString(3, priority.getLastSeen().toString());
            ps.setString(4, priority.getExpiresAt() != null ? priority.getExpiresAt().toString() : null);
            ps.setObject(5, priority.getRemainingSeconds());
            ps.setString(6, priority.getUuid().toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "UPDATE 실패: " + priority.getUuid(), e);
        }
    }

    @Override
    public void saveOrUpdate(PlayerPriority priority) {
        String sql = """
                INSERT INTO player_priority
                (uuid, name, priority, registered_at, last_seen, expires_at, remaining_seconds)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(uuid) DO UPDATE SET
                    name = excluded.name,
                    priority = excluded.priority,
                    last_seen = excluded.last_seen,
                    expires_at = excluded.expires_at,
                    remaining_seconds = excluded.remaining_seconds
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
        String sql = "SELECT * FROM player_priority WHERE expires_at IS NOT NULL AND expires_at <= ?";
        List<PlayerPriority> result = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, now.toString());
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

        // SQLite도 IN 절 사용 가능
        String placeholders = String.join(",", uuids.stream()
                .map(u -> "?")
                .toArray(String[]::new));

        String sql = "UPDATE player_priority SET expires_at = NULL, remaining_seconds = NULL, last_seen = ? " +
                "WHERE uuid IN (" + placeholders + ") " +
                "AND (expires_at IS NOT NULL OR remaining_seconds IS NOT NULL)";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            // SQLite는 문자열로 저장
            ps.setString(1, Instant.now().toString());

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

            // 만료 시간 설정
            ps.setString(1, expiresAt != null ? expiresAt.toString() : null);
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
     * ResultSet을 PlayerPriority 객체로 매핑
     */
    private PlayerPriority mapResultSet(ResultSet rs) throws SQLException {
        String expiresAtStr = rs.getString("expires_at");

        // SQLite에서 NULL Integer 처리 - getObject(col, Integer.class)가 작동하지 않음
        int remainingSecondsRaw = rs.getInt("remaining_seconds");
        Integer remainingSeconds = rs.wasNull() ? null : remainingSecondsRaw;

        return PlayerPriority.builder()
                .uuid(UUID.fromString(rs.getString("uuid")))
                .name(rs.getString("name"))
                .priority(rs.getInt("priority"))
                .registeredAt(Instant.parse(rs.getString("registered_at")))
                .lastSeen(Instant.parse(rs.getString("last_seen")))
                .expiresAt(expiresAtStr != null ? Instant.parse(expiresAtStr) : null)
                .remainingSeconds(remainingSeconds)
                .build();
    }

    /**
     * INSERT용 파라미터 설정
     */
    private void setInsertParameters(PreparedStatement ps, PlayerPriority priority) throws SQLException {
        ps.setString(1, priority.getUuid().toString());
        ps.setString(2, priority.getName());
        ps.setInt(3, priority.getPriority());
        ps.setString(4, priority.getRegisteredAt().toString());
        ps.setString(5, priority.getLastSeen().toString());
        ps.setString(6, priority.getExpiresAt() != null ? priority.getExpiresAt().toString() : null);
        ps.setObject(7, priority.getRemainingSeconds());
    }
}
