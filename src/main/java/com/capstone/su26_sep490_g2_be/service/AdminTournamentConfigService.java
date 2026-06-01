package com.capstone.su26_sep490_g2_be.service;

import com.capstone.su26_sep490_g2_be.dto.request.*;
import com.capstone.su26_sep490_g2_be.dto.response.*;
import com.capstone.su26_sep490_g2_be.enums.FormatSetupStatus;

public interface AdminTournamentConfigService {

	PageResponse<ConfigFieldCatalogItemResponse> getConfigFieldCatalog(
			String scope, Boolean isActive, int page, int size);

	ConfigFieldCatalogItemResponse getConfigFieldCatalogItem(String fieldKey);

	PageResponse<FormatListItemResponse> listFormats(
			Boolean isActive, FormatSetupStatus setupStatus, int page, int size);

	FormatDetailResponse getFormat(String code);

	FormatCreateResponse createFormat(CreateFormatRequest request);

	FormatDetailResponse updateFormat(String code, UpdateFormatRequest request);

	FormatActivePatchResponse patchFormatActive(String code, PatchFormatActiveRequest request);

	FormatSetupStatusResponse getSetupStatus(String code);

	FormatConfigFieldsFormResponse getConfigFieldsForm(String code);

	FormatConfigFieldsSaveResponse saveConfigFields(String code, UpsertFormatConfigFieldsRequest request);

	FormatRaceToRulesFormResponse getRaceToRulesForm(String code);

	FormatRaceToRulesSaveResponse saveRaceToRules(String code, UpsertFormatRaceToRulesRequest request);

	FormatSetupSummaryResponse getSetupSummary(String code);

	FormatActivateResponse activateFormat(String code);

	FormatBootstrapResponse bootstrapDefaults(String code, BootstrapDefaultsRequest request);

	PageResponse<GameTypeDetailResponse> listGameTypes(int page, int size);

	GameTypeDetailResponse updateGameType(String code, UpdateGameTypeRequest request);
}
