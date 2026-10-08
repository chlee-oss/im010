package kr.co.im010.admin.auth;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

/**
 * 사내 IP 제한 규칙: 한 줄에 IP 하나 또는 CIDR (예: 203.0.113.10, 203.0.113.0/24, 2001:db8::/32).
 */
public final class IpRules {

    private record Rule(byte[] network, int prefix) {
    }

    private final List<Rule> rules;

    private IpRules(List<Rule> rules) {
        this.rules = rules;
    }

    /** 형식이 틀린 줄이 있으면 IllegalArgumentException (메시지 = 틀린 줄). 빈 줄 · # 주석은 건너뛴다. */
    public static IpRules parse(String text) {
        List<Rule> rules = new ArrayList<>();
        if (text != null) {
            for (String raw : text.split("\\R")) {
                String line = raw.strip();
                int hash = line.indexOf('#');
                if (hash >= 0) {
                    line = line.substring(0, hash).strip();
                }
                if (line.isEmpty()) {
                    continue;
                }
                rules.add(rule(line));
            }
        }
        return new IpRules(List.copyOf(rules));
    }

    public boolean isEmpty() {
        return rules.isEmpty();
    }

    public boolean allows(String ip) {
        byte[] addr;
        try {
            addr = literal(ip);
        } catch (IllegalArgumentException e) {
            return false;
        }
        for (Rule r : rules) {
            if (r.network().length == addr.length && matches(r.network(), addr, r.prefix())) {
                return true;
            }
        }
        return false;
    }

    private static Rule rule(String line) {
        String[] parts = line.split("/", 2);
        byte[] network = literal(parts[0]);
        int max = network.length * 8;
        int prefix = max;
        if (parts.length == 2) {
            try {
                prefix = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(line);
            }
            if (prefix < 0 || prefix > max) {
                throw new IllegalArgumentException(line);
            }
        }
        return new Rule(network, prefix);
    }

    /** 숫자 주소만 받는다 (호스트 이름 조회를 하지 않도록) */
    private static byte[] literal(String ip) {
        if (ip == null || ip.isBlank() || !ip.strip().matches("[0-9A-Fa-f:.]+")) {
            throw new IllegalArgumentException(String.valueOf(ip));
        }
        try {
            return InetAddress.getByName(ip.strip()).getAddress();
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException(ip);
        }
    }

    private static boolean matches(byte[] network, byte[] addr, int prefix) {
        int full = prefix / 8;
        for (int i = 0; i < full; i++) {
            if (network[i] != addr[i]) {
                return false;
            }
        }
        int rest = prefix % 8;
        if (rest == 0) {
            return true;
        }
        int mask = 0xff << (8 - rest) & 0xff;
        return (network[full] & mask) == (addr[full] & mask);
    }
}
