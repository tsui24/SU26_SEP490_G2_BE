package com.capstone.su26_sep490_g2_be.util;

import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class MinioStorageUtil {

	private static final Set<String> ALLOWED_IMAGE_CONTENT_TYPES = Set.of(
			"image/jpeg",
			"image/png",
			"image/webp",
			"image/gif"
	);

	private MinioStorageUtil() {
	}

	public static String buildObjectKey(String folder, String originalFilename) {
		String safeFolder = normalizeFolder(folder);
		String extension = extractExtension(originalFilename);
		String fileName = UUID.randomUUID() + extension;
		return safeFolder + "/" + fileName;
	}

	public static String normalizeFolder(String folder) {
		if (!StringUtils.hasText(folder)) {
			return "images";
		}
		String normalized = folder.trim()
				.replace("\\", "/")
				.replaceAll("^/+", "")
				.replaceAll("/+$", "");
		return StringUtils.hasText(normalized) ? normalized : "images";
	}

	public static String extractExtension(String originalFilename) {
		if (!StringUtils.hasText(originalFilename)) {
			return "";
		}
		String name = originalFilename.trim();
		int dotIndex = name.lastIndexOf('.');
		if (dotIndex < 0 || dotIndex == name.length() - 1) {
			return "";
		}
		return name.substring(dotIndex).toLowerCase(Locale.ROOT);
	}

	public static String resolveContentType(MultipartFile file) {
		if (file == null) {
			return "application/octet-stream";
		}
		if (StringUtils.hasText(file.getContentType())) {
			return file.getContentType();
		}
		return guessContentTypeFromFilename(file.getOriginalFilename());
	}

	public static String guessContentTypeFromFilename(String filename) {
		String extension = extractExtension(filename);
		return switch (extension) {
			case ".jpg", ".jpeg" -> "image/jpeg";
			case ".png" -> "image/png";
			case ".webp" -> "image/webp";
			case ".gif" -> "image/gif";
			default -> "application/octet-stream";
		};
	}

	public static boolean isAllowedImage(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			return false;
		}
		String contentType = resolveContentType(file);
		return ALLOWED_IMAGE_CONTENT_TYPES.contains(contentType);
	}

	public static void validateImageFile(MultipartFile file, long maxFileSizeBytes) {
		if (file == null || file.isEmpty()) {
			throw new IllegalArgumentException("File is empty");
		}
		if (file.getSize() > maxFileSizeBytes) {
			throw new IllegalArgumentException("File exceeds maximum allowed size");
		}
		if (!isAllowedImage(file)) {
			throw new IllegalArgumentException("Only image files are allowed (jpeg, png, webp, gif)");
		}
	}
}
