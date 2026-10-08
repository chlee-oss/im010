package kr.co.im010.admin.auth;

import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.OptionalLong;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * 시간 기반 OTP (RFC 6238, HMAC-SHA1 · 6자리 · 30초). Google Authenticator 등 OTP 앱과 호환된다.
 * 앞뒤 1구간(±30초)까지 허용하고, 이미 쓴 구간은 다시 받지 않는다 (재사용 방지).
 */
public final class Totp {

    private static final int DIGITS = 6;
    private static final int PERIOD_SECONDS = 30;
    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final SecureRandom RANDOM = new SecureRandom();

    private Totp() {
    }

    public static String newSecret() {
        byte[] key = new byte[20];
        RANDOM.nextBytes(key);
        return base32(key);
    }

    public static long step(Instant now) {
        return now.getEpochSecond() / PERIOD_SECONDS;
    }

    /** 맞으면 일치한 구간 번호. lastStep 이하 구간은 이미 쓴 번호라 받지 않는다. */
    public static OptionalLong verify(String secret, String code, Instant now, Long lastStep) {
        if (secret == null || code == null || !code.matches("\\d{" + DIGITS + "}")) {
            return OptionalLong.empty();
        }
        byte[] key = unbase32(secret);
        long current = step(now);
        for (long s = current - 1; s <= current + 1; s++) {
            if (lastStep != null && s <= lastStep) {
                continue;
            }
            byte[] expected = code(key, s).getBytes(StandardCharsets.US_ASCII);
            if (MessageDigest.isEqual(expected, code.getBytes(StandardCharsets.US_ASCII))) {
                return OptionalLong.of(s);
            }
        }
        return OptionalLong.empty();
    }

    static String code(byte[] key, long step) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] h = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
            int offset = h[h.length - 1] & 0x0f;
            int binary = ((h[offset] & 0x7f) << 24) | ((h[offset + 1] & 0xff) << 16)
                    | ((h[offset + 2] & 0xff) << 8) | (h[offset + 3] & 0xff);
            return String.format("%0" + DIGITS + "d", binary % 1_000_000);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** OTP 앱 등록용 주소 (화면에서 QR 코드로 보여 준다). */
    public static String uri(String issuer, String account, String secret) {
        String label = enc(issuer) + ":" + enc(account);
        return "otpauth://totp/" + label + "?secret=" + secret + "&issuer=" + enc(issuer)
                + "&algorithm=SHA1&digits=" + DIGITS + "&period=" + PERIOD_SECONDS;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }

    static String base32(byte[] data) {
        StringBuilder out = new StringBuilder();
        int buffer = 0;
        int bits = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xff);
            bits += 8;
            while (bits >= 5) {
                out.append(BASE32.charAt((buffer >> (bits - 5)) & 31));
                bits -= 5;
            }
        }
        if (bits > 0) {
            out.append(BASE32.charAt((buffer << (5 - bits)) & 31));
        }
        return out.toString();
    }

    static byte[] unbase32(String s) {
        String clean = s.replace("=", "").replace(" ", "").toUpperCase();
        ByteBuffer out = ByteBuffer.allocate(clean.length() * 5 / 8);
        int buffer = 0;
        int bits = 0;
        for (char c : clean.toCharArray()) {
            int v = BASE32.indexOf(c);
            if (v < 0) {
                throw new IllegalArgumentException("invalid base32");
            }
            buffer = (buffer << 5) | v;
            bits += 5;
            if (bits >= 8) {
                out.put((byte) (buffer >> (bits - 8)));
                bits -= 8;
            }
        }
        return out.array();
    }
}
