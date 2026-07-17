/*
 * SteVe - SteckdosenVerwaltung - https://github.com/steve-community/steve
 * Copyright (C) 2013-2025 SteVe Community Team
 * All Rights Reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package de.rwth.idsg.steve.ocpp.ws;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PingMessage;
import org.springframework.web.socket.WebSocketSession;

import java.nio.ByteBuffer;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * @author Sevket Goekay <sevketgokay@gmail.com>
 * @since 17.03.2015
 */
@Slf4j
@RequiredArgsConstructor
public class PingTask implements Runnable {
    private final String chargeBoxId;
    private final WebSocketSession session;

    private static final PingMessage PING_MESSAGE = new PingMessage(ByteBuffer.wrap("ping".getBytes(UTF_8)));

    @Override
    public void run() {
        WebSocketLogger.sendingPing(chargeBoxId, session);
        try {
            session.sendMessage(PING_MESSAGE);
        } catch (Exception e) {
            // Пинг не ушёл — соединение со станцией мертво (напр. у неё тихо пропал
            // интернет). Рвём сессию сами, чтобы afterConnectionClosed немедленно
            // выдал DISCONNECTED в station-controll: иначе мёртвая сессия висит до
            // idle-timeout, а при возврате станции ломает штатный CONNECTED (onOpen
            // видит «призрак» и не публикует переход 0->1).
            WebSocketLogger.pingError(chargeBoxId, session, e);
            closeDeadSession();
        }
    }

    /** Закрывает мёртвую сессию, инициируя штатный путь onClose → DISCONNECTED. */
    private void closeDeadSession() {
        try {
            session.close(CloseStatus.GOING_AWAY);
        } catch (Exception closeError) {
            log.warn("[chargeBoxId={}] Failed to close dead session: {}", chargeBoxId, closeError.getMessage());
        }
    }
}
