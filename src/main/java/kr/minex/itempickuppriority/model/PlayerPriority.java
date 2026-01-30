package kr.minex.itempickuppriority.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 플레이어 우선순위 도메인 모델
 * 픽업 우선순위 정보를 담는 불변 객체 (Builder 패턴)
 */
public class PlayerPriority {

    private final UUID uuid;
    private final String name;
    private final int priority;           // 순위 (1부터 시작, 낮을수록 높은 우선순위)
    private final Instant registeredAt;   // 최초 등록 시간
    private final Instant lastSeen;       // 마지막 접속 시간
    private final Instant expiresAt;      // 만료 시각 (REAL_TIME 모드, null=접속중)
    private final Integer remainingSeconds; // 남은 초 (SERVER_TIME 모드, null=접속중)

    private PlayerPriority(Builder builder) {
        this.uuid = Objects.requireNonNull(builder.uuid, "UUID는 필수입니다");
        this.name = Objects.requireNonNull(builder.name, "이름은 필수입니다");
        this.priority = builder.priority;
        this.registeredAt = builder.registeredAt != null ? builder.registeredAt : Instant.now();
        this.lastSeen = builder.lastSeen != null ? builder.lastSeen : Instant.now();
        this.expiresAt = builder.expiresAt;
        this.remainingSeconds = builder.remainingSeconds;
    }

    // Getters
    public UUID getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public int getPriority() {
        return priority;
    }

    public Instant getRegisteredAt() {
        return registeredAt;
    }

    public Instant getLastSeen() {
        return lastSeen;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Integer getRemainingSeconds() {
        return remainingSeconds;
    }

    /**
     * 현재 접속 중인지 확인 (만료 정보가 없으면 접속 중)
     */
    public boolean isOnline() {
        return expiresAt == null && remainingSeconds == null;
    }

    /**
     * 실제 시간 기준으로 만료되었는지 확인
     */
    public boolean isExpiredByRealTime() {
        if (expiresAt == null) {
            return false;
        }
        return Instant.now().isAfter(expiresAt);
    }

    /**
     * 서버 시간 기준으로 만료되었는지 확인
     */
    public boolean isExpiredByServerTime() {
        if (remainingSeconds == null) {
            return false;
        }
        return remainingSeconds <= 0;
    }

    /**
     * 새로운 Builder 생성
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 기존 객체를 기반으로 새 Builder 생성 (복사 후 수정용)
     */
    public Builder toBuilder() {
        return new Builder()
                .uuid(this.uuid)
                .name(this.name)
                .priority(this.priority)
                .registeredAt(this.registeredAt)
                .lastSeen(this.lastSeen)
                .expiresAt(this.expiresAt)
                .remainingSeconds(this.remainingSeconds);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlayerPriority that = (PlayerPriority) o;
        return Objects.equals(uuid, that.uuid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uuid);
    }

    @Override
    public String toString() {
        return "PlayerPriority{" +
                "uuid=" + uuid +
                ", name='" + name + '\'' +
                ", priority=" + priority +
                ", registeredAt=" + registeredAt +
                ", lastSeen=" + lastSeen +
                ", expiresAt=" + expiresAt +
                ", remainingSeconds=" + remainingSeconds +
                '}';
    }

    /**
     * PlayerPriority 빌더 클래스
     */
    public static class Builder {
        private UUID uuid;
        private String name;
        private int priority = 1;
        private Instant registeredAt;
        private Instant lastSeen;
        private Instant expiresAt;
        private Integer remainingSeconds;

        public Builder uuid(UUID uuid) {
            this.uuid = uuid;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder priority(int priority) {
            if (priority < 1) {
                throw new IllegalArgumentException("순위는 1 이상이어야 합니다: " + priority);
            }
            this.priority = priority;
            return this;
        }

        public Builder registeredAt(Instant registeredAt) {
            this.registeredAt = registeredAt;
            return this;
        }

        public Builder lastSeen(Instant lastSeen) {
            this.lastSeen = lastSeen;
            return this;
        }

        public Builder expiresAt(Instant expiresAt) {
            this.expiresAt = expiresAt;
            return this;
        }

        public Builder remainingSeconds(Integer remainingSeconds) {
            this.remainingSeconds = remainingSeconds;
            return this;
        }

        /**
         * 온라인 상태로 설정 (만료 정보 제거)
         */
        public Builder markOnline() {
            this.expiresAt = null;
            this.remainingSeconds = null;
            this.lastSeen = Instant.now();
            return this;
        }

        /**
         * 오프라인 상태로 설정 (실제 시간 기준 만료)
         *
         * @param durationSeconds 만료까지 남은 시간 (초)
         */
        public Builder markOfflineRealTime(long durationSeconds) {
            this.expiresAt = Instant.now().plusSeconds(durationSeconds);
            this.remainingSeconds = null;
            this.lastSeen = Instant.now();
            return this;
        }

        /**
         * 오프라인 상태로 설정 (서버 시간 기준 만료)
         *
         * @param remainingSeconds 남은 초
         */
        public Builder markOfflineServerTime(int remainingSeconds) {
            this.expiresAt = null;
            this.remainingSeconds = remainingSeconds;
            this.lastSeen = Instant.now();
            return this;
        }

        public PlayerPriority build() {
            return new PlayerPriority(this);
        }
    }
}
