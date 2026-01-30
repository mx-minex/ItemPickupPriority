package kr.minex.itempickuppriority.cache;

import kr.minex.itempickuppriority.database.DatabaseProvider;
import kr.minex.itempickuppriority.model.PlayerPriority;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * DB-Primary 캐시 매니저
 *
 * 핵심 원칙:
 * 1. DB가 항상 진실의 원천 (Source of Truth)
 * 2. 캐시는 성능 최적화용 (없어도 동작해야 함)
 * 3. Write-Through: 쓰기 시 DB 먼저 → 성공하면 캐시 업데이트
 * 4. Read-Through: 캐시 미스 시 DB에서 로드
 *
 * PROXY_MYSQL 모드:
 * - 다른 서버에서 변경 시 캐시 무효화 메시지 수신
 * - 무효화된 데이터는 다음 조회 시 DB에서 재로드
 */
public class CacheManager {

    private final DatabaseProvider database;
    private final SessionOrderTracker sessionTracker;
    private final Logger logger;
    private final boolean debug;

    // 로컬 메모리 캐시 (단순한 Map, TTL 없음)
    private final Map<UUID, PlayerPriority> cache = new ConcurrentHashMap<>();

    // 캐시 통계
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);

    public CacheManager(DatabaseProvider database, SessionOrderTracker sessionTracker,
                        Logger logger, boolean debug) {
        this.database = database;
        this.sessionTracker = sessionTracker;
        this.logger = logger;
        this.debug = debug;
    }

    // ==================== Read-Through ====================

    /**
     * 플레이어 데이터 조회 (Read-Through)
     * 1. 캐시 확인
     * 2. 캐시 미스 → DB 로드 → 캐시에 저장
     *
     * @param uuid 플레이어 UUID
     * @return 플레이어 데이터 (없으면 empty)
     */
    public Optional<PlayerPriority> get(UUID uuid) {
        // 1. 캐시 확인
        PlayerPriority cached = cache.get(uuid);
        if (cached != null) {
            cacheHits.incrementAndGet();
            return Optional.of(cached);
        }

        // 2. 캐시 미스 - DB에서 로드
        cacheMisses.incrementAndGet();
        Optional<PlayerPriority> fromDb = database.findByUuid(uuid);

        // 3. DB 데이터를 캐시에 저장
        fromDb.ifPresent(p -> cache.put(uuid, p));

        return fromDb;
    }

    /**
     * 순위로 플레이어 조회 (Read-Through)
     */
    public Optional<PlayerPriority> getByRank(int rank) {
        // 캐시에서 순위로 검색
        Optional<PlayerPriority> cached = cache.values().stream()
                .filter(p -> p.getPriority() == rank)
                .findFirst();

        if (cached.isPresent()) {
            cacheHits.incrementAndGet();
            return cached;
        }

        // 캐시 미스 - DB에서 로드
        cacheMisses.incrementAndGet();
        Optional<PlayerPriority> fromDb = database.findByPriority(rank);
        fromDb.ifPresent(p -> cache.put(p.getUuid(), p));

        return fromDb;
    }

    /**
     * 이름으로 플레이어 조회 (Read-Through)
     */
    public Optional<PlayerPriority> getByName(String name) {
        // 캐시에서 이름으로 검색
        Optional<PlayerPriority> cached = cache.values().stream()
                .filter(p -> p.getName().equalsIgnoreCase(name))
                .findFirst();

        if (cached.isPresent()) {
            cacheHits.incrementAndGet();
            return cached;
        }

        // 캐시 미스 - DB에서 로드
        cacheMisses.incrementAndGet();
        Optional<PlayerPriority> fromDb = database.findByName(name);
        fromDb.ifPresent(p -> cache.put(p.getUuid(), p));

        return fromDb;
    }

    // ==================== Write-Through ====================

    /**
     * 플레이어 데이터 저장 (Write-Through)
     * DB 먼저 저장 → 성공하면 캐시 업데이트
     *
     * @param priority 저장할 데이터
     * @return 저장 성공 여부
     */
    public boolean save(PlayerPriority priority) {
        try {
            // 1. DB에 먼저 저장
            database.saveOrUpdate(priority);

            // 2. 성공하면 캐시 업데이트
            cache.put(priority.getUuid(), priority);

            return true;
        } catch (Exception e) {
            logger.warning("DB 저장 실패, 캐시 업데이트 스킵: " + priority.getName() + " - " + e.getMessage());
            return false;
        }
    }

    /**
     * 플레이어 데이터 업데이트 (Write-Through)
     */
    public boolean update(PlayerPriority priority) {
        try {
            // 1. DB에 먼저 업데이트
            database.update(priority);

            // 2. 성공하면 캐시 업데이트
            cache.put(priority.getUuid(), priority);

            return true;
        } catch (Exception e) {
            logger.warning("DB 업데이트 실패, 캐시 업데이트 스킵: " + priority.getName() + " - " + e.getMessage());
            return false;
        }
    }

    /**
     * 플레이어 삭제 (Write-Through)
     */
    public boolean delete(UUID uuid) {
        try {
            // 1. DB에서 먼저 삭제
            database.delete(uuid);

            // 2. 성공하면 캐시에서 제거
            cache.remove(uuid);

            return true;
        } catch (Exception e) {
            logger.warning("DB 삭제 실패: " + uuid + " - " + e.getMessage());
            return false;
        }
    }

    // ==================== 캐시 무효화 ====================

    /**
     * 특정 플레이어 캐시 무효화
     * 다음 조회 시 DB에서 재로드됨
     */
    public void invalidate(UUID uuid) {
        cache.remove(uuid);
        if (debug) {
            logger.info("캐시 무효화: " + uuid);
        }
    }

    /**
     * 여러 플레이어 캐시 무효화
     */
    public void invalidateAll(Collection<UUID> uuids) {
        uuids.forEach(cache::remove);
        if (debug) {
            logger.info("캐시 무효화: " + uuids.size() + "건");
        }
    }

    /**
     * 전체 캐시 클리어
     * 다른 서버에서 순위 초기화 시 호출
     */
    public void clear() {
        cache.clear();
        if (debug) {
            logger.info("전체 캐시 클리어");
        }
    }

    // ==================== 캐시 새로고침 ====================

    /**
     * DB에서 전체 데이터를 캐시에 로드 (워밍업)
     * 플러그인 시작 시 또는 순위 초기화 후 호출
     *
     * 중요:
     * 1. 캐시를 먼저 클리어하여 삭제된 플레이어 데이터 제거
     * 2. 이 서버의 온라인 플레이어는 온라인 상태로 표시
     */
    public void warmUp() {
        Set<UUID> onlineUuids = sessionTracker.getOnlinePlayers();

        // 캐시 클리어 (삭제된 플레이어, 변경된 순위 반영을 위해 필수)
        cache.clear();

        List<PlayerPriority> all = database.findAll();
        for (PlayerPriority priority : all) {
            // 이 서버에 온라인인 플레이어는 온라인 상태로 캐시
            if (onlineUuids.contains(priority.getUuid())) {
                PlayerPriority online = priority.toBuilder()
                        .markOnline()
                        .build();
                cache.put(priority.getUuid(), online);
            } else {
                // 오프라인 플레이어는 DB 데이터 그대로
                cache.put(priority.getUuid(), priority);
            }
        }

        if (debug) {
            logger.info("캐시 워밍업 완료: " + all.size() + "건 (온라인 " + onlineUuids.size() + "명)");
        }
    }

    /**
     * 특정 플레이어 데이터 새로고침 (DB에서 재로드)
     */
    public Optional<PlayerPriority> refresh(UUID uuid) {
        cache.remove(uuid);
        return get(uuid);  // Read-Through로 DB에서 로드
    }

    // ==================== 순위 목록 조회 ====================

    /**
     * 순위 목록 조회 (페이징)
     * 항상 DB에서 조회 (정확한 순위 보장)
     */
    public List<PlayerPriority> getRankingList(int page, int pageSize) {
        Set<UUID> onlineUuids = sessionTracker.getOnlinePlayers();
        List<PlayerPriority> dbList = database.findAllPaged(page, pageSize);

        // 온라인 플레이어는 온라인 상태로 표시
        List<PlayerPriority> result = new ArrayList<>();
        for (PlayerPriority p : dbList) {
            if (onlineUuids.contains(p.getUuid())) {
                // 온라인 플레이어는 캐시 상태 우선 (더 최신)
                PlayerPriority cached = cache.get(p.getUuid());
                if (cached != null) {
                    result.add(cached);
                } else {
                    result.add(p.toBuilder().markOnline().build());
                }
            } else {
                result.add(p);
            }
        }

        return result;
    }

    /**
     * 전체 플레이어 수 (DB 기준)
     */
    public int getTotalCount() {
        return database.count();
    }

    // ==================== 유틸리티 ====================

    /**
     * 캐시에 데이터 존재 여부 (DB 조회 없음)
     */
    public boolean containsInCache(UUID uuid) {
        return cache.containsKey(uuid);
    }

    /**
     * 현재 캐시 크기
     */
    public int cacheSize() {
        return cache.size();
    }

    /**
     * 캐시 통계 정보
     */
    public String getStats() {
        long hits = cacheHits.get();
        long misses = cacheMisses.get();
        long total = hits + misses;
        double hitRate = total > 0 ? (double) hits / total * 100 : 0;

        return String.format("캐시 통계: 히트 %d, 미스 %d, 히트율 %.1f%%, 크기 %d",
                hits, misses, hitRate, cache.size());
    }

    /**
     * 캐시에서 직접 조회 (DB 조회 없음, 내부용)
     */
    public PlayerPriority getFromCacheOnly(UUID uuid) {
        return cache.get(uuid);
    }

    /**
     * 캐시에 직접 저장 (DB 저장 없음, 내부용)
     * 주의: Write-Through 원칙 위반, 특수한 경우에만 사용
     */
    public void putToCache(UUID uuid, PlayerPriority priority) {
        cache.put(uuid, priority);
    }
}
