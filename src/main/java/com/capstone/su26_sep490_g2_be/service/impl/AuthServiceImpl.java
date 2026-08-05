package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.dto.request.*;
import com.capstone.su26_sep490_g2_be.dto.response.LoginResponse;
import com.capstone.su26_sep490_g2_be.dto.response.RegisterResponse;
import com.capstone.su26_sep490_g2_be.dto.response.UserResponse;
import com.capstone.su26_sep490_g2_be.entity.Role;
import com.capstone.su26_sep490_g2_be.entity.User;
import com.capstone.su26_sep490_g2_be.enums.EmailEventType;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.enums.RoleCode;
import com.capstone.su26_sep490_g2_be.enums.UserStatus;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.repository.RoleRepository;
import com.capstone.su26_sep490_g2_be.repository.UserRepository;
import com.capstone.su26_sep490_g2_be.service.AuthService;
import com.capstone.su26_sep490_g2_be.service.EmailService;
import com.capstone.su26_sep490_g2_be.service.MailDomainEvent;
import com.capstone.su26_sep490_g2_be.service.MailRecipient;
import com.capstone.su26_sep490_g2_be.service.OtpService;
import com.capstone.su26_sep490_g2_be.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

	private final UserRepository userRepository;
	private final RoleRepository roleRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtUtil jwtUtil;
	private final OtpService otpService;
	private final EmailService emailService;
	private final ApplicationEventPublisher eventPublisher;
	private final MailContextBuilder mailContextBuilder;

	@Override
	@Transactional(readOnly = true)
	public UserResponse getMe(Long userId) {
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_USER_NOT_FOUND));

		if (user.getStatus() == UserStatus.LOCKED) {
			throw new BusinessException(ErrorCode.AUTH_ACCOUNT_LOCKED);
		}
		if (user.getStatus() == UserStatus.DELETED) {
			throw new BusinessException(ErrorCode.AUTH_INVALID_TOKEN);
		}

		return UserResponse.builder()
				.id(user.getId())
				.email(user.getEmail())
				.phone(user.getPhone())
				.role(user.getRole().getCode())
				.status(user.getStatus().name())
				.profileCompleted(user.getProfile() != null)
				.build();
	}

	@Override
	public LoginResponse login(LoginRequest request) {
		User user = userRepository.findByEmail(request.getEmail())
				.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS));

		if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
			throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
		}

		if (user.getStatus() == UserStatus.LOCKED) {
			throw new BusinessException(ErrorCode.AUTH_ACCOUNT_LOCKED);
		}
		if (user.getStatus() == UserStatus.DELETED) {
			throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
		}

		String token = jwtUtil.generateToken(user.getId(), user.getEmail(), user.getRole().getCode());

		return LoginResponse.builder()
				.token(token)
				.expiresIn(jwtUtil.getExpirationMs())
				.build();
	}

	@Override
	@Transactional
	public RegisterResponse register(RegisterRequest request) {
		if (userRepository.existsByEmail(request.getEmail())) {
			throw new BusinessException(ErrorCode.AUTH_EMAIL_ALREADY_EXISTS);
		}
		if (request.getPhone() != null && !request.getPhone().isBlank()
				&& userRepository.existsByPhone(request.getPhone())) {
			throw new BusinessException(ErrorCode.AUTH_PHONE_ALREADY_EXISTS);
		}

		Role playerRole = roleRepository.findByCode(RoleCode.PLAYER.getCode())
				.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_ROLE_NOT_FOUND));

		User user = User.builder()
				.email(request.getEmail())
				.phone(request.getPhone())
				.passwordHash(passwordEncoder.encode(request.getPassword()))
				.role(playerRole)
				.status(UserStatus.ACTIVE)
				.build();

		user = userRepository.save(user);
		publishUserRegisteredEvent(user);

		String token = jwtUtil.generateToken(user.getId(), user.getEmail(), user.getRole().getCode());

		UserResponse userResponse = UserResponse.builder()
				.id(user.getId())
				.email(user.getEmail())
				.phone(user.getPhone())
				.role(user.getRole().getCode())
				.status(user.getStatus().name())
				.profileCompleted(false)
				.build();

		return RegisterResponse.builder()
				.token(token)
				.expiresIn(jwtUtil.getExpirationMs())
				.user(userResponse)
				.build();
	}

	private void publishUserRegisteredEvent(User user) {
		java.util.Map<String, Object> variables = new java.util.HashMap<>(mailContextBuilder.systemContext());
		mailContextBuilder.putUser(variables, user);
		eventPublisher.publishEvent(MailDomainEvent.builder()
				.eventType(EmailEventType.USER_REGISTERED)
				.variables(variables)
				.explicitRecipients(List.of(new MailRecipient(user.getId(), user.getEmail())))
				.entityKey("USER-" + user.getId())
				.build());
	}

	@Override
	public void forgotPassword(ForgotPasswordRequest request) {
		// KHÔNG được để lộ qua response việc email có tồn tại/active hay không (email enumeration) —
		// luôn trả về thành công như nhau, chỉ thực sự gửi OTP khi tài khoản tồn tại và đang ACTIVE.
		userRepository.findByEmail(request.getEmail())
				.filter(user -> user.getStatus() == UserStatus.ACTIVE)
				.ifPresent(user -> {
					try {
						String otp = otpService.generateOtp(request.getEmail());
						emailService.sendOtpEmail(request.getEmail(), otp);
					} catch (BusinessException ex) {
						// vd. AUTH_OTP_RESEND_TOO_SOON — nuốt lỗi, vẫn trả về thành công chung
						// chung để không lộ thêm tín hiệu phân biệt tài khoản tồn tại hay không.
					}
				});
	}

	@Override
	public void verifyOtp(VerifyOtpRequest request) {
		otpService.verifyOtp(request.getEmail(), request.getOtp());
	}

	@Override
	@Transactional
	public void resetPassword(ResetPasswordRequest request) {
		// verifyAndConsume: kiểm tra + xoá OTP trong 1 bước, tránh 2 request dùng cùng OTP
		// đều pass kiểm tra trước khi cái nào xoá được entry (TOCTOU).
		otpService.verifyAndConsume(request.getEmail(), request.getOtp());

		User user = userRepository.findByEmail(request.getEmail())
				.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_USER_NOT_FOUND));

		user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
		userRepository.save(user);
	}

	@Override
	@Transactional
	public void changePassword(String email, ChangePasswordRequest request) {
		User user = userRepository.findByEmail(email)
				.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_USER_NOT_FOUND));

		if (!passwordEncoder.matches(request.getOldPassword(), user.getPasswordHash())) {
			throw new BusinessException(ErrorCode.AUTH_WRONG_OLD_PASSWORD);
		}

		user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
		userRepository.save(user);
	}
}
