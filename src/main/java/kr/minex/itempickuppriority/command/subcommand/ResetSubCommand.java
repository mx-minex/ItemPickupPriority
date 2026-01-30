package kr.minex.itempickuppriority.command.subcommand;

import org.bukkit.command.CommandSender;
import kr.minex.itempickuppriority.config.MessageManager;
import kr.minex.itempickuppriority.manager.PriorityManager;

import java.util.Collections;
import java.util.List;

/**
 * 순위 초기화 서브커맨드
 * /발자 초기화
 *
 * 현재 접속 중인 플레이어를 세션 접속 순서대로 순위 재설정
 */
public class ResetSubCommand implements SubCommand {

    private final PriorityManager priorityManager;
    private final MessageManager messageManager;

    public ResetSubCommand(PriorityManager priorityManager, MessageManager messageManager) {
        this.priorityManager = priorityManager;
        this.messageManager = messageManager;
    }

    @Override
    public String getName() {
        return "초기화";
    }

    @Override
    public String[] getAliases() {
        return new String[]{"reset", "리셋"};
    }

    @Override
    public String getPermission() {
        return "itempickuppriority.admin";
    }

    @Override
    public String getUsage() {
        return "/발자 초기화";
    }

    @Override
    public String getDescription() {
        return "현재 접속자 기준으로 순위를 초기화합니다.";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        // 확인 메시지 전송
        messageManager.send(sender, "reset.processing");

        // 비동기로 초기화 실행
        priorityManager.resetRanking().thenAccept(result -> {
            if (result < 0) {
                // 오류 발생
                messageManager.send(sender, "reset.failed");
            } else if (result == 0) {
                // DB만 비움 (온라인 플레이어 없음)
                messageManager.send(sender, "reset.success-empty");
            } else {
                // 정상 초기화 완료
                messageManager.send(sender, "reset.success", "%count%", String.valueOf(result));
            }
        }).exceptionally(e -> {
            messageManager.send(sender, "error.internal");
            return null;
        });

        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }
}
