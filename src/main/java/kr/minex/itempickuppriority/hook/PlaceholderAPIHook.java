package kr.minex.itempickuppriority.hook;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import kr.minex.itempickuppriority.ItemPickupPriority;
import kr.minex.itempickuppriority.model.PlayerPriority;

import java.util.List;

/**
 * PlaceholderAPI 확장
 *
 * 지원하는 플레이스홀더:
 * - %발자_내순위% : 플레이어 본인의 순위
 * - %발자_1위%, %발자_2위% ... %발자_10위% : 해당 순위의 플레이어 이름
 * - %발자_1위_순위%, %발자_2위_순위% ... : 해당 순위 (숫자)
 * - %발자_총인원% : 전체 등록된 플레이어 수
 *
 * 영문 버전:
 * - %balja_rank% : 플레이어 본인의 순위
 * - %balja_top_1%, %balja_top_2% ... %balja_top_10% : 해당 순위의 플레이어 이름
 * - %balja_top_1_rank%, %balja_top_2_rank% ... : 해당 순위 (숫자)
 * - %balja_total% : 전체 등록된 플레이어 수
 */
public class PlaceholderAPIHook extends PlaceholderExpansion {

    private final ItemPickupPriority plugin;

    public PlaceholderAPIHook(ItemPickupPriority plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "발자";
    }

    @Override
    public String getAuthor() {
        return String.join(", ", plugin.getDescription().getAuthors());
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true; // 리로드 시에도 유지
    }

    @Override
    public String onRequest(OfflinePlayer offlinePlayer, String params) {
        // 내 순위
        if (params.equals("내순위") || params.equalsIgnoreCase("rank")) {
            if (offlinePlayer == null) return "-";
            int rank = plugin.getPriorityManager().getPlayerRank(offlinePlayer.getUniqueId());
            return rank == -1 ? "-" : String.valueOf(rank);
        }

        // 총 인원
        if (params.equals("총인원") || params.equalsIgnoreCase("total")) {
            return String.valueOf(plugin.getPriorityManager().getTotalCount());
        }

        // N위 플레이어 이름 (1위 ~ 10위)
        for (int i = 1; i <= 10; i++) {
            // 한글: 1위, 2위 ...
            if (params.equals(i + "위")) {
                return getPlayerNameAtRank(i);
            }
            // 영문: top_1, top_2 ...
            if (params.equalsIgnoreCase("top_" + i)) {
                return getPlayerNameAtRank(i);
            }
            // 순위 숫자: 1위_순위, top_1_rank
            if (params.equals(i + "위_순위") || params.equalsIgnoreCase("top_" + i + "_rank")) {
                return hasPlayerAtRank(i) ? String.valueOf(i) : "-";
            }
        }

        return null;
    }

    /**
     * 해당 순위의 플레이어 이름 반환
     * 리스트 인덱스 기반으로 조회 (DB 재정렬 후에도 정확한 순위 반영)
     *
     * @param rank 조회할 순위 (1~10)
     * @return 해당 순위 플레이어 이름, 없으면 빈 문자열
     */
    private String getPlayerNameAtRank(int rank) {
        if (rank < 1 || rank > 10) {
            return "";
        }

        // 리스트 인덱스 기반 조회 (0번 = 1위, 1번 = 2위, ...)
        List<PlayerPriority> top10 = plugin.getPriorityManager().getRankingList(1, 10);
        int index = rank - 1;

        if (index < top10.size()) {
            return top10.get(index).getName();
        }
        return "";
    }

    /**
     * 해당 순위에 플레이어가 있는지 확인
     * 리스트 인덱스 기반으로 조회
     *
     * @param rank 조회할 순위 (1~10)
     * @return 해당 순위에 플레이어가 있으면 true
     */
    private boolean hasPlayerAtRank(int rank) {
        if (rank < 1 || rank > 10) {
            return false;
        }

        List<PlayerPriority> top10 = plugin.getPriorityManager().getRankingList(1, 10);
        return rank <= top10.size();
    }
}
