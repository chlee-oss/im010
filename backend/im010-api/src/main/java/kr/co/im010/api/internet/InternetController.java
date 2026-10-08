package kr.co.im010.api.internet;

import java.util.List;
import java.util.Locale;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import kr.co.im010.core.mapper.InternetProductMapper;

@RestController
public class InternetController {

    private final InternetProductMapper internetProductMapper;

    public InternetController(InternetProductMapper internetProductMapper) {
        this.internetProductMapper = internetProductMapper;
    }

    /** 인터넷 상품 (별도 등록). type = SINGLE | BUNDLE, 생략하면 전체. */
    @GetMapping("/api/internet-products")
    public List<InternetProduct> products(@RequestParam(required = false) String type) {
        String t = null;
        if (type != null && !type.isBlank()) {
            t = type.trim().toUpperCase(Locale.ROOT);
            if (!t.equals("SINGLE") && !t.equals("BUNDLE")) {
                throw new IllegalArgumentException("type must be SINGLE or BUNDLE");
            }
        }
        return internetProductMapper.findExposed(t).stream().map(InternetProduct::from).toList();
    }
}
