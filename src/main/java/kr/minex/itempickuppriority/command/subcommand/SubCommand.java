package kr.minex.itempickuppriority.command.subcommand;

import org.bukkit.command.CommandSender;

import java.util.List;

/**
 * 서브커맨드 인터페이스
 * 모든 서브커맨드는 이 인터페이스를 구현
 */
public interface SubCommand {

    /**
     * 서브커맨드 이름 (명령어에서 사용되는 키)
     */
    String getName();

    /**
     * 별칭 (선택사항)
     */
    default String[] getAliases() {
        return new String[0];
    }

    /**
     * 권한 노드 (null이면 권한 불필요)
     */
    String getPermission();

    /**
     * 사용법 문자열
     */
    String getUsage();

    /**
     * 간단한 설명
     */
    String getDescription();

    /**
     * 명령어 실행
     *
     * @param sender 명령어 실행자
     * @param args   인자 배열 (서브커맨드 이름은 제외된 상태)
     * @return 명령어 처리 여부
     */
    boolean execute(CommandSender sender, String[] args);

    /**
     * 탭 완성 제공
     *
     * @param sender 명령어 실행자
     * @param args   현재 입력된 인자 배열
     * @return 자동완성 목록
     */
    List<String> tabComplete(CommandSender sender, String[] args);

    /**
     * 플레이어 전용 명령어인지 확인
     */
    default boolean isPlayerOnly() {
        return false;
    }
}
