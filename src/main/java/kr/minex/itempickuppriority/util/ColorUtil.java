package kr.minex.itempickuppriority.util;

import org.bukkit.ChatColor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 색상 코드 변환 유틸리티
 * & 코드를 Minecraft 색상 코드(§)로 변환
 */
public final class ColorUtil {

    // HEX 색상 패턴 (&#RRGGBB 또는 &x&R&R&G&G&B&B 형식)
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

    private ColorUtil() {
        // 유틸리티 클래스 - 인스턴스 생성 방지
    }

    /**
     * & 색상 코드를 § 코드로 변환
     *
     * @param text 변환할 텍스트
     * @return 변환된 텍스트
     */
    public static String colorize(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        // HEX 색상 변환 (1.16+)
        String result = translateHexColors(text);

        // 일반 & 색상 코드 변환
        return ChatColor.translateAlternateColorCodes('&', result);
    }

    /**
     * &#RRGGBB 형식의 HEX 색상을 Minecraft 형식으로 변환
     */
    private static String translateHexColors(String text) {
        Matcher matcher = HEX_PATTERN.matcher(text);
        StringBuffer buffer = new StringBuffer();

        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder replacement = new StringBuilder("§x");
            for (char c : hex.toCharArray()) {
                replacement.append("§").append(c);
            }
            matcher.appendReplacement(buffer, replacement.toString());
        }
        matcher.appendTail(buffer);

        return buffer.toString();
    }

    /**
     * 색상 코드 제거
     *
     * @param text 색상 코드가 포함된 텍스트
     * @return 색상 코드가 제거된 텍스트
     */
    public static String stripColor(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return ChatColor.stripColor(colorize(text));
    }
}
