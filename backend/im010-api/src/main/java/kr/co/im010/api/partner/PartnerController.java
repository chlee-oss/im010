package kr.co.im010.api.partner;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import kr.co.im010.core.mapper.PartnerMapper;
import kr.co.im010.core.row.PartnerRow;

@RestController
public class PartnerController {

    private final PartnerMapper partnerMapper;

    public PartnerController(PartnerMapper partnerMapper) {
        this.partnerMapper = partnerMapper;
    }

    /** 노출 중인 제휴사와 브랜드 칩 색상. */
    @GetMapping("/api/partners")
    public List<PartnerRow> partners() {
        return partnerMapper.findExposed();
    }
}
