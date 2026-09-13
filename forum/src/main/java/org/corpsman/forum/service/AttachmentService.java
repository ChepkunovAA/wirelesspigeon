package org.corpsman.forum.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.corpsman.common.exception.BusinessException;
import org.corpsman.common.exception.ErrorCode;
import org.corpsman.common.service.FileStorageService;
import org.corpsman.forum.dto.ForumAttachmentDto;
import org.corpsman.forum.model.ForumAttachment;
import org.corpsman.forum.repository.ForumAttachmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Вложения форума: сохранение, чтение и преобразование в DTO.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AttachmentService {

    private final ForumAttachmentRepository attachmentRepository;
    private final FileStorageService storage;

    /**
     * Прикрепляет файлы к комментарию.
     *
     * @param files  загруженные файлы (могут быть пустыми)
     * @param postId идентификатор комментария
     */
    @Transactional
    public void attachToPost(List<MultipartFile> files, Long postId) {
        saveAll(files, postId, null);
    }

    /**
     * Прикрепляет файлы к стартовому посту треда.
     *
     * @param files   загруженные файлы (могут быть пустыми)
     * @param topicId идентификатор треда
     */
    @Transactional
    public void attachToTopic(List<MultipartFile> files, Long topicId) {
        saveAll(files, null, topicId);
    }

    /**
     * Возвращает вложение по идентификатору.
     *
     * @param id идентификатор вложения
     * @return вложение
     */
    @Transactional(readOnly = true)
    public ForumAttachment getAttachment(Long id) {
        return attachmentRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND));
    }

    /**
     * Возвращает вложения всех указанных комментариев.
     *
     * @param postIds идентификаторы комментариев
     * @return карта postId -&gt; список вложений
     */
    @Transactional(readOnly = true)
    public Map<Long, List<ForumAttachmentDto>> byPostIds(List<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return Map.of();
        }
        return attachmentRepository.findByPostIdIn(postIds).stream()
                .collect(Collectors.groupingBy(
                        ForumAttachment::getPostId,
                        Collectors.mapping(this::toDto, Collectors.toList())));
    }

    /**
     * Возвращает вложения стартового поста треда.
     *
     * @param topicId идентификатор треда
     * @return список вложений
     */
    @Transactional(readOnly = true)
    public List<ForumAttachmentDto> forTopic(Long topicId) {
        return attachmentRepository.findByTopicIdOrderByIdAsc(topicId).stream()
                .map(this::toDto)
                .toList();
    }

    /**
     * Полностью удаляет вложения поста (без потомков).
     *
     * @param postId идентификатор поста
     */
    @Transactional
    public void deleteAttachmentsForPost(Long postId) {
        List<ForumAttachment> attachments = attachmentRepository.findByPostIdOrderByIdAsc(postId);
        for (ForumAttachment a : attachments) {
            try {
                storage.delete(a.getStoredName());
            } catch (Exception e) {
                log.warn("Не удалось удалить файл {} на диске", a.getStoredName(), e);
            }
        }
        attachmentRepository.deleteByPostId(postId);
        log.info("Удалены вложения поста {}", postId);
    }

    private void saveAll(List<MultipartFile> files, Long postId, Long topicId) {
        if (files == null || files.isEmpty()) {
            return;
        }
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            ForumAttachment attachment = new ForumAttachment();
            attachment.setPostId(postId);
            attachment.setTopicId(topicId);
            attachment.setFileName(StringUtils.hasText(file.getOriginalFilename())
                    ? file.getOriginalFilename()
                    : "file");
            attachment.setContentType(StringUtils.hasText(file.getContentType())
                    ? file.getContentType()
                    : "application/octet-stream");
            attachment.setSize(file.getSize());
            attachment.setStoredName(storage.store(file));
            attachmentRepository.save(attachment);
        }
    }

    private ForumAttachmentDto toDto(ForumAttachment attachment) {
        ForumAttachmentDto dto = new ForumAttachmentDto();
        dto.setId(attachment.getId());
        dto.setFileName(attachment.getFileName());
        dto.setContentType(attachment.getContentType());
        dto.setSize(attachment.getSize());
        return dto;
    }
}