package kr.minex.itempickuppriority.manager;

import org.bukkit.plugin.java.JavaPlugin;
import kr.minex.itempickuppriority.cache.CacheManager;
import kr.minex.itempickuppriority.cache.SessionOrderTracker;
import kr.minex.itempickuppriority.config.PluginConfig;
import kr.minex.itempickuppriority.database.DatabaseProvider;
import kr.minex.itempickuppriority.messaging.ProxyMessenger;
import kr.minex.itempickuppriority.model.PlayerPriority;
import kr.minex.itempickuppriority.model.TimeMode;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 우선순위 관리의 핵심 비즈니스 로직
 *
 * DB-Primary 아키텍처:
 * - DB가 항상 진실의 원천 (Source of Truth)
 * - 모든 쓰기는 DB에 먼저 → 성공하면 캐시 업데이트 (Write-Through)
 * - 캐시 미스 시 DB에서 로드 (Read-Through)
 */
public class PriorityManager {

    private final JavaPlugin plugin;
    private final Logger logger;
    private final DatabaseProvider database;
    private final CacheManager cacheManager;
    private final SessionOrderTracker sessionTracker;
    private final PluginConfig config;

    // 프록시 메시징 (PROXY_MYSQL 모드에서만 사용)
    private ProxyMessenger proxyMessenger;

    // 동시성 제어용 락
    private final Object rankLock = new Object();

    public PriorityManager(JavaPlugin plugin, DatabaseProvider database,
                           CacheManager cacheManager, SessionOrderTracker sessionTracker,
                           PluginConfig config) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.database = database;
        this.cacheManager = cacheManager;
        this.sessionTracker = sessionTracker;
        this.config = config;
    }

    /**
     * 프록시 메신저 설정 (PROXY_MYSQL 모드에서 호출)
     */
    public void setProxyMessenger(ProxyMessenger messenger) {
        this.proxyMessenger = messenger;
    }

    // ==================== 조회 (Read-Through) ====================

    /**
     * 플레이어의 현재 순위 조회
     * 리스트 인덱스 기반으로 계산 (DB 재정렬 후에도 정확한 순위 반영)
     *
     * 주의: priority 필드 값이 아닌, 전체 목록에서의 위치(인덱스+1)를 반환
     * - DB에서 삭제 후 재정렬되면 priority 필드는 갱신되지만
     * - 캐시 동기화 지연 시 priority 필드가 예전 값일 수 있음
     * - 따라서 DB 기준 정렬된 리스트에서 인덱스로 순위 계산
     *
     * @param uuid 플레이어 UUID
     * @return 순위 (없으면 -1)
     */
    public int getPlayerRank(UUID uuid) {
        // DB 기준 전체 목록 조회 (순위순 정렬됨)
        List<PlayerPriority> allPlayers = database.findAll();

        // 리스트에서 해당 플레이어의 인덱스 찾기
        for (int i = 0; i < allPlayers.size(); i++) {
            if (allPlayers.get(i).getUuid().equals(uuid)) {
                return i + 1;  // 인덱스 + 1 = 순위
            }
        }

        return -1;  // 플레이어를 찾지 못함
    }

    /**
     * 플레이어 우선순위 정보 조회
     * Read-Through: 캐시 미스 시 DB에서 자동 로드
     */
    public Optional<PlayerPriority> getPlayerPriority(UUID uuid) {
        return cacheManager.get(uuid);
    }

    /**
     * 전체 순위 목록 조회 (페이징)
     * DB 기준으로 조회 (정확한 순위 보장)
     */
    public List<PlayerPriority> getRankingList(int page, int pageSize) {
        return cacheManager.getRankingList(page, pageSize);
    }

    /**
     * 전체 플레이어 수 조회 (DB 기준)
     */
    public int getTotalCount() {
        return cacheManager.getTotalCount();
    }

    // ==================== 접속/퇴장 처리 (Write-Through) ====================

    /**
     * 플레이어 접속 시 처리
     *
     * Write-Through 패턴:
     * 1. 임시 캐시 생성 (즉각적인 픽업 판정용)
     * 2. DB에 저장/업데이트
     * 3. 성공하면 캐시 갱신
     */
    public void onPlayerJoin(UUID uuid, String name) {
        // 1. 임시 캐시 생성 (메인 스레드에서 동기 실행)
        // 비동기 DB 처리 완료 전에 픽업 이벤트가 발생해도 판정 가능
        int tempRank = cacheManager.cacheSize() + 1;
        PlayerPriority tempPriority = PlayerPriority.builder()
                .uuid(uuid)
                .name(name)
                .priority(tempRank)
                .markOnline()
                .build();
        cacheManager.putToCache(uuid, tempPriority);

        if (config.isDebug()) {
            logger.info("임시 캐시 생성: " + name + " (임시 순위: " + tempRank + ")");
        }

        // 2. 비동기로 DB 처리 (Write-Through)
        CompletableFuture.runAsync(() -> {
            synchronized (rankLock) {
                try {
                    Optional<PlayerPriority> existing = database.findByUuid(uuid);

                    PlayerPriority priority;
                    if (existing.isEmpty()) {
                        // 신규 플레이어 - 트랜잭션으로 마지막 순위 + 1 할당
                        priority = registerNewPlayer(uuid, name);
                        logger.info("신규 플레이어 등록: " + name + " (순위: " + priority.getPriority() + ")");
                    } else {
                        // 기존 플레이어 - 온라인 상태로 전환
                        PlayerPriority oldData = existing.get();
                        priority = oldData.toBuilder()
                                .name(name)
                                .markOnline()  // expires_at=null, remaining_seconds=null
                                .build();

                        // DB에 먼저 업데이트 (Write-Through)
                        database.update(priority);

                        if (config.isDebug()) {
                            logger.info("플레이어 재접속: " + name + " (순위: " + priority.getPriority() +
                                    ", 이전 expires_at: " + oldData.getExpiresAt() + ")");
                        }
                    }

                    // 3. DB 성공 후 캐시 갱신
                    cacheManager.putToCache(uuid, priority);

                    // 4. 다른 서버에 캐시 무효화 알림 (PROXY_MYSQL 모드)
                    notifyCacheInvalidate();

                } catch (Exception e) {
                    logger.log(Level.SEVERE, "플레이어 접속 처리 실패: " + name, e);
                }
            }
        });
    }

    /**
     * 신규 플레이어 등록 (트랜잭션)
     */
    private PlayerPriority registerNewPlayer(UUID uuid, String name) throws SQLException {
        return database.executeInTransaction(conn -> {
            try {
                // 최대 순위 조회
                int maxRank = 0;
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT COALESCE(MAX(priority), 0) FROM player_priority")) {
                    var rs = ps.executeQuery();
                    if (rs.next()) {
                        maxRank = rs.getInt(1);
                    }
                }

                int newRank = maxRank + 1;
                PlayerPriority p = PlayerPriority.builder()
                        .uuid(uuid)
                        .name(name)
                        .priority(newRank)
                        .markOnline()
                        .build();

                // INSERT
                String insertSql = """
                        INSERT INTO player_priority
                        (uuid, name, priority, registered_at, last_seen, expires_at, remaining_seconds)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """;
                try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                    ps.setString(1, p.getUuid().toString());
                    ps.setString(2, p.getName());
                    ps.setInt(3, p.getPriority());
                    database.setInstant(ps, 4, p.getRegisteredAt());
                    database.setInstant(ps, 5, p.getLastSeen());
                    database.setInstant(ps, 6, p.getExpiresAt());
                    ps.setObject(7, p.getRemainingSeconds());
                    ps.executeUpdate();
                }

                return p;
            } catch (SQLException e) {
                throw new RuntimeException("신규 플레이어 등록 SQL 오류", e);
            }
        });
    }

    /**
     * 플레이어 퇴장 시 처리
     *
     * Write-Through 패턴:
     * 1. 만료 시간 계산
     * 2. DB에 즉시 저장
     * 3. 성공하면 캐시 갱신
     */
    public void onPlayerQuit(UUID uuid) {
        Optional<PlayerPriority> optCurrent = cacheManager.get(uuid);
        if (optCurrent.isEmpty()) {
            return;
        }

        PlayerPriority current = optCurrent.get();
        PlayerPriority updated;

        if (config.getTimeMode() == TimeMode.REAL_TIME) {
            updated = current.toBuilder()
                    .markOfflineRealTime(config.getExpirationSeconds())
                    .build();
        } else {
            updated = current.toBuilder()
                    .markOfflineServerTime((int) config.getExpirationSeconds())
                    .build();
        }

        // Write-Through: DB에 먼저 저장
        CompletableFuture.runAsync(() -> {
            try {
                database.update(updated);
                // DB 성공 후 캐시 갱신
                cacheManager.putToCache(uuid, updated);
            } catch (Exception e) {
                logger.log(Level.SEVERE, "플레이어 퇴장 DB 저장 실패: " + updated.getName(), e);
            }
        });

        // 캐시는 즉시 업데이트 (UI 반영용)
        cacheManager.putToCache(uuid, updated);

        if (config.isDebug()) {
            logger.info("플레이어 퇴장: " + current.getName() +
                    " (만료: " + (config.getTimeMode() == TimeMode.REAL_TIME ?
                    updated.getExpiresAt() : updated.getRemainingSeconds() + "초") + ")");
        }
    }

    // ==================== 순위 변경 (Write-Through) ====================

    /**
     * 플레이어 순위 변경
     * DB 트랜잭션으로 처리 후 캐시 무효화
     */
    public CompletableFuture<Boolean> changeRank(UUID targetUuid, int newRank) {
        return CompletableFuture.supplyAsync(() -> {
            synchronized (rankLock) {
                try {
                    int currentRank = getPlayerRank(targetUuid);
                    if (currentRank == -1) {
                        logger.warning("순위 변경 실패: 플레이어를 찾을 수 없음");
                        return false;
                    }

                    if (currentRank == newRank) {
                        return true;
                    }

                    int maxRank = database.getMaxPriority();
                    int adjustedRank = Math.max(1, Math.min(newRank, maxRank));
                    final int finalNewRank = adjustedRank;

                    // DB 트랜잭션으로 순위 변경
                    boolean success = database.executeInTransaction(conn -> {
                        try {
                            if (currentRank < finalNewRank) {
                                shiftRanksDown(conn, currentRank + 1, finalNewRank);
                            } else {
                                shiftRanksUp(conn, finalNewRank, currentRank - 1);
                            }
                            updatePlayerRank(conn, targetUuid, finalNewRank);
                            return true;
                        } catch (SQLException e) {
                            throw new RuntimeException("순위 변경 SQL 오류", e);
                        }
                    });

                    if (success) {
                        // DB 성공 후 캐시 무효화 (다음 조회 시 DB에서 재로드)
                        cacheManager.clear();
                        cacheManager.warmUp();
                        notifyCacheInvalidate();
                    }

                    return success;

                } catch (Exception e) {
                    logger.log(Level.SEVERE, "순위 변경 실패", e);
                    return false;
                }
            }
        });
    }

    /**
     * 순위 초기화
     * DB 트랜잭션으로 처리 후 캐시 무효화
     */
    public CompletableFuture<Integer> resetRanking() {
        return CompletableFuture.supplyAsync(() -> {
            synchronized (rankLock) {
                try {
                    List<Map.Entry<UUID, String>> onlinePlayers =
                            sessionTracker.getOnlinePlayersWithNamesInJoinOrder();

                    // DB 트랜잭션으로 초기화
                    int result = database.executeInTransaction(conn -> {
                        try {
                            // 모든 데이터 삭제
                            try (PreparedStatement ps = conn.prepareStatement(
                                    "DELETE FROM player_priority")) {
                                ps.executeUpdate();
                            }

                            if (onlinePlayers.isEmpty()) {
                                logger.info("순위 초기화 완료: DB 비움 (온라인 플레이어 없음)");
                                return 0;
                            }

                            // 세션 순서대로 새 순위 할당
                            String insertSql = """
                                    INSERT INTO player_priority
                                    (uuid, name, priority, registered_at, last_seen, expires_at, remaining_seconds)
                                    VALUES (?, ?, ?, ?, ?, ?, ?)
                                    """;

                            int rank = 1;
                            Instant now = Instant.now();

                            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                                for (Map.Entry<UUID, String> entry : onlinePlayers) {
                                    ps.setString(1, entry.getKey().toString());
                                    ps.setString(2, entry.getValue());
                                    ps.setInt(3, rank);
                                    database.setInstant(ps, 4, now);
                                    database.setInstant(ps, 5, now);
                                    database.setInstant(ps, 6, null);
                                    ps.setObject(7, null);
                                    ps.addBatch();
                                    rank++;
                                }
                                ps.executeBatch();
                            }

                            logger.info("순위 초기화 완료: " + onlinePlayers.size() + "명");
                            return onlinePlayers.size();
                        } catch (SQLException e) {
                            throw new RuntimeException("순위 초기화 SQL 오류", e);
                        }
                    });

                    // 세션 트래커 초기화 및 재등록
                    sessionTracker.clear();
                    for (Map.Entry<UUID, String> entry : onlinePlayers) {
                        sessionTracker.recordJoin(entry.getKey(), entry.getValue());
                    }

                    // 캐시 무효화 및 워밍업
                    cacheManager.clear();
                    cacheManager.warmUp();
                    notifyRankingReset();

                    return result;

                } catch (Exception e) {
                    logger.log(Level.SEVERE, "순위 초기화 실패", e);
                    return -1;
                }
            }
        });
    }

    // ==================== 프록시 메시징 ====================

    private void notifyCacheInvalidate() {
        if (proxyMessenger != null && proxyMessenger.isEnabled()) {
            proxyMessenger.sendCacheInvalidate();
            if (config.isDebug()) {
                logger.info("프록시 캐시 무효화 메시지 전송");
            }
        }
    }

    private void notifyRankingReset() {
        if (proxyMessenger != null && proxyMessenger.isEnabled()) {
            proxyMessenger.sendRankingReset();
            if (config.isDebug()) {
                logger.info("프록시 순위 초기화 메시지 전송");
            }
        }
    }

    // ==================== 캐시 관리 ====================

    /**
     * 캐시 워밍업 (플러그인 시작 시)
     */
    public void warmUpCache() {
        cacheManager.warmUp();
    }

    /**
     * 캐시 통계 출력
     */
    public String getCacheStats() {
        return cacheManager.getStats();
    }

    // ==================== 헬퍼 메서드 ====================

    private void shiftRanksUp(Connection conn, int fromRank, int toRank) throws SQLException {
        String sqlTemp = "UPDATE player_priority SET priority = -priority " +
                "WHERE priority >= ? AND priority <= ?";
        try (PreparedStatement ps = conn.prepareStatement(sqlTemp)) {
            ps.setInt(1, fromRank);
            ps.setInt(2, toRank);
            ps.executeUpdate();
        }

        String sqlFinal = "UPDATE player_priority SET priority = (-priority) + 1 " +
                "WHERE priority < 0";
        try (PreparedStatement ps = conn.prepareStatement(sqlFinal)) {
            ps.executeUpdate();
        }
    }

    private void shiftRanksDown(Connection conn, int fromRank, int toRank) throws SQLException {
        String sqlTemp = "UPDATE player_priority SET priority = -priority " +
                "WHERE priority >= ? AND priority <= ?";
        try (PreparedStatement ps = conn.prepareStatement(sqlTemp)) {
            ps.setInt(1, fromRank);
            ps.setInt(2, toRank);
            ps.executeUpdate();
        }

        String sqlFinal = "UPDATE player_priority SET priority = (-priority) - 1 " +
                "WHERE priority < 0";
        try (PreparedStatement ps = conn.prepareStatement(sqlFinal)) {
            ps.executeUpdate();
        }
    }

    private void updatePlayerRank(Connection conn, UUID uuid, int newRank) throws SQLException {
        String sql = "UPDATE player_priority SET priority = ? WHERE uuid = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, newRank);
            ps.setString(2, uuid.toString());
            ps.executeUpdate();
        }
    }

    // ==================== Getter ====================

    public CacheManager getCacheManager() {
        return cacheManager;
    }

    public SessionOrderTracker getSessionTracker() {
        return sessionTracker;
    }
}
