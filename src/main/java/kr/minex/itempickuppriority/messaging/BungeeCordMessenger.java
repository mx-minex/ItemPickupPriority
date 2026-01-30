package kr.minex.itempickuppriority.messaging;

/**
 * BungeeCord 프록시 메시지 구현체
 * AbstractProxyMessenger를 상속하여 중복 코드 제거
 */
public class BungeeCordMessenger extends AbstractProxyMessenger {

    @Override
    protected String getProxyTypeName() {
        return "BungeeCord";
    }
}
