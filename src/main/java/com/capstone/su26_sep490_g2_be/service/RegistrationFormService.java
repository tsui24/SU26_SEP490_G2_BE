package com.capstone.su26_sep490_g2_be.service;

import com.capstone.su26_sep490_g2_be.dto.request.SubmitTournamentRegistrationRequest;
import com.capstone.su26_sep490_g2_be.dto.response.RegistrationFormPreviewResponse;
import com.capstone.su26_sep490_g2_be.entity.Registration;
import com.capstone.su26_sep490_g2_be.entity.RegistrationFormTemplate;
import com.capstone.su26_sep490_g2_be.entity.Tournament;

import java.util.List;
import java.util.Map;

public interface RegistrationFormService {

	RegistrationFormPreviewResponse resolveTemplatePreview(RegistrationFormTemplate template);

	RegistrationFormPreviewResponse resolveTournamentForm(Tournament tournament);

	void validateActiveTemplate(RegistrationFormTemplate template);

	void validateRegistrationSettings(boolean isRegister, Long registrationFormTemplateId);

	RegistrationFormTemplate loadActiveTemplate(Long templateId);

	Map<String, String> validateAndNormalizeFieldValues(
			Long templateId,
			List<SubmitTournamentRegistrationRequest.FieldValueItem> fieldValues);

	void saveFieldValues(Registration registration, Long templateId, Map<String, String> normalizedValues);
}
