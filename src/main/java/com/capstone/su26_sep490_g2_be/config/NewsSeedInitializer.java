package com.capstone.su26_sep490_g2_be.config;

import com.capstone.su26_sep490_g2_be.entity.NewsCategory;
import com.capstone.su26_sep490_g2_be.entity.NewsPost;
import com.capstone.su26_sep490_g2_be.entity.NewsTag;
import com.capstone.su26_sep490_g2_be.entity.User;
import com.capstone.su26_sep490_g2_be.enums.NewsCategoryStatus;
import com.capstone.su26_sep490_g2_be.enums.NewsPostStatus;
import com.capstone.su26_sep490_g2_be.repository.NewsCategoryRepository;
import com.capstone.su26_sep490_g2_be.repository.NewsPostRepository;
import com.capstone.su26_sep490_g2_be.repository.NewsTagRepository;
import com.capstone.su26_sep490_g2_be.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;

/**
 * Seed dữ liệu tin tức thật cho trang Tin tức.
 * <p>
 * Trước đây bảng {@code news_posts} rỗng nên {@code GET /api/v1/news} trả về danh sách trống.
 * Phía FE, {@code withDemo()} trong {@code src/constants/demoData.js} thấy rỗng thì rơi về
 * {@code DEMO_POSTS} — danh sách hiện 9 bài hard-code, nhưng bấm vào bài nào cũng gọi
 * {@code GET /api/v1/news/demo-...} và nhận 404, vì slug đó chưa từng tồn tại trong DB.
 * <p>
 * Seeder này lấp đúng chỗ đó. Khi đã có bài PUBLISHED, {@code withDemo()} luôn ưu tiên dữ liệu
 * thật nên khối demo không còn được render, và các link chết tự biến mất — không cần sửa FE.
 * Tiêu đề/ảnh/chuyên mục cố ý giữ giống bộ demo để giao diện không đổi, nhưng slug là slug thật
 * (bỏ tiền tố {@code demo-}) và mỗi bài có nội dung HTML đầy đủ cho trang chi tiết.
 * <p>
 * Idempotent theo slug — an toàn khi backend restart nhiều lần trên cùng một DB.
 * Chạy sau {@link DataInitializer} (@Order 1) vì cần user {@code owner@gmail.com} làm tác giả.
 *
 * @see com.capstone.su26_sep490_g2_be.controller.NewsController
 */
@Slf4j
@Component
@Order(5)
@RequiredArgsConstructor
public class NewsSeedInitializer implements CommandLineRunner {

	private static final String AUTHOR_EMAIL = "owner@gmail.com";

	/** Ảnh tĩnh có sẵn trong {@code public/images/} của FE — thumbnailUrl trả thẳng, không qua MinIO. */
	private static final String IMG_PLAYER = "/images/tournaments/vn-player-1.jpg";
	private static final String IMG_ACTION_1 = "/images/tournaments/action-1.jpg";
	private static final String IMG_ACTION_2 = "/images/tournaments/action-2.jpg";
	private static final String IMG_POOL_2 = "/images/tournaments/pool-2.jpg";
	private static final String IMG_POOL_4 = "/images/tournaments/pool-4.jpg";
	private static final String IMG_POOL_6 = "/images/tournaments/pool-6.jpg";

	private final UserRepository userRepository;
	private final NewsCategoryRepository categoryRepository;
	private final NewsPostRepository postRepository;
	private final NewsTagRepository tagRepository;

	@Override
	@Transactional
	public void run(String... args) {
		User author = userRepository.findByEmail(AUTHOR_EMAIL).orElse(null);
		if (author == null) {
			log.warn("NewsSeedInitializer: {} chưa tồn tại — bỏ qua.", AUTHOR_EMAIL);
			return;
		}

		NewsCategory giaiDau = seedCategory("Giải đấu", "giai-dau");
		NewsCategory ketQua = seedCategory("Kết quả", "ket-qua");
		NewsCategory thongBao = seedCategory("Thông báo", "thong-bao");
		NewsCategory chuyenMon = seedCategory("Chuyên môn", "chuyen-mon");
		NewsCategory tinHeThong = seedCategory("Tin hệ thống", "tin-he-thong");

		NewsTag tag9Ball = seedTag("9-Ball", "9-ball");
		NewsTag tag8Ball = seedTag("8-Ball", "8-ball");
		NewsTag tagGiaiDau = seedTag("Giải đấu", "tag-giai-dau");
		NewsTag tagHuongDan = seedTag("Hướng dẫn", "huong-dan");
		NewsTag tagKyThuat = seedTag("Kỹ thuật", "ky-thuat");
		NewsTag tagNguoiMoi = seedTag("Người mới", "nguoi-moi");
		NewsTag tagHeThong = seedTag("Hệ thống", "he-thong");

		int created = 0;

		created += seedPost(giaiDau, "Giải Billiards CAPSTONE Mùa 2026 chính thức khởi tranh",
				"giai-billiards-capstone-mua-2026-khoi-tranh", IMG_PLAYER,
				"2026-07-28T09:00:00Z", author, Set.of(tagGiaiDau, tag9Ball),
				"""
				<p>Sau hơn một tháng nhận đăng ký, Giải Billiards CAPSTONE Mùa 2026 đã chính thức
				khởi tranh với sự góp mặt của các cơ thủ đến từ nhiều chi nhánh trong hệ thống.</p>
				<h2>Thể thức thi đấu</h2>
				<p>Giải áp dụng thể thức loại trực tiếp, các vòng đầu đánh race-to-5, bán kết
				race-to-7 và chung kết race-to-9. Bốc thăm được thực hiện ngẫu nhiên và công bố
				công khai trên trang chi tiết giải.</p>
				<h2>Điểm mới mùa này</h2>
				<ul>
					<li>Đăng ký hoàn toàn trực tuyến, xác nhận tự động ngay sau khi thanh toán</li>
					<li>Bảng đấu và tỉ số cập nhật theo thời gian thực</li>
					<li>Thống kê phong độ từng cơ thủ sau mỗi vòng</li>
				</ul>
				<p>Theo dõi lịch thi đấu và kết quả từng trận ngay trên hệ thống để không bỏ lỡ
				những cặp đấu đáng chú ý.</p>
				""");

		created += seedPost(ketQua, "Nhìn lại những đường cơ đẹp nhất vòng bảng tuần qua",
				"nhung-duong-co-dep-nhat-vong-bang", IMG_ACTION_1,
				"2026-07-25T14:30:00Z", author, Set.of(tagKyThuat, tag9Ball),
				"""
				<p>Vòng bảng tuần qua khép lại với nhiều pha xử lý khiến khán giả phải trầm trồ.
				Cùng điểm lại những khoảnh khắc ấn tượng nhất.</p>
				<h2>Cú đánh combo ba băng</h2>
				<p>Trong trận đấu ở bảng B, một pha xử lý combo qua ba băng đã giúp cơ thủ giành
				lại thế trận khi đang bị dẫn trước hai ván.</p>
				<h2>Loạt run-out liên tiếp</h2>
				<p>Đáng chú ý nhất là loạt run-out ba ván liên tiếp — thành tích hiếm thấy ở vòng
				bảng, cho thấy sự chuẩn bị kỹ lưỡng về cả kỹ thuật lẫn thể lực.</p>
				<p>Toàn bộ tỉ số chi tiết đã được cập nhật trong mục Kết quả của từng giải đấu.</p>
				""");

		created += seedPost(thongBao, "Hướng dẫn đăng ký thi đấu trực tuyến chỉ trong 3 bước",
				"huong-dan-dang-ky-thi-dau-truc-tuyen", IMG_POOL_4,
				"2026-07-22T08:15:00Z", author, Set.of(tagHuongDan, tagNguoiMoi),
				"""
				<p>Hệ thống đăng ký trực tuyến giúp bạn giữ suất thi đấu chỉ trong vài phút,
				không cần tới trực tiếp chi nhánh.</p>
				<h2>Bước 1 — Chọn giải đấu</h2>
				<p>Vào mục Giải đấu, chọn giải đang mở đăng ký. Trang chi tiết hiển thị rõ lệ phí,
				số suất còn lại và hạn chót đăng ký.</p>
				<h2>Bước 2 — Điền form đăng ký</h2>
				<p>Họ tên và số điện thoại được điền sẵn từ hồ sơ của bạn, có thể sửa lại nếu cần.
				Với giải đôi, bạn điền thêm thông tin đồng đội.</p>
				<h2>Bước 3 — Thanh toán</h2>
				<p>Giải miễn phí sẽ được xác nhận ngay nếu còn suất. Giải có phí sẽ chuyển sang
				cổng thanh toán; hoàn tất là bạn chính thức có tên trong danh sách.</p>
				<p>Bạn có thể theo dõi trạng thái đơn trong mục "Đăng ký của tôi" bất cứ lúc nào.</p>
				""");

		created += seedPost(tinHeThong, "Hệ thống CAPSTONE mở rộng thêm cụm bàn thi đấu tiêu chuẩn",
				"mo-rong-cum-ban-thi-dau-tieu-chuan", IMG_POOL_6,
				"2026-07-19T11:00:00Z", author, Set.of(tagHeThong),
				"""
				<p>Nhằm đáp ứng lượng đăng ký tăng nhanh trong mùa giải, hệ thống vừa đưa vào vận
				hành thêm một cụm bàn thi đấu đạt chuẩn.</p>
				<h2>Thay đổi cho người chơi</h2>
				<ul>
					<li>Nhiều khung giờ trống hơn vào cuối tuần</li>
					<li>Giảm thời gian chờ giữa các trận trong ngày thi đấu cao điểm</li>
					<li>Bàn mới dùng chung tiêu chuẩn với bàn thi đấu chính thức</li>
				</ul>
				<p>Sơ đồ bàn và tình trạng sử dụng được cập nhật trực tiếp trên hệ thống đặt bàn.</p>
				""");

		created += seedPost(chuyenMon, "Luật thi đấu 9-Ball áp dụng từ mùa giải mới có gì thay đổi?",
				"luat-thi-dau-9-ball-mua-giai-moi", IMG_ACTION_2,
				"2026-07-15T16:45:00Z", author, Set.of(tag9Ball, tagKyThuat),
				"""
				<p>Mùa giải mới đi kèm một số điều chỉnh trong luật 9-Ball. Nắm rõ trước khi vào
				trận sẽ giúp bạn tránh những lỗi không đáng có.</p>
				<h2>Về cú phá</h2>
				<p>Bi số 1 vẫn phải được chạm đầu tiên. Yêu cầu về số bi chạm băng sau cú phá được
				quy định lại rõ ràng hơn để hạn chế tranh cãi.</p>
				<h2>Về lỗi và quyền ball-in-hand</h2>
				<p>Sau mỗi lỗi, đối thủ nhận ball-in-hand trên toàn bàn. Trọng tài là người đưa ra
				quyết định cuối cùng trong các tình huống gây tranh cãi.</p>
				<h2>Về thời gian suy nghĩ</h2>
				<p>Một số giải áp dụng đồng hồ bấm giờ cho mỗi lượt đánh. Thông tin cụ thể luôn được
				ghi trong điều lệ của từng giải, hãy đọc kỹ trước khi đăng ký.</p>
				""");

		created += seedPost(chuyenMon, "Bí quyết giữ ổn định tâm lý trong những ván cờ quyết định",
				"giu-on-dinh-tam-ly-van-quyet-dinh", IMG_POOL_2,
				"2026-07-11T10:20:00Z", author, Set.of(tagKyThuat),
				"""
				<p>Ở trình độ càng cao, khoảng cách kỹ thuật giữa các cơ thủ càng thu hẹp — và tâm
				lý trở thành yếu tố phân định thắng thua.</p>
				<h2>Giữ nhịp thở và nhịp đánh</h2>
				<p>Nhiều cơ thủ đánh nhanh hơn bình thường khi bị dẫn điểm. Giữ đúng nhịp quen thuộc
				là cách đơn giản nhất để không kéo theo sai lầm dây chuyền.</p>
				<h2>Tập trung vào cú đánh trước mắt</h2>
				<p>Nghĩ về tỉ số hay về trận sau chỉ làm phân tán sự tập trung. Mỗi lần lên bàn, chỉ
				xử lý đúng tình huống đang có.</p>
				<h2>Chuẩn bị trước trận</h2>
				<p>Thói quen khởi động cố định giúp cơ thể và đầu óc vào guồng nhanh hơn, đặc biệt
				với những trận đấu bắt đầu muộn.</p>
				""");

		created += seedPost(ketQua, "Tổng kết vòng bảng: những con số đáng chú ý",
				"tong-ket-vong-bang-nhung-con-so-dang-chu-y", IMG_ACTION_1,
				"2026-07-08T13:05:00Z", author, Set.of(tagGiaiDau, tag8Ball),
				"""
				<p>Vòng bảng đã khép lại. Dưới đây là những số liệu đáng chú ý nhất được tổng hợp
				từ hệ thống thống kê.</p>
				<h2>Tỉ lệ thắng khi giành quyền phá</h2>
				<p>Cơ thủ giành quyền phá thắng ván đó với tỉ lệ nhỉnh hơn rõ rệt, cho thấy tầm quan
				trọng của cú phá trong thể thức race ngắn.</p>
				<h2>Trận đấu dài nhất</h2>
				<p>Trận căng nhất vòng bảng kéo dài tới ván cuối cùng, với nhiều lượt đổi quyền
				liên tiếp ở ván quyết định.</p>
				<p>Bạn có thể xem thống kê chi tiết theo từng cơ thủ trong mục Thống kê.</p>
				""");

		created += seedPost(chuyenMon, "Cách chọn cơ phù hợp cho người mới bắt đầu",
				"cach-chon-co-cho-nguoi-moi-bat-dau", IMG_POOL_4,
				"2026-07-04T09:40:00Z", author, Set.of(tagNguoiMoi, tagHuongDan),
				"""
				<p>Cây cơ đầu tiên không cần đắt, nhưng cần vừa tay. Dưới đây là những điểm nên
				cân nhắc.</p>
				<h2>Trọng lượng</h2>
				<p>Phổ biến nhất là khoảng 19 oz. Cơ nhẹ dễ kiểm soát đường bi, cơ nặng cho lực phá
				tốt hơn. Người mới nên bắt đầu ở mức trung bình rồi điều chỉnh dần.</p>
				<h2>Đầu cơ</h2>
				<p>Đầu cơ độ cứng trung bình là lựa chọn an toàn: đủ bám để tạo xoáy cơ bản mà không
				quá khó kiểm soát.</p>
				<h2>Lời khuyên thực tế</h2>
				<p>Hãy mượn thử vài cây khác nhau tại chi nhánh trước khi mua. Cảm giác cầm cơ là thứ
				không thông số nào mô tả thay được.</p>
				""");

		created += seedPost(thongBao, "Lịch bảo trì hệ thống đặt bàn trực tuyến tháng này",
				"lich-bao-tri-he-thong-dat-ban", IMG_POOL_6,
				"2026-07-01T07:00:00Z", author, Set.of(tagHeThong),
				"""
				<p>Hệ thống đặt bàn trực tuyến sẽ được bảo trì định kỳ để nâng cấp hạ tầng và cải
				thiện tốc độ.</p>
				<h2>Thời gian</h2>
				<p>Việc bảo trì diễn ra vào khung giờ thấp điểm ban đêm, dự kiến trong khoảng hai
				giờ đồng hồ.</p>
				<h2>Ảnh hưởng</h2>
				<ul>
					<li>Tạm thời không đặt bàn trực tuyến được trong thời gian bảo trì</li>
					<li>Các đặt chỗ đã xác nhận trước đó không bị ảnh hưởng</li>
					<li>Đăng ký giải đấu và tra cứu kết quả vẫn hoạt động bình thường</li>
				</ul>
				<p>Rất mong người chơi thông cảm cho sự bất tiện này.</p>
				""");

		if (created > 0) {
			log.info("NewsSeedInitializer: đã tạo {} bài viết mẫu (5 chuyên mục, 7 thẻ)", created);
		}
	}

	private NewsCategory seedCategory(String name, String slug) {
		return categoryRepository.findBySlug(slug).orElseGet(() -> categoryRepository.save(
				NewsCategory.builder()
						.name(name)
						.slug(slug)
						.status(NewsCategoryStatus.ACTIVE.getValue())
						.build()));
	}

	private NewsTag seedTag(String name, String slug) {
		return tagRepository.findBySlug(slug).orElseGet(() -> tagRepository.save(
				NewsTag.builder().name(name).slug(slug).build()));
	}

	/** @return 1 nếu vừa tạo mới, 0 nếu bài đã tồn tại (chạy lại không nhân bản dữ liệu). */
	private int seedPost(NewsCategory category, String title, String slug, String thumbnailUrl,
			String publishedAt, User author, Set<NewsTag> tags, String content) {
		if (postRepository.existsBySlug(slug)) return 0;
		postRepository.save(NewsPost.builder()
				.category(category)
				.title(title)
				.slug(slug)
				.thumbnailUrl(thumbnailUrl)
				.content(content)
				.status(NewsPostStatus.PUBLISHED.getValue())
				.publishedAt(Instant.parse(publishedAt))
				.createdBy(author)
				.tags(new java.util.HashSet<>(tags))
				.build());
		return 1;
	}
}
