package com.capstone.su26_sep490_g2_be.repository;

import com.capstone.su26_sep490_g2_be.entity.DeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {

	Optional<DeviceToken> findByExpoToken(String expoToken);

	List<DeviceToken> findByUserId(Long userId);

	/** Người nhận được resolve theo lô nên lấy token của nhiều người trong một lượt truy vấn. */
	List<DeviceToken> findByUserIdIn(List<Long> userIds);

	void deleteByExpoToken(String expoToken);

	void deleteByExpoTokenIn(List<String> expoTokens);
}
