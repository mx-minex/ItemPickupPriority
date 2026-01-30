package kr.minex.itempickuppriority.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import kr.minex.itempickuppriority.model.OperationMode;
import kr.minex.itempickuppriority.model.TimeMode;

import java.util.logging.Logger;

/**
 * config.yml 설정 파일 관리자
 * 설정 파일을 로드하고 PluginConfig 객체로 변환
 */
public class ConfigManager {

    private final JavaPlugin plugin;
    private final Logger logger;
    private PluginConfig config;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        reload();
    }

    /**
     * 설정 파일 리로드
     */
    public void reload() {
        // 기본 설정 파일 저장 (없는 경우)
        plugin.saveDefaultConfig();
        plugin.reloadConfig();

        FileConfiguration fc = plugin.getConfig();

        try {
            config = PluginConfig.builder()
                    // 운영 모드
                    .operationMode(OperationMode.fromString(
                            fc.getString("operation-mode", "STANDALONE")))

                    // SQLite 설정
                    .sqliteFile(fc.getString("database.sqlite.file", "data.db"))

                    // MySQL 설정
                    .mysqlHost(fc.getString("database.mysql.host", "localhost"))
                    .mysqlPort(fc.getInt("database.mysql.port", 3306))
                    .mysqlDatabase(fc.getString("database.mysql.database", "itempickuppriority"))
                    .mysqlUsername(fc.getString("database.mysql.username", "root"))
                    .mysqlPassword(fc.getString("database.mysql.password", ""))
                    .mysqlPoolSize(fc.getInt("database.mysql.pool.maximum-pool-size", 10))

                    // 만료 설정
                    .timeMode(determineTimeMode(
                            OperationMode.fromString(fc.getString("operation-mode", "STANDALONE")),
                            TimeMode.fromString(fc.getString("expiration.time-mode", "REAL_TIME"))))
                    .expirationMinutes(fc.getInt("expiration.duration-minutes", 1440))

                    // 캐시 설정
                    .cacheOfflineTtlSeconds(fc.getInt("cache.offline-ttl-seconds", 300))
                    .cacheMaxSize(fc.getInt("cache.max-size", 10000))
                    .cacheRankingTtlMs(fc.getInt("cache.ranking-cache-ttl-ms", 5000))

                    // 자동 저장 설정
                    .autoSaveIntervalSeconds(fc.getInt("auto-save.interval-seconds", 300))
                    .autoSaveDirtyOnly(fc.getBoolean("auto-save.dirty-only", true))

                    // 디버그 설정
                    .debug(fc.getBoolean("debug", false))

                    .build();

            if (config.isDebug()) {
                logger.info("설정 로드 완료: 운영 모드=" + config.getOperationMode() +
                        ", 시간 모드=" + config.getTimeMode() +
                        ", 만료 시간=" + config.getExpirationMinutes() + "분");
            }

        } catch (Exception e) {
            logger.severe("설정 파일 로드 실패: " + e.getMessage());
            config = PluginConfig.defaults();
        }
    }

    /**
     * 현재 설정 반환
     */
    public PluginConfig getConfig() {
        return config;
    }

    /**
     * 운영 모드에 따른 시간 모드 결정
     * PROXY_MYSQL 모드에서는 SERVER_TIME이 지원되지 않음 (다중 서버 환경에서 데이터 일관성 문제)
     *
     * @param opMode 운영 모드
     * @param requestedMode 요청된 시간 모드
     * @return 실제 적용할 시간 모드
     */
    private TimeMode determineTimeMode(OperationMode opMode, TimeMode requestedMode) {
        if (opMode == OperationMode.PROXY_MYSQL && requestedMode == TimeMode.SERVER_TIME) {
            logger.warning("PROXY_MYSQL 모드에서는 SERVER_TIME이 지원되지 않습니다. REAL_TIME으로 변경됩니다.");
            logger.warning("(다중 서버 환경에서 remaining_seconds 동기화 불가)");
            return TimeMode.REAL_TIME;
        }
        return requestedMode;
    }
}
