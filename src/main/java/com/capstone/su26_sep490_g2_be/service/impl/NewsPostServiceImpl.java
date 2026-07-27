package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.entity.NewsCategory;
import com.capstone.su26_sep490_g2_be.entity.NewsPost;
import com.capstone.su26_sep490_g2_be.entity.NewsTag;
import com.capstone.su26_sep490_g2_be.entity.User;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.enums.NewsPostStatus;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.repository.NewsCategoryRepository;
import com.capstone.su26_sep490_g2_be.repository.NewsPostRepository;
import com.capstone.su26_sep490_g2_be.repository.NewsTagRepository;
import com.capstone.su26_sep490_g2_be.repository.UserRepository;
import com.capstone.su26_sep490_g2_be.service.NewsPostService;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.owasp.html.PolicyFactory;
import org.owasp.html.Sanitizers;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class NewsPostServiceImpl implements NewsPostService {

	/** Bài viết được viết bởi Owner/Manager (đáng tin hơn user-input thường) nhưng vẫn phải chặn script/onXxx/embed. */
	private static final PolicyFactory CONTENT_HTML_POLICY = Sanitizers.FORMATTING
			.and(Sanitizers.BLOCKS)
			.and(Sanitizers.LINKS)
			.and(Sanitizers.IMAGES)
			.and(Sanitizers.TABLES)
			.and(Sanitizers.STYLES);

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
		post.setContent(CONTENT_HTML_POLICY.sanitize(post.getContent()));
		post.setTags(resolveTags(tagIds));
		post.setStatus(NewsPostStatus.DRAFT.getValue());
		return postRepository.save(post);
	}

	@Override
	@Transactional(readOnly = true)
	public NewsPost getById(Long id, Long actingUserId) {
		// News là nội dung chung của cả chuỗi (1 chuỗi duy nhất, không phải nhiều chuỗi độc lập)
		// nên mọi Owner/Manager đều thao tác được, không giới hạn theo người tạo/chi nhánh.
		return getByIdInternal(id);
	}

	private NewsPost getByIdInternal(Long id) {
		NewsPost post = postRepository.findById(id)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
		if (post.isDeleted()) {
			throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
		}
		initializeAssociations(post);
		return post;
	}

	@Override
	@Transactional(readOnly = true)
	public NewsPost getBySlug(String slug) {
		NewsPost post = postRepository.findBySlug(slug)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
		if (post.isDeleted() || !NewsPostStatus.PUBLISHED.getValue().equals(post.getStatus())) {
			// Bài DRAFT/HIDDEN không được lộ qua endpoint public dù biết đúng slug.
			throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
		}
		initializeAssociations(post);
		return post;
	}

	@Override
	@Transactional(readOnly = true)
	public Page<NewsPost> getPublished(String search, Long categoryId, Pageable pageable) {
		String searchParam = (search == null || search.isBlank()) ? null : search.trim().toLowerCase();
		Specification<NewsPost> spec = (root, query, cb) -> {
			List<Predicate> predicates = new ArrayList<>();
			predicates.add(cb.equal(root.get("status"), NewsPostStatus.PUBLISHED.getValue()));
			predicates.add(cb.isFalse(root.get("deleted")));
			if (categoryId != null) {
				predicates.add(cb.equal(root.get("category").get("id"), categoryId));
			}
			if (searchParam != null) {
				String like = "%" + searchParam + "%";
				predicates.add(cb.or(
						cb.like(cb.lower(root.get("title")), like),
						cb.like(cb.lower(root.get("content")), like)));
			}
			return cb.and(predicates.toArray(new Predicate[0]));
		};
		Page<NewsPost> page = postRepository.findAll(spec, pageable);
		page.getContent().forEach(this::initializeAssociations);
		return page;
	}

	@Override
	@Transactional(readOnly = true)
	public Page<NewsPost> getAll(Long actingUserId, Pageable pageable) {
		Page<NewsPost> page = postRepository.findByDeletedFalse(pageable);
		page.getContent().forEach(this::initializeAssociations);
		return page;
	}

	@Override
	@Transactional
	public NewsPost update(Long id, Long actingUserId, NewsPost data, Long categoryId, Set<Long> tagIds) {
		NewsPost existing = getByIdInternal(id);
		if (!existing.getSlug().equals(data.getSlug()) && postRepository.existsBySlug(data.getSlug())) {
			throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
		}
		NewsCategory category = categoryRepository.findById(categoryId)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

		existing.setTitle(data.getTitle());
		existing.setSlug(data.getSlug());
		existing.setContent(CONTENT_HTML_POLICY.sanitize(data.getContent()));
		existing.setThumbnailUrl(data.getThumbnailUrl());
		existing.setCategory(category);
		existing.setTags(resolveTags(tagIds));
		return postRepository.save(existing);
	}

	@Override
	@Transactional
	public void publish(Long id, Long actingUserId) {
		NewsPost post = getByIdInternal(id);
		post.setStatus(NewsPostStatus.PUBLISHED.getValue());
		post.setPublishedAt(Instant.now());
		postRepository.save(post);
	}

	@Override
	@Transactional
	public void hide(Long id, Long actingUserId) {
		NewsPost post = getByIdInternal(id);
		post.setStatus(NewsPostStatus.HIDDEN.getValue());
		postRepository.save(post);
	}

	@Override
	@Transactional
	public void delete(Long id, Long actingUserId) {
		NewsPost post = getByIdInternal(id);
		post.setDeleted(true);
		postRepository.save(post);
	}

	private void initializeAssociations(NewsPost post) {
		Hibernate.initialize(post.getCategory());
		Hibernate.initialize(post.getTags());
	}

	private Set<NewsTag> resolveTags(Set<Long> tagIds) {
		if (tagIds == null || tagIds.isEmpty()) return new HashSet<>();
		return new HashSet<>(tagRepository.findAllById(tagIds));
	}
}
