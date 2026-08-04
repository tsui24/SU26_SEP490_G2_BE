package com.capstone.su26_sep490_g2_be.util;

import com.capstone.su26_sep490_g2_be.entity.Participant;
import com.capstone.su26_sep490_g2_be.entity.ParticipantMember;
import com.capstone.su26_sep490_g2_be.entity.User;
import com.capstone.su26_sep490_g2_be.enums.ParticipantMemberRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * L1 unit tests for {@link ParticipantMemberFactory}.
 *
 * <p>Mirrors the <b>ParticipantMemberFactory</b> sheet in Report 5.1_UnitTests_L1.xlsx.
 * Spec source: UCS Report 3.1 — UC-28 (a doubles entry becomes one participant of two members).
 *
 * <p>Three separate flows create a doubles participant — online registration, manual entry and the
 * spreadsheet import — and all three go through this class so the composed name and the member
 * roles cannot drift apart between them.
 */
@DisplayName("L1 · ParticipantMemberFactory — UC-28")
class ParticipantMemberFactoryTest {

	private static final Participant PARTICIPANT = Participant.builder().id(4L).build();
	private static final User CAPTAIN_ACCOUNT = User.builder().id(11L).email("player@btms.vn").build();

	// ══════════════════════════ composeDoubleDisplayName ══════════════════════════

	@Test
	@DisplayName("TC-001 · Two three-word names are shortened to first and last")
	void TC001_compose_shortensVietnameseNames() {
		assertEquals("Nguyễn A/Trần B",
				ParticipantMemberFactory.composeDoubleDisplayName("Nguyễn Văn A", "Trần Thị B"),
				"the middle name is dropped so a pair still fits one bracket cell");
	}

	@Test
	@DisplayName("TC-002 · A two-word name is kept whole")
	void TC002_compose_keepsShortNames() {
		assertEquals("Nguyễn A/Trần B",
				ParticipantMemberFactory.composeDoubleDisplayName("Nguyễn A", "Trần B"));
	}

	@Test
	@DisplayName("TC-003 · A single-word name is kept whole")
	void TC003_compose_singleWordName() {
		assertEquals("Alice/Bob", ParticipantMemberFactory.composeDoubleDisplayName("Alice", "Bob"));
	}

	@Test
	@DisplayName("TC-004 · A four-word name keeps only its first and last word")
	void TC004_compose_longName() {
		assertEquals("Nguyễn C/Trần D",
				ParticipantMemberFactory.composeDoubleDisplayName("Nguyễn Thị Bích C", "Trần Văn Minh D"));
	}

	@Test
	@DisplayName("TC-005 · Extra spaces between words are collapsed")
	void TC005_compose_collapsesWhitespace() {
		assertEquals("Nguyễn A/Trần B",
				ParticipantMemberFactory.composeDoubleDisplayName("  Nguyễn   Văn   A  ", " Trần Thị B "));
	}

	@Test
	@DisplayName("TC-006 · A missing name leaves an empty side rather than the word null")
	void TC006_compose_nullName() {
		assertEquals("/Trần B", ParticipantMemberFactory.composeDoubleDisplayName(null, "Trần Thị B"));
	}

	// ══════════════════════════ buildDoubleMembers ══════════════════════════

	@Test
	@DisplayName("TC-007 · A pair is recorded as a captain and a partner")
	void TC007_buildMembers_assignsRoles() {
		List<ParticipantMember> members = ParticipantMemberFactory.buildDoubleMembers(
				PARTICIPANT, "Nguyễn Văn A", "0901234567", CAPTAIN_ACCOUNT, "Trần Thị B", "0907654321");

		assertEquals(2, members.size());
		assertEquals(ParticipantMemberRole.CAPTAIN.name(), members.get(0).getRole());
		assertEquals(ParticipantMemberRole.PARTNER.name(), members.get(1).getRole());
	}

	@Test
	@DisplayName("TC-008 · Each member keeps their own full name and phone number")
	void TC008_buildMembers_keepsFullDetails() {
		List<ParticipantMember> members = ParticipantMemberFactory.buildDoubleMembers(
				PARTICIPANT, "Nguyễn Văn A", "0901234567", CAPTAIN_ACCOUNT, "Trần Thị B", "0907654321");

		assertEquals("Nguyễn Văn A", members.get(0).getFullName(),
				"the member row keeps the full name even though the display name is shortened");
		assertEquals("0901234567", members.get(0).getPhone());
		assertEquals("Trần Thị B", members.get(1).getFullName());
		assertEquals("0907654321", members.get(1).getPhone());
	}

	@Test
	@DisplayName("TC-009 · Only the captain is linked to a user account")
	void TC009_buildMembers_onlyCaptainHasAccount() {
		List<ParticipantMember> members = ParticipantMemberFactory.buildDoubleMembers(
				PARTICIPANT, "Nguyễn Văn A", "0901234567", CAPTAIN_ACCOUNT, "Trần Thị B", "0907654321");

		assertEquals(11L, members.get(0).getUser().getId());
		assertNull(members.get(1).getUser(),
				"the partner is named by the person registering and need not have an account");
	}

	@Test
	@DisplayName("TC-010 · An imported pair has no account on either side")
	void TC010_buildMembers_importedPairHasNoAccount() {
		List<ParticipantMember> members = ParticipantMemberFactory.buildDoubleMembers(
				PARTICIPANT, "Nguyễn Văn A", "0901234567", null, "Trần Thị B", "0907654321");

		assertNull(members.get(0).getUser());
		assertNull(members.get(1).getUser());
	}

	@Test
	@DisplayName("TC-011 · Both members point back at the same participant")
	void TC011_buildMembers_sharesParticipant() {
		List<ParticipantMember> members = ParticipantMemberFactory.buildDoubleMembers(
				PARTICIPANT, "Nguyễn Văn A", "0901234567", CAPTAIN_ACCOUNT, "Trần Thị B", null);

		assertEquals(PARTICIPANT, members.get(0).getParticipant());
		assertEquals(PARTICIPANT, members.get(1).getParticipant());
		assertNull(members.get(1).getPhone(), "a partner's phone number is optional");
	}
}
