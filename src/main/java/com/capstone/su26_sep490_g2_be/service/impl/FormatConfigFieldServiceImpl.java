package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.entity.FormatConfigField;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.repository.FormatConfigFieldRepository;
import com.capstone.su26_sep490_g2_be.service.FormatConfigFieldService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FormatConfigFieldServiceImpl implements FormatConfigFieldService {

	private final FormatConfigFieldRepository repository;

	@Override
	public List<FormatConfigField> getByFormat(String formatCode) {
		return repository.findByFormatCodeOrderByIdAsc(formatCode);
	}

	@Override
	public List<FormatConfigField> getVisibleByFormat(String formatCode) {
		return repository.findByFormatCodeAndIsVisibleToOwnerTrueOrderByIdAsc(formatCode);
	}

	@Override
	public FormatConfigField getByFormatAndFieldKey(String formatCode, String fieldKey) {
		return repository.findByFormatCodeAndFieldKey(formatCode, fieldKey)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
	}

	@Override
	@Transactional
	public FormatConfigField upsert(FormatConfigField field) {
		return repository.findByFormatCodeAndFieldKey(field.getFormatCode(), field.getFieldKey())
				.map(existing -> {
					existing.setDefaultValue(field.getDefaultValue());
					existing.setIsRequired(field.getIsRequired());
					existing.setIsVisibleToOwner(field.getIsVisibleToOwner());
					return repository.save(existing);
				})
				.orElseGet(() -> repository.save(field));
	}

	@Override
	@Transactional
	public void delete(Long id) {
		FormatConfigField field = repository.findById(id)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
		repository.delete(field);
	}
}
