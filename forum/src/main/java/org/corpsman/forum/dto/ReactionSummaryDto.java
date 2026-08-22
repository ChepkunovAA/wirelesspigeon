package org.corpsman.forum.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Сводка реакций одной стороны на контент другой.
 */
@Getter
@AllArgsConstructor
public class ReactionSummaryDto {

    private final long likes;

    private final long dislikes;

    /**
     * Итог: лайки минус дизлайки.
     *
     * @return итог
     */
    public long getTotal() {
        return likes - dislikes;
    }
}
