package kr.minex.itempickuppriority.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TimeMode enum 테스트
 */
class TimeModeTest {

    @Test
    @DisplayName("유효한 문자열 파싱")
    void 유효한_문자열_파싱() {
        assertEquals(TimeMode.REAL_TIME, TimeMode.fromString("REAL_TIME"));
        assertEquals(TimeMode.REAL_TIME, TimeMode.fromString("real_time"));
        assertEquals(TimeMode.REAL_TIME, TimeMode.fromString("Real_Time"));

        assertEquals(TimeMode.SERVER_TIME, TimeMode.fromString("SERVER_TIME"));
        assertEquals(TimeMode.SERVER_TIME, TimeMode.fromString("server_time"));
    }

    @Test
    @DisplayName("하이픈 형식도 파싱 가능")
    void 하이픈_형식_파싱() {
        assertEquals(TimeMode.REAL_TIME, TimeMode.fromString("REAL-TIME"));
        assertEquals(TimeMode.REAL_TIME, TimeMode.fromString("real-time"));
        assertEquals(TimeMode.SERVER_TIME, TimeMode.fromString("SERVER-TIME"));
        assertEquals(TimeMode.SERVER_TIME, TimeMode.fromString("server-time"));
    }

    @Test
    @DisplayName("잘못된 문자열은 REAL_TIME 반환 (기본값)")
    void 잘못된_문자열_기본값_반환() {
        assertEquals(TimeMode.REAL_TIME, TimeMode.fromString("INVALID"));
        assertEquals(TimeMode.REAL_TIME, TimeMode.fromString("unknown"));
        assertEquals(TimeMode.REAL_TIME, TimeMode.fromString(""));
        assertEquals(TimeMode.REAL_TIME, TimeMode.fromString(null));
    }
}
