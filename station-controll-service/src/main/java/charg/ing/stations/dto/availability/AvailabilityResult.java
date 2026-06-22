package charg.ing.stations.dto.availability;

import charg.ing.stations.enums.UnavailabilityReason;

/**
 * Результат проверки доступности станции/коннектора. При {@code available == false} несёт
 * типизированную {@link UnavailabilityReason} и человекочитаемое сообщение.
 */
public record AvailabilityResult(boolean available, UnavailabilityReason reason, String message) {

    public static AvailabilityResult ok() {
        return new AvailabilityResult(true, null, null);
    }

    public static AvailabilityResult deny(UnavailabilityReason reason, String message) {
        return new AvailabilityResult(false, reason, message);
    }
}
