package com.capstone.su26_sep490_g2_be.service;

import com.capstone.su26_sep490_g2_be.entity.FormatConfigField;

import java.util.List;

public interface FormatConfigFieldService {

	List<FormatConfigField> getByFormat(String formatCode);

	List<FormatConfigField> getVisibleByFormat(String formatCode);

	FormatConfigField getByFormatAndFieldKey(String formatCode, String fieldKey);

	FormatConfigField upsert(FormatConfigField field);

	void delete(Long id);
}
