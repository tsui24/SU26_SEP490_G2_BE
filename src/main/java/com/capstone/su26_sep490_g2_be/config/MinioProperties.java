package com.capstone.su26_sep490_g2_be.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.minio")
public class MinioProperties {

	private String endpoint = "http://localhost:9000";

	private String accessKey = "admin";

	private String secretKey = "";

	private String bucket = "sep490-images";

	private boolean secure = false;

	private String defaultFolder = "images";

	private long maxFileSizeBytes = 5_242_880L;

	private int presignedUrlExpirySeconds = 3600;

	private int connectTimeoutMs = 3_000;

	private int readTimeoutMs = 5_000;

	private int writeTimeoutMs = 5_000;
}
