package kr.minex.itempickuppriority.config;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import kr.minex.itempickuppriority.util.ColorUtil;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.logging.Logger;

/**
 * messages.yml 메시지 파일 관리자
 * 다국어 지원 및 플레이스홀더 치환 기능 제공
 */
public class MessageManager {

    private final JavaPlugin plugin;
    private final Logger logger;
    private FileConfiguration messages;
    private String prefix;

    // 숫자 포맷터 (1000 -> 1,000)
    private final NumberFormat numberFormat = NumberFormat.getInstance();

    public MessageManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        reload();
    }

    /**
     * 메시지 파일 리로드
     */
    public void reload() {
        File messagesFile = new File(plugin.getDataFolder(), "messages.yml");

        // 파일이 없으면 기본 파일 복사
        if (!messagesFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }

        messages = YamlConfiguration.loadConfiguration(messagesFile);

        // 기본값 병합 (누락된 키 추가)
        InputStream defaultStream = plugin.getResource("messages.yml");
        if (defaultStream != null) {
            YamlConfiguration defaultMessages = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defaultStream, StandardCharsets.UTF_8));
            messages.setDefaults(defaultMessages);
        }

        // 접두사 캐싱
        prefix = ColorUtil.colorize(messages.getString("messages.prefix", "&6[발자] &r"));
    }

    /**
     * 메시지 가져오기 (플레이스홀더 치환 없음)
     *
     * @param key 메시지 키 (messages. 접두사 생략 가능)
     * @return 색상 코드가 적용된 메시지
     */
    public String get(String key) {
        String fullKey = key.startsWith("messages.") ? key : "messages." + key;
        String message = messages.getString(fullKey);

        if (message == null) {
            logger.warning("메시지 키를 찾을 수 없습니다: " + fullKey);
            return prefix + "&c메시지를 찾을 수 없습니다: " + key;
        }

        return ColorUtil.colorize(message);
    }

    /**
     * 메시지 가져오기 (플레이스홀더 치환)
     *
     * @param key          메시지 키
     * @param placeholders 플레이스홀더 (키, 값, 키, 값, ...)
     * @return 플레이스홀더가 치환된 메시지
     */
    public String get(String key, Object... placeholders) {
        String message = get(key);
        return replacePlaceholders(message, placeholders);
    }

    /**
     * 접두사가 포함된 메시지 가져오기
     *
     * @param key          메시지 키
     * @param placeholders 플레이스홀더
     * @return 접두사 + 메시지
     */
    public String getWithPrefix(String key, Object... placeholders) {
        return prefix + get(key, placeholders);
    }

    /**
     * 플레이어에게 메시지 전송 (접두사 포함)
     *
     * @param sender       메시지 수신자
     * @param key          메시지 키
     * @param placeholders 플레이스홀더
     */
    public void send(CommandSender sender, String key, Object... placeholders) {
        String message = getWithPrefix(key, placeholders);

        // 멀티라인 메시지 처리
        for (String line : message.split("\n")) {
            sender.sendMessage(line);
        }
    }

    /**
     * 플레이어에게 메시지 전송 (접두사 없음)
     *
     * @param sender       메시지 수신자
     * @param key          메시지 키
     * @param placeholders 플레이스홀더
     */
    public void sendRaw(CommandSender sender, String key, Object... placeholders) {
        String message = get(key, placeholders);

        // 멀티라인 메시지 처리
        for (String line : message.split("\n")) {
            sender.sendMessage(line);
        }
    }

    /**
     * 플레이스홀더 치환
     *
     * @param message      원본 메시지
     * @param placeholders 플레이스홀더 배열 (키, 값, 키, 값, ...)
     * @return 치환된 메시지
     */
    private String replacePlaceholders(String message, Object... placeholders) {
        if (placeholders == null || placeholders.length == 0) {
            return message;
        }

        String result = message;
        for (int i = 0; i < placeholders.length - 1; i += 2) {
            String placeholder = "%" + placeholders[i] + "%";
            String value = formatValue(placeholders[i + 1]);
            result = result.replace(placeholder, value);
        }

        return result;
    }

    /**
     * 값 포맷팅 (숫자는 천 단위 구분)
     */
    private String formatValue(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Number) {
            return numberFormat.format(value);
        }
        return String.valueOf(value);
    }

    /**
     * 현재 접두사 반환
     */
    public String getPrefix() {
        return prefix;
    }
}
