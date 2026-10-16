package kr.co.im010.admin.ai;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import kr.co.im010.admin.auth.Action;
import kr.co.im010.admin.auth.RequiresPermission;
import kr.co.im010.core.row.AiCategoryRow;

/** 콘텐츠관리: CT-01 알뜰AI 게시글관리 · CT-02 알뜰AI 카테고리관리 · 이미지 업로드 */
@RestController
@RequestMapping("/admin/api/contents")
public class AiAdminController {

    public record PublishRequest(OffsetDateTime publishAt) {
    }

    public record StatusRequest(String status) {
    }

    public record PreviewRequest(String bodyMd) {
    }

    private static final String CT01 = AiPostAdminService.PROGRAM;
    private static final String CT02 = AiCategoryAdminService.PROGRAM;

    private final AiPostAdminService posts;
    private final AiCategoryAdminService categories;
    private final UploadService uploads;

    public AiAdminController(AiPostAdminService posts, AiCategoryAdminService categories, UploadService uploads) {
        this.posts = posts;
        this.categories = categories;
        this.uploads = uploads;
    }

    @GetMapping("/posts")
    @RequiresPermission(program = CT01, action = Action.VIEW)
    public AiPostAdminService.Page list(@RequestParam(required = false) String category,
                                        @RequestParam(required = false) String state,
                                        @RequestParam(required = false) String q,
                                        @RequestParam(defaultValue = "1") int page) {
        return posts.list(category, state, q, page);
    }

    @GetMapping("/posts/{id}")
    @RequiresPermission(program = CT01, action = Action.VIEW)
    public AiPostAdminService.Detail detail(@PathVariable long id) {
        return posts.detail(id);
    }

    @PostMapping("/posts")
    @RequiresPermission(program = CT01, action = Action.EDIT)
    public AiPostAdminService.Detail create(@RequestBody AiPostAdminService.PostRequest body) {
        return posts.create(body);
    }

    @PutMapping("/posts/{id}")
    @RequiresPermission(program = CT01, action = Action.EDIT)
    public AiPostAdminService.Detail update(@PathVariable long id, @RequestBody AiPostAdminService.PostRequest body) {
        return posts.update(id, body);
    }

    @PostMapping("/posts/{id}/publish")
    @RequiresPermission(program = CT01, action = Action.APPROVE)
    public AiPostAdminService.Detail publish(@PathVariable long id, @RequestBody PublishRequest body) {
        return posts.publish(id, body.publishAt());
    }

    @PostMapping("/posts/{id}/status")
    @RequiresPermission(program = CT01, action = Action.APPROVE)
    public AiPostAdminService.Detail status(@PathVariable long id, @RequestBody StatusRequest body) {
        return posts.setStatus(id, body.status());
    }

    @DeleteMapping("/posts/{id}")
    @RequiresPermission(program = CT01, action = Action.DELETE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        posts.delete(id);
    }

    @PostMapping("/posts/preview")
    @RequiresPermission(program = CT01, action = Action.EDIT)
    public Map<String, String> preview(@RequestBody PreviewRequest body) {
        return Map.of("html", posts.preview(body.bodyMd()));
    }

    @GetMapping("/related-options")
    @RequiresPermission(program = CT01, action = Action.VIEW)
    public List<AiPostAdminService.RelatedOption> related(@RequestParam(required = false) String q) {
        return posts.relatedOptions(q);
    }

    @PostMapping("/uploads")
    @RequiresPermission(program = CT01, action = Action.EDIT)
    public Map<String, String> upload(@RequestPart("file") MultipartFile file) {
        return Map.of("url", uploads.saveImage(file));
    }

    @GetMapping("/categories")
    @RequiresPermission(program = CT01, action = Action.VIEW)   // 글쓰기 화면의 카테고리 선택에도 쓴다
    public List<AiCategoryRow> categories() {
        return categories.list();
    }

    @PostMapping("/categories")
    @RequiresPermission(program = CT02, action = Action.EDIT)
    public List<AiCategoryRow> createCategory(@RequestBody AiCategoryAdminService.CategoryRequest body) {
        return categories.create(body);
    }

    @PutMapping("/categories/{code}")
    @RequiresPermission(program = CT02, action = Action.EDIT)
    public List<AiCategoryRow> updateCategory(@PathVariable String code, @RequestBody AiCategoryAdminService.CategoryRequest body) {
        return categories.update(code, body);
    }
}
