package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

	private final JavaMailSender mailSender;

	@Override
	public void sendOtpEmail(String to, String otp) {
		SimpleMailMessage message = new SimpleMailMessage();
		message.setTo(to);
		message.setSubject("Mã OTP đặt lại mật khẩu - Giải đấu Bi-a");
		message.setText("Mã OTP của bạn là: " + otp + "\n\nMã có hiệu lực trong 5 phút.\nVui lòng không chia sẻ mã này với bất kỳ ai.");

		mailSender.send(message);
		log.info("OTP email sent to {}", to);
	}
}
