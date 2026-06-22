package charg.ing.stations.service;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    @Value("${spring.mail.username:flagman-inc@yandex.ru}")
    private String fromEmail;

    @Value("${verification.email.from:flagman-inc@yandex.ru}")
    private String verificationFromEmail;

    @Value("${app.base-url:http://localhost:8005}")
    private String baseUrl;

    private final JavaMailSender mailSender;

    public Mono<Void> sendVerificationEmail(String toEmail, String token) {
        return Mono.fromRunnable(() -> {
            try {
                String subject = "Подтверждение email для Charging Stations";
                String verificationUrl = baseUrl + "/api/v1/auth/verify-email?token=" + token;

                String htmlContent = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <meta charset="UTF-8">
                        <style>
                            body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                            .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                            .header { background-color: #4CAF50; color: white; padding: 10px; text-align: center; }
                            .content { padding: 20px; background-color: #f9f9f9; }
                            .button {
                                display: inline-block;
                                padding: 12px 24px;
                                background-color: #4CAF50;
                                color: white;
                                text-decoration: none;
                                border-radius: 4px;
                                font-weight: bold;
                            }
                            .footer { margin-top: 20px; padding-top: 20px; border-top: 1px solid #ddd; font-size: 12px; color: #777; }
                        </style>
                    </head>
                    <body>
                        <div class="container">
                            <div class="header">
                                <h1>Charging Stations</h1>
                            </div>
                            <div class="content">
                                <h2>Подтверждение email</h2>
                                <p>Спасибо за регистрацию! Для завершения регистрации, пожалуйста, подтвердите ваш email:</p>
                                <p style="text-align: center; margin: 30px 0;">
                                    <a href="%s" class="button">Подтвердить Email</a>
                                </p>
                                <p>Или скопируйте ссылку в браузер:</p>
                                <p style="background-color: #eee; padding: 10px; border-radius: 4px; word-break: break-all;">
                                    %s
                                </p>
                                <p>Ссылка действительна в течение 24 часов.</p>
                                <p>Если вы не регистрировались, просто проигнорируйте это письмо.</p>
                            </div>
                            <div class="footer">
                                <p>© 2024 Charging Stations. Все права защищены.</p>
                                <p>Это письмо отправлено автоматически, пожалуйста, не отвечайте на него.</p>
                            </div>
                        </div>
                    </body>
                    </html>
                    """.formatted(verificationUrl, verificationUrl);

                sendEmail(toEmail, subject, htmlContent, true);
                log.info("Verification email sent to: {}", toEmail);

            } catch (Exception e) {
                log.error("Failed to send verification email to: {}", toEmail, e);
            }
        });
    }

    public Mono<Void> sendPasswordResetEmail(String toEmail, String token) {
        return Mono.fromRunnable(() -> {
            try {
                String subject = "Сброс пароля для Charging Stations";
                String resetUrl = baseUrl + "/api/v1/auth/password/reset?token=" + token;

                String htmlContent = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <meta charset="UTF-8">
                        <style>
                            body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                            .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                            .header { background-color: #2196F3; color: white; padding: 10px; text-align: center; }
                            .content { padding: 20px; background-color: #f9f9f9; }
                            .button {
                                display: inline-block;
                                padding: 12px 24px;
                                background-color: #2196F3;
                                color: white;
                                text-decoration: none;
                                border-radius: 4px;
                                font-weight: bold;
                            }
                            .warning { background-color: #fff3cd; border: 1px solid #ffeaa7; padding: 10px; border-radius: 4px; margin: 20px 0; }
                            .footer { margin-top: 20px; padding-top: 20px; border-top: 1px solid #ddd; font-size: 12px; color: #777; }
                        </style>
                    </head>
                    <body>
                        <div class="container">
                            <div class="header">
                                <h1>Charging Stations</h1>
                            </div>
                            <div class="content">
                                <h2>Сброс пароля</h2>
                                <p>Мы получили запрос на сброс пароля для вашего аккаунта.</p>
                                <div class="warning">
                                    <p><strong>Внимание:</strong> Если вы не запрашивали сброс пароля, пожалуйста, проигнорируйте это письмо.</p>
                                </div>
                                <p style="text-align: center; margin: 30px 0;">
                                    <a href="%s" class="button">Сбросить пароль</a>
                                </p>
                                <p>Или скопируйте ссылку в браузер:</p>
                                <p style="background-color: #eee; padding: 10px; border-radius: 4px; word-break: break-all;">
                                    %s
                                </p>
                                <p>Ссылка действительна в течение 1 часа.</p>
                            </div>
                            <div class="footer">
                                <p>© 2024 Charging Stations. Все права защищены.</p>
                                <p>Это письмо отправлено автоматически, пожалуйста, не отвечайте на него.</p>
                            </div>
                        </div>
                    </body>
                    </html>
                    """.formatted(resetUrl, resetUrl);

                sendEmail(toEmail, subject, htmlContent, true);
                log.info("Password reset email sent to: {}", toEmail);

            } catch (Exception e) {
                log.error("Failed to send password reset email to: {}", toEmail, e);
            }
        });
    }

    private void sendEmail(String to, String subject, String content, boolean isHtml) {
        if (!StringUtils.hasText(to) || !to.contains("@")) {
            log.warn("Invalid email address: {}", to);
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(verificationFromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(content, isHtml);

            mailSender.send(message);

        } catch (Exception e) {
            log.error("Failed to send email to: {}", to, e);
            throw new RuntimeException("Failed to send email", e);
        }
    }
}