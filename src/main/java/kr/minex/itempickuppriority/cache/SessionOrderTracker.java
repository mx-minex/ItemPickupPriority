package kr.minex.itempickuppriority.cache;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * 현재 서버 세션에서의 플레이어 접속 순서 추적
 * - /발자 초기화 명령어에서 사용
 * - 서버 재시작 시 초기화됨
 *
 * 핵심 기능:
 * - 플레이어가 서버에 접속한 순서를 기록
 * - 퇴장해도 접속 순서는 유지 (재접속 시 기존 순서 사용)
 * - 초기화 시 현재 온라인인 플레이어만 접속 순서대로 반환
 */
public class SessionOrderTracker {

    // 접속 순서대로 저장 (CopyOnWriteArrayList로 스레드 안전 + 순서 유지)
    private final List<UUID> joinOrder = new CopyOnWriteArrayList<>();

    // 현재 온라인 플레이어 (빠른 조회용)
    private final Set<UUID> onlinePlayers = ConcurrentHashMap.newKeySet();

    // UUID -> 플레이어 이름 매핑 (이름 조회용)
    private final Map<UUID, String> playerNames = new ConcurrentHashMap<>();

    /**
     * 플레이어 접속 기록
     *
     * @param uuid 플레이어 UUID
     * @param name 플레이어 이름
     */
    public void recordJoin(UUID uuid, String name) {
        // 접속 순서에 없으면 추가 (이미 있으면 기존 순서 유지)
        if (!joinOrder.contains(uuid)) {
            joinOrder.add(uuid);
        }

        // 온라인 상태로 표시
        onlinePlayers.add(uuid);

        // 이름 저장 (최신 이름으로 갱신)
        playerNames.put(uuid, name);
    }

    /**
     * 플레이어 퇴장 기록
     *
     * @param uuid 플레이어 UUID
     */
    public void recordQuit(UUID uuid) {
        // joinOrder에서는 제거하지 않음 (순서 유지)
        onlinePlayers.remove(uuid);
    }

    /**
     * 플레이어가 현재 온라인인지 확인
     */
    public boolean isOnline(UUID uuid) {
        return onlinePlayers.contains(uuid);
    }

    /**
     * 현재 온라인 플레이어 수
     */
    public int getOnlineCount() {
        return onlinePlayers.size();
    }

    /**
     * 플레이어의 접속 순서 조회 (1부터 시작)
     *
     * @param uuid 플레이어 UUID
     * @return 접속 순서 (없으면 -1)
     */
    public int getJoinPosition(UUID uuid) {
        int index = joinOrder.indexOf(uuid);
        return index >= 0 ? index + 1 : -1;
    }

    /**
     * 플레이어 이름 조회
     */
    public String getPlayerName(UUID uuid) {
        return playerNames.get(uuid);
    }

    /**
     * 현재 접속 중인 플레이어의 접속 순서 반환
     * - 초기화 명령어에서 사용
     * - 접속 순서대로 정렬된 UUID 리스트 반환
     *
     * @return 온라인 플레이어 UUID 리스트 (접속 순서대로)
     */
    public List<UUID> getOnlinePlayersInJoinOrder() {
        return joinOrder.stream()
                .filter(onlinePlayers::contains)
                .collect(Collectors.toList());
    }

    /**
     * 현재 접속 중인 플레이어의 접속 순서와 이름 반환
     *
     * @return 순서대로 정렬된 (UUID, 이름) 쌍 리스트
     */
    public List<Map.Entry<UUID, String>> getOnlinePlayersWithNamesInJoinOrder() {
        return joinOrder.stream()
                .filter(onlinePlayers::contains)
                .map(uuid -> Map.entry(uuid, playerNames.getOrDefault(uuid, "Unknown")))
                .collect(Collectors.toList());
    }

    /**
     * 모든 플레이어의 접속 순서 반환 (오프라인 포함)
     *
     * @return 전체 접속 순서 UUID 리스트
     */
    public List<UUID> getAllJoinOrder() {
        return new ArrayList<>(joinOrder);
    }

    /**
     * 현재 온라인 플레이어 UUID 집합 반환
     */
    public Set<UUID> getOnlinePlayers() {
        return new HashSet<>(onlinePlayers);
    }

    /**
     * 특정 플레이어를 접속 순서에서 제거
     * (일반적으로는 사용하지 않음, 관리자 명령어용)
     */
    public void removeFromOrder(UUID uuid) {
        joinOrder.remove(uuid);
        onlinePlayers.remove(uuid);
        playerNames.remove(uuid);
    }

    /**
     * 세션 초기화 (서버 재시작 시)
     * 모든 추적 데이터 삭제
     */
    public void clear() {
        joinOrder.clear();
        onlinePlayers.clear();
        playerNames.clear();
    }

    /**
     * 디버그용 정보 출력
     */
    public String getDebugInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("SessionOrderTracker Debug Info:\n");
        sb.append("  Total join order: ").append(joinOrder.size()).append("\n");
        sb.append("  Online players: ").append(onlinePlayers.size()).append("\n");
        sb.append("  Join order:\n");

        int position = 1;
        for (UUID uuid : joinOrder) {
            String name = playerNames.getOrDefault(uuid, "Unknown");
            boolean online = onlinePlayers.contains(uuid);
            sb.append("    ").append(position++).append(". ")
                    .append(name).append(" (").append(uuid).append(")")
                    .append(online ? " [ONLINE]" : " [OFFLINE]")
                    .append("\n");
        }

        return sb.toString();
    }
}
