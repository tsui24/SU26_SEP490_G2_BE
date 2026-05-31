package com.capstone.su26_sep490_g2_be.service;

import com.capstone.su26_sep490_g2_be.entity.NewsPost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Set;

public interface NewsPostService {

	NewsPost create(NewsPost post, Long authorUserId, Long categoryId, Set<Long> tagIds);

	NewsPost getById(Long id);

	NewsPost getBySlug(String slug);

	Page<NewsPost> getPublished(Pageable pageable);

	Page<NewsPost> getPublishedByCategory(Long categoryId, Pageable pageable);

	Page<NewsPost> getAll(Pageable pageable);

	NewsPost update(Long id, NewsPost post, Long categoryId, Set<Long> tagIds);

	void publish(Long id);

	void hide(Long id);

	void delete(Long id);
}
