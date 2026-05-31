package com.capstone.su26_sep490_g2_be.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "news_tags")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NewsTag {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(length = 100, nullable = false)
	private String name;

	@Column(length = 100, nullable = false, unique = true)
	private String slug;
}
