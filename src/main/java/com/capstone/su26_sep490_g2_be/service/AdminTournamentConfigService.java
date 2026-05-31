package com.capstone.su26_sep490_g2_be.service;

import com.capstone.su26_sep490_g2_be.dto.request.*;
import com.capstone.su26_sep490_g2_be.dto.response.*;
import com.capstone.su26_sep490_g2_be.enums.FormatSetupStatus;

public interface AdminTournamentConfigService {

	ConfigFieldCatalogListResponse getConfigFieldCatalog(String scope, Boolean isActive);

	ConfigFieldCatalogItemResponse getConfigFieldCatalogItem(String fieldKey);

	FormatListResponse listFormats(Boolean isActive, FormatSetupStatus setupStatus);

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

	GameTypeListResponse listGameTypes();

	GameTypeDetailResponse updateGameType(String code, UpdateGameTypeRequest request);
}
