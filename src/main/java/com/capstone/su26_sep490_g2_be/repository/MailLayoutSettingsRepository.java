package com.capstone.su26_sep490_g2_be.repository;

import com.capstone.su26_sep490_g2_be.entity.MailLayoutSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MailLayoutSettingsRepository extends JpaRepository<MailLayoutSettings, Long> {

	Optional<MailLayoutSettings> findFirstByOrderByIdAsc();
}
