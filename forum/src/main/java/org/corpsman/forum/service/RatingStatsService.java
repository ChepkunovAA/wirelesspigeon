package org.corpsman.forum.service;

import lombok.RequiredArgsConstructor;
import org.corpsman.forum.dto.ForumRatingDto;
import org.corpsman.forum.dto.ReactionSummaryDto;
import org.corpsman.forum.repository.ForumPostRepository;
import org.corpsman.forum.repository.ForumTopicRepository;
import org.corpsman.forum.repository.PostDislikeRepository;
import org.corpsman.forum.repository.PostLikeRepository;
import org.corpsman.forum.repository.TopicDislikeRepository;
import org.corpsman.forum.repository.TopicLikeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Сервис агрегированных рейтингов пользователей по реакциям форума.
 */
@Service
@RequiredArgsConstructor
public class RatingStatsService {

    private final ForumTopicRepository topicRepository;
    private final ForumPostRepository postRepository;
    private final TopicLikeRepository topicLikeRepository;
    private final TopicDislikeRepository topicDislikeRepository;
    private final PostLikeRepository postLikeRepository;
    private final PostDislikeRepository postDislikeRepository;

    /**
     * Возвращает рейтинг пользователя: реакции на его контенте (полученные)
     * и поставленные им другим участникам (отданные).
     *
     * @param userId идентификатор пользователя
     * @return агрегированный рейтинг
     */
    @Transactional(readOnly = true)
    public ForumRatingDto statsFor(Long userId) {
        long receivedLikes = topicRepository.sumLikesByAuthor(userId)
                + postRepository.sumLikesByAuthor(userId);
        long receivedDislikes = topicRepository.sumDislikesByAuthor(userId)
                + postRepository.sumDislikesByAuthor(userId);
        long givenLikes = topicLikeRepository.countByUserId(userId)
                + postLikeRepository.countByUserId(userId);
        long givenDislikes = topicDislikeRepository.countByUserId(userId)
                + postDislikeRepository.countByUserId(userId);
        return new ForumRatingDto(receivedLikes, receivedDislikes, givenLikes, givenDislikes);
    }

    /**
     * Реакции одного пользователя на контент другого (треды и комментарии).
     *
     * @param fromUserId кто ставил реакции
     * @param toUserId   чей контент оценивался
     * @return сводка лайков и дизлайков
     */
    @Transactional(readOnly = true)
    public ReactionSummaryDto pairwise(Long fromUserId, Long toUserId) {
        List<Long> topicIds = topicRepository.findIdsByAuthor(toUserId);
        List<Long> postIds = postRepository.findIdsByAuthor(toUserId);
        long likes = topicLikeRepository.countByUserIdAndTopicIdIn(fromUserId, topicIds)
                + postLikeRepository.countByUserIdAndPostIdIn(fromUserId, postIds);
        long dislikes = topicDislikeRepository.countByUserIdAndTopicIdIn(fromUserId, topicIds)
                + postDislikeRepository.countByUserIdAndPostIdIn(fromUserId, postIds);
        return new ReactionSummaryDto(likes, dislikes);
    }
}
