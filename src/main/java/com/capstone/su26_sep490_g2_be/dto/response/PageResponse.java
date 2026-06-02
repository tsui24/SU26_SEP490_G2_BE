package com.capstone.su26_sep490_g2_be.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Wrapper phục vụ phân trang cho Frontend")
public class PageResponse<T> {

	@Schema(description = "Danh sách dữ liệu chính")
	private List<T> content;

	@Schema(description = "Trang hiện tại (0-indexed)")
	private int pageNumber;

	@Schema(description = "Số lượng phần tử trên 1 trang")
	private int pageSize;

	@Schema(description = "Tổng số phần tử trong DB")
	private long totalElements;

	@Schema(description = "Tổng số trang")
	private int totalPages;

	@Schema(description = "Có phải trang cuối cùng không")
	@JsonProperty("isLast")
	private boolean isLast;

	/**
	 * Tạo PageResponse từ Page của Spring (giữ nguyên kiểu dữ liệu)
	 *
	 * @param page đối tượng Page của Spring
	 * @param <T>  kiểu dữ liệu phần tử
	 * @return PageResponse đã được chuẩn hóa
	 */
	public static <T> PageResponse<T> of(Page<T> page) {
		if (page == null) {
			return null;
		}
		return PageResponse.<T>builder()
				.content(page.getContent())
				.pageNumber(page.getNumber())
				.pageSize(page.getSize())
				.totalElements(page.getTotalElements())
				.totalPages(page.getTotalPages())
				.isLast(page.isLast())
				.build();
	}

	/**
	 * Tạo PageResponse từ Page của Spring kèm theo hàm mapper để convert Entity sang DTO
	 *
	 * @param page   đối tượng Page của Spring chứa các phần tử gốc (ví dụ: Entity)
	 * @param mapper hàm biến đổi từ kiểu S sang kiểu T (ví dụ: Entity -> DTO)
	 * @param <S>    kiểu dữ liệu gốc
	 * @param <T>    kiểu dữ liệu đích
	 * @return PageResponse chứa dữ liệu đã được map sang DTO
	 */
	public static <S, T> PageResponse<T> of(Page<S> page, Function<S, T> mapper) {
		if (page == null) {
			return null;
		}
		List<T> mappedContent = page.getContent().stream()
				.map(mapper)
				.collect(Collectors.toList());

		return PageResponse.<T>builder()
				.content(mappedContent)
				.pageNumber(page.getNumber())
				.pageSize(page.getSize())
				.totalElements(page.getTotalElements())
				.totalPages(page.getTotalPages())
				.isLast(page.isLast())
				.build();
	}

	/**
	 * Phân trang in-memory khi filter không map trực tiếp xuống DB (vd. setupStatus tính toán).
	 */
	public static <T> PageResponse<T> of(List<T> all, int page, int size) {
		if (all == null) {
			return null;
		}
		int safeSize = Math.max(size, 1);
		int safePage = Math.max(page, 0);
		int total = all.size();
		int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / safeSize);
		int from = Math.min(safePage * safeSize, total);
		int to = Math.min(from + safeSize, total);

		return PageResponse.<T>builder()
				.content(all.subList(from, to))
				.pageNumber(safePage)
				.pageSize(safeSize)
				.totalElements(total)
				.totalPages(totalPages)
				.isLast(totalPages == 0 || safePage >= totalPages - 1)
				.build();
	}
}
