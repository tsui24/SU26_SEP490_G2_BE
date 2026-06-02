package com.capstone.su26_sep490_g2_be.util;

import lombok.experimental.UtilityClass;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@UtilityClass
public class PageableUtil {

	public static Pageable create(int page, int size, String sortProperty) {
		int safePage = Math.max(page, 0);
		int safeSize = Math.min(Math.max(size, 1), 100);
		return PageRequest.of(safePage, safeSize, Sort.by(sortProperty).ascending());
	}
}
