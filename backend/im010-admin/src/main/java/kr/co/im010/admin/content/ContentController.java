package kr.co.im010.admin.content;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import kr.co.im010.admin.auth.Action;
import kr.co.im010.admin.auth.RequiresPermission;
import kr.co.im010.core.row.FaqRow;
import kr.co.im010.core.row.FooterRow;
import kr.co.im010.core.row.TermsRow;

/** ST-04 약관관리 · ST-06 Footer관리 · ST-07 1:1문의관리 [FAQ] */
@RestController
@RequestMapping("/admin/api/settings")
public class ContentController {

    private final ContentService service;

    public ContentController(ContentService service) {
        this.service = service;
    }

    @GetMapping("/terms/{type}")
    @RequiresPermission(program = ContentService.TERMS, action = Action.VIEW)
    public List<TermsRow> terms(@PathVariable String type) {
        return service.terms(type);
    }

    @GetMapping("/terms/item/{id}")
    @RequiresPermission(program = ContentService.TERMS, action = Action.VIEW)
    public TermsRow term(@PathVariable long id) {
        return service.term(id);
    }

    @PostMapping("/terms/{type}")
    @RequiresPermission(program = ContentService.TERMS, action = Action.EDIT)
    public TermsRow createTerm(@PathVariable String type, @RequestBody ContentService.TermsRequest body) {
        return service.createTerm(type, body);
    }

    @PutMapping("/terms/item/{id}")
    @RequiresPermission(program = ContentService.TERMS, action = Action.EDIT)
    public TermsRow updateTerm(@PathVariable long id, @RequestBody ContentService.TermsRequest body) {
        return service.updateTerm(id, body);
    }

    @DeleteMapping("/terms/item/{id}")
    @RequiresPermission(program = ContentService.TERMS, action = Action.EDIT)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTerm(@PathVariable long id) {
        service.deleteTerm(id);
    }

    @PostMapping("/terms/item/{id}/publish")
    @RequiresPermission(program = ContentService.TERMS, action = Action.EDIT)
    public TermsRow publishTerm(@PathVariable long id) {
        return service.publishTerm(id);
    }

    @GetMapping("/footer")
    @RequiresPermission(program = ContentService.FOOTER, action = Action.VIEW)
    public List<FooterRow> footer() {
        return service.footerHistory();
    }

    @PostMapping("/footer")
    @RequiresPermission(program = ContentService.FOOTER, action = Action.EDIT)
    public List<FooterRow> saveFooter(@RequestBody ContentService.FooterRequest body) {
        return service.saveFooter(body);
    }

    @GetMapping("/faqs")
    @RequiresPermission(program = ContentService.FAQ, action = Action.VIEW)
    public List<FaqRow> faqs() {
        return service.faqs();
    }

    @PostMapping("/faqs")
    @RequiresPermission(program = ContentService.FAQ, action = Action.EDIT)
    public FaqRow createFaq(@RequestBody ContentService.FaqRequest body) {
        return service.createFaq(body);
    }

    @PutMapping("/faqs/{id}")
    @RequiresPermission(program = ContentService.FAQ, action = Action.EDIT)
    public FaqRow updateFaq(@PathVariable long id, @RequestBody ContentService.FaqRequest body) {
        return service.updateFaq(id, body);
    }

    @DeleteMapping("/faqs/{id}")
    @RequiresPermission(program = ContentService.FAQ, action = Action.EDIT)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFaq(@PathVariable long id) {
        service.deleteFaq(id);
    }
}
