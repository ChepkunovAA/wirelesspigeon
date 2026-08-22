package org.corpsman.forum.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Рейтинг пользователя форума по реакциям.
 */
@Getter
@AllArgsConstructor
public class ForumRatingDto {

    private final long receivedLikes;

    private final long receivedDislikes;

    private final long givenLikes;

    private final long givenDislikes;

    /**
     * Совокупный рейтинг: полученные лайки минус полученные дизлайки.
     *
     * @return совокупный рейтинг
     */
    public long getReceivedTotal() {
        return receivedLikes - receivedDislikes;
    }

    /**
     * Относительный отданный рейтинг: поставленные лайки минус дизлайки.
     *
     * @return отданный рейтинг
     */
    public long getGivenTotal() {
        return givenLikes - givenDislikes;
    }
}
