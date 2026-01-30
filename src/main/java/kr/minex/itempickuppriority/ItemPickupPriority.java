package kr.minex.itempickuppriority;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import kr.minex.itempickuppriority.cache.CacheManager;
import kr.minex.itempickuppriority.cache.SessionOrderTracker;
import kr.minex.itempickuppriority.command.BaljaCommand;
import kr.minex.itempickuppriority.command.BaljaTabCompleter;
import kr.minex.itempickuppriority.command.subcommand.ChangeRankSubCommand;
import kr.minex.itempickuppriority.command.subcommand.RankSubCommand;
import kr.minex.itempickuppriority.command.subcommand.ReloadSubCommand;
import kr.minex.itempickuppriority.command.subcommand.ResetSubCommand;
import kr.minex.itempickuppriority.config.ConfigManager;
import kr.minex.itempickuppriority.config.MessageManager;
import kr.minex.itempickuppriority.config.PluginConfig;
import kr.minex.itempickuppriority.database.DatabaseProvider;
import kr.minex.itempickuppriority.database.MySQLProvider;
import kr.minex.itempickuppriority.database.SQLiteProvider;
import kr.minex.itempickuppriority.model.OperationMode;
import kr.minex.itempickuppriority.listener.ItemPickupListener;
import kr.minex.itempickuppriority.listener.PlayerJoinQuitListener;
import kr.minex.itempickuppriority.listener.ProxyMessageListener;
import kr.minex.itempickuppriority.manager.ExpirationManager;
import kr.minex.itempickuppriority.manager.PriorityManager;
import kr.minex.itempickuppriority.messaging.BungeeCordMessenger;
import kr.minex.itempickuppriority.messaging.ProxyMessenger;
import kr.minex.itempickuppriority.messaging.VelocityMessenger;
import kr.minex.itempickuppriority.pickup.PickupPriorityResolver;

import java.util.Objects;

/**
 * ItemPickupPriority (발자) 플러그인 메인 클래스
 *
 * 아이템 픽업 우선순위를 관리하는 플러그인
 * - 낮은 순위일수록 높은 픽업 우선순위
 * - 세션 접속 순서 기반 초기화 지원
 * - STANDALONE (SQLite) / PROXY_MYSQL (MySQL) 모드 지원
 */
public final class ItemPickupPriority extends JavaPlugin {

    private static ItemPickupPriority instance;

    // 설정
    private ConfigManager configManager;
    private MessageManager messageManager;
    private PluginConfig config;

    // 데이터베이스
    private DatabaseProvider database;

    // 캐시
    private CacheManager cacheManager;
    private SessionOrderTracker sessionTracker;

    // 매니저
    private PriorityManager priorityManager;
    private ExpirationManager expirationManager;

    // 픽업 리졸버
    private PickupPriorityResolver pickupResolver;

    // 프록시 메시징 (PROXY_MYSQL 모드에서만 사용)
    private ProxyMessenger proxyMessenger;
    private ProxyMessageListener proxyMessageListener;

    @Override
    public void onEnable() {
        instance = this;

        // 리로드 감지
        boolean isReload = Bukkit.getOnlinePlayers().size() > 0;

        try {
            // 1. 설정 로드
            initializeConfig();

            // 2. 데이터베이스 초기화
            initializeDatabase();

            // 3. 캐시 초기화
            initializeCache();

            // 4. 매니저 초기화
            initializeManagers();

            // 5. 유령 유저 복구 (서버 비정상 종료 후 재시작 시)
            recoverGhostUsers();

            // 6. 리스너 등록
            registerListeners();

            // 7. 명령어 등록
            registerCommands();

            // 8. 프록시 메시징 초기화 (PROXY_MYSQL 모드에서만)
            initializeProxyMessaging();

            // 9. PlaceholderAPI 연동 (선택적)
            registerPlaceholderAPI();

            // 10. 리로드 시 기존 플레이어 처리
            if (isReload) {
                handleReload();
            }

            // 시작 배너 출력
            printStartupBanner();

        } catch (Exception e) {
            getLogger().severe("플러그인 초기화 실패: " + e.getMessage());
            e.printStackTrace();
            Bukkit.getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        // 1. 스케줄러 정리
        Bukkit.getScheduler().cancelTasks(this);

        // 2. 프록시 메시징 해제
        if (proxyMessageListener != null) {
            proxyMessageListener.unregister();
        }
        if (proxyMessenger != null) {
            proxyMessenger.unregister();
        }

        // 3. 만료 관리자 중지
        if (expirationManager != null) {
            expirationManager.stop();
        }

        // 4. Write-Through 패턴: 모든 변경이 즉시 DB에 저장되므로 별도 저장 불필요
        // (기존 saveAllSync() 제거됨)

        // 5. 데이터베이스 종료
        if (database != null) {
            database.close();
        }

        // 6. 캐시 정리
        if (cacheManager != null) {
            cacheManager.clear();
        }
        if (sessionTracker != null) {
            sessionTracker.clear();
        }

        // 7. static 참조 제거
        instance = null;

        getLogger().info("ItemPickupPriority 플러그인이 비활성화되었습니다.");
    }

    /**
     * 설정 초기화
     */
    private void initializeConfig() {
        saveDefaultConfig();
        configManager = new ConfigManager(this);
        config = configManager.getConfig();

        messageManager = new MessageManager(this);

        if (config.isDebug()) {
            getLogger().info("디버그 모드 활성화");
        }
    }

    /**
     * 데이터베이스 초기화
     * 운영 모드에 따라 SQLite 또는 MySQL 사용
     */
    private void initializeDatabase() throws java.sql.SQLException {
        OperationMode mode = config.getOperationMode();

        if (mode == OperationMode.PROXY_MYSQL) {
            // PROXY_MYSQL 모드: MySQL 사용
            validateMySQLConfig();
            database = new MySQLProvider(
                    config.getMysqlHost(),
                    config.getMysqlPort(),
                    config.getMysqlDatabase(),
                    config.getMysqlUsername(),
                    config.getMysqlPassword(),
                    config.getMysqlPoolSize(),
                    getLogger()
            );
            database.initialize();
            // 참고: PROXY_MYSQL + SERVER_TIME 조합은 ConfigManager에서 자동으로 REAL_TIME으로 변경됨

            getLogger().info("MySQL 데이터베이스 연결 완료: " + config.getMysqlHost() + ":" + config.getMysqlPort());
        } else {
            // STANDALONE 모드: SQLite 사용
            database = new SQLiteProvider(getDataFolder(), config.getSqliteFile(), getLogger());
            database.initialize();

            getLogger().info("SQLite 데이터베이스 연결 완료");
        }
    }

    /**
     * MySQL 설정 검증
     * 필수 설정이 누락된 경우 예외 발생
     */
    private void validateMySQLConfig() {
        if (config.getMysqlHost() == null || config.getMysqlHost().isEmpty()) {
            throw new IllegalStateException("MySQL 호스트가 설정되지 않았습니다. config.yml의 database.mysql.host를 확인하세요.");
        }
        if (config.getMysqlDatabase() == null || config.getMysqlDatabase().isEmpty()) {
            throw new IllegalStateException("MySQL 데이터베이스명이 설정되지 않았습니다. config.yml의 database.mysql.database를 확인하세요.");
        }
        if (config.getMysqlUsername() == null || config.getMysqlUsername().isEmpty()) {
            throw new IllegalStateException("MySQL 사용자명이 설정되지 않았습니다. config.yml의 database.mysql.username을 확인하세요.");
        }
    }

    /**
     * 캐시 초기화
     * DB-Primary 아키텍처: CacheManager가 Read-Through/Write-Through 패턴 제공
     */
    private void initializeCache() {
        sessionTracker = new SessionOrderTracker();
        cacheManager = new CacheManager(database, sessionTracker, getLogger(), config.isDebug());

        getLogger().info("캐시 매니저 초기화 완료 (DB-Primary 모드)");
    }

    /**
     * 매니저 초기화
     * DB-Primary 아키텍처: Write-Through로 즉시 저장하므로 주기적 저장 불필요
     */
    private void initializeManagers() {
        // 우선순위 매니저
        priorityManager = new PriorityManager(this, database, cacheManager, sessionTracker, config);
        priorityManager.warmUpCache();  // DB에서 캐시로 초기 로드

        // 픽업 리졸버
        pickupResolver = new PickupPriorityResolver(cacheManager, config, getLogger());

        // 만료 관리자
        expirationManager = new ExpirationManager(this, database, priorityManager, sessionTracker, config);
        expirationManager.start();

        // Write-Through 패턴: DB에 즉시 저장하므로 주기적 저장 스케줄러 불필요
        // (기존 saveDirty() 스케줄러 제거)
    }

    /**
     * 유령 유저 복구 - 서버 비정상 종료 후 재시작 시 실행
     *
     * 유령 유저(Ghost User) 정의:
     * - DB에서 expires_at = NULL AND remaining_seconds = NULL (온라인 상태)
     * - 하지만 실제로는 서버에 접속해 있지 않음
     *
     * 발생 원인:
     * - 서버 크래시, kill -9, 정전 등으로 onDisable() 미호출
     * - handlePlayerQuit()이 실행되지 않아 만료시간이 설정되지 않음
     *
     * 복구 방법:
     * - 현재 서버에 실제 접속 중인 플레이어를 제외한 모든 "온라인 상태" 유저에게 만료시간 설정
     *
     * PROXY_MYSQL 모드 안전성:
     * - 다른 서버에서 실제 온라인인 플레이어도 일시적으로 만료시간이 설정될 수 있음
     * - 하지만 해당 서버에서 3초마다 clearExpirationBatch()로 만료시간을 클리어하므로 안전
     * - 최악의 경우 3초간 오프라인으로 잘못 판정될 수 있으나, 곧 복구됨
     */
    private void recoverGhostUsers() {
        // 현재 서버에 실제 접속 중인 플레이어 UUID 수집
        java.util.Set<java.util.UUID> onlineUuids = new java.util.HashSet<>();
        for (org.bukkit.entity.Player player : Bukkit.getOnlinePlayers()) {
            onlineUuids.add(player.getUniqueId());
        }

        // 만료 시간 계산
        java.time.Instant expiresAt = null;
        Integer remainingSeconds = null;

        if (config.getTimeMode() == kr.minex.itempickuppriority.model.TimeMode.REAL_TIME) {
            // REAL_TIME 모드: 현재 시간 + 만료 시간
            expiresAt = java.time.Instant.now().plusSeconds(config.getExpirationSeconds());
        } else {
            // SERVER_TIME 모드: 남은 시간(초)
            remainingSeconds = (int) config.getExpirationSeconds();
        }

        // 유령 유저 복구 실행
        int recovered = database.recoverGhostUsers(onlineUuids, expiresAt, remainingSeconds);

        if (recovered > 0) {
            getLogger().warning("유령 유저 " + recovered + "명 복구됨 (서버 비정상 종료 후 재시작 감지)");
            // 캐시 갱신
            cacheManager.warmUp();
        }
    }

    /**
     * 리스너 등록
     */
    private void registerListeners() {
        // 접속/퇴장 리스너
        getServer().getPluginManager().registerEvents(
                new PlayerJoinQuitListener(priorityManager, sessionTracker, config, getLogger()),
                this
        );

        // 아이템 픽업 리스너
        getServer().getPluginManager().registerEvents(
                new ItemPickupListener(pickupResolver, config, getLogger()),
                this
        );
    }

    /**
     * 명령어 등록
     */
    private void registerCommands() {
        // 메인 명령어
        BaljaCommand baljaCommand = new BaljaCommand(priorityManager, messageManager);

        // 서브커맨드 등록
        baljaCommand.registerSubCommand(new RankSubCommand(priorityManager, messageManager));
        baljaCommand.registerSubCommand(new ResetSubCommand(priorityManager, messageManager));
        baljaCommand.registerSubCommand(new ChangeRankSubCommand(priorityManager, messageManager));
        baljaCommand.registerSubCommand(new ReloadSubCommand(this, configManager, messageManager));

        // 명령어 등록
        Objects.requireNonNull(getCommand("발자")).setExecutor(baljaCommand);
        Objects.requireNonNull(getCommand("발자")).setTabCompleter(new BaljaTabCompleter(baljaCommand));
    }

    /**
     * PlaceholderAPI 연동 (선택적)
     * PlaceholderAPI가 설치된 경우에만 플레이스홀더 확장 등록
     */
    private void registerPlaceholderAPI() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new kr.minex.itempickuppriority.hook.PlaceholderAPIHook(this).register();
            getLogger().info("PlaceholderAPI 연동 완료");
        }
    }

    /**
     * 프록시 메시징 초기화
     * PROXY_MYSQL 모드에서만 활성화
     *
     * BungeeCord와 Velocity 둘 다 지원하며, 서버 설정에 따라 자동 감지
     */
    private void initializeProxyMessaging() {
        if (config.getOperationMode() != OperationMode.PROXY_MYSQL) {
            return;
        }

        // 프록시 타입 감지 및 메신저 생성
        proxyMessenger = detectProxyMessenger();

        if (proxyMessenger == null) {
            getLogger().warning("프록시 환경이 감지되지 않았습니다. 프록시 메시징이 비활성화됩니다.");
            getLogger().warning("BungeeCord: spigot.yml의 bungeecord: true 설정 확인");
            getLogger().warning("Velocity: paper-global.yml의 velocity 설정 확인");
            return;
        }

        // 메신저 등록
        proxyMessenger.register(this);

        // PriorityManager에 메신저 연결
        priorityManager.setProxyMessenger(proxyMessenger);

        // 메시지 리스너 등록
        proxyMessageListener = new ProxyMessageListener(
                this, priorityManager, proxyMessenger, config.isDebug());
        proxyMessageListener.register();

        getLogger().info("프록시 메시징 시스템 초기화 완료");
    }

    /**
     * 프록시 타입 감지 및 적절한 메신저 반환
     *
     * @return 감지된 프록시 메신저, 프록시가 없으면 null
     */
    private ProxyMessenger detectProxyMessenger() {
        // Velocity 감지 (Paper 1.19+)
        // Paper의 경우 velocity가 활성화되면 paper-global.yml에 설정됨
        try {
            // Paper의 Velocity 지원 확인
            Class<?> paperConfigClass = Class.forName("io.papermc.paper.configuration.GlobalConfiguration");
            if (paperConfigClass != null) {
                // Paper 서버이고 Velocity가 설정되어 있을 가능성
                // 실제로는 spigot.yml의 bungeecord 설정도 확인해야 함
                if (getServer().spigot().getConfig().getBoolean("settings.bungeecord", false)) {
                    getLogger().info("BungeeCord/Velocity 환경 감지됨 (Paper)");
                    // Velocity와 BungeeCord 모두 BungeeCord 채널 호환
                    return new VelocityMessenger();
                }
            }
        } catch (ClassNotFoundException e) {
            // Paper가 아닌 Spigot 서버
        }

        // BungeeCord 감지 (Spigot)
        try {
            if (getServer().spigot().getConfig().getBoolean("settings.bungeecord", false)) {
                getLogger().info("BungeeCord 환경 감지됨");
                return new BungeeCordMessenger();
            }
        } catch (Exception e) {
            getLogger().warning("Spigot 설정 확인 실패: " + e.getMessage());
        }

        return null;
    }

    /**
     * 플러그인 리로드 시 기존 플레이어 처리
     */
    private void handleReload() {
        getLogger().info("플러그인 리로드 감지됨. 기존 플레이어 데이터 복구 중...");

        for (Player player : Bukkit.getOnlinePlayers()) {
            // 세션 순서 기록
            sessionTracker.recordJoin(player.getUniqueId(), player.getName());

            // 우선순위 데이터 로드
            priorityManager.onPlayerJoin(player.getUniqueId(), player.getName());
        }

        getLogger().info(Bukkit.getOnlinePlayers().size() + "명의 플레이어 데이터 복구 완료");
    }

    // ==================== Getter ====================

    public static ItemPickupPriority getInstance() {
        return instance;
    }

    public PluginConfig getPluginConfig() {
        return config;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public MessageManager getMessageManager() {
        return messageManager;
    }

    public DatabaseProvider getDatabase() {
        return database;
    }

    public CacheManager getCacheManager() {
        return cacheManager;
    }

    public SessionOrderTracker getSessionTracker() {
        return sessionTracker;
    }

    public PriorityManager getPriorityManager() {
        return priorityManager;
    }

    public PickupPriorityResolver getPickupResolver() {
        return pickupResolver;
    }

    /**
     * 플러그인 시작 배너 출력
     */
    private void printStartupBanner() {
        String version = getDescription().getVersion();
        OperationMode mode = config.getOperationMode();
        String dbType = mode == OperationMode.PROXY_MYSQL ? "MySQL" : "SQLite";

        getLogger().info("========================================");
        getLogger().info("  ItemPickupPriority Plugin v" + version);
        getLogger().info("  운영 모드: " + mode.name());
        getLogger().info("  데이터베이스: " + dbType);
        getLogger().info("  시간 모드: " + config.getTimeMode().name());
        getLogger().info("  Created by Junseo5");
        getLogger().info("  Bug reports: Discord - Junseo5#3213");
        getLogger().info("========================================");
    }
}
