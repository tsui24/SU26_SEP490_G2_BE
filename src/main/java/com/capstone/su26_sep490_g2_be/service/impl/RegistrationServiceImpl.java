package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.dto.request.RejectRegistrationRequest;
import com.capstone.su26_sep490_g2_be.dto.request.SubmitTournamentRegistrationRequest;
import com.capstone.su26_sep490_g2_be.dto.response.CheckoutResponse;
import com.capstone.su26_sep490_g2_be.dto.response.PageResponse;
import com.capstone.su26_sep490_g2_be.dto.response.TournamentRegistrationResponse;
import com.capstone.su26_sep490_g2_be.entity.*;
import com.capstone.su26_sep490_g2_be.enums.EmailEventType;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.enums.ParticipantStatus;
import com.capstone.su26_sep490_g2_be.enums.PaymentStatus;
import com.capstone.su26_sep490_g2_be.enums.RegistrationStatus;
import com.capstone.su26_sep490_g2_be.enums.TournamentStatus;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.repository.ParticipantRepository;
import com.capstone.su26_sep490_g2_be.repository.PaymentRepository;
import com.capstone.su26_sep490_g2_be.repository.RegistrationFieldDefinitionRepository;
import com.capstone.su26_sep490_g2_be.repository.RegistrationFieldValueRepository;
import com.capstone.su26_sep490_g2_be.repository.RegistrationRepository;
import com.capstone.su26_sep490_g2_be.repository.TournamentRepository;
import com.capstone.su26_sep490_g2_be.repository.UserRepository;
import com.capstone.su26_sep490_g2_be.service.MailDomainEvent;
import com.capstone.su26_sep490_g2_be.service.MailRecipient;
import com.capstone.su26_sep490_g2_be.service.PayOSService;
import com.capstone.su26_sep490_g2_be.service.RegistrationFormService;
import com.capstone.su26_sep490_g2_be.service.RegistrationService;
import com.capstone.su26_sep490_g2_be.util.PageableUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RegistrationServiceImpl implements RegistrationService {

	private static final List<String> FULL_NAME_KEYS = List.of(
			"player_full_name", "player1_full_name", "full_name");
	private static final List<String> PHONE_KEYS = List.of(
			"player_phone", "player1_phone", "phone");

	private final RegistrationRepository registrationRepository;
	private final TournamentRepository tournamentRepository;
	private final UserRepository userRepository;
	private final RegistrationFormService registrationFormService;
	private final RegistrationFieldValueRepository fieldValueRepository;
	private final RegistrationFieldDefinitionRepository fieldDefinitionRepository;
	private final PaymentRepository paymentRepository;
	private final PayOSService payOSService;
	private final ParticipantRepository participantRepository;
	private final ApplicationEventPublisher eventPublisher;
	private final MailContextBuilder mailContextBuilder;

	@Override
	@Transactional
	public Registration register(Long tournamentId, Long userId, Registration registration) {
		if (registrationRepository.existsByTournamentIdAndUserId(tournamentId, userId)) {
			throw new BusinessException(ErrorCode.REGISTRATION_ALREADY_EXISTS);
		}
		Tournament tournament = tournamentRepository.findById(tournamentId)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
		if (!TournamentStatus.OPEN_FOR_REGISTRATION.getValue().equals(tournament.getStatus())) {
			throw new BusinessException(ErrorCode.REGISTRATION_NOT_OPEN);
		}
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_USER_NOT_FOUND));
		registration.setTournament(tournament);
		registration.setUser(user);
		registration.setStatus(RegistrationStatus.PENDING_PAYMENT.getValue());
		return registrationRepository.save(registration);
	}

	@Override
	@Transactional
	public TournamentRegistrationResponse submitRegistration(
			Long tournamentId, Long userId, SubmitTournamentRegistrationRequest request) {
		if (registrationRepository.existsByTournamentIdAndUserId(tournamentId, userId)) {
			throw new BusinessException(ErrorCode.REGISTRATION_ALREADY_EXISTS);
		}
		Tournament tournament = tournamentRepository.findById(tournamentId)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
		if (!Boolean.TRUE.equals(tournament.getIsRegister())) {
			throw new BusinessException(ErrorCode.INVALID_OPERATION);
		}
		if (!TournamentStatus.OPEN_FOR_REGISTRATION.getValue().equals(tournament.getStatus())) {
			throw new BusinessException(ErrorCode.REGISTRATION_NOT_OPEN);
		}
		if (tournament.getRegistrationFormTemplateId() == null) {
			throw new BusinessException(ErrorCode.REG_TEMPLATE_REQUIRED);
		}

		registrationFormService.loadActiveTemplate(tournament.getRegistrationFormTemplateId());
		Map<String, String> normalizedValues = registrationFormService.validateAndNormalizeFieldValues(
				tournament.getRegistrationFormTemplateId(), request.getFieldValues());

		User user = userRepository.findById(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_USER_NOT_FOUND));

		Registration registration = Registration.builder()
				.tournament(tournament)
				.user(user)
				.registrationType(request.getRegistrationType())
				.playerFullName(resolveValue(normalizedValues, FULL_NAME_KEYS, "N/A"))
				.playerPhone(resolveValue(normalizedValues, PHONE_KEYS, "N/A"))
				.note(request.getNote())
				.status(RegistrationStatus.PENDING_PAYMENT.getValue())
				.build();
		registration = registrationRepository.save(registration);
		registrationFormService.saveFieldValues(
				registration, tournament.getRegistrationFormTemplateId(), normalizedValues);

		// Giải miễn phí → tự động xét duyệt ngay (slot check + pessimistic lock)
		BigDecimal fee = tournament.getEntryFee();
		if (fee == null || fee.compareTo(BigDecimal.ZERO) <= 0) {
			approveOrRejectBySlot(registration);
			registration = registrationRepository.findById(registration.getId()).orElse(registration);
		}

		return toResponse(registration);
	}

	@Override
	@Transactional(readOnly = true)
	public TournamentRegistrationResponse getRegistrationDetail(
			Long registrationId, Long requestingUserId, boolean isStaff) {
		Registration registration = getById(registrationId);
		if (!isStaff) {
			if (requestingUserId == null || !registration.getUser().getId().equals(requestingUserId)) {
				throw new BusinessException(ErrorCode.AUTH_ACCESS_DENIED);
			}
		}
		return toResponse(registration);
	}

	@Override
	@Transactional(readOnly = true)
	public PageResponse<TournamentRegistrationResponse> getMyRegistrations(Long userId, int page, int size) {
		Pageable pageable = PageableUtil.create(page, size, "createdAt");
		return PageResponse.of(
				registrationRepository.findByUserId(userId, pageable),
				this::toResponse);
	}

	@Override
	@Transactional(readOnly = true)
	public PageResponse<TournamentRegistrationResponse> getTournamentRegistrations(
			Long tournamentId, String status, int page, int size) {
		tournamentRepository.findById(tournamentId)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
		Pageable pageable = PageableUtil.create(page, size, "createdAt");
		Page<Registration> result = (status == null || status.isBlank())
				? registrationRepository.findByTournamentId(tournamentId, pageable)
				: registrationRepository.findByTournamentIdAndStatus(tournamentId, status.trim(), pageable);
		return PageResponse.of(result, this::toResponse);
	}

	@Override
	public Registration getById(Long id) {
		return registrationRepository.findById(id)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
	}

	@Override
	public Page<Registration> getByTournament(Long tournamentId, Pageable pageable) {
		return registrationRepository.findByTournamentId(tournamentId, pageable);
	}

	@Override
	public Page<Registration> getByTournamentAndStatus(Long tournamentId, String status, Pageable pageable) {
		return registrationRepository.findByTournamentIdAndStatus(tournamentId, status, pageable);
	}

	@Override
	public Page<Registration> getByUser(Long userId, Pageable pageable) {
		return registrationRepository.findByUserId(userId, pageable);
	}

	@Override
	@Transactional
	public TournamentRegistrationResponse approve(Long registrationId, Long approvedByUserId) {
		Registration reg = getById(registrationId);
		User approver = userRepository.findById(approvedByUserId)
				.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_USER_NOT_FOUND));
		reg.setStatus(RegistrationStatus.APPROVED.getValue());
		reg.setApprovedBy(approver);
		reg.setApprovedAt(Instant.now());
		registrationRepository.save(reg);
		autoCreateParticipant(reg);
		publishRegistrationEvent(EmailEventType.REGISTRATION_APPROVED, reg);
		return toResponse(reg);
	}

	@Override
	@Transactional
	public TournamentRegistrationResponse reject(Long registrationId, RejectRegistrationRequest request) {
		Registration reg = getById(registrationId);
		reg.setStatus(RegistrationStatus.REJECTED.getValue());
		reg.setRejectedReason(request.getReason());
		reg.setRejectedAt(Instant.now());
		registrationRepository.save(reg);
		publishRegistrationEvent(EmailEventType.REGISTRATION_REJECTED, reg);
		return toResponse(reg);
	}

	@Override
	@Transactional
	public void cancel(Long registrationId, Long requestingUserId) {
		Registration reg = getById(registrationId);
		if (!reg.getUser().getId().equals(requestingUserId)) {
			throw new BusinessException(ErrorCode.AUTH_ACCESS_DENIED);
		}
		reg.setStatus(RegistrationStatus.CANCELLED.getValue());
		registrationRepository.save(reg);
		publishRegistrationEvent(EmailEventType.REGISTRATION_CANCELLED, reg);
	}

	/** Publish sự kiện mail cho các thay đổi trạng thái đăng ký — bỏ qua nếu không có email nhận. */
	private void publishRegistrationEvent(EmailEventType eventType, Registration reg) {
		if (reg.getUser() == null || reg.getUser().getEmail() == null) {
			return;
		}
		Map<String, Object> variables = new HashMap<>(mailContextBuilder.systemContext());
		mailContextBuilder.putRegistration(variables, reg);
		eventPublisher.publishEvent(MailDomainEvent.builder()
				.eventType(eventType)
				.tournamentId(reg.getTournament().getId())
				.variables(variables)
				.explicitRecipients(List.of(new MailRecipient(reg.getUser().getId(), reg.getUser().getEmail())))
				.entityKey("REGISTRATION-" + reg.getId())
				.build());
	}

	private TournamentRegistrationResponse toResponse(Registration registration) {
		List<TournamentRegistrationResponse.FieldValueItem> fieldValues = fieldValueRepository
				.findByRegistrationIdOrderByIdAsc(registration.getId()).stream()
				.map(value -> {
					RegistrationFieldDefinition definition = value.getFieldDefinition();
					if (definition == null) {
						definition = fieldDefinitionRepository
								.findById(value.getId().getFieldKey()).orElse(null);
					}
					return TournamentRegistrationResponse.FieldValueItem.builder()
							.fieldKey(value.getId().getFieldKey())
							.label(definition != null ? definition.getLabel() : value.getId().getFieldKey())
							.value(value.getValue())
							.build();
				})
				.toList();

		return TournamentRegistrationResponse.builder()
				.id(registration.getId())
				.tournamentId(registration.getTournament().getId())
				.tournamentName(registration.getTournament().getName())
				.userId(registration.getUser().getId())
				.registrationType(registration.getRegistrationType())
				.playerFullName(registration.getPlayerFullName())
				.playerPhone(registration.getPlayerPhone())
				.status(registration.getStatus())
				.note(registration.getNote())
				.createdAt(registration.getCreatedAt())
				.fieldValues(fieldValues)
				.build();
	}

	@Override
	@Transactional(readOnly = true)
	public TournamentRegistrationResponse getMyRegistrationForTournament(Long tournamentId, Long userId) {
		return registrationRepository.findByTournamentIdAndUserId(tournamentId, userId)
				.map(this::toResponse)
				.orElse(null);
	}

	@Override
	@Transactional
	public CheckoutResponse checkout(Long registrationId, Long userId) {
		Registration reg = getById(registrationId);
		if (!reg.getUser().getId().equals(userId)) {
			throw new BusinessException(ErrorCode.AUTH_ACCESS_DENIED);
		}
		if (RegistrationStatus.PAID.getValue().equals(reg.getStatus())
				|| RegistrationStatus.APPROVED.getValue().equals(reg.getStatus())) {
			throw new BusinessException(ErrorCode.PAYMENT_ALREADY_PAID);
		}
		Tournament tournament = reg.getTournament();
		BigDecimal fee = tournament.getEntryFee();
		if (fee == null || fee.compareTo(BigDecimal.ZERO) <= 0) {
			throw new BusinessException(ErrorCode.PAYMENT_NOT_REQUIRED);
		}

		// Tìm payment PENDING hiện có (để tái sử dụng checkout URL)
		Payment existing = paymentRepository.findByRegistrationId(registrationId).stream()
				.filter(p -> PaymentStatus.PENDING.getValue().equals(p.getStatus()) && p.getCheckoutUrl() != null)
				.findFirst()
				.orElse(null);
		if (existing != null) {
			return CheckoutResponse.builder()
					.paymentId(existing.getId())
					.registrationId(registrationId)
					.orderCode(Long.parseLong(existing.getTransactionCode() != null
							? existing.getTransactionCode().split(":")[0] : String.valueOf(existing.getId())))
					.amount(fee)
					.checkoutUrl(existing.getCheckoutUrl())
					.description(tournament.getName())
					.build();
		}

		// Tạo payment record trước để lấy ID dùng làm orderCode
		Payment payment = Payment.builder()
				.user(reg.getUser())
				.registration(reg)
				.amount(fee)
				.paymentMethod("PAYOS")
				.status(PaymentStatus.PENDING.getValue())
				.build();
		payment = paymentRepository.save(payment);

		long orderCode = payment.getId();
		String desc = tournament.getName().length() > 25
				? tournament.getName().substring(0, 25)
				: tournament.getName();

		String checkoutUrl = payOSService.createPaymentLink(orderCode, fee.longValue(), desc);

		payment.setCheckoutUrl(checkoutUrl);
		paymentRepository.save(payment);

		return CheckoutResponse.builder()
				.paymentId(payment.getId())
				.registrationId(registrationId)
				.orderCode(orderCode)
				.amount(fee)
				.checkoutUrl(checkoutUrl)
				.description(tournament.getName())
				.build();
	}

	@Override
	@Transactional
	public void markAsPaid(long orderCode, String transactionRef) {
		// orderCode == payment.id
		Payment payment = paymentRepository.findById(orderCode).orElse(null);
		if (payment == null) return;
		if (PaymentStatus.SUCCESS.getValue().equals(payment.getStatus())) return; // idempotent

		payment.setStatus(PaymentStatus.SUCCESS.getValue());
		payment.setTransactionCode(transactionRef != null ? transactionRef : String.valueOf(orderCode));
		payment.setPaidAt(Instant.now());
		paymentRepository.save(payment);

		Registration reg = payment.getRegistration();
		if (reg == null) return;
		publishPaymentSuccessEvent(payment, reg);
		if (!RegistrationStatus.PENDING_PAYMENT.getValue().equals(reg.getStatus())) return;

		approveOrRejectBySlot(reg);
	}

	private void publishPaymentSuccessEvent(Payment payment, Registration reg) {
		if (reg.getUser() == null || reg.getUser().getEmail() == null) {
			return;
		}
		Map<String, Object> variables = new HashMap<>(mailContextBuilder.systemContext());
		mailContextBuilder.putRegistration(variables, reg);
		mailContextBuilder.putPayment(variables, payment);
		eventPublisher.publishEvent(MailDomainEvent.builder()
				.eventType(EmailEventType.PAYMENT_SUCCESS)
				.tournamentId(reg.getTournament().getId())
				.variables(variables)
				.explicitRecipients(List.of(new MailRecipient(reg.getUser().getId(), reg.getUser().getEmail())))
				.entityKey("PAYMENT-" + payment.getId())
				.build());
	}

	/**
	 * Kiểm tra slot còn trống (pessimistic lock) rồi tự động APPROVED hoặc REJECTED.
	 * Phải gọi trong @Transactional.
	 */
	private void approveOrRejectBySlot(Registration reg) {
		Tournament tournament = tournamentRepository
				.findByIdWithLock(reg.getTournament().getId())
				.orElse(reg.getTournament());

		long approved = registrationRepository.countByTournamentIdAndStatus(
				tournament.getId(), RegistrationStatus.APPROVED.getValue());

		if (approved < tournament.getMaxParticipants()) {
			reg.setStatus(RegistrationStatus.APPROVED.getValue());
			registrationRepository.save(reg);
			// UC-28: Auto-create Participant từ Registration được duyệt
			autoCreateParticipant(reg);
			publishRegistrationEvent(EmailEventType.REGISTRATION_APPROVED, reg);
		} else {
			reg.setStatus(RegistrationStatus.REJECTED.getValue());
			reg.setRejectedReason("Giải đã đủ " + tournament.getMaxParticipants()
					+ " người tham gia. Liên hệ ban tổ chức để được hoàn tiền (nếu có).");
			reg.setRejectedAt(Instant.now());
			registrationRepository.save(reg);
			publishRegistrationEvent(EmailEventType.REGISTRATION_REJECTED, reg);
		}
	}

	private void autoCreateParticipant(Registration reg) {
		if (participantRepository.existsByRegistrationId(reg.getId())) return;
		Participant participant = Participant.builder()
				.tournament(reg.getTournament())
				.registration(reg)
				.participantType(reg.getTournament().getParticipantType())
				.displayName(reg.getPlayerFullName())
				.status(ParticipantStatus.ACTIVE.getValue())
				.build();
		participantRepository.save(participant);
	}

	private String resolveValue(Map<String, String> values, List<String> keys, String fallback) {
		for (String key : keys) {
			String value = values.get(key);
			if (value != null && !value.isBlank()) {
				return value;
			}
		}
		return fallback;
	}
}
