package com.capstone.su26_sep490_g2_be.service;

import com.capstone.su26_sep490_g2_be.dto.request.*;
import com.capstone.su26_sep490_g2_be.dto.response.*;

public interface AdminRegistrationFormService {

	PageResponse<RegistrationFieldCatalogItemResponse> getFieldCatalog(Boolean isActive, int page, int size);

	RegistrationFieldCatalogItemResponse getFieldCatalogItem(String fieldKey);

	RegistrationFieldCatalogItemResponse createField(CreateRegistrationFieldRequest request);

	RegistrationFieldCatalogItemResponse updateField(String fieldKey, UpdateRegistrationFieldRequest request);

	RegistrationFieldCatalogItemResponse patchFieldActive(String fieldKey, PatchRegistrationFieldActiveRequest request);

	PageResponse<RegistrationFormTemplateListItemResponse> listTemplates(Boolean isActive, int page, int size);

	RegistrationFormTemplateDetailResponse getTemplate(Long id);

	RegistrationFormTemplateCreateResponse createTemplate(Long adminUserId, CreateRegistrationFormTemplateRequest request);

	RegistrationFormTemplateDetailResponse updateTemplate(Long id, UpdateRegistrationFormTemplateRequest request);

	RegistrationFormTemplateActivePatchResponse patchTemplateActive(
			Long id, PatchRegistrationFormTemplateActiveRequest request);

	RegistrationFormTemplateFieldsFormResponse getTemplateFieldsForm(Long id);

	RegistrationFormTemplateFieldsSaveResponse saveTemplateFields(
			Long id, UpsertRegistrationFormTemplateFieldsRequest request);

	void deleteTemplateField(Long id, String fieldKey);

	RegistrationFormPreviewResponse getTemplatePreview(Long id);

	OwnerRegistrationFormTemplateListResponse listActiveTemplatesForOwner();

	RegistrationFormPreviewResponse getActiveTemplatePreview(Long id);
}
