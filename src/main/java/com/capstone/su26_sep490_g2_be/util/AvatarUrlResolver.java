package com.capstone.su26_sep490_g2_be.util;

import com.capstone.su26_sep490_g2_be.service.MinioStorageService;
import org.springframework.util.StringUtils;

import java.net.URI;

/**
 * DB {@code user_profiles.avatar_url} lưu MinIO object key (vd. {@code avatars/uuid.jpg}),
 * không lưu presigned URL (có thời hạn).
 */
public final class AvatarUrlResolver {

	private AvatarUrlResolver() {
	}

	/**
	 * Chuẩn hóa input từ FE: ưu tiên objectKey; nếu gửi URL đầy đủ thì cố gắng tách object key.
	 */
	public static String normalizeForStorage(String avatarUrlOrObjectKey, String bucket) {
		if (!StringUtils.hasText(avatarUrlOrObjectKey)) {
			return null;
		}
		String trimmed = avatarUrlOrObjectKey.trim();
		if (!trimmed.contains("://")) {
			return trimmed.replaceAll("^/+", "");
		}
		try {
			String path = URI.create(trimmed).getPath();
			if (!StringUtils.hasText(path)) {
				return null;
			}
			String normalizedPath = path.startsWith("/") ? path.substring(1) : path;
			String bucketPrefix = bucket + "/";
			if (normalizedPath.startsWith(bucketPrefix)) {
				return normalizedPath.substring(bucketPrefix.length());
			}
			return normalizedPath;
		} catch (IllegalArgumentException ex) {
			return null;
		}
	}

	/**
	 * Trả presigned URL mới cho client hiển thị ảnh.
	 * Dùng cho detail — kiểm tra object tồn tại trước.
	 */
	public static String resolveForResponse(
			String storedValue, MinioStorageService minioStorageService, String bucket) {
		if (!StringUtils.hasText(storedValue)) {
			return null;
		}
		String key = storedValue.trim();
		if (key.contains("://")) {
			key = normalizeForStorage(key, bucket);
		}
		if (!StringUtils.hasText(key) || !minioStorageService.exists(key)) {
			return null;
		}
		return minioStorageService.getPresignedUrl(key);
	}

	/**
	 * Presign nhanh cho list (bỏ StatObject) — tránh N lần gọi MinIO khi phân trang.
	 * Object thiếu sẽ trả null; FE vẫn fallback ảnh mặc định như cũ.
	 */
	public static String resolveForList(
			String storedValue, MinioStorageService minioStorageService, String bucket) {
		if (!StringUtils.hasText(storedValue)) {
			return null;
		}
		String key = storedValue.trim();
		if (key.contains("://")) {
			key = normalizeForStorage(key, bucket);
		}
		if (!StringUtils.hasText(key)) {
			return null;
		}
		try {
			return minioStorageService.getPresignedUrl(key);
		} catch (Exception ex) {
			return null;
		}
	}
}
