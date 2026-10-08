package kr.co.im010.api.plan;

import java.util.Locale;

/** 통신망. DB 코드는 SKT / KT / LGU, 화면 표기는 SKT / KT / LGU+. */
public enum Network {
    SKT("SKT"), KT("KT"), LGU("LGU+");

    private final String label;

    Network(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** "skt", "LGU", "lgu+" 등을 받아 들인다. 모르는 값이면 IllegalArgumentException. */
    public static Network parse(String value) {
        if (value == null) {
            throw new IllegalArgumentException("network is required");
        }
        String v = value.trim().toUpperCase(Locale.ROOT).replace("+", "");
        return Network.valueOf(v);
    }

    public static String labelOf(String code) {
        return code == null ? null : parse(code).label();
    }
}
