package kr.minex.itempickuppriority.command.subcommand;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import kr.minex.itempickuppriority.config.MessageManager;
import kr.minex.itempickuppriority.manager.PriorityManager;
import kr.minex.itempickuppriority.model.PlayerPriority;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 순위 변경 서브커맨드
 * /발자 순위변경 <플레이어> <순위>
 *
 * 플레이어를 지정한 순위에 삽입 (기존 순위 밀림)
 */
public class ChangeRankSubCommand implements SubCommand {

    private final PriorityManager priorityManager;
    private final MessageManager messageManager;

    public ChangeRankSubCommand(PriorityManager priorityManager, MessageManager messageManager) {
        this.priorityManager = priorityManager;
        this.messageManager = messageManager;
    }

    @Override
    public String getName() {
        return "순위변경";
    }

    @Override
    public String[] getAliases() {
        return new String[]{"changerank", "setrank", "변경"};
    }

    @Override
    public String getPermission() {
        return "itempickuppriority.admin";
    }

    @Override
    public String getUsage() {
        return "/발자 순위변경 <플레이어> <순위>";
    }

    @Override
    public String getDescription() {
        return "특정 플레이어의 순위를 변경합니다.";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        // 인자 검증
        if (args.length < 2) {
            messageManager.send(sender, "error.usage", "usage", getUsage());
            return true;
        }

        String targetName = args[0];
        String rankStr = args[1];

        // 순위 파싱
        int newRank;
        try {
            newRank = Integer.parseInt(rankStr);
            if (newRank < 1) {
                messageManager.send(sender, "error.invalid-rank");
                return true;
            }
        } catch (NumberFormatException e) {
            messageManager.send(sender, "error.invalid-rank");
            return true;
        }

        // 플레이어 찾기 (온라인 우선, 오프라인도 지원)
        UUID targetUuid = findPlayerUuid(targetName);

        if (targetUuid == null) {
            messageManager.send(sender, "error.player-not-found", "player", targetName);
            return true;
        }

        // 현재 순위 확인
        int currentRank = priorityManager.getPlayerRank(targetUuid);
        if (currentRank == -1) {
            messageManager.send(sender, "error.player-not-registered", "player", targetName);
            return true;
        }

        // 처리 중 메시지
        messageManager.send(sender, "changerank.processing", "player", targetName, "rank", newRank);

        // 비동기로 순위 변경
        priorityManager.changeRank(targetUuid, newRank).thenAccept(success -> {
            if (success) {
                messageManager.send(sender, "changerank.success",
                        "player", targetName,
                        "old_rank", currentRank,
                        "new_rank", newRank);
            } else {
                messageManager.send(sender, "changerank.failed", "player", targetName);
            }
        }).exceptionally(e -> {
            messageManager.send(sender, "error.internal");
            return null;
        });

        return true;
    }

    /**
     * 플레이어 이름으로 UUID 찾기
     */
    private UUID findPlayerUuid(String name) {
        // 온라인 플레이어 먼저 검색
        Player onlinePlayer = Bukkit.getPlayerExact(name);
        if (onlinePlayer != null) {
            return onlinePlayer.getUniqueId();
        }

        // 오프라인 플레이어 검색 (DB에서)
        Optional<PlayerPriority> dbPlayer = findPlayerByName(name);
        return dbPlayer.map(PlayerPriority::getUuid).orElse(null);
    }

    /**
     * DB에서 이름으로 플레이어 검색
     */
    private Optional<PlayerPriority> findPlayerByName(String name) {
        // 전체 목록에서 검색 (대소문자 무시)
        List<PlayerPriority> all = priorityManager.getRankingList(1, Integer.MAX_VALUE);
        return all.stream()
                .filter(p -> p.getName().equalsIgnoreCase(name))
                .findFirst();
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            // 플레이어 이름 자동완성 (온라인 + 등록된 플레이어)
            String prefix = args[0].toLowerCase();
            Set<String> names = new HashSet<>();

            // 온라인 플레이어
            for (Player player : Bukkit.getOnlinePlayers()) {
                names.add(player.getName());
            }

            // 등록된 플레이어 (최대 100명)
            List<PlayerPriority> registered = priorityManager.getRankingList(1, 100);
            for (PlayerPriority p : registered) {
                names.add(p.getName());
            }

            return names.stream()
                    .filter(name -> name.toLowerCase().startsWith(prefix))
                    .sorted()
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            // 순위 제안 (1, 2, 3, ... 또는 현재 순위 기반)
            String prefix = args[1];
            List<String> suggestions = new ArrayList<>();

            // 1-10 기본 제안
            for (int i = 1; i <= 10; i++) {
                String s = String.valueOf(i);
                if (s.startsWith(prefix)) {
                    suggestions.add(s);
                }
            }

            return suggestions;
        }

        return Collections.emptyList();
    }
}
