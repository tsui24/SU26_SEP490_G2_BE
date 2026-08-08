package com.capstone.su26_sep490_g2_be.repository;

import com.capstone.su26_sep490_g2_be.entity.EmailSendLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

/**
 * Đọc {@code email_send_logs} dưới góc nhìn "hộp thông báo của một người dùng".
 *
 * Tách riêng khỏi {@link EmailSendLogRepository} — vốn phục vụ màn quản trị lịch sử gửi mail —
 * để không phải sửa file đang chạy, và vì hai bên lọc theo tiêu chí khác hẳn nhau: bên kia tra
 * cứu theo giải đấu và template, bên này luôn khoá cứng theo người nhận.
 *
 * Spring Data cho phép nhiều repository trên cùng một entity, nên đây không phải bảng mới.
 */
public interface NotificationLogRepository extends JpaRepository<EmailSendLog, Long> {

	/**
	 * Thông báo của một người: chỉ lấy bản ghi đã gửi thành công. Bản QUEUED chưa chắc tới nơi,
	 * còn FAILED/CANCELLED là chuyện nội bộ của hệ thống mail — hiện lên app chỉ gây hoang mang.
	 */
	@EntityGraph(attributePaths = { "tournament", "rule" })
	@Query("""
		SELECT l FROM EmailSendLog l
		WHERE l.recipientUser.id = :userId
		AND l.status = 'SENT'
		""")
	Page<EmailSendLog> findMine(@Param("userId") Long userId, Pageable pageable);

	/**
	 * Đếm thông báo mới hơn mốc đã đọc để hiện badge trên chuông.
	 * {@code after} null nghĩa là người dùng chưa từng mở màn thông báo — đếm toàn bộ.
	 */
	@Query("""
		SELECT COUNT(l) FROM EmailSendLog l
		WHERE l.recipientUser.id = :userId
		AND l.status = 'SENT'
		AND (:after IS NULL OR l.createdAt > :after)
		""")
	long countUnread(@Param("userId") Long userId, @Param("after") Instant after);
}
