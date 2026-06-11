package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.config.MinioProperties;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.service.MinioStorageService;
import com.capstone.su26_sep490_g2_be.util.MinioStorageUtil;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class MinioStorageServiceImpl implements MinioStorageService {

	private final MinioClient minioClient;
	private final MinioProperties minioProperties;

	@Override
	public String uploadImage(MultipartFile file) {
		return uploadImage(file, minioProperties.getDefaultFolder());
	}

	@Override
	public String uploadImage(MultipartFile file, String folder) {
		try {
			MinioStorageUtil.validateImageFile(file, minioProperties.getMaxFileSizeBytes());
		} catch (IllegalArgumentException ex) {
			throw new BusinessException(ErrorCode.STORAGE_INVALID_FILE, ex.getMessage());
		}

		String objectKey = MinioStorageUtil.buildObjectKey(folder, file.getOriginalFilename());
		String contentType = MinioStorageUtil.resolveContentType(file);

		try (InputStream inputStream = file.getInputStream()) {
			return upload(objectKey, inputStream, file.getSize(), contentType);
		} catch (BusinessException ex) {
			throw ex;
		} catch (Exception ex) {
			log.error("Failed to read multipart file for upload: {}", objectKey, ex);
			throw new BusinessException(ErrorCode.STORAGE_UPLOAD_FAILED);
		}
	}

	@Override
	public String upload(String objectKey, InputStream inputStream, long size, String contentType) {
		if (!StringUtils.hasText(objectKey)) {
			throw new BusinessException(ErrorCode.STORAGE_INVALID_FILE, "Object key không được để trống");
		}
		try {
			minioClient.putObject(
					PutObjectArgs.builder()
							.bucket(minioProperties.getBucket())
							.object(objectKey)
							.stream(inputStream, size, -1)
							.contentType(contentType)
							.build());
			log.debug("Uploaded object to MinIO: {}", objectKey);
			return objectKey;
		} catch (Exception ex) {
			log.error("MinIO upload failed: {}", objectKey, ex);
			throw new BusinessException(ErrorCode.STORAGE_UPLOAD_FAILED);
		}
	}

	@Override
	public InputStream getObject(String objectKey) {
		try {
			return minioClient.getObject(
					GetObjectArgs.builder()
							.bucket(minioProperties.getBucket())
							.object(objectKey)
							.build());
		} catch (ErrorResponseException ex) {
			if ("NoSuchKey".equals(ex.errorResponse().code())) {
				throw new BusinessException(ErrorCode.STORAGE_OBJECT_NOT_FOUND);
			}
			log.error("MinIO get object failed: {}", objectKey, ex);
			throw new BusinessException(ErrorCode.STORAGE_DOWNLOAD_FAILED);
		} catch (Exception ex) {
			log.error("MinIO get object failed: {}", objectKey, ex);
			throw new BusinessException(ErrorCode.STORAGE_DOWNLOAD_FAILED);
		}
	}

	@Override
	public byte[] getObjectBytes(String objectKey) {
		try (InputStream inputStream = getObject(objectKey)) {
			return inputStream.readAllBytes();
		} catch (BusinessException ex) {
			throw ex;
		} catch (Exception ex) {
			log.error("MinIO read bytes failed: {}", objectKey, ex);
			throw new BusinessException(ErrorCode.STORAGE_DOWNLOAD_FAILED);
		}
	}

	@Override
	public String getPresignedUrl(String objectKey) {
		return getPresignedUrl(objectKey, minioProperties.getPresignedUrlExpirySeconds());
	}

	@Override
	public String getPresignedUrl(String objectKey, int expirySeconds) {
		if (!exists(objectKey)) {
			throw new BusinessException(ErrorCode.STORAGE_OBJECT_NOT_FOUND);
		}
		try {
			return minioClient.getPresignedObjectUrl(
					GetPresignedObjectUrlArgs.builder()
							.method(Method.GET)
							.bucket(minioProperties.getBucket())
							.object(objectKey)
							.expiry(expirySeconds)
							.build());
		} catch (Exception ex) {
			log.error("MinIO presigned URL failed: {}", objectKey, ex);
			throw new BusinessException(ErrorCode.STORAGE_DOWNLOAD_FAILED);
		}
	}

	@Override
	public boolean exists(String objectKey) {
		try {
			minioClient.statObject(
					StatObjectArgs.builder()
							.bucket(minioProperties.getBucket())
							.object(objectKey)
							.build());
			return true;
		} catch (ErrorResponseException ex) {
			if ("NoSuchKey".equals(ex.errorResponse().code())) {
				return false;
			}
			log.error("MinIO stat object failed: {}", objectKey, ex);
			throw new BusinessException(ErrorCode.STORAGE_DOWNLOAD_FAILED);
		} catch (Exception ex) {
			log.error("MinIO stat object failed: {}", objectKey, ex);
			throw new BusinessException(ErrorCode.STORAGE_DOWNLOAD_FAILED);
		}
	}

	@Override
	public void deleteObject(String objectKey) {
		if (!exists(objectKey)) {
			throw new BusinessException(ErrorCode.STORAGE_OBJECT_NOT_FOUND);
		}
		try {
			minioClient.removeObject(
					RemoveObjectArgs.builder()
							.bucket(minioProperties.getBucket())
							.object(objectKey)
							.build());
			log.debug("Deleted MinIO object: {}", objectKey);
		} catch (Exception ex) {
			log.error("MinIO delete failed: {}", objectKey, ex);
			throw new BusinessException(ErrorCode.STORAGE_UPLOAD_FAILED);
		}
	}
}
