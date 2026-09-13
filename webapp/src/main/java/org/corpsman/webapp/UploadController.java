package org.corpsman.webapp;

import lombok.RequiredArgsConstructor;
import org.corpsman.common.exception.BusinessException;
import org.corpsman.common.exception.ErrorCode;
import org.corpsman.common.service.FileStorageService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Раздаёт загруженные файлы (аватарки и т.п.) по относительному пути.
 */
@Controller
@RequestMapping
@RequiredArgsConstructor
public class UploadController {

    private final FileStorageService fileStorageService;

    /**
     * Отдаёт загруженный файл (аватарки и т.п.) по относительному пути.
     *
     * @param request HTTP-запрос
     * @return файл
     */
    @GetMapping("/uploads/**")
    public ResponseEntity<Resource> serveUpload(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String prefix = "/uploads/";
        int idx = requestUri.indexOf(prefix);
        if (idx < 0) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        }
        String relativePath = requestUri.substring(idx + prefix.length());
        if (relativePath.isBlank() || relativePath.contains("..")) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        }
        Resource resource = new FileSystemResource(fileStorageService.load(relativePath));
        String contentType;
        try {
            contentType = java.nio.file.Files.probeContentType(resource.getFile().toPath());
        } catch (java.io.IOException e) {
            contentType = null;
        }
        if (contentType == null) {
            contentType = "application/octet-stream";
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000")
                .body(resource);
    }
}