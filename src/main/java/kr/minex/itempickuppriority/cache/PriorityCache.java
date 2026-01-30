package kr.minex.itempickuppriority.cache;

import kr.minex.itempickuppriority.model.PlayerPriority;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 우선순위 데이터 메모리 캐시
 * - TTL 기반 자동 만료
 * - ConcurrentHashMap으로 스레드 안전
 * - 정렬된 순위 목록 캐싱
 */
public class PriorityCache {

    private final ConcurrentHashMap<UUID, CacheEntry> cache = new ConcurrentHashMap<>();
    private final long defaultTtlSeconds;
    private final int maxSize;

    // 정렬된 순위 목록 캐시
    private volatile List<PlayerPriority> sortedRankingCache;
    private volatile long rankingCacheTimestamp;
    private final long rankingCacheTtlMs;

    /**
     * 캐시 생성
     *
     * @param defaultTtlSeconds   기본 TTL (초)
     * @param maxSize             최대 캐시 크기
     * @param rankingCacheTtlMs   순위 목록 캐시 TTL (밀리초)
     */
    public PriorityCache(long defaultTtlSeconds, int maxSize, long rankingCacheTtlMs) {
        this.defaultTtlSeconds = defaultTtlSeconds;
        this.maxSize = maxSize;
        this.rankingCacheTtlMs = rankingCacheTtlMs;
    }

    /**
     * 캐시에서 엔트리 조회
     *
     * @param uuid 플레이어 UUID
     * @return 캐시 엔트리 (만료되었거나 없으면 null)
     */
    public CacheEntry get(UUID uuid) {
        CacheEntry entry = cache.get(uuid);
        if (entry != null && entry.isExpired()) {
            cache.remove(uuid);
            invalidateRankingCache();
            return null;
        }
        return entry;
    }

    /**
     * 캐시에 엔트리 저장 (기본 TTL)
     */
    public void put(UUID uuid, PlayerPriority priority) {
        put(uuid, priority, defaultTtlSeconds);
    }

    /**
     * 캐시에 엔트리 저장 (지정 TTL)
     *
     * @param uuid       플레이어 UUID
     * @param priority   우선순위 데이터
     * @param ttlSeconds TTL (초), 0 이하면 무기한
     */
    public void put(UUID uuid, PlayerPriority priority, long ttlSeconds) {
        // 최대 크기 확인
        if (cache.size() >= maxSize && !cache.containsKey(uuid)) {
            evictOldest();
        }

        Long ttl = ttlSeconds > 0 ? ttlSeconds : null;
        cache.put(uuid, new CacheEntry(priority, ttl));
        invalidateRankingCache();
    }

    /**
     * 캐시에 엔트리 저장 (CacheEntry 직접)
     */
    public void put(UUID uuid, CacheEntry entry) {
        if (cache.size() >= maxSize && !cache.containsKey(uuid)) {
            evictOldest();
        }
        cache.put(uuid, entry);
        invalidateRankingCache();
    }

    /**
     * 기존 엔트리 업데이트 (dirty 플래그 설정)
     */
    public void update(UUID uuid, PlayerPriority priority) {
        CacheEntry entry = cache.get(uuid);
        if (entry != null) {
            entry.updatePriority(priority);
        } else {
            put(uuid, priority);
        }
        invalidateRankingCache();
    }

    /**
     * 캐시에서 엔트리 제거
     */
    public void remove(UUID uuid) {
        cache.remove(uuid);
        invalidateRankingCache();
    }

    /**
     * 캐시 전체 삭제
     */
    public void clear() {
        cache.clear();
        invalidateRankingCache();
    }

    /**
     * 캐시에 UUID 존재 여부 확인
     */
    public boolean contains(UUID uuid) {
        CacheEntry entry = cache.get(uuid);
        if (entry != null && entry.isExpired()) {
            cache.remove(uuid);
            return false;
        }
        return entry != null;
    }

    /**
     * 현재 캐시 크기
     */
    public int size() {
        return cache.size();
    }

    /**
     * 모든 UUID 조회
     */
    public Set<UUID> keys() {
        return new HashSet<>(cache.keySet());
    }

    /**
     * 모든 엔트리 조회
     */
    public Collection<CacheEntry> values() {
        return new ArrayList<>(cache.values());
    }

    /**
     * 정렬된 순위 목록 조회 (캐시됨)
     */
    public List<PlayerPriority> getSortedRanking() {
        // 캐시 유효성 확인
        if (sortedRankingCache != null &&
                System.currentTimeMillis() - rankingCacheTimestamp < rankingCacheTtlMs) {
            return sortedRankingCache;
        }

        // 새로 정렬
        List<PlayerPriority> sorted = cache.values().stream()
                .filter(e -> !e.isExpired())
                .map(CacheEntry::getPriorityWithoutTouch)
                .sorted(Comparator.comparingInt(PlayerPriority::getPriority))
                .collect(Collectors.toList());

        sortedRankingCache = sorted;
        rankingCacheTimestamp = System.currentTimeMillis();

        return sorted;
    }

    /**
     * 순위 목록 캐시 무효화
     */
    public void invalidateRankingCache() {
        sortedRankingCache = null;
    }

    /**
     * dirty 상태인 모든 엔트리 조회
     */
    public List<CacheEntry> getDirtyEntries() {
        return cache.values().stream()
                .filter(CacheEntry::isDirty)
                .collect(Collectors.toList());
    }

    /**
     * 만료된 엔트리 정리
     *
     * @return 정리된 엔트리 수
     */
    public int cleanupExpired() {
        int count = 0;
        Iterator<Map.Entry<UUID, CacheEntry>> it = cache.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, CacheEntry> entry = it.next();
            if (entry.getValue().isExpired()) {
                it.remove();
                count++;
            }
        }
        if (count > 0) {
            invalidateRankingCache();
        }
        return count;
    }

    /**
     * 가장 오래된 엔트리 제거 (LRU)
     */
    private void evictOldest() {
        cache.entrySet().stream()
                .min(Comparator.comparing(e -> e.getValue().getLastAccessedAt()))
                .ifPresent(oldest -> {
                    cache.remove(oldest.getKey());
                    invalidateRankingCache();
                });
    }
}
