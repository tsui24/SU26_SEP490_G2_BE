package com.capstone.su26_sep490_g2_be.controller;

import com.capstone.su26_sep490_g2_be.dto.response.ApiResponse;
import com.capstone.su26_sep490_g2_be.dto.response.ImageUploadResponse;
import com.capstone.su26_sep490_g2_be.service.MinioStorageService;
import com.capstone.su26_sep490_g2_be.util.MinioStorageUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Tag(name = "Storage", description = "Upload / lấy ảnh từ MinIO (test Postman)")
@RestController
@RequestMapping("/api/v1/storage")
@RequiredArgsConstructor
public class StorageController {

	private final MinioStorageService minioStorageService;

	@Operation(summary = "Upload ảnh", description = "Form-data: field `file` (bắt buộc), `folder` (tùy chọn, mặc định images)")
	@PostMapping(value = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<ApiResponse<ImageUploadResponse>> uploadImage(
			@Parameter(description = "File ảnh (jpeg, png, webp, gif)") @RequestParam("file") MultipartFile file,
			@Parameter(description = "Folder trong bucket, vd. avatars, tournaments") @RequestParam(value = "folder", required = false) String folder) {

		String objectKey = StringUtils.hasText(folder)
				? minioStorageService.uploadImage(file, folder)
				: minioStorageService.uploadImage(file);
		String url = minioStorageService.getPresignedUrl(objectKey);

		ImageUploadResponse response = ImageUploadResponse.builder()
				.objectKey(objectKey)
				.url(url)
				.build();
		return ResponseEntity.ok(ApiResponse.success("Upload successful", response));
	}

	@Operation(summary = "Lấy presigned URL", description = "Trả URL tạm để mở ảnh trên browser/Postman GET")
	@GetMapping("/images/url")
	public ResponseEntity<ApiResponse<Map<String, String>>> getImageUrl(
			@Parameter(description = "Object key trả về khi upload", example = "images/uuid.jpg")
			@RequestParam("objectKey") String objectKey) {

		String url = minioStorageService.getPresignedUrl(objectKey);
		return ResponseEntity.ok(ApiResponse.success(Map.of(
				"objectKey", objectKey,
				"url", url
		)));
	}

	@Operation(summary = "Tải ảnh qua backend", description = "Trả binary ảnh — dùng Postman Send and Download hoặc xem Preview")
	@GetMapping("/images/download")
	public ResponseEntity<byte[]> downloadImage(
			@Parameter(description = "Object key trả về khi upload", example = "images/uuid.jpg")
			@RequestParam("objectKey") String objectKey) {

		byte[] bytes = minioStorageService.getObjectBytes(objectKey);
		String contentType = MinioStorageUtil.guessContentTypeFromFilename(objectKey);

		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + extractFilename(objectKey) + "\"")
				.contentType(MediaType.parseMediaType(contentType))
				.body(bytes);
	}

	private String extractFilename(String objectKey) {
		int slash = objectKey.lastIndexOf('/');
		return slash >= 0 ? objectKey.substring(slash + 1) : objectKey;
	}
}
