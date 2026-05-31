package com.capstone.su26_sep490_g2_be.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

/**
 * MinIO image storage — inject và gọi từ service/controller khi cần.
 * Lưu {@code objectKey} vào DB; dùng {@link #getPresignedUrl(String)} để trả URL cho client.
 */
public interface MinioStorageService {

	/**
	 * Upload ảnh vào folder mặc định ({@code app.minio.default-folder}).
	 *
	 * @return object key trong bucket (vd. {@code images/uuid.jpg})
	 */
	String uploadImage(MultipartFile file);

	/**
	 * Upload ảnh vào folder tùy chọn (vd. {@code tournaments}, {@code avatars}).
	 */
	String uploadImage(MultipartFile file, String folder);

	/**
	 * Upload stream với object key đã biết.
	 */
	String upload(String objectKey, InputStream inputStream, long size, String contentType);

	InputStream getObject(String objectKey);

	byte[] getObjectBytes(String objectKey);

	/**
	 * URL tạm (presigned GET) để client tải/xem ảnh.
	 */
	String getPresignedUrl(String objectKey);

	String getPresignedUrl(String objectKey, int expirySeconds);

	boolean exists(String objectKey);

	void deleteObject(String objectKey);
}
