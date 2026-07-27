package com.capstone.su26_sep490_g2_be.util;

import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
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
		if (!hasValidImageSignature(file)) {
			// Content-Type header do client tự khai báo trên multipart request, hoàn toàn có thể giả
			// mạo — phải soi magic bytes thật của file để chắc chắn đây đúng là ảnh, không phải file
			// tuỳ ý đội lốt "Content-Type: image/png".
			throw new IllegalArgumentException("File content does not match a valid image format");
		}
	}

	/** Kiểm tra magic bytes đầu file khớp với 1 trong 4 định dạng ảnh cho phép (JPEG/PNG/GIF/WEBP). */
	private static boolean hasValidImageSignature(MultipartFile file) {
		byte[] header;
		try (InputStream in = file.getInputStream()) {
			header = in.readNBytes(12);
		} catch (IOException e) {
			return false;
		}
		if (header.length >= 3
				&& (header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8 && (header[2] & 0xFF) == 0xFF) {
			return true; // JPEG
		}
		if (header.length >= 4
				&& (header[0] & 0xFF) == 0x89 && header[1] == 'P' && header[2] == 'N' && header[3] == 'G') {
			return true; // PNG
		}
		if (header.length >= 6 && header[0] == 'G' && header[1] == 'I' && header[2] == 'F'
				&& header[3] == '8' && (header[4] == '7' || header[4] == '9') && header[5] == 'a') {
			return true; // GIF87a / GIF89a
		}
		if (header.length >= 12
				&& header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
				&& header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P') {
			return true; // WEBP (RIFF....WEBP)
		}
		return false;
	}
}
