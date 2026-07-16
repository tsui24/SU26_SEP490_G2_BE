package com.capstone.su26_sep490_g2_be.util;

import com.capstone.su26_sep490_g2_be.entity.Participant;
import com.capstone.su26_sep490_g2_be.entity.ParticipantMember;
import com.capstone.su26_sep490_g2_be.entity.User;
import com.capstone.su26_sep490_g2_be.enums.ParticipantMemberRole;
import lombok.experimental.UtilityClass;

import java.util.List;

/**
 * Xây dựng displayName ghép + 2 {@link ParticipantMember} cho participant đôi.
 * Dùng chung cho cả 3 nơi tạo participant đôi (đăng ký online, thêm tay, import Excel)
 * để tránh 3 nơi lệch logic ghép tên/gán role.
 */
@UtilityClass
public class ParticipantMemberFactory {

	public static String composeDoubleDisplayName(String name1, String name2) {
		return shorten(name1) + "/" + shorten(name2);
	}

	public static List<ParticipantMember> buildDoubleMembers(
			Participant participant,
			String name1, String phone1, User user1,
			String name2, String phone2) {
		return List.of(
				ParticipantMember.builder()
						.participant(participant)
						.user(user1)
						.fullName(name1)
						.phone(phone1)
						.role(ParticipantMemberRole.CAPTAIN.name())
						.build(),
				ParticipantMember.builder()
						.participant(participant)
						.user(null)
						.fullName(name2)
						.phone(phone2)
						.role(ParticipantMemberRole.PARTNER.name())
						.build());
	}

	/** Rút gọn tên kiểu Việt Nam: giữ họ (từ đầu) + tên (từ cuối), bỏ chữ đệm ở giữa. */
	private static String shorten(String fullName) {
		if (fullName == null) return "";
		String trimmed = fullName.trim();
		String[] tokens = trimmed.split("\\s+");
		if (tokens.length < 3) {
			return trimmed;
		}
		return tokens[0] + " " + tokens[tokens.length - 1];
	}
}
