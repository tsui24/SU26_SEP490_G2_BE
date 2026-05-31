package com.capstone.su26_sep490_g2_be.service;

import com.capstone.su26_sep490_g2_be.entity.NewsTag;

import java.util.List;

public interface NewsTagService {

	List<NewsTag> getAll();

	NewsTag getById(Long id);

	NewsTag getBySlug(String slug);

	NewsTag create(NewsTag tag);

	NewsTag update(Long id, NewsTag tag);

	void delete(Long id);
}
