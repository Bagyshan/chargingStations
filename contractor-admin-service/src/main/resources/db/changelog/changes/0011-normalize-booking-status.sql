--liquibase formatted sql
--changeset analyst:011-normalize-booking-status
--comment: Мирор бронирований раньше писал в booking.status тип события
--          (START_RESERVATION/STOP_RESERVATION) вместо каноничного статуса, из-за чего
--          аналитика (фильтр по COMPLETED) ничего не находила. Нормализуем существующие строки.
UPDATE booking SET status = 'COMPLETED' WHERE status = 'STOP_RESERVATION';
UPDATE booking SET status = 'ACTIVE' WHERE status = 'START_RESERVATION';

--rollback UPDATE booking SET status = 'STOP_RESERVATION' WHERE status = 'COMPLETED';
--rollback UPDATE booking SET status = 'START_RESERVATION' WHERE status = 'ACTIVE';
