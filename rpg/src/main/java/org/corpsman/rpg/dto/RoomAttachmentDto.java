package org.corpsman.rpg.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * Вложение поста комнаты.
 */
@Getter
@Setter
public class RoomAttachmentDto {

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