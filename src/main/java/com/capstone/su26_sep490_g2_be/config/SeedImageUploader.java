package com.capstone.su26_sep490_g2_be.config;

import com.capstone.su26_sep490_g2_be.config.bootstrap.SeedImages;
import com.capstone.su26_sep490_g2_be.service.MinioStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

/**
 * Upload ảnh mẫu (đóng gói sẵn trong {@code src/main/resources/seed-images/}) lên MinIO trước khi
 * các seeder khác chạy — {@code Tournament.thumbnailUrl}/{@code bannerUrl} và
 * {@code Branch.imageKeys} lưu <b>object key</b>, không phải URL tĩnh (xem
 * {@code OwnerTournamentServiceImpl#resolveImageForResponse} / {@code BranchServiceImpl#resolveImages}
 * — cả hai đều gọi {@code AvatarUrlResolver.resolveForResponse}, presign qua MinIO và trả {@code null}
 * nếu object không tồn tại). Set thẳng một đường dẫn tĩnh như file news dùng
 * ({@code NewsSeedInitializer}) sẽ luôn ra ảnh vỡ cho tournament/branch vì object đó chưa từng có
 * trong bucket.
 * <p>
 * Idempotent theo object key — {@code exists()} rồi mới upload, an toàn khi backend restart nhiều
 * lần. Không throw nếu MinIO chưa sẵn sàng lúc khởi động — bỏ qua ảnh đó, {@link SeedImages} vẫn trả
 * về key như bình thường, các nơi hiển thị đã có sẵn fallback ảnh mặc định khi presign thất bại.
 * <p>
 * Chạy trước {@link DataInitializer} (@Order 1).
 */
@Slf4j
@Component
@Order(0)
@RequiredArgsConstructor
public class SeedImageUploader implements CommandLineRunner {

	private final MinioStorageService minioStorageService;

	@Override
	public void run(String... args) {
		int uploaded = 0;
		for (String filename : SeedImages.TOURNAMENT_IMAGE_FILENAMES) {
			if (uploadIfMissing("seed-images/tournaments/" + filename,
					SeedImages.TOURNAMENTS_FOLDER + "/" + filename)) {
				uploaded++;
			}
		}
		for (String filename : SeedImages.BRANCH_IMAGE_FILENAMES) {
			if (uploadIfMissing("seed-images/tournaments/" + filename,
					SeedImages.BRANCHES_FOLDER + "/" + filename)) {
				uploaded++;
			}
		}
		if (uploaded > 0) {
			log.info("SeedImageUploader: đã upload {} ảnh mẫu lên MinIO", uploaded);
		}
	}

	private boolean uploadIfMissing(String classpathLocation, String objectKey) {
		try {
			if (minioStorageService.exists(objectKey)) {
				return false;
			}
		} catch (Exception ex) {
			log.warn("SeedImageUploader: MinIO chưa sẵn sàng, bỏ qua '{}': {}", objectKey, ex.getMessage());
			return false;
		}

		ClassPathResource resource = new ClassPathResource(classpathLocation);
		if (!resource.exists()) {
			log.warn("SeedImageUploader: thiếu file resource '{}'", classpathLocation);
			return false;
		}

		try (InputStream in = resource.getInputStream()) {
			long size = resource.contentLength();
			minioStorageService.upload(objectKey, in, size, "image/jpeg");
			return true;
		} catch (IOException ex) {
			log.warn("SeedImageUploader: đọc file '{}' thất bại: {}", classpathLocation, ex.getMessage());
			return false;
		} catch (Exception ex) {
			log.warn("SeedImageUploader: upload '{}' thất bại: {}", objectKey, ex.getMessage());
			return false;
		}
	}
}
