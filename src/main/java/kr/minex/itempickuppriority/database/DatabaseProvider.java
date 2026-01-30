package kr.minex.itempickuppriority.database;

import kr.minex.itempickuppriority.model.PlayerPriority;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * 데이터베이스 제공자 인터페이스
 * SQLite와 MySQL 구현체가 이 인터페이스를 구현
 */
public interface DatabaseProvider {

    /**
     * 데이터베이스 초기화 (테이블 생성 등)
     */
    void initialize() throws SQLException;

    /**
     * 데이터베이스 연결 종료
     */
    void close();

    /**
     * 커넥션 획득
     */
    Connection getConnection() throws SQLException;

    // ==================== CRUD 작업 ====================

    /**
     * UUID로 플레이어 조회
     */
    Optional<PlayerPriority> findByUuid(UUID uuid);

    /**
     * 이름으로 플레이어 조회
     */
    Optional<PlayerPriority> findByName(String name);

    /**
     * 순위로 플레이어 조회
     */
    Optional<PlayerPriority> findByPriority(int priority);

    /**
     * 모든 플레이어 조회 (순위 오름차순)
     */
    List<PlayerPriority> findAll();

    /**
     * 페이징된 순위 목록 조회
     *
     * @param page     페이지 번호 (1부터 시작)
     * @param pageSize 페이지 크기
     */
    List<PlayerPriority> findAllPaged(int page, int pageSize);

    /**
     * 전체 플레이어 수 조회
     */
    int count();

    /**
     * 현재 최대 순위 조회 (가장 큰 priority 값)
     */
    int getMaxPriority();

    /**
     * 플레이어 저장 (INSERT)
     */
    void insert(PlayerPriority priority);

    /**
     * 플레이어 정보 업데이트 (UPDATE)
     */
    void update(PlayerPriority priority);

    /**
     * 플레이어 저장 또는 업데이트 (UPSERT)
     */
    void saveOrUpdate(PlayerPriority priority);

    /**
     * 플레이어 삭제
     */
    void delete(UUID uuid);

    /**
     * 모든 플레이어 삭제
     */
    void deleteAll();

    // ==================== 순위 조작 ====================

    /**
     * 특정 순위 이상의 모든 플레이어 순위 증가 (+1)
     *
     * @param fromPriority 이 순위부터 (포함)
     * @param toPriority   이 순위까지 (포함)
     */
    void incrementPriorities(int fromPriority, int toPriority);

    /**
     * 특정 순위 이상의 모든 플레이어 순위 감소 (-1)
     *
     * @param fromPriority 이 순위부터 (포함)
     */
    void decrementPrioritiesAbove(int fromPriority);

    /**
     * 플레이어 삭제 및 순위 재정렬을 단일 트랜잭션으로 실행
     * 만료된 플레이어 제거 시 사용
     *
     * @param uuid 삭제할 플레이어 UUID
     * @param removedRank 삭제되는 플레이어의 순위
     */
    void deleteAndReorderInTransaction(UUID uuid, int removedRank);

    /**
     * 특정 플레이어의 순위만 업데이트
     */
    void updatePriority(UUID uuid, int newPriority);

    // ==================== 만료 관련 ====================

    /**
     * 실제 시간 기준 만료된 플레이어 조회
     */
    List<PlayerPriority> findExpiredByRealTime(Instant now);

    /**
     * 서버 시간 기준 만료된 플레이어 조회 (remaining_seconds <= 0)
     */
    List<PlayerPriority> findExpiredByServerTime();

    /**
     * 서버 시간 기준 만료된 플레이어 조회 (remaining_seconds <= threshold)
     * 유예 시간 적용 시 음수 threshold 사용 (예: -5면 5초 유예)
     *
     * @param threshold 임계값 (예: -5면 remaining_seconds <= -5 인 것만 조회)
     */
    List<PlayerPriority> findExpiredByServerTime(int threshold);

    /**
     * 모든 오프라인 플레이어의 remaining_seconds 감소
     *
     * @param decrementBy 감소할 초
     */
    void decrementRemainingSeconds(int decrementBy);

    /**
     * 여러 플레이어의 만료시간을 일괄 클리어 (온라인 상태로 전환)
     * PROXY_MYSQL 모드에서 서버 이동 시 다른 서버의 만료 체크에서
     * 삭제되지 않도록 하기 위해 사용
     *
     * @param uuids 온라인 플레이어 UUID 목록
     * @return 업데이트된 행 수
     */
    int clearExpirationBatch(Collection<UUID> uuids);

    // ==================== 유령 유저 복구 ====================

    /**
     * 유령 유저(ghost user) 복구 - 서버 비정상 종료 후 재시작 시 호출
     *
     * 유령 유저 정의:
     * - expires_at = NULL AND remaining_seconds = NULL (온라인 상태)
     * - 하지만 실제로는 서버에 접속해 있지 않음
     *
     * 발생 원인:
     * - 서버 크래시, kill -9, 정전 등으로 onDisable() 미호출
     * - handlePlayerQuit()이 실행되지 않아 만료시간이 설정되지 않음
     *
     * 복구 방법:
     * - 현재 서버에 실제 접속 중인 플레이어를 제외한 모든 "온라인 상태" 유저에게 만료시간 설정
     *
     * PROXY_MYSQL 모드 주의사항:
     * - 다른 서버에서 실제 온라인인 플레이어도 있을 수 있음
     * - 하지만 해당 서버에서 3초마다 clearExpirationBatch()로 만료시간을 클리어하므로
     *   일시적으로 만료시간이 설정되어도 곧 클리어됨 (안전함)
     *
     * @param excludeUuids 제외할 UUID 목록 (현재 서버에 실제 접속 중인 플레이어)
     * @param expiresAt 설정할 만료 시간 (REAL_TIME 모드)
     * @param remainingSeconds 설정할 남은 시간 (SERVER_TIME 모드, null이면 expiresAt 사용)
     * @return 복구된 유령 유저 수
     */
    int recoverGhostUsers(Collection<UUID> excludeUuids, Instant expiresAt, Integer remainingSeconds);

    // ==================== 트랜잭션 ====================

    /**
     * 트랜잭션 내에서 작업 실행
     *
     * @param operation 트랜잭션 내에서 실행할 작업
     * @return 작업 결과
     */
    <T> T executeInTransaction(Function<Connection, T> operation) throws SQLException;

    // ==================== 타입 확인 ====================

    /**
     * MySQL 데이터베이스 여부 확인
     * PriorityManager에서 트랜잭션 내 SQL 실행 시 Timestamp 처리에 사용
     *
     * @return MySQL이면 true, SQLite이면 false
     */
    default boolean isMySQL() {
        return false;
    }

    // ==================== Timestamp 헬퍼 ====================

    /**
     * PreparedStatement에 Instant 값을 설정 (DB 타입에 맞게)
     * - SQLite: TEXT (ISO-8601 문자열)
     * - MySQL: TIMESTAMP
     *
     * @param ps      PreparedStatement
     * @param index   파라미터 인덱스
     * @param instant 설정할 Instant 값 (null 가능)
     */
    default void setInstant(PreparedStatement ps, int index, Instant instant) throws SQLException {
        if (isMySQL()) {
            ps.setTimestamp(index, instant != null ? Timestamp.from(instant) : null);
        } else {
            ps.setString(index, instant != null ? instant.toString() : null);
        }
    }

    // ==================== 비동기 작업 ====================

    /**
     * 비동기로 UUID로 플레이어 조회
     */
    default CompletableFuture<Optional<PlayerPriority>> findByUuidAsync(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> findByUuid(uuid));
    }

    /**
     * 비동기로 모든 플레이어 조회
     */
    default CompletableFuture<List<PlayerPriority>> findAllAsync() {
        return CompletableFuture.supplyAsync(this::findAll);
    }

    /**
     * 비동기로 저장 또는 업데이트
     */
    default CompletableFuture<Void> saveOrUpdateAsync(PlayerPriority priority) {
        return CompletableFuture.runAsync(() -> saveOrUpdate(priority));
    }
}
