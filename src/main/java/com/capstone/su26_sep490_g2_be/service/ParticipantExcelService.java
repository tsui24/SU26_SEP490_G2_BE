package com.capstone.su26_sep490_g2_be.service;

import com.capstone.su26_sep490_g2_be.dto.request.ParticipantImportConfirmRequest;
import com.capstone.su26_sep490_g2_be.dto.response.ImportParticipantResultResponse;
import com.capstone.su26_sep490_g2_be.dto.response.ParticipantImportPreviewResponse;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface ParticipantExcelService {

	byte[] buildImportTemplate(Long tournamentId) throws IOException;

	byte[] buildImportTemplateCsv(Long tournamentId);

	String getTemplateFilename();

	String getTemplateCsvFilename();

	ParticipantImportPreviewResponse previewFromExcel(Long tournamentId, MultipartFile file);

	ImportParticipantResultResponse confirmImport(Long tournamentId, ParticipantImportConfirmRequest request);
}
