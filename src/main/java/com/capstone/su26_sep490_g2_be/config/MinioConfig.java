package com.capstone.su26_sep490_g2_be.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class MinioConfig {

	private final MinioProperties minioProperties;

	@Bean
	public MinioClient minioClient() {
		OkHttpClient httpClient = new OkHttpClient.Builder()
				.connectTimeout(minioProperties.getConnectTimeoutMs(), TimeUnit.MILLISECONDS)
				.readTimeout(minioProperties.getReadTimeoutMs(), TimeUnit.MILLISECONDS)
				.writeTimeout(minioProperties.getWriteTimeoutMs(), TimeUnit.MILLISECONDS)
				.build();
		return MinioClient.builder()
				.endpoint(minioProperties.getEndpoint())
				.credentials(minioProperties.getAccessKey(), minioProperties.getSecretKey())
				.httpClient(httpClient)
				.build();
	}

	@Bean
	public ApplicationRunner minioBucketInitializer(MinioClient minioClient) {
		return args -> {
			String bucket = minioProperties.getBucket();
			try {
				boolean exists = minioClient.bucketExists(
						BucketExistsArgs.builder().bucket(bucket).build());
				if (!exists) {
					minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
					log.info("Created MinIO bucket: {}", bucket);
				} else {
					log.info("MinIO bucket ready: {}", bucket);
				}
			} catch (Exception ex) {
				log.warn(
						"MinIO unavailable at startup (endpoint={}). App continues; storage APIs fail until MinIO is up. Cause: {}",
						minioProperties.getEndpoint(),
						ex.getMessage());
			}
		};
	}
}
