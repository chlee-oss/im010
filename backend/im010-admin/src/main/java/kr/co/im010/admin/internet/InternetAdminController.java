package kr.co.im010.admin.internet;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import kr.co.im010.admin.auth.Action;
import kr.co.im010.admin.auth.RequiresPermission;
import kr.co.im010.core.row.InternetPartnerRow;
import kr.co.im010.core.row.InternetProductAdminRow;

/** PA-01 제휴사관리 [인터넷] · PR-02 인터넷관리 */
@RestController
@RequestMapping("/admin/api/internet")
public class InternetAdminController {

    private final InternetAdminService service;

    public InternetAdminController(InternetAdminService service) {
        this.service = service;
    }

    @GetMapping("/partners")
    @RequiresPermission(program = InternetAdminService.PARTNERS, action = Action.VIEW)
    public List<InternetPartnerRow> partners() {
        return service.partners();
    }

    /** PR-02 에서 담당 업체를 고를 때 쓰는 목록 */
    @GetMapping("/partner-options")
    @RequiresPermission(program = InternetAdminService.PRODUCTS, action = Action.VIEW)
    public List<InternetPartnerRow> partnerOptions() {
        return service.partners();
    }

    @PostMapping("/partners")
    @RequiresPermission(program = InternetAdminService.PARTNERS, action = Action.EDIT)
    public InternetAdminService.PartnerResult createPartner(@RequestBody InternetAdminService.PartnerRequest body) {
        return service.createPartner(body);
    }

    @PutMapping("/partners/{id}")
    @RequiresPermission(program = InternetAdminService.PARTNERS, action = Action.EDIT)
    public InternetAdminService.PartnerResult updatePartner(@PathVariable long id,
                                                            @RequestBody InternetAdminService.PartnerRequest body) {
        return service.updatePartner(id, body);
    }

    @GetMapping("/products")
    @RequiresPermission(program = InternetAdminService.PRODUCTS, action = Action.VIEW)
    public List<InternetProductAdminRow> products(@RequestParam(required = false) String type) {
        return service.products(type);
    }

    @PostMapping("/products")
    @RequiresPermission(program = InternetAdminService.PRODUCTS, action = Action.EDIT)
    public InternetProductAdminRow createProduct(@RequestBody InternetAdminService.ProductRequest body) {
        return service.createProduct(body);
    }

    @PutMapping("/products/{id}")
    @RequiresPermission(program = InternetAdminService.PRODUCTS, action = Action.EDIT)
    public InternetProductAdminRow updateProduct(@PathVariable long id, @RequestBody InternetAdminService.ProductRequest body) {
        return service.updateProduct(id, body);
    }
}
