package org.corpsman.forum.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.corpsman.common.model.BaseEntity;

import java.util.HashSet;
import java.util.Set;

/**
 * Тег треда форума.
 */
@Entity
@Table(name = "forum_tags")
@Getter
@Setter
public class ForumTag extends BaseEntity {

    @Column(unique = true, nullable = false, length = 50)
    private String name;

    @ManyToMany(mappedBy = "tags")
    private Set<ForumTopic> topics = new HashSet<>();
}