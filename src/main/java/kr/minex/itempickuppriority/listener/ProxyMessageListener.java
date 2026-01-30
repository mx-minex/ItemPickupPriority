package kr.minex.itempickuppriority.listener;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import kr.minex.itempickuppriority.manager.PriorityManager;
import kr.minex.itempickuppriority.messaging.AbstractProxyMessenger;
import kr.minex.itempickuppriority.messaging.ProxyMessenger;

import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 프록시 서버로부터 플러그인 메시지 수신 리스너
 * 다른 서버에서 보낸 캐시 무효화/순위 초기화 메시지 처리
 *
 * 추가로 첫 플레이어 접속 시 서버 이름 조회 요청
 */
public class ProxyMessageListener implements PluginMessageListener, Listener {

    private final JavaPlugin plugin;
    private final Logger logger;
    private final PriorityManager priorityManager;
    private final ProxyMessenger messenger;
    private final boolean debug;

    // 서버 이름 조회 완료 여부
    private boolean serverNameRequested = false;

    // BungeeCord 기본 채널
    private static final String BUNGEECORD_CHANNEL = "BungeeCord";

    public ProxyMessageListener(JavaPlugin plugin, PriorityManager priorityManager,
                                ProxyMessenger messenger, boolean debug) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.priorityManager = priorityManager;
        this.messenger = messenger;
        this.debug = debug;
    }

    /**
     * 리스너 등록
     */
    public void register() {
        // 플러그인 메시지 채널 등록 (수신용)
        plugin.getServer().getMessenger().registerIncomingPluginChannel(
                plugin, ProxyMessenger.CHANNEL, this);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(
                plugin, BUNGEECORD_CHANNEL, this);

        // 이벤트 리스너 등록 (첫 플레이어 접속 시 서버 이름 조회)
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        logger.info("프록시 메시지 리스너 등록 완료");
    }

    /**
     * 리스너 해제
     */
    public void unregister() {
        plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin, ProxyMessenger.CHANNEL, this);
        plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin, BUNGEECORD_CHANNEL, this);

        logger.info("프록시 메시지 리스너 해제 완료");
    }

    /**
     * 첫 플레이어 접속 시 서버 이름 조회 요청
     * 서버 이름을 알아야 자기 서버 메시지 필터링 가능
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!serverNameRequested && messenger instanceof AbstractProxyMessenger) {
            // 약간의 딜레이 후 요청 (플레이어가 완전히 접속한 후)
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                ((AbstractProxyMessenger) messenger).requestServerName();
                serverNameRequested = true;

                if (debug) {
                    logger.info("서버 이름 조회 요청 전송 (플레이어: " + event.getPlayer().getName() + ")");
                }
            }, 20L); // 1초 후
        }
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (channel.equals(BUNGEECORD_CHANNEL)) {
            handleBungeeCordMessage(message);
        } else if (channel.equals(ProxyMessenger.CHANNEL)) {
            handleSyncMessage(message);
        }
    }

    /**
     * BungeeCord 기본 채널 메시지 처리
     * GetServer 응답 및 Forward 메시지 처리
     */
    private void handleBungeeCordMessage(byte[] message) {
        try {
            ByteArrayDataInput in = ByteStreams.newDataInput(message);
            String subchannel = in.readUTF();

            if ("GetServer".equals(subchannel)) {
                // 서버 이름 응답 처리
                String serverName = in.readUTF();
                updateServerName(serverName);
            } else if (ProxyMessenger.CHANNEL.equals(subchannel)) {
                // Forward로 전달된 동기화 메시지
                short length = in.readShort();
                byte[] msgBytes = new byte[length];
                in.readFully(msgBytes);
                String jsonMessage = new String(msgBytes, StandardCharsets.UTF_8);
                processSyncMessage(jsonMessage);
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "BungeeCord 메시지 파싱 실패", e);
        }
    }

    /**
     * 동기화 채널 직접 메시지 처리
     */
    private void handleSyncMessage(byte[] message) {
        try {
            String jsonMessage = new String(message, StandardCharsets.UTF_8);
            processSyncMessage(jsonMessage);
        } catch (Exception e) {
            logger.log(Level.WARNING, "동기화 메시지 파싱 실패", e);
        }
    }

    /**
     * 서버 이름 업데이트
     */
    private void updateServerName(String serverName) {
        if (messenger instanceof AbstractProxyMessenger) {
            ((AbstractProxyMessenger) messenger).setServerName(serverName);
        }
    }

    /**
     * 동기화 메시지 처리
     * JSON 파싱하여 메시지 타입에 따라 적절한 동작 수행
     */
    private void processSyncMessage(String jsonMessage) {
        try {
            // 간단한 JSON 파싱 (의존성 최소화)
            String type = extractJsonValue(jsonMessage, "type");
            String server = extractJsonValue(jsonMessage, "server");

            if (type == null || server == null) {
                if (debug) {
                    logger.warning("잘못된 동기화 메시지 형식: " + jsonMessage);
                }
                return;
            }

            // 자기 서버가 보낸 메시지 무시
            if (server.equals(messenger.getServerName())) {
                if (debug) {
                    logger.info("자기 서버 메시지 무시: " + type);
                }
                return;
            }

            if (debug) {
                logger.info("프록시 메시지 수신: " + type + " from " + server);
            }

            // 메시지 타입에 따라 처리
            switch (type) {
                case ProxyMessenger.MessageType.CACHE_INVALIDATE:
                    handleCacheInvalidate();
                    break;
                case ProxyMessenger.MessageType.RANKING_RESET:
                    handleRankingReset();
                    break;
                default:
                    if (debug) {
                        logger.warning("알 수 없는 메시지 타입: " + type);
                    }
            }

        } catch (Exception e) {
            logger.log(Level.WARNING, "동기화 메시지 처리 실패", e);
        }
    }

    /**
     * 캐시 무효화 처리
     * 다른 서버에서 순위가 변경되었으므로 캐시를 다시 로드
     */
    private void handleCacheInvalidate() {
        if (debug) {
            logger.info("캐시 무효화 처리 시작");
        }

        // 비동기로 캐시 재로드
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            priorityManager.getCacheManager().warmUp();

            if (debug) {
                logger.info("캐시 무효화 처리 완료");
            }
        });
    }

    /**
     * 순위 초기화 처리
     * 다른 서버에서 순위가 초기화되었으므로 캐시를 비우고 다시 로드
     *
     * 중요: 이 서버의 온라인 플레이어 정보는 보존해야 함
     * - SessionOrderTracker는 초기화하되, 현재 온라인 플레이어는 다시 등록
     * - 캐시 클리어 후 DB에서 다시 로드하면 loadAllToCache()에서 온라인 플레이어 처리
     */
    private void handleRankingReset() {
        if (debug) {
            logger.info("순위 초기화 메시지 처리 시작");
        }

        // 비동기로 캐시 재로드
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            // 1. 현재 온라인 플레이어 정보 백업
            var onlinePlayers = priorityManager.getSessionTracker()
                    .getOnlinePlayersWithNamesInJoinOrder();

            // 2. SessionOrderTracker 초기화
            priorityManager.getSessionTracker().clear();

            // 3. 온라인 플레이어 다시 등록
            for (var entry : onlinePlayers) {
                priorityManager.getSessionTracker().recordJoin(entry.getKey(), entry.getValue());
            }

            // 4. 캐시 클리어 및 DB에서 재로드
            priorityManager.getCacheManager().clear();
            priorityManager.getCacheManager().warmUp();

            if (debug) {
                logger.info("순위 초기화 메시지 처리 완료 (온라인 플레이어 " + onlinePlayers.size() + "명 보존)");
            }
        });
    }

    /**
     * 간단한 JSON 값 추출
     * 외부 라이브러리 의존성 최소화
     *
     * @param json JSON 문자열
     * @param key  추출할 키
     * @return 값 (없으면 null)
     */
    private String extractJsonValue(String json, String key) {
        String searchKey = "\"" + key + "\":";
        int keyIndex = json.indexOf(searchKey);
        if (keyIndex == -1) {
            return null;
        }

        int valueStart = keyIndex + searchKey.length();

        // 범위 체크
        if (valueStart >= json.length()) {
            return null;
        }

        // 문자열 값인 경우
        if (json.charAt(valueStart) == '"') {
            int valueEnd = json.indexOf('"', valueStart + 1);
            if (valueEnd == -1) {
                return null;
            }
            return json.substring(valueStart + 1, valueEnd);
        }

        // 숫자 값인 경우
        int valueEnd = valueStart;
        while (valueEnd < json.length()) {
            char c = json.charAt(valueEnd);
            if (c == ',' || c == '}') {
                break;
            }
            valueEnd++;
        }
        return json.substring(valueStart, valueEnd).trim();
    }
}
