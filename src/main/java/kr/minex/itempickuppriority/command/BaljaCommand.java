package kr.minex.itempickuppriority.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import kr.minex.itempickuppriority.command.subcommand.SubCommand;
import kr.minex.itempickuppriority.config.MessageManager;
import kr.minex.itempickuppriority.manager.PriorityManager;
import kr.minex.itempickuppriority.model.PlayerPriority;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 발자 메인 명령어 핸들러
 * /발자 - 본인 순위 조회
 * /발자 순위 [페이지] - 전체 순위 조회
 * /발자 초기화 - 순위 초기화 (관리자)
 * /발자 순위변경 <플레이어> <순위> - 순위 변경 (관리자)
 * /발자 리로드 - 설정 리로드 (관리자)
 */
public class BaljaCommand implements CommandExecutor {

    private final PriorityManager priorityManager;
    private final MessageManager messageManager;
    private final Map<String, SubCommand> subCommands = new HashMap<>();

    public BaljaCommand(PriorityManager priorityManager, MessageManager messageManager) {
        this.priorityManager = priorityManager;
        this.messageManager = messageManager;
    }

    /**
     * 서브커맨드 등록
     */
    public void registerSubCommand(SubCommand subCommand) {
        subCommands.put(subCommand.getName().toLowerCase(), subCommand);

        // 별칭도 등록
        for (String alias : subCommand.getAliases()) {
            subCommands.put(alias.toLowerCase(), subCommand);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // 인자가 없으면 본인 순위 조회
        if (args.length == 0) {
            return handleMyRank(sender);
        }

        // 서브커맨드 찾기
        String subCommandName = args[0].toLowerCase();
        SubCommand subCommand = subCommands.get(subCommandName);

        if (subCommand == null) {
            // 알 수 없는 서브커맨드 -> 도움말
            sendHelp(sender);
            return true;
        }

        // 권한 체크
        if (subCommand.getPermission() != null && !sender.hasPermission(subCommand.getPermission())) {
            messageManager.send(sender, "error.no-permission");
            return true;
        }

        // 플레이어 전용 체크
        if (subCommand.isPlayerOnly() && !(sender instanceof Player)) {
            messageManager.send(sender, "error.player-only");
            return true;
        }

        // 인자 배열에서 서브커맨드 이름 제거
        String[] subArgs = new String[args.length - 1];
        System.arraycopy(args, 1, subArgs, 0, args.length - 1);

        // 서브커맨드 실행
        return subCommand.execute(sender, subArgs);
    }

    /**
     * 본인 순위 조회 (/발자)
     * TOP 10 순위와 본인 순위를 함께 표시
     */
    private boolean handleMyRank(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messageManager.send(sender, "error.player-only");
            return true;
        }

        // 헤더
        messageManager.send(sender, "command.my-rank-header-line");
        messageManager.send(sender, "command.top-title");
        messageManager.send(sender, "command.my-rank-header");

        // TOP 10 조회 (이미 순위순 정렬됨)
        List<PlayerPriority> top10 = priorityManager.getRankingList(1, 10);

        // 10개 슬롯 출력: 리스트 인덱스가 곧 순위 (0번 = 1위, 1번 = 2위, ...)
        // DB에서 삭제 후 재정렬되므로 리스트 순서를 신뢰해야 함
        for (int i = 0; i < 10; i++) {
            int displayRank = i + 1;  // 표시용 순위 (1~10)

            if (i < top10.size()) {
                // 데이터가 있는 슬롯
                PlayerPriority pp = top10.get(i);
                String status = getStatusIcon(pp);
                messageManager.send(sender, "command.top-entry",
                        "rank", displayRank,
                        "player", pp.getName(),
                        "status", status);
            } else {
                // 빈 슬롯
                messageManager.send(sender, "command.top-empty-slot", "rank", displayRank);
            }
        }

        // 구분선
        messageManager.send(sender, "command.separator");

        // 내 순위
        int myRank = priorityManager.getPlayerRank(player.getUniqueId());
        if (myRank == -1) {
            messageManager.send(sender, "command.my-rank-not-registered");
        } else {
            messageManager.send(sender, "command.my-rank",
                    "player", player.getName(),
                    "rank", myRank);
        }

        // 푸터
        messageManager.send(sender, "command.footer-line");

        return true;
    }

    /**
     * 플레이어 상태 아이콘 반환
     */
    private String getStatusIcon(PlayerPriority priority) {
        // 현재 서버에서 온라인 확인
        boolean isLocalOnline = Bukkit.getPlayer(priority.getUuid()) != null;

        // 다른 서버에서 온라인 확인 (만료 정보가 없으면 어딘가에서 온라인)
        boolean isRemoteOnline = priority.getExpiresAt() == null && priority.getRemainingSeconds() == null;

        if (isLocalOnline || isRemoteOnline) {
            return messageManager.get("ranking.status.online");
        } else {
            String remainingTime = getRemainingTimeText(priority);
            if (remainingTime != null) {
                return messageManager.get("ranking.status.offline-with-time", "time", remainingTime);
            }
            return messageManager.get("ranking.status.offline");
        }
    }

    /**
     * 남은 만료 시간 텍스트 반환
     */
    private String getRemainingTimeText(PlayerPriority priority) {
        if (priority.getExpiresAt() != null) {
            Duration remaining = Duration.between(Instant.now(), priority.getExpiresAt());
            if (remaining.isNegative() || remaining.isZero()) {
                return null;
            }
            return formatDuration(remaining);
        }

        if (priority.getRemainingSeconds() != null) {
            int seconds = priority.getRemainingSeconds();
            if (seconds <= 0) {
                return null;
            }
            return formatDuration(Duration.ofSeconds(seconds));
        }

        return null;
    }

    /**
     * Duration을 한국어 포맷으로 변환
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

        if (days == 0 && hours == 0 && minutes == 0) {
            sb.append(seconds).append("초");
        }

        return sb.toString().trim();
    }

    /**
     * 도움말 출력
     */
    private void sendHelp(CommandSender sender) {
        messageManager.send(sender, "help.header");
        messageManager.send(sender, "help.my-rank");
        messageManager.send(sender, "help.ranking");

        // 관리자 명령어
        if (sender.hasPermission("itempickuppriority.admin")) {
            messageManager.send(sender, "help.reset");
            messageManager.send(sender, "help.changerank");
            messageManager.send(sender, "help.reload");
        }
    }

    /**
     * 등록된 서브커맨드 목록 반환
     */
    public Map<String, SubCommand> getSubCommands() {
        return subCommands;
    }
}
