package com.capstone.su26_sep490_g2_be.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "facebook_posts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FacebookPost extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "tournament_id")
	private Tournament tournament;

	@Column(name = "facebook_post_id", length = 100, nullable = false)
	private String facebookPostId;

	@Column(columnDefinition = "TEXT")
	private String content;

	@Column(name = "post_type", length = 30, nullable = false)
	private String postType;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "posted_by")
	private User postedBy;

	@Column(name = "posted_at", nullable = false)
	private Instant postedAt;

	@Column(name = "permalink_url", length = 1000)
	private String permalinkUrl;

	@Column(name = "full_picture_url", length = 2000)
	private String fullPictureUrl;

	/** Cache tương tác từ Facebook — list không cần gọi Graph API realtime */
	@Column(name = "likes_count")
	private Integer likesCount;

	@Column(name = "comments_count")
	private Integer commentsCount;

	@Column(name = "shares_count")
	private Integer sharesCount;

	@Column(name = "impressions")
	private Integer impressions;

	@Column(name = "reach")
	private Integer reach;

	@Column(name = "engaged_users")
	private Integer engagedUsers;

	@Column(name = "stats_synced_at")
	private Instant statsSyncedAt;
}
