package org.corpsman.forum.service;

import org.corpsman.forum.dto.ForumRatingDto;
import org.corpsman.forum.repository.ForumPostRepository;
import org.corpsman.forum.repository.ForumTopicRepository;
import org.corpsman.forum.repository.PostDislikeRepository;
import org.corpsman.forum.repository.PostLikeRepository;
import org.corpsman.forum.repository.TopicDislikeRepository;
import org.corpsman.forum.repository.TopicLikeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RatingStatsServiceTest {

    @Mock
    private ForumTopicRepository topicRepository;

    @Mock
    private ForumPostRepository postRepository;

    @Mock
    private TopicLikeRepository topicLikeRepository;

    @Mock
    private TopicDislikeRepository topicDislikeRepository;

    @Mock
    private PostLikeRepository postLikeRepository;

    @Mock
    private PostDislikeRepository postDislikeRepository;

    @InjectMocks
    private RatingStatsService ratingStatsService;

    @Test
    void aggregatesReceivedAndGivenReactions() {
        long userId = 5L;
        when(topicRepository.sumLikesByAuthor(userId)).thenReturn(7L);
        when(topicRepository.sumDislikesByAuthor(userId)).thenReturn(2L);
        when(postRepository.sumLikesByAuthor(userId)).thenReturn(3L);
        when(postRepository.sumDislikesByAuthor(userId)).thenReturn(4L);
        when(topicLikeRepository.countByUserId(userId)).thenReturn(10L);
        when(topicDislikeRepository.countByUserId(userId)).thenReturn(1L);
        when(postLikeRepository.countByUserId(userId)).thenReturn(5L);
        when(postDislikeRepository.countByUserId(userId)).thenReturn(6L);

        ForumRatingDto dto = ratingStatsService.statsFor(userId);

        assertThat(dto.getReceivedLikes()).isEqualTo(10L);
        assertThat(dto.getReceivedDislikes()).isEqualTo(6L);
        assertThat(dto.getReceivedTotal()).isEqualTo(4L);
        assertThat(dto.getGivenLikes()).isEqualTo(15L);
        assertThat(dto.getGivenDislikes()).isEqualTo(7L);
        assertThat(dto.getGivenTotal()).isEqualTo(8L);
    }

    @Test
    void pairwiseCountsReactionsBetweenTwoUsers() {
        long viewerId = 1L;
        long targetId = 2L;
        when(topicRepository.findIdsByAuthor(targetId)).thenReturn(List.of(10L));
        when(postRepository.findIdsByAuthor(targetId)).thenReturn(List.of(20L, 21L));
        when(topicLikeRepository.countByUserIdAndTopicIdIn(viewerId, List.of(10L))).thenReturn(2L);
        when(topicDislikeRepository.countByUserIdAndTopicIdIn(viewerId, List.of(10L))).thenReturn(0L);
        when(postLikeRepository.countByUserIdAndPostIdIn(viewerId, List.of(20L, 21L))).thenReturn(1L);
        when(postDislikeRepository.countByUserIdAndPostIdIn(viewerId, List.of(20L, 21L))).thenReturn(3L);

        var dto = ratingStatsService.pairwise(viewerId, targetId);

        assertThat(dto.getLikes()).isEqualTo(3L);
        assertThat(dto.getDislikes()).isEqualTo(3L);
    }
}
