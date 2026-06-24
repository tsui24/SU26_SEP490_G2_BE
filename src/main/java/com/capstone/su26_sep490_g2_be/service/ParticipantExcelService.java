package com.capstone.su26_sep490_g2_be.service;

import com.capstone.su26_sep490_g2_be.dto.response.ImportParticipantResultResponse;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface ParticipantExcelService {

	byte[] buildImportTemplate() throws IOException;

	byte[] buildImportTemplateCsv();

	String getTemplateFilename();

	String getTemplateCsvFilename();

	ImportParticipantResultResponse importFromExcel(Long tournamentId, MultipartFile file);
}
