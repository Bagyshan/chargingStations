package charg.ing.stations.bot;

import lombok.Data;

/** Состояние диалога одного чата. Хранится в памяти до завершения обращения. */
@Data
public class BotSession {
    private ConversationState state;
    private String description;

    public BotSession(ConversationState state) {
        this.state = state;
    }
}
