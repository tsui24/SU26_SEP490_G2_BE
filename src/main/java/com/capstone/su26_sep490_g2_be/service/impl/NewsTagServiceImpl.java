package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.entity.NewsPost;
import com.capstone.su26_sep490_g2_be.entity.NewsTag;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.repository.NewsPostRepository;
import com.capstone.su26_sep490_g2_be.repository.NewsTagRepository;
import com.capstone.su26_sep490_g2_be.service.NewsTagService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NewsTagServiceImpl implements NewsTagService {

	private final NewsTagRepository tagRepository;
	private final NewsPostRepository postRepository;

	@Override
	public List<NewsTag> getAll() {
		return tagRepository.findAll();
	}

	@Override
	public NewsTag getById(Long id) {
		return tagRepository.findById(id)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
	}

	@Override
	public NewsTag getBySlug(String slug) {
		return tagRepository.findBySlug(slug)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
	}

	@Override
	@Transactional
	public NewsTag create(NewsTag tag) {
		if (tagRepository.existsBySlug(tag.getSlug())) {
			throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
		}
		return tagRepository.save(tag);
	}

	@Override
	@Transactional
	public NewsTag update(Long id, NewsTag data) {
		NewsTag existing = getById(id);
		if (!existing.getSlug().equals(data.getSlug()) && tagRepository.existsBySlug(data.getSlug())) {
			throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
		}
		existing.setName(data.getName());
		existing.setSlug(data.getSlug());
		return tagRepository.save(existing);
	}

	@Override
	@Transactional
	public void delete(Long id) {
		NewsTag tag = getById(id);
		for (NewsPost post : postRepository.findByTags_Id(id)) {
			post.getTags().remove(tag);
			postRepository.save(post);
		}
		tagRepository.delete(tag);
	}
}
