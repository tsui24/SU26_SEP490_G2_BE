package com.capstone.su26_sep490_g2_be.service;

import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OtpService {

	private static final long OTP_TTL_MS = 5 * 60 * 1000; // 5 minutes
	private static final int OTP_LENGTH = 6;
	private static final SecureRandom RANDOM = new SecureRandom();

	private final Map<String, OtpEntry> otpStore = new ConcurrentHashMap<>();

	public String generateOtp(String email) {
		String otp = String.format("%0" + OTP_LENGTH + "d", RANDOM.nextInt((int) Math.pow(10, OTP_LENGTH)));
		otpStore.put(email.toLowerCase(), new OtpEntry(otp, Instant.now().plusMillis(OTP_TTL_MS)));
		return otp;
	}

	public void verifyOtp(String email, String otp) {
		OtpEntry entry = otpStore.get(email.toLowerCase());
		if (entry == null) {
			throw new BusinessException(ErrorCode.AUTH_INVALID_OTP);
		}
		if (Instant.now().isAfter(entry.expiresAt())) {
			otpStore.remove(email.toLowerCase());
			throw new BusinessException(ErrorCode.AUTH_OTP_EXPIRED);
		}
		if (!entry.otp().equals(otp)) {
			throw new BusinessException(ErrorCode.AUTH_INVALID_OTP);
		}
	}

	public void invalidate(String email) {
		otpStore.remove(email.toLowerCase());
	}

	private record OtpEntry(String otp, Instant expiresAt) {}
}
