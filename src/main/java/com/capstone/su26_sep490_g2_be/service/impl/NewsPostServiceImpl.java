package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.entity.NewsCategory;
import com.capstone.su26_sep490_g2_be.entity.NewsPost;
import com.capstone.su26_sep490_g2_be.entity.NewsTag;
import com.capstone.su26_sep490_g2_be.entity.User;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.repository.NewsCategoryRepository;
import com.capstone.su26_sep490_g2_be.repository.NewsPostRepository;
import com.capstone.su26_sep490_g2_be.repository.NewsTagRepository;
import com.capstone.su26_sep490_g2_be.repository.UserRepository;
import com.capstone.su26_sep490_g2_be.service.NewsPostService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class NewsPostServiceImpl implements NewsPostService {

	private final NewsPostRepository postRepository;
	private final NewsCategoryRepository categoryRepository;
	private final NewsTagRepository tagRepository;
	private final UserRepository userRepository;

	@Override
	@Transactional
	public NewsPost create(NewsPost post, Long authorUserId, Long categoryId, Set<Long> tagIds) {
		if (postRepository.existsBySlug(post.getSlug())) {
			throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
		}
		User author = userRepository.findById(authorUserId)
				.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_USER_NOT_FOUND));
		NewsCategory category = categoryRepository.findById(categoryId)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

		post.setCreatedBy(author);
		post.setCategory(category);
		post.setTags(resolveTags(tagIds));
		post.setStatus("DRAFT");
		return postRepository.save(post);
	}

	@Override
	public NewsPost getById(Long id) {
		return postRepository.findById(id)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
	}

	@Override
	public NewsPost getBySlug(String slug) {
		return postRepository.findBySlug(slug)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
	}

	@Override
	public Page<NewsPost> getPublished(Pageable pageable) {
		return postRepository.findByStatus("PUBLISHED", pageable);
	}

	@Override
	public Page<NewsPost> getPublishedByCategory(Long categoryId, Pageable pageable) {
		return postRepository.findByCategoryIdAndStatus(categoryId, "PUBLISHED", pageable);
	}

	@Override
	public Page<NewsPost> getAll(Pageable pageable) {
		return postRepository.findAll(pageable);
	}

	@Override
	@Transactional
	public NewsPost update(Long id, NewsPost data, Long categoryId, Set<Long> tagIds) {
		NewsPost existing = getById(id);
		if (!existing.getSlug().equals(data.getSlug()) && postRepository.existsBySlug(data.getSlug())) {
			throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
		}
		NewsCategory category = categoryRepository.findById(categoryId)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

		existing.setTitle(data.getTitle());
		existing.setSlug(data.getSlug());
		existing.setContent(data.getContent());
		existing.setThumbnailUrl(data.getThumbnailUrl());
		existing.setCategory(category);
		existing.setTags(resolveTags(tagIds));
		return postRepository.save(existing);
	}

	@Override
	@Transactional
	public void publish(Long id) {
		NewsPost post = getById(id);
		post.setStatus("PUBLISHED");
		post.setPublishedAt(Instant.now());
		postRepository.save(post);
	}

	@Override
	@Transactional
	public void hide(Long id) {
		NewsPost post = getById(id);
		post.setStatus("HIDDEN");
		postRepository.save(post);
	}

	@Override
	@Transactional
	public void delete(Long id) {
		postRepository.delete(getById(id));
	}

	private Set<NewsTag> resolveTags(Set<Long> tagIds) {
		if (tagIds == null || tagIds.isEmpty()) return new HashSet<>();
		return new HashSet<>(tagRepository.findAllById(tagIds));
	}
}
