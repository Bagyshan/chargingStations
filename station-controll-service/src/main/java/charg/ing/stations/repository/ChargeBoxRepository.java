package charg.ing.stations.repository;

import charg.ing.stations.entity.ChargeBoxEntity;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.query.Procedure;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.locationtech.jts.geom.Point;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;


@Repository
public interface ChargeBoxRepository extends JpaRepository<ChargeBoxEntity, Long> {


    @Modifying
    @Query("UPDATE ChargeBoxEntity c SET c.version = c.version + 1 " +
            "WHERE c.id = :id AND c.version = :originalVersion")
    void incrementVersion(@Param("id") String id, @Param("originalVersion") Long originalVersion);


    @Modifying
    @Query("UPDATE ChargeBoxEntity c SET c.version = c.version + 1 " +
            "WHERE c.chargeBoxId = :chargeBoxId")
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    int incrementChargeBoxVersion(@Param("chargeBoxId") String chargeBoxId);


    @Query("SELECT cb FROM ChargeBoxEntity cb WHERE cb.chargeBoxId = :chargeBoxId")
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    ChargeBoxEntity findChargeBoxForUpdate(@Param("chargeBoxId") String chargeBoxId);

    boolean existsByChargeBoxId(String chargeBoxId);
    ChargeBoxEntity findByChargeBoxId(String chargeBoxId);

    @Modifying
    @Query("UPDATE ChargeBoxEntity c SET c.online = :online, c.lastSeenAt = :lastSeenAt " +
            "WHERE c.chargeBoxId = :chargeBoxId")
    int updateConnectivity(@Param("chargeBoxId") String chargeBoxId,
                           @Param("online") boolean online,
                           @Param("lastSeenAt") java.time.Instant lastSeenAt);

    /**
     * Гасит online у станций, от которых давно не было сигнала. NULL last_seen_at НЕ трогаем —
     * это «ещё не слышали» (после деплоя), а не «потеряли». Возвращает число помеченных offline.
     */
    @Modifying
    @Query("UPDATE ChargeBoxEntity c SET c.online = false " +
            "WHERE c.online = true AND c.lastSeenAt IS NOT NULL AND c.lastSeenAt < :threshold")
    int markStaleOffline(@Param("threshold") java.time.Instant threshold);


//    // Использование нативных функций
//    @Query(value = """
//        SELECT * FROM find_stations_in_radius(
//            :latitude,
//            :longitude,
//            :radius,
//            :maxResults
//        )
//        """, nativeQuery = true)
//    List<Object[]> findStationsInRadiusNative(
//            @Param("latitude") double latitude,
//            @Param("longitude") double longitude,
//            @Param("radius") double radius,
//            @Param("maxResults") int maxResults
//    );
//
//    // Альтернативный вариант с использованием SQL
//    @Query(value = """
//        SELECT cb.*,
//               ST_Distance(
//                   geography(cb.geolocation),
//                   geography(ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326))
//               ) as distance
//        FROM charge_box cb
//        WHERE cb.geolocation IS NOT NULL
//          AND ST_DWithin(
//            geography(cb.geolocation),
//            geography(ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)),
//            :radius
//          )
//        ORDER BY distance ASC
//        LIMIT :limit
//        """, nativeQuery = true)
//    List<ChargeBoxEntity> findStationsWithinRadius(
//            @Param("latitude") double latitude,
//            @Param("longitude") double longitude,
//            @Param("radius") double radius,
//            @Param("limit") int limit
//    );

    @Query(value = """
        SELECT cb.id,
               cb.charge_box_id,
               ST_X(cb.geolocation) as longitude,
               ST_Y(cb.geolocation) as latitude
        FROM charge_box cb
        WHERE cb.geolocation IS NOT NULL
        """, nativeQuery = true)
    List<Object[]> findAllWithCoordinates();

    // Проверка существования геолокации
    Boolean existsByChargeBoxIdAndGeolocationIsNotNull(String chargeBoxId);

    // Найти по chargeBoxId с геолокацией
    Optional<ChargeBoxEntity> findByChargeBoxIdAndGeolocationIsNotNull(String chargeBoxId);

    @Procedure(procedureName = "update_station_geolocation")
    void updateStationGeolocation(
            @Param("station_id") Integer stationId,
            @Param("station_latitude") Double latitude,
            @Param("station_longitude") Double longitude
    );
}