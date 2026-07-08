package com.capstone.su26_sep490_g2_be.service;

/** userId có thể null khi email không gắn với tài khoản nào (vd. custom list nhập tay). */
public record MailRecipient(Long userId, String email) {
}
