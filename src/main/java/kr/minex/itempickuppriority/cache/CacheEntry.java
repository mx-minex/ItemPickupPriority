package kr.minex.itempickuppriority.cache;

import kr.minex.itempickuppriority.model.PlayerPriority;

import java.time.Instant;

/**
 * 캐시 엔트리
 * PlayerPriority와 함께 캐시 메타데이터(TTL, dirty 플래그 등) 저장
 */
public class CacheEntry {

    private PlayerPriority priority;
    private final Instant createdAt;
    private Instant lastAccessedAt;
    private Instant expiresAt;
    private boolean dirty; // 변경되었지만 아직 DB에 저장되지 않음

    /**
     * 무기한 TTL로 엔트리 생성
     */
    public CacheEntry(PlayerPriority priority) {
        this(priority, null);
    }

    /**
     * 지정된 TTL로 엔트리 생성
     *
     * @param priority    플레이어 우선순위 데이터
     * @param ttlSeconds  TTL(초), null이면 무기한
     */
    public CacheEntry(PlayerPriority priority, Long ttlSeconds) {
        this.priority = priority;
        this.createdAt = Instant.now();
        this.lastAccessedAt = Instant.now();
        this.expiresAt = ttlSeconds != null ? Instant.now().plusSeconds(ttlSeconds) : null;
        this.dirty = false;
    }

    /**
     * 캐시 엔트리가 만료되었는지 확인
     */
    public boolean isExpired() {
        if (expiresAt == null) {
            return false; // 무기한
        }
        return Instant.now().isAfter(expiresAt);
    }

    /**
     * 우선순위 데이터 조회 (접근 시간 갱신)
     */
    public PlayerPriority getPriority() {
        this.lastAccessedAt = Instant.now();
        return priority;
    }

    /**
     * 우선순위 데이터 조회 (접근 시간 갱신 안 함)
     */
    public PlayerPriority getPriorityWithoutTouch() {
        return priority;
    }

    /**
     * 우선순위 데이터 업데이트 (dirty 플래그 설정)
     */
    public void updatePriority(PlayerPriority newPriority) {
        this.priority = newPriority;
        this.lastAccessedAt = Instant.now();
        this.dirty = true;
    }

    /**
     * TTL 갱신
     */
    public void refreshTtl(long ttlSeconds) {
        this.expiresAt = Instant.now().plusSeconds(ttlSeconds);
        this.lastAccessedAt = Instant.now();
    }

    /**
     * TTL 제거 (무기한으로 설정)
     */
    public void clearTtl() {
        this.expiresAt = null;
    }

    // Getters and Setters
    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastAccessedAt() {
        return lastAccessedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }

    /**
     * dirty 플래그 해제 (DB 저장 후 호출)
     */
    public void markClean() {
        this.dirty = false;
    }

    /**
     * dirty 플래그 설정 (수정 후 호출)
     */
    public void markDirty() {
        this.dirty = true;
    }
}
