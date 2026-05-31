package com.capstone.su26_sep490_g2_be.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "news_categories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NewsCategory extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String name;

	@Column(nullable = false, unique = true)
	private String slug;

	@Column(length = 30, nullable = false)
	@Builder.Default
	private String status = "ACTIVE";
}
