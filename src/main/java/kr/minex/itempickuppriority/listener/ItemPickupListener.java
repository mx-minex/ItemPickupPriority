package kr.minex.itempickuppriority.listener;

import org.bukkit.Location;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import kr.minex.itempickuppriority.config.PluginConfig;
import kr.minex.itempickuppriority.pickup.PickupPriorityResolver;

import java.util.Collection;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * 아이템 픽업 이벤트 리스너
 * 우선순위 시스템에 따라 픽업 허용/거부 결정
 */
public class ItemPickupListener implements Listener {

    private final PickupPriorityResolver resolver;
    private final PluginConfig config;
    private final Logger logger;

    // 픽업 후보 검색 반경 (픽업 범위 + 여유분)
    private static final double SEARCH_RADIUS = 3.0;

    public ItemPickupListener(PickupPriorityResolver resolver, PluginConfig config, Logger logger) {
        this.resolver = resolver;
        this.config = config;
        this.logger = logger;
    }

    /**
     * 아이템 픽업 이벤트 처리
     * - 플레이어가 아닌 엔티티는 무시
     * - 우선순위에 따라 픽업 허용/거부
     *
     * EventPriority.HIGHEST: 다른 플러그인보다 나중에 실행하여 최종 판단
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        // 플레이어가 아닌 경우 무시 (호퍼 마인카트 등)
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        Item item = event.getItem();

        // 아이템 주변의 플레이어 수집
        Collection<Player> nearbyPlayers = getNearbyPlayers(item.getLocation());

        // 본인만 있으면 허용 (성능 최적화)
        if (nearbyPlayers.size() <= 1) {
            if (config.isDebug()) {
                logger.info("픽업 허용 (단독): " + player.getName() +
                        " (아이템: " + item.getItemStack().getType() + ")");
            }
            return;
        }

        // 우선순위 판정
        boolean canPickup = resolver.canPickup(item, player, nearbyPlayers);

        if (!canPickup) {
            event.setCancelled(true);

            if (config.isDebug()) {
                logger.info("픽업 거부: " + player.getName() +
                        " (아이템: " + item.getItemStack().getType() +
                        ", 근처 플레이어: " + nearbyPlayers.size() + "명)");
            }
        } else {
            if (config.isDebug()) {
                logger.info("픽업 허용: " + player.getName() +
                        " (아이템: " + item.getItemStack().getType() +
                        ", 근처 플레이어: " + nearbyPlayers.size() + "명)");
            }
        }
    }

    /**
     * 아이템 위치 주변의 플레이어 수집
     *
     * @param location 아이템 위치
     * @return 주변 플레이어 목록
     */
    private Collection<Player> getNearbyPlayers(Location location) {
        if (location.getWorld() == null) {
            return java.util.Collections.emptyList();
        }

        return location.getWorld().getNearbyEntities(location, SEARCH_RADIUS, SEARCH_RADIUS, SEARCH_RADIUS)
                .stream()
                .filter(entity -> entity instanceof Player)
                .map(entity -> (Player) entity)
                .filter(Player::isOnline)
                .collect(Collectors.toList());
    }
}
