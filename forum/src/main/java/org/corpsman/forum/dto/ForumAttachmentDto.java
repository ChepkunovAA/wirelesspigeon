package org.corpsman.forum.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * Вложение поста или треда.
 */
@Getter
@Setter
public class ForumAttachmentDto {

    private Long id;

    private String fileName;

    private String contentType;

    private long size;

    public boolean isImage() {
        return contentType != null && contentType.startsWith("image/");
    }

    public boolean isVideo() {
        return contentType != null && contentType.startsWith("video/");
    }
}