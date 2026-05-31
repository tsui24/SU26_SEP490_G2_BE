package com.capstone.su26_sep490_g2_be.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class MinioConfig {

	private final MinioProperties minioProperties;

	@Bean
	public MinioClient minioClient() {
		return MinioClient.builder()
				.endpoint(minioProperties.getEndpoint())
				.credentials(minioProperties.getAccessKey(), minioProperties.getSecretKey())
				.build();
	}

	@Bean
	public ApplicationRunner minioBucketInitializer(MinioClient minioClient) {
		return args -> {
			String bucket = minioProperties.getBucket();
			boolean exists = minioClient.bucketExists(
					BucketExistsArgs.builder().bucket(bucket).build());
			if (!exists) {
				minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
				log.info("Created MinIO bucket: {}", bucket);
			}
		};
	}
}
