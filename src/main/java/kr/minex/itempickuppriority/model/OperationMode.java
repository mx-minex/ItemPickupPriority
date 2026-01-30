package kr.minex.itempickuppriority.model;

/**
 * 플러그인 운영 모드
 * - STANDALONE: 단일 Bukkit 서버 (SQLite 사용)
 * - PROXY_MYSQL: 프록시 환경 (MySQL 사용, Velocity/BungeeCord와 통신)
 */
public enum OperationMode {
    /**
     * 단일 서버 모드
     * - SQLite 데이터베이스 사용
     * - 프록시 통신 비활성화
     */
    STANDALONE,

    /**
     * 프록시 서버 모드
     * - MySQL 데이터베이스 사용
     * - Velocity/BungeeCord와 Plugin Message 통신
     */
    PROXY_MYSQL;

    /**
     * 문자열로부터 OperationMode 파싱
     *
     * @param value 설정 값 문자열
     * @return 매칭되는 OperationMode, 없으면 STANDALONE 반환
     */
    public static OperationMode fromString(String value) {
        if (value == null || value.isEmpty()) {
            return STANDALONE;
        }
        try {
            return valueOf(value.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            return STANDALONE;
        }
    }
}
