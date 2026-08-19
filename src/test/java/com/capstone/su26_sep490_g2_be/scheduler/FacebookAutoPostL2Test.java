package com.capstone.su26_sep490_g2_be.scheduler;

import com.capstone.su26_sep490_g2_be.controller.support.AbstractControllerIntegrationTest;
import com.capstone.su26_sep490_g2_be.controller.support.TestAccounts;
import com.capstone.su26_sep490_g2_be.entity.Tournament;
import com.capstone.su26_sep490_g2_be.enums.ParticipantType;
import com.capstone.su26_sep490_g2_be.enums.TournamentStatus;
import com.capstone.su26_sep490_g2_be.repository.BranchRepository;
import com.capstone.su26_sep490_g2_be.repository.FacebookPostRepository;
import com.capstone.su26_sep490_g2_be.repository.TournamentRepository;
import com.capstone.su26_sep490_g2_be.service.FacebookPublishService;
import com.capstone.su26_sep490_g2_be.service.TournamentPublishedEvent;
import com.capstone.su26_sep490_g2_be.service.impl.FacebookAutoPostListener;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.AopTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BJ-07 FacebookAutoPostListener — gọi thẳng listener (bỏ proxy {@code @Async}) để assert
 * {@code facebook_posts}; mock client Facebook, không đăng bài thật.
 */
@TestPropertySource(properties = "spring.task.scheduling.enabled=false")
class FacebookAutoPostL2Test extends AbstractControllerIntegrationTest {

	@MockitoBean
	private FacebookPublishService facebookPublishService;

	@Autowired private FacebookAutoPostListener facebookAutoPostListener;
	@Autowired private TournamentRepository tournamentRepository;
	@Autowired private FacebookPostRepository facebookPostRepository;
	@Autowired private BranchRepository branchRepository;

	private Tournament tournament(boolean show) {
		var owner = seedUser(TestAccounts.OWNER_EMAIL);
		var branch = branchRepository.findByOwnerId(owner.getId()).stream()
				.filter(b -> b.getName().contains("Thủ Đức")).findFirst().orElseThrow();
		Instant now = Instant.now();
		return tournamentRepository.save(Tournament.builder()
				.name("L2 BJ-07 " + System.nanoTime())
				.gameType("9_BALL")
				.format("SINGLE_ELIMINATION")
				.participantType(ParticipantType.SINGLE.getValue())
				.status(TournamentStatus.OPEN_FOR_REGISTRATION.getValue())
				.maxParticipants(16)
				.tableCount(1)
				.entryFee(BigDecimal.ZERO)
				.prizePool(BigDecimal.ZERO)
				.registrationDeadline(now.plus(7, ChronoUnit.DAYS))
				.startAt(now.plus(10, ChronoUnit.DAYS))
				.endAt(now.plus(11, ChronoUnit.DAYS))
				.isShowTournament(show)
				.isPublicRatio(show)
				.isRegister(true)
				.createdBy(owner)
				.branch(branch)
				.version(0L)
				.build());
	}

	private FacebookAutoPostListener rawListener() {
		return AopTestUtils.getUltimateTargetObject(facebookAutoPostListener);
	}

	/** TC-SCH-BJ07-001 */
	@Test
	void publishedPublicTournament_writesFacebookPostRow() {
		// Nếu code sai: giải đã bật hiện công khai mà fanpage không có bài, khán giả không biết giải mới.
		when(facebookPublishService.publishTextPost(anyString(), any())).thenReturn("fb_l2_bj07_1");
		Tournament t = tournament(true);

		rawListener().onTournamentPublished(new TournamentPublishedEvent(t.getId()));

		assertEquals(1, facebookPostRepository.findByTournamentIdOrderByPostedAtDesc(t.getId()).size());
		assertEquals("fb_l2_bj07_1",
				facebookPostRepository.findByTournamentIdOrderByPostedAtDesc(t.getId()).get(0).getFacebookPostId());
	}

	/** TC-SCH-BJ07-002 */
	@Test
	void hiddenTournament_doesNotPostToFacebook() {
		// Nếu code sai: giải BTC cố tình ẩn vẫn bị đăng lên fanpage, lộ giải chưa muốn công bố.
		Tournament t = tournament(false);

		rawListener().onTournamentPublished(new TournamentPublishedEvent(t.getId()));

		assertTrue(facebookPostRepository.findByTournamentIdOrderByPostedAtDesc(t.getId()).isEmpty());
		verify(facebookPublishService, never()).publishTextPost(anyString(), any());
		verify(facebookPublishService, never()).publishPhotoFromMinio(anyString(), anyString());
	}
}
