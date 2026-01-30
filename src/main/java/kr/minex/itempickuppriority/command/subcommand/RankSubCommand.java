package kr.minex.itempickuppriority.command.subcommand;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import kr.minex.itempickuppriority.config.MessageManager;
import kr.minex.itempickuppriority.manager.PriorityManager;
import kr.minex.itempickuppriority.model.PlayerPriority;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

/**
 * 순위 조회 서브커맨드
 * /발자 순위 [페이지]
 */
public class RankSubCommand implements SubCommand {

    private final PriorityManager priorityManager;
    private final MessageManager messageManager;

    private static final int PAGE_SIZE = 10;

    public RankSubCommand(PriorityManager priorityManager, MessageManager messageManager) {
        this.priorityManager = priorityManager;
        this.messageManager = messageManager;
    }

    @Override
    public String getName() {
        return "순위";
    }

    @Override
    public String[] getAliases() {
        return new String[]{"rank", "랭킹", "list"};
    }

    @Override
    public String getPermission() {
        return null; // 권한 불필요
    }

    @Override
    public String getUsage() {
        return "/발자 순위 [페이지]";
    }

    @Override
    public String getDescription() {
        return "전체 발자 순위를 조회합니다.";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        int page = 1;

        // 페이지 번호 파싱
        if (args.length > 0) {
            try {
                page = Integer.parseInt(args[0]);
                if (page < 1) {
                    page = 1;
                }
            } catch (NumberFormatException e) {
                messageManager.send(sender, "error.invalid-page");
                return true;
            }
        }

        // 전체 인원 수 및 페이지 계산
        int totalCount = priorityManager.getTotalCount();
        int totalPages = (int) Math.ceil((double) totalCount / PAGE_SIZE);

        if (totalPages == 0) {
            totalPages = 1;
        }

        if (page > totalPages) {
            page = totalPages;
        }

        // 순위 목록 조회
        List<PlayerPriority> rankings = priorityManager.getRankingList(page, PAGE_SIZE);

        // 헤더 출력
        messageManager.send(sender, "ranking.header", "page", page, "total_pages", totalPages);

        // 순위 목록 출력: 리스트 인덱스 기반 순위 계산
        // DB에서 삭제 후 재정렬되므로 리스트 순서를 신뢰해야 함
        if (rankings.isEmpty()) {
            messageManager.send(sender, "ranking.empty");
        } else {
            int startRank = (page - 1) * PAGE_SIZE + 1;  // 해당 페이지의 시작 순위
            for (int i = 0; i < rankings.size(); i++) {
                PlayerPriority priority = rankings.get(i);
                String status = getStatusIcon(priority);
                int displayRank = startRank + i;  // 표시용 순위

                messageManager.send(sender, "ranking.entry",
                        "rank", displayRank,
                        "player", priority.getName(),
                        "status", status);
            }
        }

        // 푸터 (페이지 정보)
        if (totalPages > 1) {
            messageManager.send(sender, "ranking.footer",
                    "page", page,
                    "total_pages", totalPages,
                    "total_count", totalCount);
        }

        return true;
    }

    /**
     * 플레이어 상태 아이콘 반환 (실시간 온라인 상태 + 남은 시간)
     *
     * PROXY_MYSQL 모드에서의 온라인 판정:
     * 1. 현재 서버에서 Bukkit.getPlayer()로 직접 확인
     * 2. 다른 서버에서 온라인인 경우: expires_at과 remaining_seconds가 모두 null
     */
    private String getStatusIcon(PlayerPriority priority) {
        // 1. 현재 서버에서 온라인 확인
        boolean isLocalOnline = Bukkit.getPlayer(priority.getUuid()) != null;

        // 2. 다른 서버에서 온라인 확인 (만료 정보가 없으면 어딘가에서 온라인)
        boolean isRemoteOnline = priority.getExpiresAt() == null && priority.getRemainingSeconds() == null;

        if (isLocalOnline || isRemoteOnline) {
            return messageManager.get("ranking.status.online");
        } else {
            // 오프라인인 경우 남은 시간 계산
            String remainingTime = getRemainingTimeText(priority);
            if (remainingTime != null) {
                return messageManager.get("ranking.status.offline-with-time", "time", remainingTime);
            }
            return messageManager.get("ranking.status.offline");
        }
    }

    /**
     * 남은 만료 시간 텍스트 반환
     *
     * @param priority 플레이어 우선순위 정보
     * @return 남은 시간 문자열 (예: "23시간 45분"), 만료 정보 없거나 이미 만료된 경우 null
     */
    private String getRemainingTimeText(PlayerPriority priority) {
        // REAL_TIME 모드: expiresAt 기준
        if (priority.getExpiresAt() != null) {
            Duration remaining = Duration.between(Instant.now(), priority.getExpiresAt());
            // 이미 만료된 경우 null 반환 (표시하지 않음)
            if (remaining.isNegative() || remaining.isZero()) {
                return null;
            }
            return formatDuration(remaining);
        }

        // SERVER_TIME 모드: remainingSeconds 기준
        if (priority.getRemainingSeconds() != null) {
            int seconds = priority.getRemainingSeconds();
            // 이미 만료된 경우 null 반환 (표시하지 않음)
            if (seconds <= 0) {
                return null;
            }
            return formatDuration(Duration.ofSeconds(seconds));
        }

        return null;
    }

    /**
     * Duration을 한국어 포맷으로 변환
     * 예: 1일 2시간 30분, 45분, 30초
     */
    private String formatDuration(Duration duration) {
        long totalSeconds = duration.getSeconds();

        long days = totalSeconds / 86400;
        long hours = (totalSeconds % 86400) / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        StringBuilder sb = new StringBuilder();

        if (days > 0) {
            sb.append(days).append("일 ");
        }
        if (hours > 0) {
            sb.append(hours).append("시간 ");
        }
        if (minutes > 0) {
            sb.append(minutes).append("분");
        }

        // 1분 미만일 경우 초 표시
        if (days == 0 && hours == 0 && minutes == 0) {
            sb.append(seconds).append("초");
        }

        return sb.toString().trim();
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            // 페이지 번호 제안
            int totalCount = priorityManager.getTotalCount();
            int totalPages = (int) Math.ceil((double) totalCount / PAGE_SIZE);

            if (totalPages <= 1) {
                return Collections.singletonList("1");
            }

            // 1부터 totalPages까지 제안
            return java.util.stream.IntStream.rangeClosed(1, Math.min(totalPages, 10))
                    .mapToObj(String::valueOf)
                    .toList();
        }
        return Collections.emptyList();
    }
}
