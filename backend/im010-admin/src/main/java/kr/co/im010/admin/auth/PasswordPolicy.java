package kr.co.im010.admin.auth;

/** 관리자 비밀번호 규칙: 10자 이상, 영문 대문자 · 소문자 · 숫자 · 특수문자 중 3종류 이상, 아이디 포함 금지. */
public final class PasswordPolicy {

    private PasswordPolicy() {
    }

    /** 규칙에 맞으면 null, 아니면 화면에 보여 줄 이유. */
    public static String check(String password, String loginId) {
        if (password == null || password.length() < 10) {
            return "비밀번호는 10자 이상이어야 합니다";
        }
        if (password.length() > 64) {
            return "비밀번호는 64자 이하여야 합니다";
        }
        int kinds = 0;
        kinds += password.chars().anyMatch(Character::isUpperCase) ? 1 : 0;
        kinds += password.chars().anyMatch(Character::isLowerCase) ? 1 : 0;
        kinds += password.chars().anyMatch(Character::isDigit) ? 1 : 0;
        kinds += password.chars().anyMatch(c -> !Character.isLetterOrDigit(c)) ? 1 : 0;
        if (kinds < 3) {
            return "영문 대문자 · 소문자 · 숫자 · 특수문자 중 3종류 이상을 섞어 주세요";
        }
        if (loginId != null && password.toLowerCase().contains(loginId.toLowerCase())) {
            return "비밀번호에 아이디를 넣을 수 없습니다";
        }
        return null;
    }
}
