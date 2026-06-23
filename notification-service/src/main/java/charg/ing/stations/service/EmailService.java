package charg.ing.stations.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    @Value("${app.verification.email.from:bagishan01@gmail.com}")
    private String fromEmail;

    @Value("${app.base-url:http://localhost:8005}")
    private String baseUrl;

    private final JavaMailSender mailSender;

    public void sendVerificationEmail(String toEmail, String token) {
        try {
            String subject = "Подтверждение email для Charging Stations";
            String verificationUrl = baseUrl + "/api/v1/auth/verify-email?token=" + token;

            String htmlContent = buildVerificationEmailHtml(verificationUrl);

            sendEmail(toEmail, subject, htmlContent, true);
            log.info("Verification email sent to: {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send verification email to: {}", toEmail, e);
            throw new RuntimeException("Failed to send verification email", e);
        }
    }

    public void sendPasswordResetEmail(String toEmail, String token) {
        try {
            String subject = "Сброс пароля для Charging Stations";
//            String resetUrl = baseUrl + "/api/v1/auth/reset-password/confirm?token=" + token;


//            String htmlContent = buildPasswordResetEmailHtml(resetUrl);

            sendEmail(toEmail, subject, token, true);
            log.info("Password reset email sent to: {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send password reset email to: {}", toEmail, e);
            throw new RuntimeException("Failed to send password reset email", e);
        }
    }

    public void sendStationFaultedEmail(String toEmail, java.util.Map<String, Object> metadata) {
        try {
            Object chargeBoxId = metadata != null ? metadata.get("chargeBoxId") : null;
            Object connectorId = metadata != null ? metadata.get("connectorId") : null;
            Object status = metadata != null ? metadata.get("status") : null;
            Object errorCode = metadata != null ? metadata.get("errorCode") : null;

            String subject = "⚠ Неисправность зарядной станции " + (chargeBoxId != null ? chargeBoxId : "");
            String htmlContent = buildStationFaultedHtml(chargeBoxId, connectorId, status, errorCode);

            sendEmail(toEmail, subject, htmlContent, true);
            log.info("Station faulted email sent to: {}", toEmail);
        } catch (Exception e) {
            // Не пробрасываем — сбой одного получателя не должен ломать рассылку остальным.
            log.error("Failed to send station faulted email to: {}", toEmail, e);
        }
    }

    @Async("mailExecutor")
    protected void sendEmail(String to, String subject, String content, boolean isHtml) throws MessagingException {
        if (!StringUtils.hasText(to) || !to.contains("@")) {
            log.warn("Invalid email address: {}", to);
            return;
        }

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(fromEmail);
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(content, isHtml);

        mailSender.send(message);
    }

    private String buildVerificationEmailHtml(String url) {
        return """
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
                    """.formatted(url, url);
    }

    private String buildPasswordResetEmailHtml(String url) {
        // аналогично
        return "";
    }

    private String buildStationFaultedHtml(Object chargeBoxId, Object connectorId, Object status, Object errorCode) {
        return """
                    <!DOCTYPE html>
                    <html>
                    <head><meta charset="UTF-8">
                        <style>
                            body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                            .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                            .header { background-color: #c0392b; color: white; padding: 10px; text-align: center; }
                            .content { padding: 20px; background-color: #f9f9f9; }
                            table { width: 100%%; border-collapse: collapse; margin-top: 10px; }
                            td { padding: 8px; border-bottom: 1px solid #ddd; }
                            .label { color: #777; width: 40%%; }
                        </style>
                    </head>
                    <body>
                        <div class="container">
                            <div class="header"><h2>⚠ Неисправность зарядной станции</h2></div>
                            <div class="content">
                                <p>Зафиксирована неисправность станции. Требуется внимание оператора.</p>
                                <table>
                                    <tr><td class="label">Станция</td><td>%s</td></tr>
                                    <tr><td class="label">Коннектор</td><td>%s</td></tr>
                                    <tr><td class="label">Статус</td><td>%s</td></tr>
                                    <tr><td class="label">Код ошибки</td><td>%s</td></tr>
                                </table>
                            </div>
                        </div>
                    </body>
                    </html>
                    """.formatted(
                String.valueOf(chargeBoxId),
                connectorId != null ? String.valueOf(connectorId) : "-",
                String.valueOf(status),
                errorCode != null ? String.valueOf(errorCode) : "-");
    }
}