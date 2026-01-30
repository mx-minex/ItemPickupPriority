package kr.minex.itempickuppriority.pickup;

import org.bukkit.Location;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import kr.minex.itempickuppriority.cache.CacheManager;
import kr.minex.itempickuppriority.config.PluginConfig;
import kr.minex.itempickuppriority.model.PlayerPriority;

import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * 아이템 픽업 우선순위 결정 로직
 *
 * 마인크래프트 픽업 범위 공식:
 * - 플레이어 히트박스 기준 수평 ±1블록, 수직 ±0.5블록
 * - 플레이어 히트박스: 0.6 x 1.8 x 0.6 (너비 x 높이 x 깊이)
 * - 아이템 히트박스: 0.25 x 0.25 x 0.25
 */
public class PickupPriorityResolver {

    private final CacheManager cacheManager;
    private final PluginConfig config;
    private final Logger logger;

    // 픽업 범위 상수
    private static final double PICKUP_RANGE_HORIZONTAL = 1.0;
    private static final double PICKUP_RANGE_VERTICAL = 0.5;
    private static final double PLAYER_WIDTH = 0.6;
    private static final double PLAYER_HEIGHT_NORMAL = 1.8;
    private static final double PLAYER_HEIGHT_SNEAKING = 1.5;
    private static final double PLAYER_HEIGHT_SWIMMING = 0.6;

    public PickupPriorityResolver(CacheManager cacheManager, PluginConfig config, Logger logger) {
        this.cacheManager = cacheManager;
        this.config = config;
        this.logger = logger;
    }

    /**
     * 주어진 플레이어가 아이템을 픽업할 수 있는지 판단
     *
     * @param item          대상 아이템
     * @param player        픽업을 시도하는 플레이어
     * @param nearbyPlayers 아이템 근처의 모든 플레이어 (이미 필터링됨)
     * @return 픽업 가능 여부
     */
    public boolean canPickup(Item item, Player player, Collection<Player> nearbyPlayers) {
        // 1. 근처에 다른 플레이어가 없으면 바로 허용
        if (nearbyPlayers.size() <= 1) {
            return true;
        }

        // 2. 픽업 범위 내 플레이어만 필터링
        List<Player> candidates = nearbyPlayers.stream()
                .filter(p -> isInPickupRange(item, p))
                .collect(Collectors.toList());

        // 3. 경쟁자가 없거나 본인만 있으면 허용
        if (candidates.isEmpty()) {
            return true;
        }
        if (candidates.size() == 1 && candidates.contains(player)) {
            return true;
        }
        if (!candidates.contains(player)) {
            // 본인이 범위 밖이면 불허
            return false;
        }

        // 4. 우선순위 기준 정렬
        List<Player> sorted = sortByPriority(candidates);

        // 5. 최우선 플레이어가 본인인지 확인
        boolean canPickup = sorted.get(0).getUniqueId().equals(player.getUniqueId());

        if (config.isDebug()) {
            logger.info("픽업 판정: " + player.getName() +
                    " -> " + (canPickup ? "허용" : "거부") +
                    " (후보: " + candidates.size() + "명, 1순위: " + sorted.get(0).getName() + ")");
        }

        return canPickup;
    }

    /**
     * 아이템이 플레이어의 픽업 범위 내에 있는지 확인
     */
    public boolean isInPickupRange(Item item, Player player) {
        Location itemLoc = item.getLocation();
        Location playerLoc = player.getLocation();

        // 플레이어 히트박스 계산
        double playerHeight = getPlayerHeight(player);
        double halfWidth = PLAYER_WIDTH / 2;

        // 픽업 박스 범위 계산
        double pickupMinX = playerLoc.getX() - halfWidth - PICKUP_RANGE_HORIZONTAL;
        double pickupMaxX = playerLoc.getX() + halfWidth + PICKUP_RANGE_HORIZONTAL;
        double pickupMinY = playerLoc.getY() - PICKUP_RANGE_VERTICAL;
        double pickupMaxY = playerLoc.getY() + playerHeight + PICKUP_RANGE_VERTICAL;
        double pickupMinZ = playerLoc.getZ() - halfWidth - PICKUP_RANGE_HORIZONTAL;
        double pickupMaxZ = playerLoc.getZ() + halfWidth + PICKUP_RANGE_HORIZONTAL;

        double itemX = itemLoc.getX();
        double itemY = itemLoc.getY();
        double itemZ = itemLoc.getZ();

        // 수평: inclusive (<=), 수직: exclusive (<)
        return itemX >= pickupMinX && itemX <= pickupMaxX &&
                itemY > pickupMinY && itemY < pickupMaxY &&
                itemZ >= pickupMinZ && itemZ <= pickupMaxZ;
    }

    /**
     * 플레이어 상태에 따른 히트박스 높이 반환
     */
    private double getPlayerHeight(Player player) {
        if (player.isSwimming() || player.isGliding()) {
            return PLAYER_HEIGHT_SWIMMING;
        } else if (player.isSneaking()) {
            return PLAYER_HEIGHT_SNEAKING;
        }
        return PLAYER_HEIGHT_NORMAL;
    }

    /**
     * 플레이어 목록을 우선순위 기준으로 정렬
     * - 1차: priority 오름차순 (낮을수록 높은 우선순위)
     * - 2차: registeredAt 오름차순 (먼저 등록된 사람 우선)
     *
     * 성능 최적화:
     * - 캐시에서만 조회 (DB 조회 없음) - 메인 스레드 블로킹 방지
     * - 캐시 미스 시 최하위 우선순위 부여 (정상적으로는 발생하지 않음)
     * - 플레이어 접속 시 이미 캐시에 로드되어 있음
     */
    private List<Player> sortByPriority(List<Player> players) {
        // 각 플레이어의 우선순위 조회 (캐시에서만 - DB 조회 없음)
        Map<UUID, Integer> rankMap = new HashMap<>();
        Map<UUID, PlayerPriority> priorityMap = new HashMap<>();

        for (Player player : players) {
            // getFromCacheOnly: DB 조회 없이 캐시에서만 조회
            PlayerPriority cached = cacheManager.getFromCacheOnly(player.getUniqueId());
            if (cached != null) {
                rankMap.put(player.getUniqueId(), cached.getPriority());
                priorityMap.put(player.getUniqueId(), cached);
            } else {
                // 캐시 미스 - 최하위 우선순위 (정상적으로는 발생하지 않음)
                rankMap.put(player.getUniqueId(), Integer.MAX_VALUE);
                if (config.isDebug()) {
                    logger.warning("캐시 미스: " + player.getName() + " - 최하위 우선순위 적용");
                }
            }
        }

        // 디버그 로그: 모든 후보자 순위 출력
        if (config.isDebug()) {
            StringBuilder sb = new StringBuilder("픽업 후보자 순위: ");
            for (Player player : players) {
                int rank = rankMap.getOrDefault(player.getUniqueId(), Integer.MAX_VALUE);
                sb.append(player.getName()).append("=");
                sb.append(rank == Integer.MAX_VALUE ? "MAX" : rank);
                sb.append(", ");
            }
            logger.info(sb.toString());
        }

        // 정렬: 순위 오름차순, 동순위일 경우 등록 시간 오름차순
        return players.stream()
                .sorted((p1, p2) -> {
                    int rank1 = rankMap.getOrDefault(p1.getUniqueId(), Integer.MAX_VALUE);
                    int rank2 = rankMap.getOrDefault(p2.getUniqueId(), Integer.MAX_VALUE);

                    // 1차: priority 비교
                    int priorityCompare = Integer.compare(rank1, rank2);
                    if (priorityCompare != 0) {
                        return priorityCompare;
                    }

                    // 2차: registeredAt 비교 (둘 다 우선순위 정보가 있는 경우만)
                    PlayerPriority pp1 = priorityMap.get(p1.getUniqueId());
                    PlayerPriority pp2 = priorityMap.get(p2.getUniqueId());
                    if (pp1 != null && pp2 != null) {
                        return pp1.getRegisteredAt().compareTo(pp2.getRegisteredAt());
                    }

                    return 0;
                })
                .collect(Collectors.toList());
    }

    /**
     * 두 플레이어 간 우선순위 비교
     * 캐시에서만 조회 (DB 조회 없음) - 메인 스레드 블로킹 방지
     *
     * @return 음수: player1이 높은 우선순위, 양수: player2가 높은 우선순위, 0: 동일
     */
    public int comparePriority(Player player1, Player player2) {
        PlayerPriority pp1 = cacheManager.getFromCacheOnly(player1.getUniqueId());
        PlayerPriority pp2 = cacheManager.getFromCacheOnly(player2.getUniqueId());

        if (pp1 == null && pp2 == null) return 0;
        if (pp1 == null) return 1;
        if (pp2 == null) return -1;

        return Integer.compare(pp1.getPriority(), pp2.getPriority());
    }
}
