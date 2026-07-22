package com.capstone.su26_sep490_g2_be.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "news_posts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NewsPost extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "category_id", nullable = false)
	private NewsCategory category;

	@Column(nullable = false)
	private String title;

	@Column(nullable = false, unique = true)
	private String slug;

	@Column(name = "thumbnail_url", length = 1000)
	private String thumbnailUrl;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String content;

	@Column(length = 30, nullable = false)
	@Builder.Default
	private String status = "DRAFT";

	@Column(name = "published_at")
	private Instant publishedAt;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "created_by", nullable = false)
	private User createdBy;

	@ManyToMany(fetch = FetchType.LAZY)
	@JoinTable(
			name = "news_post_tags",
			joinColumns = @JoinColumn(name = "post_id"),
			inverseJoinColumns = @JoinColumn(name = "tag_id")
	)
	@Builder.Default
	private Set<NewsTag> tags = new HashSet<>();

	@Column(nullable = false)
	@ColumnDefault("false")
	@Builder.Default
	private boolean deleted = false;
}
