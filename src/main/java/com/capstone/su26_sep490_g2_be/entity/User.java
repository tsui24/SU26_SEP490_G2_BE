package com.capstone.su26_sep490_g2_be.entity;

import com.capstone.su26_sep490_g2_be.enums.UserStatus;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true)
	private String email;

	@Column(length = 20)
	private String phone;

	@Column(name = "password_hash", nullable = false, length = 500)
	private String passwordHash;

	@ManyToOne(fetch = FetchType.EAGER)
	@JoinColumn(name = "role_id", nullable = false)
	private Role role;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private UserStatus status;

	@OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
	private UserProfile profile;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "owner_id")
	private User owner;

	@Column(name = "manage_all_branches")
	@Builder.Default
	private Boolean manageAllBranches = false;

	/** Chỉ dùng cho STAFF — 1 staff làm việc tại đúng 1 chi nhánh. Manager dùng branch_managers thay vì cột này. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "branch_id")
	private Branch branch;
}
