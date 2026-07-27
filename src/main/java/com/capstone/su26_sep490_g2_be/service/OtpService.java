package com.capstone.su26_sep490_g2_be.service;

import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class OtpService {

	private static final long OTP_TTL_MS = 5 * 60 * 1000; // 5 minutes
	private static final long RESEND_COOLDOWN_MS = 60 * 1000; // 1 minute giữa 2 lần gửi
	private static final int OTP_LENGTH = 6;
	private static final int MAX_ATTEMPTS = 5;
	private static final SecureRandom RANDOM = new SecureRandom();

	private final Map<String, OtpEntry> otpStore = new ConcurrentHashMap<>();

	public String generateOtp(String email) {
		String key = email.toLowerCase();
		OtpEntry existing = otpStore.get(key);
		if (existing != null && Instant.now().isBefore(existing.issuedAt().plusMillis(RESEND_COOLDOWN_MS))) {
			// Chặn spam gửi lại OTP (email-bombing) và tránh ghi đè OTP hợp lệ người dùng vừa nhận.
			throw new BusinessException(ErrorCode.AUTH_OTP_RESEND_TOO_SOON);
		}
		String otp = String.format("%0" + OTP_LENGTH + "d", RANDOM.nextInt((int) Math.pow(10, OTP_LENGTH)));
		otpStore.put(key, new OtpEntry(otp, Instant.now(), Instant.now().plusMillis(OTP_TTL_MS), new AtomicInteger(0)));
		return otp;
	}

	/** Kiểm tra OTP còn hiệu lực, KHÔNG tiêu thụ (dùng cho bước xác nhận OTP độc lập trước khi đổi mật khẩu). */
	public void verifyOtp(String email, String otp) {
		checkAndMaybeConsume(email, otp, false);
	}

	/** Kiểm tra + xoá OTP ngay trong 1 thao tác nguyên tử — tránh race 2 request dùng chung 1 OTP đều pass trước khi bị xoá. */
	public void verifyAndConsume(String email, String otp) {
		checkAndMaybeConsume(email, otp, true);
	}

	private void checkAndMaybeConsume(String email, String otp, boolean consume) {
		String key = email.toLowerCase();
		OtpEntry entry = otpStore.get(key);
		if (entry == null) {
			throw new BusinessException(ErrorCode.AUTH_INVALID_OTP);
		}
		if (Instant.now().isAfter(entry.expiresAt())) {
			otpStore.remove(key);
			throw new BusinessException(ErrorCode.AUTH_OTP_EXPIRED);
		}
		if (!entry.otp().equals(otp)) {
			if (entry.attempts().incrementAndGet() >= MAX_ATTEMPTS) {
				// Khoá hẳn OTP này lại — buộc người dùng yêu cầu mã mới thay vì brute-force tiếp.
				otpStore.remove(key);
				throw new BusinessException(ErrorCode.AUTH_OTP_TOO_MANY_ATTEMPTS);
			}
			throw new BusinessException(ErrorCode.AUTH_INVALID_OTP);
		}
		if (consume) {
			otpStore.remove(key);
		}
	}

	public void invalidate(String email) {
		otpStore.remove(email.toLowerCase());
	}

	private record OtpEntry(String otp, Instant issuedAt, Instant expiresAt, AtomicInteger attempts) {}
}
