package kr.minex.itempickuppriority.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import kr.minex.itempickuppriority.cache.SessionOrderTracker;
import kr.minex.itempickuppriority.config.PluginConfig;
import kr.minex.itempickuppriority.manager.PriorityManager;

import java.util.logging.Logger;

/**
 * 플레이어 접속/퇴장 이벤트 리스너
 * - 접속 시: 세션 순서 기록, 우선순위 데이터 로드
 * - 퇴장 시: 세션 순서 갱신, 만료 시간 설정
 */
public class PlayerJoinQuitListener implements Listener {

    private final PriorityManager priorityManager;
    private final SessionOrderTracker sessionOrderTracker;
    private final PluginConfig config;
    private final Logger logger;

    public PlayerJoinQuitListener(PriorityManager priorityManager,
                                   SessionOrderTracker sessionOrderTracker,
                                   PluginConfig config,
                                   Logger logger) {
        this.priorityManager = priorityManager;
        this.sessionOrderTracker = sessionOrderTracker;
        this.config = config;
        this.logger = logger;
    }

    /**
     * 플레이어 접속 이벤트 처리
     * - 세션 접속 순서 기록
     * - 우선순위 데이터 로드 (비동기)
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // 1. 세션 접속 순서 기록
        sessionOrderTracker.recordJoin(player.getUniqueId(), player.getName());

        if (config.isDebug()) {
            int position = sessionOrderTracker.getJoinPosition(player.getUniqueId());
            logger.info("플레이어 접속: " + player.getName() +
                    " (세션 순서: " + position + ")");
        }

        // 2. 우선순위 데이터 로드 (PriorityManager에서 비동기 처리)
        priorityManager.onPlayerJoin(player.getUniqueId(), player.getName());
    }

    /**
     * 플레이어 퇴장 이벤트 처리
     * - 세션 온라인 상태 갱신 (순서는 유지)
     * - 만료 시간 설정 및 데이터 저장
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // 1. 세션 온라인 상태 갱신 (접속 순서는 유지)
        sessionOrderTracker.recordQuit(player.getUniqueId());

        if (config.isDebug()) {
            logger.info("플레이어 퇴장: " + player.getName());
        }

        // 2. 만료 시간 설정 및 데이터 저장 (PriorityManager에서 처리)
        priorityManager.onPlayerQuit(player.getUniqueId());
    }
}
