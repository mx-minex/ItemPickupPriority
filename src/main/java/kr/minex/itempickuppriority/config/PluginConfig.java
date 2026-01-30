package kr.minex.itempickuppriority.config;

import kr.minex.itempickuppriority.model.OperationMode;
import kr.minex.itempickuppriority.model.TimeMode;

/**
 * 플러그인 설정 값 홀더 (불변 객체)
 * ConfigManager에서 로드된 설정 값을 저장
 */
public class PluginConfig {

    // 운영 모드
    private final OperationMode operationMode;

    // 데이터베이스 설정 - SQLite
    private final String sqliteFile;

    // 데이터베이스 설정 - MySQL
    private final String mysqlHost;
    private final int mysqlPort;
    private final String mysqlDatabase;
    private final String mysqlUsername;
    private final String mysqlPassword;
    private final int mysqlPoolSize;

    // 만료 설정
    private final TimeMode timeMode;
    private final int expirationMinutes;

    // 캐시 설정
    private final int cacheOfflineTtlSeconds;
    private final int cacheMaxSize;
    private final int cacheRankingTtlMs;

    // 자동 저장 설정
    private final int autoSaveIntervalSeconds;
    private final boolean autoSaveDirtyOnly;

    // 디버그 설정
    private final boolean debug;

    private PluginConfig(Builder builder) {
        this.operationMode = builder.operationMode;
        this.sqliteFile = builder.sqliteFile;
        this.mysqlHost = builder.mysqlHost;
        this.mysqlPort = builder.mysqlPort;
        this.mysqlDatabase = builder.mysqlDatabase;
        this.mysqlUsername = builder.mysqlUsername;
        this.mysqlPassword = builder.mysqlPassword;
        this.mysqlPoolSize = builder.mysqlPoolSize;
        this.timeMode = builder.timeMode;
        this.expirationMinutes = builder.expirationMinutes;
        this.cacheOfflineTtlSeconds = builder.cacheOfflineTtlSeconds;
        this.cacheMaxSize = builder.cacheMaxSize;
        this.cacheRankingTtlMs = builder.cacheRankingTtlMs;
        this.autoSaveIntervalSeconds = builder.autoSaveIntervalSeconds;
        this.autoSaveDirtyOnly = builder.autoSaveDirtyOnly;
        this.debug = builder.debug;
    }

    // Getters
    public OperationMode getOperationMode() {
        return operationMode;
    }

    public String getSqliteFile() {
        return sqliteFile;
    }

    public String getMysqlHost() {
        return mysqlHost;
    }

    public int getMysqlPort() {
        return mysqlPort;
    }

    public String getMysqlDatabase() {
        return mysqlDatabase;
    }

    public String getMysqlUsername() {
        return mysqlUsername;
    }

    public String getMysqlPassword() {
        return mysqlPassword;
    }

    public int getMysqlPoolSize() {
        return mysqlPoolSize;
    }

    public TimeMode getTimeMode() {
        return timeMode;
    }

    public int getExpirationMinutes() {
        return expirationMinutes;
    }

    /**
     * 만료 시간을 초 단위로 반환
     */
    public long getExpirationSeconds() {
        return expirationMinutes * 60L;
    }

    public int getCacheOfflineTtlSeconds() {
        return cacheOfflineTtlSeconds;
    }

    public int getCacheMaxSize() {
        return cacheMaxSize;
    }

    public int getCacheRankingTtlMs() {
        return cacheRankingTtlMs;
    }

    public int getAutoSaveIntervalSeconds() {
        return autoSaveIntervalSeconds;
    }

    public boolean isAutoSaveDirtyOnly() {
        return autoSaveDirtyOnly;
    }

    public boolean isDebug() {
        return debug;
    }

    /**
     * 기본 설정으로 생성
     */
    public static PluginConfig defaults() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * PluginConfig 빌더 클래스
     */
    public static class Builder {
        private OperationMode operationMode = OperationMode.STANDALONE;
        private String sqliteFile = "data.db";
        private String mysqlHost = "localhost";
        private int mysqlPort = 3306;
        private String mysqlDatabase = "itempickuppriority";
        private String mysqlUsername = "root";
        private String mysqlPassword = "";
        private int mysqlPoolSize = 10;
        private TimeMode timeMode = TimeMode.REAL_TIME;
        private int expirationMinutes = 10; // 10분
        private int cacheOfflineTtlSeconds = 300; // 5분
        private int cacheMaxSize = 10000;
        private int cacheRankingTtlMs = 5000; // 5초
        private int autoSaveIntervalSeconds = 300; // 5분
        private boolean autoSaveDirtyOnly = true;
        private boolean debug = false;

        public Builder operationMode(OperationMode operationMode) {
            this.operationMode = operationMode;
            return this;
        }

        public Builder sqliteFile(String sqliteFile) {
            this.sqliteFile = sqliteFile;
            return this;
        }

        public Builder mysqlHost(String mysqlHost) {
            this.mysqlHost = mysqlHost;
            return this;
        }

        public Builder mysqlPort(int mysqlPort) {
            this.mysqlPort = mysqlPort;
            return this;
        }

        public Builder mysqlDatabase(String mysqlDatabase) {
            this.mysqlDatabase = mysqlDatabase;
            return this;
        }

        public Builder mysqlUsername(String mysqlUsername) {
            this.mysqlUsername = mysqlUsername;
            return this;
        }

        public Builder mysqlPassword(String mysqlPassword) {
            this.mysqlPassword = mysqlPassword;
            return this;
        }

        public Builder mysqlPoolSize(int mysqlPoolSize) {
            this.mysqlPoolSize = mysqlPoolSize;
            return this;
        }

        public Builder timeMode(TimeMode timeMode) {
            this.timeMode = timeMode;
            return this;
        }

        public Builder expirationMinutes(int expirationMinutes) {
            this.expirationMinutes = expirationMinutes;
            return this;
        }

        public Builder cacheOfflineTtlSeconds(int cacheOfflineTtlSeconds) {
            this.cacheOfflineTtlSeconds = cacheOfflineTtlSeconds;
            return this;
        }

        public Builder cacheMaxSize(int cacheMaxSize) {
            this.cacheMaxSize = cacheMaxSize;
            return this;
        }

        public Builder cacheRankingTtlMs(int cacheRankingTtlMs) {
            this.cacheRankingTtlMs = cacheRankingTtlMs;
            return this;
        }

        public Builder autoSaveIntervalSeconds(int autoSaveIntervalSeconds) {
            this.autoSaveIntervalSeconds = autoSaveIntervalSeconds;
            return this;
        }

        public Builder autoSaveDirtyOnly(boolean autoSaveDirtyOnly) {
            this.autoSaveDirtyOnly = autoSaveDirtyOnly;
            return this;
        }

        public Builder debug(boolean debug) {
            this.debug = debug;
            return this;
        }

        public PluginConfig build() {
            return new PluginConfig(this);
        }
    }
}
