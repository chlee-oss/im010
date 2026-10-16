package kr.co.im010.admin.ai;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import kr.co.im010.admin.audit.AuditService;
import kr.co.im010.admin.web.ApiException;
import kr.co.im010.core.content.UploadProperties;

/**
 * 이미지 업로드 (알뜰AI 썸네일 · 본문). 파일 앞부분(시그니처)으로 형식을 확인하고 jpg · png · webp · gif 만 받는다
 * (SVG 는 스크립트를 담을 수 있어 받지 않음). 저장 이름은 무작위라 원래 파일 이름은 쓰지 않는다.
 * 저장 위치: {im010.upload.dir}/ai/yyyy/MM/{uuid}.{ext} → 주소 /uploads/ai/yyyy/MM/{uuid}.{ext}
 */
@Service
public class UploadService {

    private final UploadProperties props;
    private final AuditService audit;

    public UploadService(UploadProperties props, AuditService audit) {
        this.props = props;
        this.audit = audit;
    }

    public String saveImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("파일을 골라 주세요");
        }
        if (file.getSize() > props.maxBytes()) {
            throw ApiException.badRequest("이미지는 " + props.maxBytes() / 1024 / 1024 + "MB 이하여야 합니다");
        }
        byte[] head = new byte[12];
        try (InputStream in = file.getInputStream()) {
            int n = in.readNBytes(head, 0, head.length);
            String ext = imageType(head, n);
            if (ext == null) {
                throw ApiException.badRequest("jpg · png · webp · gif 이미지만 올릴 수 있습니다");
            }
            LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
            String rel = "ai/%d/%02d/%s.%s".formatted(today.getYear(), today.getMonthValue(), UUID.randomUUID(), ext);
            Path target = Path.of(props.dir()).toAbsolutePath().normalize().resolve(rel);
            Files.createDirectories(target.getParent());
            file.transferTo(target);
            audit.action(AiPostAdminService.PROGRAM, "AI_IMAGE_UPLOAD", "/uploads/" + rel, file.getSize() + " bytes");
            return "/uploads/" + rel;
        } catch (IOException e) {
            throw ApiException.badRequest("파일을 저장하지 못했습니다");
        }
    }

    /** 파일 시그니처로 형식 판단. 모르는 형식이면 null */
    static String imageType(byte[] h, int n) {
        if (n >= 3 && (h[0] & 0xff) == 0xFF && (h[1] & 0xff) == 0xD8 && (h[2] & 0xff) == 0xFF) {
            return "jpg";
        }
        if (n >= 8 && (h[0] & 0xff) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G') {
            return "png";
        }
        if (n >= 6 && h[0] == 'G' && h[1] == 'I' && h[2] == 'F' && h[3] == '8') {
            return "gif";
        }
        if (n >= 12 && h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F' && h[8] == 'W' && h[9] == 'E' && h[10] == 'B'
                && h[11] == 'P') {
            return "webp";
        }
        return null;
    }
}
