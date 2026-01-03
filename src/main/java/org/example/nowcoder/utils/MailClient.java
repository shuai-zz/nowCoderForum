package org.example.nowcoder.utils;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * @author 23211
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class MailClient {
    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    public String sender;

    public void sendMail(String receiver, String subject, String content) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage);

            helper.setFrom(sender);
            helper.setTo(receiver);
            helper.setSubject( subject);
            helper.setText(content, true);
            mailSender.send(mimeMessage);
        } catch (MessagingException e) {
            log.error("Failed to send email:{}", e.getMessage());
        }

    }
}
