package charg.ing.stations.bot;

/** Шаг диалога с пользователем в боте поддержки. */
public enum ConversationState {
    /** Ждём описание проблемы. */
    AWAIT_DESCRIPTION,
    /** Ждём контакт для связи (или /skip). */
    AWAIT_CONTACT
}
