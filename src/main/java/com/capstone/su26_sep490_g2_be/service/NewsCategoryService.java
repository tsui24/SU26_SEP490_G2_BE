package com.capstone.su26_sep490_g2_be.service;

import com.capstone.su26_sep490_g2_be.entity.NewsCategory;

import java.util.List;

public interface NewsCategoryService {

	List<NewsCategory> getAll();

	List<NewsCategory> getActive();

	NewsCategory getById(Long id);

	NewsCategory getBySlug(String slug);

	NewsCategory create(NewsCategory category);

	NewsCategory update(Long id, NewsCategory category);

	void setStatus(Long id, String status);
}
