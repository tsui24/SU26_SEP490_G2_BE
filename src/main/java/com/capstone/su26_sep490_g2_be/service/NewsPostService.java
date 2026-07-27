package com.capstone.su26_sep490_g2_be.service;

import com.capstone.su26_sep490_g2_be.entity.NewsPost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Set;

public interface NewsPostService {

	NewsPost create(NewsPost post, Long authorUserId, Long categoryId, Set<Long> tagIds);

	/** CMS (Owner/Manager) — chỉ xem được bài viết trong cùng chuỗi (Owner của mình / chuỗi mình thuộc về). */
	NewsPost getById(Long id, Long actingUserId);

	NewsPost getBySlug(String slug);

	Page<NewsPost> getPublished(String search, Long categoryId, Pageable pageable);

	/** CMS (Owner/Manager) — chỉ liệt kê bài viết trong cùng chuỗi. */
	Page<NewsPost> getAll(Long actingUserId, Pageable pageable);

	NewsPost update(Long id, Long actingUserId, NewsPost post, Long categoryId, Set<Long> tagIds);

	void publish(Long id, Long actingUserId);

	void hide(Long id, Long actingUserId);

	void delete(Long id, Long actingUserId);
}
