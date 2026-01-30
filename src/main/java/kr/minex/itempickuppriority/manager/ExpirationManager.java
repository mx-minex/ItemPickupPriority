package kr.minex.itempickuppriority.manager;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import kr.minex.itempickuppriority.cache.SessionOrderTracker;
import kr.minex.itempickuppriority.config.PluginConfig;
import kr.minex.itempickuppriority.database.DatabaseProvider;
import kr.minex.itempickuppriority.model.OperationMode;
import kr.minex.itempickuppriority.model.PlayerPriority;
import kr.minex.itempickuppriority.model.TimeMode;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 플레이어 우선순위 만료 관리
 * - 실제 시간 모드: 주기적으로 만료 확인
 * - 서버 가동 시간 모드: 서버 틱마다 remaining_seconds 감소
 *
 * PROXY_MYSQL 모드 서버 이동 처리:
 * - 만료 체크 시 유예 시간(GRACE_PERIOD_SECONDS)을 적용하여
 *   서버 이동 중인 플레이어가 즉시 삭제되지 않도록 함
 */
public class ExpirationManager {

    /**
     * 서버 이동 유예 시간 (초)
     * 만료 시점으로부터 이 시간이 지나야 실제로 삭제됨
     * 서버 이동에 보통 1-3초 소요, 여유있게 3초로 설정
     */
    private static final long GRACE_PERIOD_SECONDS = 3;

    private final JavaPlugin plugin;
    private final Logger logger;
    private final DatabaseProvider database;
    private final PriorityManager priorityManager;
    private final SessionOrderTracker sessionTracker;
    private final PluginConfig config;

    private BukkitTask expirationTask;

    public ExpirationManager(JavaPlugin plugin, DatabaseProvider database,
                             PriorityManager priorityManager, SessionOrderTracker sessionTracker,
                             PluginConfig config) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.database = database;
        this.priorityManager = priorityManager;
        this.sessionTracker = sessionTracker;
        this.config = config;
    }

    /**
     * 만료 체크 스케줄러 시작
     */
    public void start() {
        if (config.getTimeMode() == TimeMode.REAL_TIME) {
            startRealTimeChecker();
        } else {
            startServerTimeChecker();
        }

        if (config.isDebug()) {
            logger.info("만료 관리자 시작: " + config.getTimeMode());
        }
    }

    /**
     * 실제 시간 기준 만료 확인 (3초마다)
     * 3초 주기로 충분히 빠르며 서버 부하도 미미함
     */
    private void startRealTimeChecker() {
        // 즉시 첫 번째 체크 실행 (0틱 지연)
        expirationTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
                plugin,
                this::checkRealTimeExpiration,
                0L,    // 즉시 시작
                60L    // 3초마다 실행 (20틱 = 1초)
        );
    }

    /**
     * 실제 시간 기준 만료 체크 로직
     *
     * PROXY_MYSQL 모드 서버 이동 처리:
     * 1. 먼저 현재 서버의 온라인 플레이어 만료시간을 DB에서 클리어
     *    → 다른 서버에서 만료 체크 시 해당 플레이어가 온라인으로 판단됨
     * 2. 그 다음 만료된 플레이어 조회 및 삭제
     *
     * 유예 시간 적용:
     * - 만료 시점으로부터 GRACE_PERIOD_SECONDS가 지나야 삭제
     * - 서버 이동 중인 플레이어가 즉시 삭제되는 것을 방지
     * - 예: 만료 시간이 10:00:00이면 10:00:10 이후에 삭제 대상이 됨
     */
    private void checkRealTimeExpiration() {
        try {
            // PROXY_MYSQL 모드: 현재 서버 온라인 플레이어의 만료시간 클리어
            // 서버 이동 시 다른 서버의 만료 체크에서 삭제되지 않도록 함
            if (config.getOperationMode() == OperationMode.PROXY_MYSQL) {
                clearOnlinePlayersExpiration();
            }

            // 유예 시간을 뺀 시점을 기준으로 만료 체크
            // 즉, "지금 만료된 것"이 아니라 "3초 전에 만료된 것"만 삭제
            Instant checkTime = Instant.now().minusSeconds(GRACE_PERIOD_SECONDS);
            List<PlayerPriority> expired = database.findExpiredByRealTime(checkTime);

            if (expired.isEmpty()) {
                return;
            }

            if (config.isDebug()) {
                logger.info("만료된 플레이어 발견: " + expired.size() + "명 (유예 " + GRACE_PERIOD_SECONDS + "초 경과)");
            }

            // 실제로 삭제된 플레이어 수 추적
            int actuallyRemoved = 0;
            for (PlayerPriority priority : expired) {
                if (removeExpiredPlayer(priority)) {
                    actuallyRemoved++;
                }
            }

            // 실제 삭제가 발생한 경우에만 캐시 재로드 (성능 최적화)
            if (actuallyRemoved > 0) {
                priorityManager.getCacheManager().warmUp();
            }

        } catch (Exception e) {
            logger.log(Level.SEVERE, "실제 시간 만료 체크 실패", e);
        }
    }

    /**
     * 현재 서버의 온라인 플레이어 만료시간을 DB에서 일괄 클리어
     *
     * PROXY_MYSQL 모드에서 서버 이동 시 발생하는 문제 해결:
     * - A서버 퇴장 → expires_at 설정
     * - B서버 접속 → 비동기로 markOnline() 처리
     * - A서버 만료 체크 → B서버의 markOnline()이 아직 반영되지 않아 삭제됨
     *
     * 이 메서드는 각 서버에서 3초마다 실행되어:
     * - 현재 서버에 온라인인 플레이어의 expires_at/remaining_seconds를 null로 설정
     * - 다른 서버에서 만료 조회 시 해당 플레이어가 온라인으로 판단됨
     *
     * 성능 최적화:
     * - 기존: N명의 플레이어 = N개의 SELECT + ~N/2개의 UPDATE 쿼리
     * - 개선: 단일 배치 UPDATE 쿼리로 처리 (WHERE uuid IN (...))
     */
    private void clearOnlinePlayersExpiration() {
        Set<UUID> onlinePlayers = sessionTracker.getOnlinePlayers();
        if (onlinePlayers.isEmpty()) {
            return;
        }

        try {
            // 단일 배치 UPDATE로 모든 온라인 플레이어의 만료시간 클리어
            // WHERE 조건에 이미 온라인 상태인 플레이어는 제외됨
            int clearedCount = database.clearExpirationBatch(onlinePlayers);

            // 캐시도 업데이트 (온라인 플레이어는 markOnline 상태로)
            if (clearedCount > 0) {
                for (UUID uuid : onlinePlayers) {
                    PlayerPriority cached = priorityManager.getCacheManager().getFromCacheOnly(uuid);
                    if (cached != null && (cached.getExpiresAt() != null || cached.getRemainingSeconds() != null)) {
                        PlayerPriority updated = cached.toBuilder()
                                .markOnline()
                                .build();
                        priorityManager.getCacheManager().putToCache(uuid, updated);
                    }
                }

                if (config.isDebug()) {
                    logger.info("온라인 플레이어 만료시간 일괄 클리어 완료: " + clearedCount + "명");
                }
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "온라인 플레이어 만료시간 일괄 클리어 실패", e);
        }
    }

    /**
     * 서버 가동 시간 기준 만료 확인 (1초마다)
     */
    private void startServerTimeChecker() {
        expirationTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
                plugin,
                this::checkServerTimeExpiration,
                20L,  // 1초 후 시작
                20L   // 1초마다 실행
        );
    }

    /**
     * 서버 시간 기준 만료 체크 로직
     *
     * 유예 시간 적용:
     * - remaining_seconds가 -GRACE_PERIOD_SECONDS 이하일 때만 삭제
     * - 서버 이동 중인 플레이어가 즉시 삭제되는 것을 방지
     */
    private void checkServerTimeExpiration() {
        try {
            // 1. 모든 오프라인 플레이어의 remaining_seconds 1초 감소
            database.decrementRemainingSeconds(1);

            // 2. 만료된 플레이어 조회 (remaining_seconds <= -GRACE_PERIOD_SECONDS)
            // 유예 시간을 적용하여 "3초 전에 만료된 것"만 삭제
            List<PlayerPriority> expired = database.findExpiredByServerTime((int) -GRACE_PERIOD_SECONDS);

            if (expired.isEmpty()) {
                return;
            }

            if (config.isDebug()) {
                logger.info("만료된 플레이어 발견: " + expired.size() + "명 (유예 " + GRACE_PERIOD_SECONDS + "초 경과)");
            }

            // 실제로 삭제된 플레이어 수 추적
            int actuallyRemoved = 0;
            for (PlayerPriority priority : expired) {
                if (removeExpiredPlayer(priority)) {
                    actuallyRemoved++;
                }
            }

            // 실제 삭제가 발생한 경우에만 캐시 재로드 (성능 최적화)
            if (actuallyRemoved > 0) {
                priorityManager.getCacheManager().warmUp();
            }

        } catch (Exception e) {
            logger.log(Level.SEVERE, "서버 시간 만료 체크 실패", e);
        }
    }

    /**
     * 만료된 플레이어 제거 및 순위 재정렬
     * 단일 트랜잭션으로 실행하여 데이터 무결성 보장
     *
     * PROXY_MYSQL 모드에서의 안전 처리:
     * - 삭제 전 DB에서 최신 상태를 다시 확인
     * - 다른 서버에서 이미 온라인 처리된 경우 삭제하지 않음
     *
     * @param priority 만료된 플레이어 정보
     * @return 실제로 삭제되었으면 true, 스킵되었으면 false
     */
    private boolean removeExpiredPlayer(PlayerPriority priority) {
        // 삭제 전 DB에서 최신 상태 재확인 (다른 서버에서 온라인 처리되었을 수 있음)
        var latestOpt = database.findByUuid(priority.getUuid());
        if (latestOpt.isEmpty()) {
            // 이미 다른 서버에서 삭제됨
            priorityManager.getCacheManager().invalidate(priority.getUuid());
            return false;
        }

        PlayerPriority latest = latestOpt.get();

        // 만료 시간이 null이면 이미 다른 서버에서 온라인 처리됨 (삭제하지 않음)
        if (latest.getExpiresAt() == null && latest.getRemainingSeconds() == null) {
            if (config.isDebug()) {
                logger.info("만료 삭제 스킵: " + priority.getName() + " (다른 서버에서 온라인)");
            }
            // 캐시만 최신 상태로 갱신
            priorityManager.getCacheManager().putToCache(priority.getUuid(), latest);
            return false;
        }

        // 만료 시간이 연장되었으면 삭제하지 않음 (다른 서버에서 새로 퇴장)
        // 예: 원래 만료 시간이 10:00:00이었는데 DB에서 10:05:00으로 변경됨
        if (latest.getExpiresAt() != null) {
            Instant graceCutoff = Instant.now().minusSeconds(GRACE_PERIOD_SECONDS);
            if (latest.getExpiresAt().isAfter(graceCutoff)) {
                if (config.isDebug()) {
                    logger.info("만료 삭제 스킵: " + priority.getName() +
                            " (만료 시간 연장됨: " + latest.getExpiresAt() + ")");
                }
                // 캐시만 최신 상태로 갱신
                priorityManager.getCacheManager().putToCache(priority.getUuid(), latest);
                return false;
            }
        }

        // 4중 방어: last_seen이 캐시의 퇴장 시간보다 최근이면 삭제하지 않음
        // 예: A서버 퇴장(10:00:00) → B서버 접속(10:00:02) → B서버 퇴장(10:00:05)
        // A서버의 만료 체크에서 last_seen=10:00:05가 priority.getLastSeen()=10:00:00보다 최근
        if (latest.getLastSeen() != null && priority.getLastSeen() != null) {
            if (latest.getLastSeen().isAfter(priority.getLastSeen())) {
                if (config.isDebug()) {
                    logger.info("만료 삭제 스킵: " + priority.getName() +
                            " (다른 서버에서 활동함: " + latest.getLastSeen() + " > " + priority.getLastSeen() + ")");
                }
                // 캐시만 최신 상태로 갱신
                priorityManager.getCacheManager().putToCache(priority.getUuid(), latest);
                return false;
            }
        }

        int removedRank = latest.getPriority();

        // 플레이어 삭제 및 순위 재정렬 (단일 트랜잭션)
        database.deleteAndReorderInTransaction(priority.getUuid(), removedRank);

        // 캐시에서 제거
        priorityManager.getCacheManager().invalidate(priority.getUuid());

        if (config.isDebug()) {
            logger.info("만료된 플레이어 제거: " + priority.getName() + " (순위: " + removedRank + ")");
        }

        return true;
    }

    /**
     * 스케줄러 중지
     */
    public void stop() {
        if (expirationTask != null && !expirationTask.isCancelled()) {
            expirationTask.cancel();
            expirationTask = null;

            if (config.isDebug()) {
                logger.info("만료 관리자 중지");
            }
        }
    }

    /**
     * 스케줄러 재시작 (설정 변경 시)
     */
    public void restart() {
        stop();
        start();
    }
}
