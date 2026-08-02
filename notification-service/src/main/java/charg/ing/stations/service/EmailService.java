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
            String subject = "Подтверждение email · BatEnergy";
            String verificationUrl = baseUrl + "/api/v1/auth/verify-email?token=" + token;

            String htmlContent = buildVerificationEmailHtml(verificationUrl);

            sendEmail(toEmail, subject, htmlContent, true);
            log.info("Verification email sent to: {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send verification email to: {}", toEmail, e);
            throw new RuntimeException("Failed to send verification email", e);
        }
    }

    public void sendEmailChangeEmail(String toEmail, String token) {
        try {
            String subject = "Подтверждение новой почты · BatEnergy";
            String confirmUrl = baseUrl + "/api/v1/auth/confirm-email-change?token=" + token;

            String htmlContent = buildEmailChangeHtml(confirmUrl, toEmail);

            sendEmail(toEmail, subject, htmlContent, true);
            log.info("Email change confirmation sent to: {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send email change confirmation to: {}", toEmail, e);
            throw new RuntimeException("Failed to send email change confirmation", e);
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

    // Фирменная «шапка» письма: логотип-надпись на бренд-градиенте (amber → violet).
    // Без символа '%' в CSS — иначе поломается String.formatted().
    private static final String BRAND_HEADER = """
                <div style="background-color:#5A2E5C;background-image:linear-gradient(135deg,#FFB43A,#FFA20D,#8E4368,#5A2E5C);padding:30px 24px;text-align:center;">
                    <div style="font-size:26px;font-weight:800;color:#ffffff;letter-spacing:0.5px;">BatEnergy</div>
                    <div style="font-size:12px;color:#ffffff;opacity:0.9;margin-top:4px;">Зарядные станции для электромобилей</div>
                </div>
            """;

    private static final String BRAND_FOOTER = """
                <div style="padding:20px 30px;border-top:1px solid #eee;font-size:12px;color:#999;">
                    <p style="margin:0 0 4px;">© 2026 BatEnergy. Все права защищены.</p>
                    <p style="margin:0;">Письмо отправлено автоматически — пожалуйста, не отвечайте на него.</p>
                </div>
            """;

    private String buildVerificationEmailHtml(String url) {
        return """
                <!DOCTYPE html>
                <html lang="ru">
                <head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1.0"></head>
                <body style="margin:0;padding:0;background:#f5f6f8;font-family:Arial,Helvetica,sans-serif;color:#333;">
                    <div style="padding:24px 12px;">
                        <div style="max-width:600px;margin:0 auto;background:#ffffff;border-radius:16px;overflow:hidden;border:1px solid #ececf0;">
                            %s
                            <div style="padding:30px 30px 6px;">
                                <h2 style="color:#5A2E5C;margin:0 0 14px;font-size:21px;">Подтверждение email</h2>
                                <p style="margin:0 0 14px;font-size:15px;line-height:1.6;color:#444;">Спасибо за регистрацию в BatEnergy! Чтобы завершить её, подтвердите ваш адрес электронной почты:</p>
                                <p style="text-align:center;margin:30px 0;">
                                    <a href="%s" style="display:inline-block;padding:14px 32px;background-color:#FFA20D;color:#2A0E33;text-decoration:none;border-radius:12px;font-weight:800;font-size:15px;">Подтвердить email</a>
                                </p>
                                <p style="margin:0 0 8px;font-size:14px;color:#666;">Или скопируйте ссылку в браузер:</p>
                                <p style="background:#f3eaf3;padding:12px 14px;border-radius:10px;word-break:break-all;color:#5A2E5C;font-size:13px;margin:0 0 18px;">%s</p>
                                <p style="font-size:13px;color:#888;margin:0 0 6px;">Ссылка действительна в течение 24 часов.</p>
                                <p style="font-size:13px;color:#888;margin:0 0 16px;">Если вы не регистрировались в BatEnergy, просто проигнорируйте это письмо.</p>
                            </div>
                            %s
                        </div>
                    </div>
                </body>
                </html>
                """.formatted(BRAND_HEADER, url, url, BRAND_FOOTER);
    }

    private String buildPasswordResetEmailHtml(String url) {
        // аналогично
        return "";
    }

    private String buildEmailChangeHtml(String url, String newEmail) {
        return """
                <!DOCTYPE html>
                <html lang="ru">
                <head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1.0"></head>
                <body style="margin:0;padding:0;background:#f5f6f8;font-family:Arial,Helvetica,sans-serif;color:#333;">
                    <div style="padding:24px 12px;">
                        <div style="max-width:600px;margin:0 auto;background:#ffffff;border-radius:16px;overflow:hidden;border:1px solid #ececf0;">
                            %s
                            <div style="padding:30px 30px 6px;">
                                <h2 style="color:#5A2E5C;margin:0 0 14px;font-size:21px;">Смена электронной почты</h2>
                                <p style="margin:0 0 14px;font-size:15px;line-height:1.6;color:#444;">Вы указали адрес <b style="color:#5A2E5C;">%s</b> как новую почту для вашего аккаунта BatEnergy. Чтобы завершить смену, подтвердите этот адрес:</p>
                                <p style="text-align:center;margin:30px 0;">
                                    <a href="%s" style="display:inline-block;padding:14px 32px;background-color:#FFA20D;color:#2A0E33;text-decoration:none;border-radius:12px;font-weight:800;font-size:15px;">Подтвердить новую почту</a>
                                </p>
                                <p style="margin:0 0 8px;font-size:14px;color:#666;">Или скопируйте ссылку в браузер:</p>
                                <p style="background:#f3eaf3;padding:12px 14px;border-radius:10px;word-break:break-all;color:#5A2E5C;font-size:13px;margin:0 0 18px;">%s</p>
                                <p style="font-size:13px;color:#888;margin:0 0 6px;">Ссылка действительна 24 часа. Пока вы не перейдёте по ней, почта аккаунта не изменится, и вход выполняется по старому адресу.</p>
                                <p style="font-size:13px;color:#888;margin:0 0 16px;">Если вы не запрашивали смену почты, просто проигнорируйте это письмо.</p>
                            </div>
                            %s
                        </div>
                    </div>
                </body>
                </html>
                """.formatted(BRAND_HEADER, newEmail, url, url, BRAND_FOOTER);
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
