package charg.ing.stations.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Статус смены почты для клиента. {@code pendingNewEmail} != null — есть
 * незавершённый запрос (письмо на новый адрес отправлено, но ссылка ещё не
 * подтверждена). Приложение сверяет {@code currentEmail} со своим значением,
 * чтобы понять, что смена уже применилась на сервере.
 */
@Schema(description = "Статус смены email пользователя")
public record EmailChangeStatusResponse(
        @Schema(description = "Текущий email на сервере") String currentEmail,
        @Schema(description = "Ожидающий подтверждения новый email или null") String pendingNewEmail
) {}
