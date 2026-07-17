--liquibase formatted sql
--changeset analyst:012-booking-price-nullable
--comment: Мирор бронирований наполняется из топика booking.events, который несёт только
--          totalSum/totalMinutes, но НЕ price_per_minute. Из-за NOT NULL на price_per_minute
--          каждая вставка падала (DataAccessException) и брони не сохранялись вовсе → пустая
--          аналитика. Делаем колонку необязательной (для аналитики цена-за-минуту не нужна —
--          выручка считается по total_sum).
ALTER TABLE booking ALTER COLUMN price_per_minute DROP NOT NULL;

--rollback ALTER TABLE booking ALTER COLUMN price_per_minute SET NOT NULL;
