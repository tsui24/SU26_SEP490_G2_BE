package com.capstone.su26_sep490_g2_be.service;

import com.capstone.su26_sep490_g2_be.dto.response.ImportTableResultResponse;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface BilliardTableExcelService {

	byte[] buildImportTemplate() throws IOException;

	byte[] buildImportTemplateCsv();

	String getTemplateFilename();

	String getTemplateCsvFilename();

	ImportTableResultResponse importFromExcel(Long ownerId, MultipartFile file);
}
