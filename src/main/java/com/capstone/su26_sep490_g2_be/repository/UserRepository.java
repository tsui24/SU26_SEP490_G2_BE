package com.capstone.su26_sep490_g2_be.repository;

import com.capstone.su26_sep490_g2_be.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

	Optional<User> findByEmail(String email);

	boolean existsByEmail(String email);
	boolean existsByPhone(String phoneNumber);

	@Query("SELECT u FROM User u JOIN u.role r LEFT JOIN u.profile p " +
		   "WHERE r.code IN ('STAFF', 'MANAGER') " +
		   "AND (:roleCode IS NULL OR r.code = :roleCode) " +
		   "AND (:search IS NULL OR LOWER(p.fullName) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(p.displayName) LIKE LOWER(CONCAT('%', :search, '%')))")
	List<User> searchEmployees(@Param("roleCode") String roleCode, @Param("search") String search);

	@Query("SELECT u FROM User u JOIN u.role r LEFT JOIN u.profile p " +
		   "WHERE r.code = 'STAFF' " +
		   "AND (:search IS NULL OR " +
		   "     LOWER(p.fullName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
		   "     LOWER(p.displayName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
		   "     LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
		   "     u.phone LIKE CONCAT('%', :search, '%'))")
	List<User> searchStaffsByManager(@Param("search") String search);
}
