package kr.minex.itempickuppriority.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import kr.minex.itempickuppriority.command.subcommand.SubCommand;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 발자 명령어 탭 자동완성
 */
public class BaljaTabCompleter implements TabCompleter {

    private final BaljaCommand baljaCommand;

    public BaljaTabCompleter(BaljaCommand baljaCommand) {
        this.baljaCommand = baljaCommand;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 0) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            // 첫 번째 인자: 서브커맨드 목록
            String prefix = args[0].toLowerCase();
            return getAvailableSubCommands(sender).stream()
                    .filter(name -> name.toLowerCase().startsWith(prefix))
                    .sorted()
                    .collect(Collectors.toList());
        }

        // 두 번째 이후 인자: 해당 서브커맨드의 탭 완성
        String subCommandName = args[0].toLowerCase();
        SubCommand subCommand = baljaCommand.getSubCommands().get(subCommandName);

        if (subCommand == null) {
            return Collections.emptyList();
        }

        // 권한 체크
        if (subCommand.getPermission() != null && !sender.hasPermission(subCommand.getPermission())) {
            return Collections.emptyList();
        }

        // 서브커맨드 이름을 제외한 인자 전달
        String[] subArgs = new String[args.length - 1];
        System.arraycopy(args, 1, subArgs, 0, args.length - 1);

        return subCommand.tabComplete(sender, subArgs);
    }

    /**
     * 사용 가능한 서브커맨드 목록 반환 (권한 기반)
     */
    private Set<String> getAvailableSubCommands(CommandSender sender) {
        Set<String> available = new HashSet<>();

        for (Map.Entry<String, SubCommand> entry : baljaCommand.getSubCommands().entrySet()) {
            SubCommand subCommand = entry.getValue();

            // 메인 이름만 추가 (별칭은 제외)
            if (!entry.getKey().equals(subCommand.getName().toLowerCase())) {
                continue;
            }

            // 권한 체크
            if (subCommand.getPermission() == null || sender.hasPermission(subCommand.getPermission())) {
                available.add(subCommand.getName());
            }
        }

        return available;
    }
}
