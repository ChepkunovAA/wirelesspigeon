package org.corpsman.forum.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Ответ на запрос лайка/дизлайка.
 */
@Getter
@AllArgsConstructor
public class LikeResponse {

    private final int likes;
    private final boolean liked;
    private final int dislikes;
    private final boolean disliked;
}