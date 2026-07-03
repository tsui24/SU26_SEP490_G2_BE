package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.entity.NewsCategory;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.enums.NewsCategoryStatus;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.repository.NewsCategoryRepository;
import com.capstone.su26_sep490_g2_be.service.NewsCategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NewsCategoryServiceImpl implements NewsCategoryService {

	private final NewsCategoryRepository categoryRepository;

	@Override
	public List<NewsCategory> getAll() {
		return categoryRepository.findAll();
	}

	@Override
	public List<NewsCategory> getActive() {
		return categoryRepository.findByStatus(NewsCategoryStatus.ACTIVE.getValue());
	}

	@Override
	public NewsCategory getById(Long id) {
		return categoryRepository.findById(id)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
	}

	@Override
	public NewsCategory getBySlug(String slug) {
		return categoryRepository.findBySlug(slug)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
	}

	@Override
	@Transactional
	public NewsCategory create(NewsCategory category) {
		if (categoryRepository.existsBySlug(category.getSlug())) {
			throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
		}
		return categoryRepository.save(category);
	}

	@Override
	@Transactional
	public NewsCategory update(Long id, NewsCategory data) {
		NewsCategory existing = getById(id);
		if (!existing.getSlug().equals(data.getSlug()) && categoryRepository.existsBySlug(data.getSlug())) {
			throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
		}
		existing.setName(data.getName());
		existing.setSlug(data.getSlug());
		return categoryRepository.save(existing);
	}

	@Override
	@Transactional
	public void setStatus(Long id, String status) {
		NewsCategory existing = getById(id);
		existing.setStatus(status);
		categoryRepository.save(existing);
	}
}
