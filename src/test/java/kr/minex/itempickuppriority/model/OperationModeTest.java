package kr.minex.itempickuppriority.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OperationMode enum 테스트
 */
class OperationModeTest {

    @Test
    @DisplayName("유효한 문자열 파싱")
    void 유효한_문자열_파싱() {
        assertEquals(OperationMode.STANDALONE, OperationMode.fromString("STANDALONE"));
        assertEquals(OperationMode.STANDALONE, OperationMode.fromString("standalone"));
        assertEquals(OperationMode.STANDALONE, OperationMode.fromString("StandAlone"));

        assertEquals(OperationMode.PROXY_MYSQL, OperationMode.fromString("PROXY_MYSQL"));
        assertEquals(OperationMode.PROXY_MYSQL, OperationMode.fromString("proxy_mysql"));
    }

    @Test
    @DisplayName("공백 포함 문자열 파싱")
    void 공백_포함_문자열_파싱() {
        assertEquals(OperationMode.STANDALONE, OperationMode.fromString("  STANDALONE  "));
        assertEquals(OperationMode.PROXY_MYSQL, OperationMode.fromString(" proxy_mysql "));
    }

    @Test
    @DisplayName("잘못된 문자열은 STANDALONE 반환")
    void 잘못된_문자열_기본값_반환() {
        assertEquals(OperationMode.STANDALONE, OperationMode.fromString("INVALID"));
        assertEquals(OperationMode.STANDALONE, OperationMode.fromString("unknown"));
        assertEquals(OperationMode.STANDALONE, OperationMode.fromString(""));
        assertEquals(OperationMode.STANDALONE, OperationMode.fromString(null));
    }
}
