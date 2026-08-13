package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.config.MailProperties;
import com.capstone.su26_sep490_g2_be.entity.Branch;
import com.capstone.su26_sep490_g2_be.entity.FacebookPost;
import com.capstone.su26_sep490_g2_be.entity.Tournament;
import com.capstone.su26_sep490_g2_be.repository.FacebookPostRepository;
import com.capstone.su26_sep490_g2_be.repository.TournamentRepository;
import com.capstone.su26_sep490_g2_be.service.FacebookPublishService;
import com.capstone.su26_sep490_g2_be.service.MinioStorageService;
import com.capstone.su26_sep490_g2_be.service.TournamentPublishedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class FacebookAutoPostListener {

	private final TournamentRepository tournamentRepository;
	private final FacebookPublishService facebookPublishService;
	private final MinioStorageService minioStorageService;
	private final FacebookPostRepository facebookPostRepository;
	private final MailProperties mailProperties;

	private static final DateTimeFormatter VN_DATE = DateTimeFormatter
			.ofPattern("dd/MM/yyyy HH:mm")
			.withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

	@Async("mailTaskExecutor")
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	@Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
	public void onTournamentPublished(TournamentPublishedEvent event) {
		try {
			Optional<Tournament> opt = tournamentRepository.findById(event.tournamentId());
			if (opt.isEmpty()) {
				log.warn("Facebook auto-post: tournament {} not found", event.tournamentId());
				return;
			}

			Tournament t = opt.get();

			if (!Boolean.TRUE.equals(t.getIsShowTournament())) {
				log.info("Facebook auto-post skipped: tournament {} is not public (isShowTournament=false)", t.getId());
				return;
			}

			String tournamentUrl = mailProperties.tournamentPublicUrl(t.getId());
			String content = composeFacebookPost(t, tournamentUrl);
			String thumbnailKey = t.getThumbnailUrl();
			boolean hasImage = thumbnailKey != null && !thumbnailKey.isBlank() && minioObjectExists(thumbnailKey);

			String fbPostId;
			String postType;
			if (hasImage) {
				fbPostId = facebookPublishService.publishPhotoFromMinio(content, thumbnailKey);
				postType = "PHOTO";
				log.info("Facebook auto-post (with image) for tournament {}: postId={}", t.getId(), fbPostId);
			} else {
				fbPostId = facebookPublishService.publishTextPost(content, tournamentUrl);
				postType = "TEXT";
				log.info("Facebook auto-post (text only) for tournament {}: postId={}", t.getId(), fbPostId);
			}

			facebookPostRepository.save(FacebookPost.builder()
					.tournament(t)
					.facebookPostId(fbPostId)
					.content(content)
					.postType(postType)
					.postedAt(Instant.now())
					.build());

		} catch (Exception ex) {
			log.error("Facebook auto-post failed for tournament {}: {}", event.tournamentId(), ex.getMessage(), ex);
		}
	}

	private String composeFacebookPost(Tournament t, String tournamentUrl) {
		StringBuilder sb = new StringBuilder();

		sb.append("🏆 THÔNG BÁO GIẢI ĐẤU MỚI!\n");
		sb.append("━━━━━━━━━━━━━━━━━━━━━━\n\n");

		sb.append("🎱 ").append(t.getName()).append("\n\n");

		sb.append("📋 THÔNG TIN GIẢI ĐẤU:\n");

		if (t.getGameType() != null) {
			sb.append("• Thể loại: ").append(formatGameType(t.getGameType())).append("\n");
		}

		if (t.getFormat() != null) {
			sb.append("• Thể thức: ").append(formatTournamentFormat(t.getFormat())).append("\n");
		}

		if (t.getParticipantType() != null) {
			sb.append("• Hình thức: ").append(formatParticipantType(t.getParticipantType())).append("\n");
		}

		sb.append("• Số lượng: tối đa ").append(t.getMaxParticipants()).append(" VĐV\n");

		if (t.getEntryFee() != null && t.getEntryFee().compareTo(BigDecimal.ZERO) > 0) {
			sb.append("• Phí tham gia: ").append(formatMoney(t.getEntryFee())).append("\n");
		} else {
			sb.append("• Phí tham gia: MIỄN PHÍ\n");
		}

		if (t.getPrizePool() != null && t.getPrizePool().compareTo(BigDecimal.ZERO) > 0) {
			sb.append("• Tổng giải thưởng: ").append(formatMoney(t.getPrizePool())).append("\n");
		}

		if (t.getPrizeDescription() != null && !t.getPrizeDescription().isBlank()) {
			sb.append("• Giải thưởng: ").append(t.getPrizeDescription()).append("\n");
		}

		sb.append("\n📅 LỊCH TRÌNH:\n");

		if (Boolean.TRUE.equals(t.getIsRegister()) && t.getRegistrationDeadline() != null) {
			sb.append("• Hạn đăng ký: ").append(formatDate(t.getRegistrationDeadline())).append("\n");
		}

		if (t.getStartAt() != null) {
			sb.append("• Bắt đầu: ").append(formatDate(t.getStartAt())).append("\n");
		}

		if (t.getEndAt() != null) {
			sb.append("• Kết thúc: ").append(formatDate(t.getEndAt())).append("\n");
		}

		Branch branch = t.getBranch();
		if (branch != null) {
			sb.append("\n📍 ĐỊA ĐIỂM:\n");
			sb.append("• ").append(branch.getName()).append("\n");
			sb.append("• ").append(branch.getAddress()).append("\n");
			if (branch.getPhone() != null && !branch.getPhone().isBlank()) {
				sb.append("• Liên hệ: ").append(branch.getPhone()).append("\n");
			}
		} else if (t.getVenueName() != null) {
			sb.append("\n📍 ĐỊA ĐIỂM:\n");
			sb.append("• ").append(t.getVenueName()).append("\n");
			if (t.getVenueAddress() != null) {
				sb.append("• ").append(t.getVenueAddress()).append("\n");
			}
		}

		if (t.getDescription() != null && !t.getDescription().isBlank()) {
			sb.append("\n📝 ").append(t.getDescription()).append("\n");
		}

		sb.append("\n━━━━━━━━━━━━━━━━━━━━━━\n");
		if (Boolean.TRUE.equals(t.getIsRegister())) {
			sb.append("🔥 ĐĂNG KÝ NGAY — SỐ LƯỢNG CÓ HẠN!\n");
		}
		if (tournamentUrl != null && !tournamentUrl.isBlank()) {
			sb.append("\n🔗 Xem thông tin giải đấu:\n");
			sb.append(tournamentUrl).append("\n");
		}
		sb.append("#BTMS #Billiard #GiaiDau #Pool #BiA");

		return sb.toString();
	}

	private String formatGameType(String code) {
		return switch (code) {
			case "8_BALL" -> "Pool 8 Bi";
			case "9_BALL" -> "Pool 9 Bi";
			case "10_BALL" -> "Pool 10 Bi";
			default -> code;
		};
	}

	private String formatTournamentFormat(String code) {
		return switch (code) {
			case "SINGLE_ELIMINATION" -> "Loại trực tiếp";
			case "DOUBLE_ELIMINATION" -> "Loại trực tiếp kép";
			case "PROGRESSIVE_ROUND_ROBIN" -> "Vòng tròn loại dần + Playoff";
			default -> code;
		};
	}

	private String formatParticipantType(String type) {
		return switch (type) {
			case "SINGLE" -> "Đơn";
			case "DOUBLE" -> "Đôi";
			case "TEAM" -> "Đội";
			default -> type;
		};
	}

	private String formatDate(Instant instant) {
		return VN_DATE.format(instant);
	}

	private String formatMoney(BigDecimal amount) {
		long value = amount.longValue();
		if (value >= 1_000_000) {
			return String.format("%,.0f triệu VNĐ", amount.doubleValue() / 1_000_000);
		}
		return String.format("%,d VNĐ", value);
	}

	private boolean minioObjectExists(String key) {
		try {
			return minioStorageService.exists(key);
		} catch (Exception ex) {
			return false;
		}
	}
}
