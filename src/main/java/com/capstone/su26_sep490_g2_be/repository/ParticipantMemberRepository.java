package com.capstone.su26_sep490_g2_be.repository;

import com.capstone.su26_sep490_g2_be.entity.ParticipantMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ParticipantMemberRepository extends JpaRepository<ParticipantMember, Long> {

	List<ParticipantMember> findByParticipantId(Long participantId);

	boolean existsByParticipantIdAndUserId(Long participantId, Long userId);
}
