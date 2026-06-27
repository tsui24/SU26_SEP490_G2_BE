package com.capstone.su26_sep490_g2_be.util;

import com.capstone.su26_sep490_g2_be.entity.User;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.capstone.su26_sep490_g2_be.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SecurityUtil {

    private final UserRepository userRepository;

    /**
     * Lấy userId từ JWT credentials đã được đặt trong JwtAuthenticationFilter.
     * Dùng khi chỉ cần id mà không cần load entity.
     */
    public static Long extractUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getCredentials() instanceof Long userId)) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_TOKEN);
        }
        return userId;
    }

    /**
     * Load đầy đủ User entity của người đang đăng nhập.
     * Dùng khi cần gán approvedBy, createdBy, hay bất kỳ quan hệ nào cần User.
     */
    public User resolveCurrentUser(Authentication authentication) {
        Long userId = extractUserId(authentication);
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_USER_NOT_FOUND));
    }
}
