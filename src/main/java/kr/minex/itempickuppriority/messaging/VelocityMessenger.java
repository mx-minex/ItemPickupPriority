package kr.minex.itempickuppriority.messaging;

/**
 * Velocity 프록시 메시지 구현체
 * AbstractProxyMessenger를 상속하여 중복 코드 제거
 *
 * Velocity는 BungeeCord 채널 호환 모드를 지원하므로
 * 동일한 구현 사용 가능
 */
public class VelocityMessenger extends AbstractProxyMessenger {

    @Override
    protected String getProxyTypeName() {
        return "Velocity";
    }
}
