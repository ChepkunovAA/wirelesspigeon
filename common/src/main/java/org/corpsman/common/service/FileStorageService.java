package org.corpsman.common.service;

import lombok.extern.slf4j.Slf4j;
import org.corpsman.common.exception.BusinessException;
import org.corpsman.common.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Хранение загруженных файлов на диске.
 */
@Service
@Slf4j
public class FileStorageService {

    private final Path root;

    public FileStorageService(@Value("${app.upload-dir:uploads}") String uploadDir) {
        this.root = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось создать каталог для файлов: " + root, e);
        }
        log.info("Каталог вложений: {}", root);
    }

    /**
     * Сохраняет файл на диск и возвращает уникальное имя на диске.
     *
     * @param file загружаемый файл
     * @return сохранённое имя
     */
    public String store(MultipartFile file) {
        String extension = StringUtils.getFilenameExtension(file.getOriginalFilename());
        String stored = UUID.randomUUID().toString().replace("-", "")
                + (StringUtils.hasText(extension) ? "." + extension : "");
        Path target = root.resolve(stored).normalize();
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("Не удалось сохранить файл {}", stored, e);
            throw new BusinessException(ErrorCode.FILE_STORAGE_ERROR);
        }
        return stored;
    }

    /**
     * Возвращает путь к сохранённому файлу.
     *
     * @param storedName имя файла на диске
     * @return путь
     */
    public Path load(String storedName) {
        Path path = root.resolve(storedName).normalize();
        if (!path.startsWith(root) || !Files.exists(path)) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        }
        return path;
    }

    /**
     * Удаляет файл с диска (если существует).
     *
     * @param storedName имя файла на диске
     */
    public void delete(String storedName) {
        Path path = root.resolve(storedName).normalize();
        if (!path.startsWith(root)) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.error("Не удалось удалить файл {}", storedName, e);
        }
    }
}