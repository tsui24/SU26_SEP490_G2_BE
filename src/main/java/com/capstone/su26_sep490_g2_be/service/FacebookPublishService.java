package com.capstone.su26_sep490_g2_be.service;

import java.util.List;

public interface FacebookPublishService {

	/**
	 * Đăng bài text (+ link tùy chọn) lên Facebook Page.
	 *
	 * @return Facebook post id (dạng "{pageId}_{postId}")
	 */
	String publishTextPost(String message, String link);

	/**
	 * Đăng bài kèm ảnh lên Facebook Page.
	 * Ảnh là URL public (MinIO presigned hoặc CDN).
	 *
	 * @return Facebook post/photo id
	 */
	String publishPhotoPost(String message, String imageUrl);

	/**
	 * Đăng bài kèm nhiều ảnh (multi-photo post).
	 *
	 * @param imageUrls danh sách URL ảnh public
	 * @return Facebook post id
	 */
	String publishMultiPhotoPost(String message, List<String> imageUrls);

	/**
	 * Đăng bài kèm 1 ảnh lấy từ MinIO (backend upload binary trực tiếp).
	 *
	 * @param minioObjectKey object key trong MinIO (vd. "images/uuid.jpg")
	 * @return Facebook post/photo id
	 */
	String publishPhotoFromMinio(String message, String minioObjectKey);

	/**
	 * Đăng bài kèm nhiều ảnh từ MinIO.
	 *
	 * @param minioObjectKeys danh sách MinIO object key
	 * @return Facebook post id
	 */
	String publishMultiPhotoFromMinio(String message, List<String> minioObjectKeys);
}
