package kr.minex.itempickuppriority.model;

/**
 * 우선순위 만료 시간 계산 모드
 * - REAL_TIME: 실제 시간 기준 (서버 꺼져도 시간 흐름)
 * - SERVER_TIME: 서버 가동 시간 기준 (서버 꺼지면 타이머 정지)
 */
public enum TimeMode {
    /**
     * 실제 시간 기준 모드
     * - 서버가 꺼져있어도 시간이 흐름
     * - expires_at 필드에 만료 시각 저장
     */
    REAL_TIME,

    /**
     * 서버 가동 시간 기준 모드
     * - 서버가 꺼지면 타이머 정지
     * - remaining_seconds 필드에 남은 초 저장
     * - 서버 틱마다 remaining_seconds 감소
     */
    SERVER_TIME;

    /**
     * 문자열로부터 TimeMode 파싱
     *
     * @param value 설정 값 문자열
     * @return 매칭되는 TimeMode, 없으면 REAL_TIME 반환 (기본값)
     */
    public static TimeMode fromString(String value) {
        if (value == null || value.isEmpty()) {
            return REAL_TIME;
        }
        try {
            return valueOf(value.toUpperCase().trim().replace("-", "_"));
        } catch (IllegalArgumentException e) {
            return REAL_TIME;
        }
    }
}
