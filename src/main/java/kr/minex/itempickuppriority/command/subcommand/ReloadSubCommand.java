package kr.minex.itempickuppriority.command.subcommand;

import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import kr.minex.itempickuppriority.config.ConfigManager;
import kr.minex.itempickuppriority.config.MessageManager;

import java.util.Collections;
import java.util.List;

/**
 * 설정 리로드 서브커맨드
 * /발자 리로드
 */
public class ReloadSubCommand implements SubCommand {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final MessageManager messageManager;

    public ReloadSubCommand(JavaPlugin plugin, ConfigManager configManager, MessageManager messageManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.messageManager = messageManager;
    }

    @Override
    public String getName() {
        return "리로드";
    }

    @Override
    public String[] getAliases() {
        return new String[]{"reload", "rl"};
    }

    @Override
    public String getPermission() {
        return "itempickuppriority.admin";
    }

    @Override
    public String getUsage() {
        return "/발자 리로드";
    }

    @Override
    public String getDescription() {
        return "설정 파일을 다시 불러옵니다.";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        try {
            // 설정 파일 리로드
            plugin.reloadConfig();
            configManager.reload();
            messageManager.reload();

            messageManager.send(sender, "reload.success");
        } catch (Exception e) {
            messageManager.send(sender, "reload.failed");
            plugin.getLogger().severe("설정 리로드 실패: " + e.getMessage());
        }

        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }
}
