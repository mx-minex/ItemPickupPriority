package kr.minex.itempickuppriority.messaging;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

/**
 * 프록시 메시지 공통 구현체
 * BungeeCord와 Velocity 모두 BungeeCord 채널 호환이므로 단일 구현체로 통합
 *
 * 중복 코드 제거 및 유지보수성 향상
 */
public abstract class AbstractProxyMessenger implements ProxyMessenger {

    protected JavaPlugin plugin;
    protected Logger logger;
    protected String serverName = "unknown";
    protected boolean enabled = false;

    // BungeeCord 기본 채널 (Velocity도 호환)
    protected static final String BUNGEECORD_CHANNEL = "BungeeCord";

    /**
     * 프록시 타입 이름 반환 (로그 출력용)
     */
    protected abstract String getProxyTypeName();

    @Override
    public void register(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();

        // 플러그인 메시지 채널 등록
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, BUNGEECORD_CHANNEL);

        this.enabled = true;

        logger.info(getProxyTypeName() + " 메시징 시스템 등록 완료");
    }

    @Override
    public void unregister() {
        if (plugin != null) {
            plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin, CHANNEL);
            plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin, BUNGEECORD_CHANNEL);

            if (logger != null) {
                logger.info(getProxyTypeName() + " 메시징 시스템 해제 완료");
            }
        }
        this.enabled = false;
    }

    @Override
    public void sendCacheInvalidate() {
        sendMessage(MessageType.CACHE_INVALIDATE);
    }

    @Override
    public void sendRankingReset() {
        sendMessage(MessageType.RANKING_RESET);
    }

    @Override
    public String getServerName() {
        return serverName;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 서버 이름 설정
     *
     * @param serverName 프록시에서 받은 서버 이름
     */
    public void setServerName(String serverName) {
        this.serverName = serverName;
        if (logger != null) {
            logger.info(getProxyTypeName() + " 서버 이름 설정: " + serverName);
        }
    }

    /**
     * 프록시에 서버 이름 조회 요청
     * 첫 플레이어 접속 시 호출되어야 함
     */
    public void requestServerName() {
        Player player = getAnyPlayer();
        if (player == null) {
            return;
        }

        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("GetServer");
        player.sendPluginMessage(plugin, BUNGEECORD_CHANNEL, out.toByteArray());

        if (isDebugEnabled()) {
            logger.info("서버 이름 조회 요청 전송");
        }
    }

    /**
     * 메시지 전송
     */
    protected void sendMessage(String messageType) {
        if (!enabled) {
            return;
        }

        Player player = getAnyPlayer();
        if (player == null) {
            // 온라인 플레이어가 없으면 메시지 전송 불가
            // 경고 로그는 스팸 방지를 위해 debug 모드에서만 출력
            if (isDebugEnabled() && logger != null) {
                logger.warning("프록시 메시지 전송 실패: 온라인 플레이어 없음");
            }
            return;
        }

        // JSON 형식 메시지 생성
        String json = String.format(
                "{\"type\":\"%s\",\"server\":\"%s\",\"timestamp\":%d}",
                messageType,
                serverName,
                System.currentTimeMillis()
        );

        // Forward 서브채널을 사용하여 모든 서버에 브로드캐스트
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("Forward");
        out.writeUTF("ALL");  // 모든 서버에 전송
        out.writeUTF(CHANNEL);  // 서브채널

        byte[] messageBytes = json.getBytes(StandardCharsets.UTF_8);
        out.writeShort(messageBytes.length);
        out.write(messageBytes);

        player.sendPluginMessage(plugin, BUNGEECORD_CHANNEL, out.toByteArray());

        if (isDebugEnabled()) {
            logger.info("프록시 메시지 전송: " + messageType);
        }
    }

    /**
     * 온라인 플레이어 중 아무나 반환
     */
    protected Player getAnyPlayer() {
        var players = Bukkit.getOnlinePlayers();
        if (players.isEmpty()) {
            return null;
        }
        return players.iterator().next();
    }

    /**
     * 디버그 모드 확인
     */
    protected boolean isDebugEnabled() {
        return plugin != null && plugin.getConfig().getBoolean("debug", false);
    }
}
