package kr.minex.itempickuppriority.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PlayerPriority 도메인 모델 테스트
 */
class PlayerPriorityTest {

    @Nested
    @DisplayName("Builder 테스트")
    class BuilderTest {

        @Test
        @DisplayName("필수 필드만으로 객체 생성 성공")
        void 필수_필드로_생성() {
            // given
            UUID uuid = UUID.randomUUID();
            String name = "TestPlayer";

            // when
            PlayerPriority priority = PlayerPriority.builder()
                    .uuid(uuid)
                    .name(name)
                    .priority(1)
                    .build();

            // then
            assertEquals(uuid, priority.getUuid());
            assertEquals(name, priority.getName());
            assertEquals(1, priority.getPriority());
            assertNotNull(priority.getRegisteredAt());
            assertNotNull(priority.getLastSeen());
            assertNull(priority.getExpiresAt());
            assertNull(priority.getRemainingSeconds());
        }

        @Test
        @DisplayName("UUID 없이 생성 시 예외 발생")
        void UUID_없이_생성시_예외() {
            // when & then
            assertThrows(NullPointerException.class, () -> {
                PlayerPriority.builder()
                        .name("TestPlayer")
                        .priority(1)
                        .build();
            });
        }

        @Test
        @DisplayName("이름 없이 생성 시 예외 발생")
        void 이름_없이_생성시_예외() {
            // when & then
            assertThrows(NullPointerException.class, () -> {
                PlayerPriority.builder()
                        .uuid(UUID.randomUUID())
                        .priority(1)
                        .build();
            });
        }

        @Test
        @DisplayName("순위 0 이하로 설정 시 예외 발생")
        void 순위_0이하_설정시_예외() {
            // when & then
            assertThrows(IllegalArgumentException.class, () -> {
                PlayerPriority.builder()
                        .uuid(UUID.randomUUID())
                        .name("TestPlayer")
                        .priority(0)
                        .build();
            });

            assertThrows(IllegalArgumentException.class, () -> {
                PlayerPriority.builder()
                        .uuid(UUID.randomUUID())
                        .name("TestPlayer")
                        .priority(-1)
                        .build();
            });
        }

        @Test
        @DisplayName("toBuilder로 복사 후 수정")
        void toBuilder_복사_수정() {
            // given
            PlayerPriority original = PlayerPriority.builder()
                    .uuid(UUID.randomUUID())
                    .name("Original")
                    .priority(5)
                    .build();

            // when
            PlayerPriority modified = original.toBuilder()
                    .name("Modified")
                    .priority(3)
                    .build();

            // then
            assertEquals(original.getUuid(), modified.getUuid());
            assertEquals("Modified", modified.getName());
            assertEquals(3, modified.getPriority());
        }
    }

    @Nested
    @DisplayName("온라인/오프라인 상태 테스트")
    class OnlineStatusTest {

        @Test
        @DisplayName("새로 생성된 객체는 온라인 상태")
        void 새_객체_온라인_상태() {
            // given
            PlayerPriority priority = PlayerPriority.builder()
                    .uuid(UUID.randomUUID())
                    .name("TestPlayer")
                    .priority(1)
                    .build();

            // then
            assertTrue(priority.isOnline());
            assertFalse(priority.isExpiredByRealTime());
            assertFalse(priority.isExpiredByServerTime());
        }

        @Test
        @DisplayName("markOfflineRealTime으로 오프라인 전환")
        void 실제시간_오프라인_전환() {
            // given
            PlayerPriority priority = PlayerPriority.builder()
                    .uuid(UUID.randomUUID())
                    .name("TestPlayer")
                    .priority(1)
                    .markOfflineRealTime(3600) // 1시간
                    .build();

            // then
            assertFalse(priority.isOnline());
            assertNotNull(priority.getExpiresAt());
            assertNull(priority.getRemainingSeconds());
            assertFalse(priority.isExpiredByRealTime()); // 아직 만료 안됨
        }

        @Test
        @DisplayName("markOfflineServerTime으로 오프라인 전환")
        void 서버시간_오프라인_전환() {
            // given
            PlayerPriority priority = PlayerPriority.builder()
                    .uuid(UUID.randomUUID())
                    .name("TestPlayer")
                    .priority(1)
                    .markOfflineServerTime(600) // 10분
                    .build();

            // then
            assertFalse(priority.isOnline());
            assertNull(priority.getExpiresAt());
            assertEquals(600, priority.getRemainingSeconds());
            assertFalse(priority.isExpiredByServerTime()); // 아직 만료 안됨
        }

        @Test
        @DisplayName("markOnline으로 다시 온라인 전환")
        void 다시_온라인_전환() {
            // given
            PlayerPriority offline = PlayerPriority.builder()
                    .uuid(UUID.randomUUID())
                    .name("TestPlayer")
                    .priority(1)
                    .markOfflineRealTime(3600)
                    .build();

            // when
            PlayerPriority online = offline.toBuilder()
                    .markOnline()
                    .build();

            // then
            assertTrue(online.isOnline());
            assertNull(online.getExpiresAt());
            assertNull(online.getRemainingSeconds());
        }
    }

    @Nested
    @DisplayName("만료 확인 테스트")
    class ExpirationTest {

        @Test
        @DisplayName("실제 시간 기준 만료 확인")
        void 실제시간_만료_확인() {
            // given - 과거 시간으로 expiresAt 설정
            PlayerPriority expired = PlayerPriority.builder()
                    .uuid(UUID.randomUUID())
                    .name("TestPlayer")
                    .priority(1)
                    .expiresAt(Instant.now().minusSeconds(10))
                    .build();

            // then
            assertTrue(expired.isExpiredByRealTime());
        }

        @Test
        @DisplayName("서버 시간 기준 만료 확인")
        void 서버시간_만료_확인() {
            // given - remainingSeconds 0으로 설정
            PlayerPriority expired = PlayerPriority.builder()
                    .uuid(UUID.randomUUID())
                    .name("TestPlayer")
                    .priority(1)
                    .remainingSeconds(0)
                    .build();

            // then
            assertTrue(expired.isExpiredByServerTime());

            // given - remainingSeconds 음수로 설정
            PlayerPriority negativeRemaining = PlayerPriority.builder()
                    .uuid(UUID.randomUUID())
                    .name("TestPlayer")
                    .priority(1)
                    .remainingSeconds(-10)
                    .build();

            // then
            assertTrue(negativeRemaining.isExpiredByServerTime());
        }
    }

    @Nested
    @DisplayName("equals/hashCode 테스트")
    class EqualsHashCodeTest {

        @Test
        @DisplayName("같은 UUID면 동일한 객체로 판단")
        void 같은_UUID_동일_객체() {
            // given
            UUID uuid = UUID.randomUUID();

            PlayerPriority p1 = PlayerPriority.builder()
                    .uuid(uuid)
                    .name("Player1")
                    .priority(1)
                    .build();

            PlayerPriority p2 = PlayerPriority.builder()
                    .uuid(uuid)
                    .name("Player2") // 다른 이름
                    .priority(5) // 다른 순위
                    .build();

            // then
            assertEquals(p1, p2);
            assertEquals(p1.hashCode(), p2.hashCode());
        }

        @Test
        @DisplayName("다른 UUID면 다른 객체로 판단")
        void 다른_UUID_다른_객체() {
            // given
            PlayerPriority p1 = PlayerPriority.builder()
                    .uuid(UUID.randomUUID())
                    .name("Player1")
                    .priority(1)
                    .build();

            PlayerPriority p2 = PlayerPriority.builder()
                    .uuid(UUID.randomUUID())
                    .name("Player1") // 같은 이름
                    .priority(1) // 같은 순위
                    .build();

            // then
            assertNotEquals(p1, p2);
        }
    }
}
