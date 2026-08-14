package com.capstone.su26_sep490_g2_be.config.bootstrap;

import lombok.experimental.UtilityClass;

/**
 * Ảnh minh hoạ bi-a dùng chung cho các seeder — {@code Tournament.thumbnailUrl}/{@code bannerUrl}
 * và {@code Branch.imageKeys} đều lưu <b>MinIO object key</b> (không phải URL tĩnh), nên các hằng số
 * ở đây là <b>object key</b> đã được {@link com.capstone.su26_sep490_g2_be.config.SeedImageUploader}
 * upload sẵn lên bucket lúc khởi động — không tồn tại trên MinIO thì API sẽ presign ra {@code null}
 * và FE fallback ảnh mặc định.
 * <p>
 * File gốc đóng gói tại {@code src/main/resources/seed-images/tournaments/*.jpg}.
 */
@UtilityClass
public class SeedImages {

	public static final String TOURNAMENTS_FOLDER = "seed/tournaments";
	public static final String BRANCHES_FOLDER = "seed/branches";

	/** Toàn bộ file ảnh giải đấu đóng gói sẵn trong resources — {@link com.capstone.su26_sep490_g2_be.config.SeedImageUploader} upload hết. */
	public static final String[] TOURNAMENT_IMAGE_FILENAMES = {
			"vn-player-1.jpg",
			"action-1.jpg",
			"action-2.jpg",
			"pool-2.jpg",
			"pool-4.jpg",
			"pool-6.jpg",
			"billiards-hall-1.jpg",
			"billiards-action-3.jpg",
			"billiards-action-4.jpg",
			"nine-ball-rack-1.jpg",
			"nine-ball-rack-2.jpg",
			"tournament-crowd-1.jpg",
			"tournament-open-1.jpg",
	};

	/** Ảnh phù hợp làm ảnh đại diện chi nhánh (không phải cận cảnh trận đấu) — tái dùng file tournament, upload sang folder riêng. */
	public static final String[] BRANCH_IMAGE_FILENAMES = {
			"billiards-hall-1.jpg",
			"pool-4.jpg",
			"pool-6.jpg",
	};

	/** Chọn ảnh ổn định theo tên giải — idempotent qua nhiều lần chạy seed. */
	public static String thumbnailFor(String tournamentName) {
		String filename = TOURNAMENT_IMAGE_FILENAMES[
				Math.floorMod(tournamentName.hashCode(), TOURNAMENT_IMAGE_FILENAMES.length)];
		return TOURNAMENTS_FOLDER + "/" + filename;
	}

	public static String bannerFor(String tournamentName) {
		int idx = Math.floorMod(tournamentName.hashCode() + 5, TOURNAMENT_IMAGE_FILENAMES.length);
		return TOURNAMENTS_FOLDER + "/" + TOURNAMENT_IMAGE_FILENAMES[idx];
	}

	/** Object key cho ảnh chi nhánh — dùng trực tiếp trong {@code Branch.imageKeys} (JSON list). */
	public static String branchImageKey(String branchName, int index) {
		String filename = BRANCH_IMAGE_FILENAMES[
				Math.floorMod(branchName.hashCode() + index, BRANCH_IMAGE_FILENAMES.length)];
		return BRANCHES_FOLDER + "/" + filename;
	}
}
