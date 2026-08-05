package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.entity.Branch;
import com.capstone.su26_sep490_g2_be.entity.FacebookPost;
import com.capstone.su26_sep490_g2_be.entity.Tournament;
import com.capstone.su26_sep490_g2_be.repository.FacebookPostRepository;
import com.capstone.su26_sep490_g2_be.repository.TournamentRepository;
import com.capstone.su26_sep490_g2_be.service.FacebookPublishService;
import com.capstone.su26_sep490_g2_be.service.MinioStorageService;
import com.capstone.su26_sep490_g2_be.service.TournamentPublishedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * L1 unit tests for {@link FacebookAutoPostListener}.
 *
 * <p>Mirrors the <b>FacebookAutoPost</b> sheet in Report 5.1_UnitTests_L1.xlsx.
 * Spec source: UCS Report 3.1 — FT-22, the social publishing use cases (UC-52…53). Like the email
 * wave, the sub-numbers are filled in when that wave is written up.
 *
 * <p>This listener writes a public Facebook post the moment a tournament opens for registration.
 * Nobody proof-reads it, so what the composer puts in — and leaves out — is the whole risk: an
 * entry fee, a prize figure and a venue address go out to the page exactly as assembled here.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("L1 · FacebookAutoPost — FT-22")
class FacebookAutoPostListenerTest {

	@Mock TournamentRepository tournamentRepository;
	@Mock FacebookPublishService facebookPublishService;
	@Mock MinioStorageService minioStorageService;
	@Mock FacebookPostRepository facebookPostRepository;

	@InjectMocks FacebookAutoPostListener listener;

	private static final Long TOURNAMENT_ID = 77L;
	private static final TournamentPublishedEvent EVENT =
			TournamentPublishedEvent.builder().tournamentId(TOURNAMENT_ID).build();

	/** A public tournament with every optional field left empty. */
	private static Tournament.TournamentBuilder publicTournament() {
		return Tournament.builder()
				.id(TOURNAMENT_ID).name("Summer Open 2026")
				.isShowTournament(true).isRegister(false)
				.maxParticipants(32)
				.entryFee(BigDecimal.ZERO);
	}

	private String publishedText(Tournament tournament) {
		when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(tournament));
		when(facebookPublishService.publishTextPost(anyString(), eq(null))).thenReturn("fb_1");

		listener.onTournamentPublished(EVENT);

		ArgumentCaptor<String> content = ArgumentCaptor.forClass(String.class);
		verify(facebookPublishService).publishTextPost(content.capture(), eq(null));
		return content.getValue();
	}

	// ══════════════════════════ the publishing flow ══════════════════════════

	@Test
	@DisplayName("TC-001 · An event for a tournament that no longer exists is dropped")
	void TC001_onPublished_tournamentGone() {
		when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.empty());

		listener.onTournamentPublished(EVENT);

		// The listener runs after commit and asynchronously, so the row may be gone by then
		verify(facebookPublishService, never()).publishTextPost(anyString(), any());
		verify(facebookPostRepository, never()).save(any());
	}

	@Test
	@DisplayName("TC-002 · A tournament kept off the public site is never posted")
	void TC002_onPublished_notPublic() {
		when(tournamentRepository.findById(TOURNAMENT_ID))
				.thenReturn(Optional.of(publicTournament().isShowTournament(false).build()));

		listener.onTournamentPublished(EVENT);

		// Posting one would publish to the world exactly what the organiser chose to hide
		verify(facebookPublishService, never()).publishTextPost(anyString(), any());
		verify(facebookPostRepository, never()).save(any());
	}

	@Test
	@DisplayName("TC-003 · A tournament with a usable thumbnail is posted as a photo")
	void TC003_onPublished_photoPost() {
		when(tournamentRepository.findById(TOURNAMENT_ID))
				.thenReturn(Optional.of(publicTournament().thumbnailUrl("tournaments/77.jpg").build()));
		when(minioStorageService.exists("tournaments/77.jpg")).thenReturn(true);
		when(facebookPublishService.publishPhotoFromMinio(anyString(), eq("tournaments/77.jpg")))
				.thenReturn("fb_photo_1");

		listener.onTournamentPublished(EVENT);

		ArgumentCaptor<FacebookPost> saved = ArgumentCaptor.forClass(FacebookPost.class);
		verify(facebookPostRepository).save(saved.capture());
		assertEquals("fb_photo_1", saved.getValue().getFacebookPostId());
		assertEquals("PHOTO", saved.getValue().getPostType());
		assertEquals(TOURNAMENT_ID, saved.getValue().getTournament().getId());
		// The content is stored alongside the id so the page history stays readable in our own UI
		assertTrue(saved.getValue().getContent().contains("Summer Open 2026"));
		verify(facebookPublishService, never()).publishTextPost(anyString(), any());
	}

	@Test
	@DisplayName("TC-004 · A tournament with no thumbnail is posted as text")
	void TC004_onPublished_textPost() {
		when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(publicTournament().build()));
		when(facebookPublishService.publishTextPost(anyString(), eq(null))).thenReturn("fb_text_1");

		listener.onTournamentPublished(EVENT);

		ArgumentCaptor<FacebookPost> saved = ArgumentCaptor.forClass(FacebookPost.class);
		verify(facebookPostRepository).save(saved.capture());
		assertEquals("TEXT", saved.getValue().getPostType());
		verify(minioStorageService, never()).exists(anyString());
	}

	@Test
	@DisplayName("TC-005 · A blank thumbnail column is read as no image")
	void TC005_onPublished_blankThumbnail() {
		when(tournamentRepository.findById(TOURNAMENT_ID))
				.thenReturn(Optional.of(publicTournament().thumbnailUrl("   ").build()));
		when(facebookPublishService.publishTextPost(anyString(), eq(null))).thenReturn("fb_text_1");

		listener.onTournamentPublished(EVENT);

		verify(minioStorageService, never()).exists(anyString());
		verify(facebookPublishService).publishTextPost(anyString(), eq(null));
	}

	@Test
	@DisplayName("TC-006 · A thumbnail whose file has gone falls back to a text post")
	void TC006_onPublished_thumbnailMissingInStorage() {
		when(tournamentRepository.findById(TOURNAMENT_ID))
				.thenReturn(Optional.of(publicTournament().thumbnailUrl("tournaments/77.jpg").build()));
		when(minioStorageService.exists("tournaments/77.jpg")).thenReturn(false);
		when(facebookPublishService.publishTextPost(anyString(), eq(null))).thenReturn("fb_text_1");

		listener.onTournamentPublished(EVENT);

		// A dangling key must cost the picture, not the announcement
		verify(facebookPublishService, never()).publishPhotoFromMinio(anyString(), anyString());
		verify(facebookPublishService).publishTextPost(anyString(), eq(null));
	}

	@Test
	@DisplayName("TC-007 · Storage being unreachable is read as no image, not as a failure")
	void TC007_onPublished_storageThrows() {
		when(tournamentRepository.findById(TOURNAMENT_ID))
				.thenReturn(Optional.of(publicTournament().thumbnailUrl("tournaments/77.jpg").build()));
		when(minioStorageService.exists("tournaments/77.jpg")).thenThrow(new RuntimeException("MinIO down"));
		when(facebookPublishService.publishTextPost(anyString(), eq(null))).thenReturn("fb_text_1");

		listener.onTournamentPublished(EVENT);

		verify(facebookPostRepository).save(any(FacebookPost.class));
	}

	@Test
	@DisplayName("TC-008 · Facebook refusing the post does not break the publishing transaction")
	void TC008_onPublished_publishThrows() {
		when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(publicTournament().build()));
		when(facebookPublishService.publishTextPost(anyString(), eq(null)))
				.thenThrow(new RuntimeException("Graph API 400"));

		assertDoesNotThrow(() -> listener.onTournamentPublished(EVENT));

		// The listener runs after the commit; letting it throw would only lose the log line,
		// while the tournament itself is already open for registration
		verify(facebookPostRepository, never()).save(any());
	}

	@Test
	@DisplayName("TC-009 · A failure recording the post is swallowed too")
	void TC009_onPublished_saveThrows() {
		when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(publicTournament().build()));
		when(facebookPublishService.publishTextPost(anyString(), eq(null))).thenReturn("fb_text_1");
		when(facebookPostRepository.save(any(FacebookPost.class)))
				.thenThrow(new RuntimeException("constraint violation"));

		// The post is already on the page by this point — there is nothing left to roll back
		assertDoesNotThrow(() -> listener.onTournamentPublished(EVENT));
	}

	// ══════════════════════════ what the post says ══════════════════════════

	@Test
	@DisplayName("TC-010 · The post leads with the tournament name and closes with the hashtags")
	void TC010_compose_skeleton() {
		String post = publishedText(publicTournament().build());

		assertTrue(post.startsWith("🏆 THÔNG BÁO GIẢI ĐẤU MỚI!"));
		assertTrue(post.contains("🎱 Summer Open 2026"));
		assertTrue(post.contains("• Số lượng: tối đa 32 VĐV"));
		assertTrue(post.endsWith("#BTMS #Billiard #GiaiDau #Pool #BiA"));
	}

	@Test
	@DisplayName("TC-011 · The three codes are written out in Vietnamese")
	void TC011_compose_codesTranslated() {
		String post = publishedText(publicTournament()
				.gameType("9_BALL").format("SINGLE_ELIMINATION").participantType("DOUBLE").build());

		// The page audience is players, not the API — a raw code would read as a bug
		assertTrue(post.contains("• Thể loại: Pool 9 Bi"));
		assertTrue(post.contains("• Thể thức: Loại trực tiếp"));
		assertTrue(post.contains("• Hình thức: Đôi"));
	}

	@Test
	@DisplayName("TC-012 · A code with no translation is printed as it stands")
	void TC012_compose_unknownCodesPassThrough() {
		String post = publishedText(publicTournament()
				.gameType("CAROM_3C").format("PROGRESSIVE_ROUND_ROBIN").participantType("TEAM").build());

		// A new game type added by an Admin must not stop the announcement going out
		assertTrue(post.contains("• Thể loại: CAROM_3C"));
		assertTrue(post.contains("• Thể thức: PROGRESSIVE_ROUND_ROBIN"));
		assertTrue(post.contains("• Hình thức: Đội"));
	}

	@Test
	@DisplayName("TC-013 · Details that were never filled in are left out entirely")
	void TC013_compose_nullDetailsOmitted() {
		String post = publishedText(publicTournament().build());

		assertFalse(post.contains("• Thể loại:"));
		assertFalse(post.contains("• Thể thức:"));
		assertFalse(post.contains("• Hình thức:"));
		// An empty bullet would look worse on the page than no bullet
		assertFalse(post.contains("null"));
	}

	@Test
	@DisplayName("TC-014 · A tournament with no entry fee is advertised as free")
	void TC014_compose_freeEntry() {
		String post = publishedText(publicTournament().entryFee(BigDecimal.ZERO).build());

		assertTrue(post.contains("• Phí tham gia: MIỄN PHÍ"));
	}

	@Test
	@DisplayName("TC-015 · An entry fee below a million is printed in đồng")
	void TC015_compose_feeUnderOneMillion() {
		String post = publishedText(publicTournament().entryFee(new BigDecimal("300000")).build());

		// The grouping separator follows the JVM locale, so only the digits are asserted
		assertTrue(post.matches("(?s).*• Phí tham gia: 300[.,]000 VNĐ.*"));
	}

	@Test
	@DisplayName("TC-016 · A fee of a million or more is printed in millions")
	void TC016_compose_feeInMillions() {
		String post = publishedText(publicTournament().entryFee(new BigDecimal("2000000")).build());

		assertTrue(post.contains("• Phí tham gia: 2 triệu VNĐ"));
	}

	@Test
	@DisplayName("TC-017 · A prize of one and a half million is advertised as two million")
	void TC017_compose_prizeRoundedUp() {
		String post = publishedText(publicTournament().prizePool(new BigDecimal("1500000")).build());

		// %,.0f rounds half up, so a 1.500.000 prize goes onto the public page as "2 triệu VNĐ"
		assertTrue(post.contains("• Tổng giải thưởng: 2 triệu VNĐ"));
	}

	@Test
	@DisplayName("TC-018 · A tournament with no prize pool says nothing about one")
	void TC018_compose_zeroPrizeOmitted() {
		String post = publishedText(publicTournament().prizePool(BigDecimal.ZERO).build());

		assertFalse(post.contains("Tổng giải thưởng"));
	}

	@Test
	@DisplayName("TC-019 · A written prize description is added under the figure")
	void TC019_compose_prizeDescription() {
		String post = publishedText(publicTournament()
				.prizePool(new BigDecimal("20000000")).prizeDescription("Nhất 10tr, nhì 6tr, ba 4tr").build());

		assertTrue(post.contains("• Tổng giải thưởng: 20 triệu VNĐ"));
		assertTrue(post.contains("• Giải thưởng: Nhất 10tr, nhì 6tr, ba 4tr"));
	}

	@Test
	@DisplayName("TC-020 · A blank prize description is skipped")
	void TC020_compose_blankPrizeDescriptionOmitted() {
		String post = publishedText(publicTournament().prizeDescription("   ").build());

		assertFalse(post.contains("• Giải thưởng:"));
	}

	@Test
	@DisplayName("TC-021 · A tournament taking entries shows the deadline and the call to action")
	void TC021_compose_registrationOpen() {
		String post = publishedText(publicTournament()
				.isRegister(true)
				.registrationDeadline(Instant.parse("2026-06-01T02:00:00Z"))
				.startAt(Instant.parse("2026-06-10T03:30:00Z"))
				.endAt(Instant.parse("2026-06-12T10:00:00Z"))
				.build());

		// Formatted in Vietnam time whatever the server clock is set to
		assertTrue(post.contains("• Hạn đăng ký: 01/06/2026 09:00"));
		assertTrue(post.contains("• Bắt đầu: 10/06/2026 10:30"));
		assertTrue(post.contains("• Kết thúc: 12/06/2026 17:00"));
		assertTrue(post.contains("🔥 ĐĂNG KÝ NGAY"));
	}

	@Test
	@DisplayName("TC-022 · A tournament not taking entries omits the deadline and the call to action")
	void TC022_compose_registrationClosed() {
		String post = publishedText(publicTournament()
				.isRegister(false)
				.registrationDeadline(Instant.parse("2026-06-01T02:00:00Z"))
				.startAt(Instant.parse("2026-06-10T03:30:00Z"))
				.build());

		// An invitational still gets its schedule, just no sign-up prompt it cannot honour
		assertFalse(post.contains("Hạn đăng ký"));
		assertFalse(post.contains("ĐĂNG KÝ NGAY"));
		assertTrue(post.contains("• Bắt đầu: 10/06/2026 10:30"));
	}

	@Test
	@DisplayName("TC-023 · A tournament held at a branch takes its address from the branch")
	void TC023_compose_branchVenue() {
		String post = publishedText(publicTournament()
				.branch(Branch.builder().id(1L).name("Chi nhánh Quận 1")
						.address("12 Nguyễn Huệ, Quận 1").phone("02838001234").build())
				.venueName("Không dùng đến")
				.build());

		assertTrue(post.contains("📍 ĐỊA ĐIỂM:"));
		assertTrue(post.contains("• Chi nhánh Quận 1"));
		assertTrue(post.contains("• 12 Nguyễn Huệ, Quận 1"));
		assertTrue(post.contains("• Liên hệ: 02838001234"));
		// The branch wins over the free-text venue, so the two cannot contradict each other
		assertFalse(post.contains("Không dùng đến"));
	}

	@Test
	@DisplayName("TC-024 · A branch with no phone number simply omits the contact line")
	void TC024_compose_branchWithoutPhone() {
		String post = publishedText(publicTournament()
				.branch(Branch.builder().id(1L).name("Chi nhánh Quận 7").address("99 Nguyễn Thị Thập").build())
				.build());

		assertTrue(post.contains("• Chi nhánh Quận 7"));
		assertFalse(post.contains("Liên hệ:"));
	}

	@Test
	@DisplayName("TC-025 · A tournament held elsewhere uses the free-text venue")
	void TC025_compose_freeTextVenue() {
		String post = publishedText(publicTournament()
				.venueName("CLB Bida Sao Mai").venueAddress("45 Lê Lợi, Quận 3").build());

		assertTrue(post.contains("• CLB Bida Sao Mai"));
		assertTrue(post.contains("• 45 Lê Lợi, Quận 3"));
	}

	@Test
	@DisplayName("TC-026 · A venue with no address given prints only its name")
	void TC026_compose_venueWithoutAddress() {
		String post = publishedText(publicTournament().venueName("CLB Bida Sao Mai").build());

		assertTrue(post.contains("📍 ĐỊA ĐIỂM:"));
		assertTrue(post.contains("• CLB Bida Sao Mai"));
	}

	@Test
	@DisplayName("TC-027 · With neither a branch nor a venue the location block is dropped")
	void TC027_compose_noVenueBlock() {
		String post = publishedText(publicTournament().build());

		assertFalse(post.contains("📍 ĐỊA ĐIỂM:"));
	}

	@Test
	@DisplayName("TC-028 · The description is appended only when there is one")
	void TC028_compose_description() {
		assertTrue(publishedText(publicTournament().description("Giải mở rộng toàn miền Nam").build())
				.contains("📝 Giải mở rộng toàn miền Nam"));
	}

	@Test
	@DisplayName("TC-029 · A blank description adds no empty block")
	void TC029_compose_blankDescriptionOmitted() {
		assertFalse(publishedText(publicTournament().description("   ").build()).contains("📝"));
	}
}
