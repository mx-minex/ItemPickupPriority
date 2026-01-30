package kr.minex.itempickuppriority.messaging;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * 프록시 서버 간 메시지 통신 인터페이스
 * BungeeCord와 Velocity 모두 지원
 *
 * 다중 서버 환경에서 캐시 동기화를 위한 메시지 브로드캐스트
 */
public interface ProxyMessenger {

    /**
     * 플러그인 메시지 채널 이름
     * BungeeCord와 Velocity 모두 동일한 채널 사용
     */
    String CHANNEL = "itempickuppriority:sync";

    /**
     * 메시지 타입 상수
     */
    interface MessageType {
        String CACHE_INVALIDATE = "CACHE_INVALIDATE";
        String RANKING_RESET = "RANKING_RESET";
    }

    /**
     * 메신저 등록 및 채널 구독
     *
     * @param plugin 플러그인 인스턴스
     */
    void register(JavaPlugin plugin);

    /**
     * 메신저 등록 해제
     */
    void unregister();

    /**
     * 캐시 무효화 메시지 브로드캐스트
     * 다른 서버에 캐시를 무효화하도록 알림
     *
     * 사용 시점:
     * - 순위 변경 시
     * - 플레이어 만료 처리 시
     */
    void sendCacheInvalidate();

    /**
     * 순위 초기화 메시지 브로드캐스트
     * 다른 서버에 순위가 초기화되었음을 알림
     *
     * 사용 시점:
     * - /발자 초기화 명령어 실행 시
     */
    void sendRankingReset();

    /**
     * 현재 서버 이름 반환
     * 자기 서버가 보낸 메시지 무시용
     *
     * @return 서버 이름
     */
    String getServerName();

    /**
     * 메신저가 활성화되어 있는지 확인
     *
     * @return 활성화 여부
     */
    boolean isEnabled();
}
