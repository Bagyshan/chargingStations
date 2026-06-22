package charg.ing.stations.entity;

import charg.ing.stations.enums.ServiceStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.Transaction;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import java.math.BigDecimal;

import java.time.Instant;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "charge_box", uniqueConstraints = @UniqueConstraint(columnNames = {"charge_box_id"}))
public class ChargeBoxEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(name = "charge_box_id", nullable = false, unique = true)
    private String chargeBoxId;

    @Column(name = "ocpp_protocol")
    private String ocppProtocol;

    @Column(name = "charge_point_vendor")
    private String chargePointVendor;

    @Column(name = "charge_point_model")
    private String chargePointModel;

    @Column(name = "charge_point_serial_number")
    private String chargePointSerialNumber;

    @Column(name = "charge_box_serial_number")
    private String chargeBoxSerialNumber;

    @Column(name = "firmware_version")
    private String firmwareVersion;

    @Column(name = "iccid")
    private String iccid;

    @Column(name = "imsi")
    private String imsi;

    @Column(name = "meter_type")
    private String meterType;

    @Column(name = "meter_serial_number")
    private String meterSerialNumber;

    @Column(name = "ocpp_tag")
    private String ocppTag;

    @Column(name = "action_type")
    private String actionType;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "owner_id")
    private String ownerId;

    @Column(name = "power")
    private String power;

    @Column(name = "kw_cost")
    private BigDecimal kwCost;

    @Column(name = "booking_minute_cost")
    private BigDecimal bookingMinuteCost;

    /** Административный статус станции (выведена ли оператором из эксплуатации). */
    @Enumerated(EnumType.STRING)
    @Column(name = "service_status", length = 20, nullable = false)
    private ServiceStatus serviceStatus = ServiceStatus.IN_SERVICE;

    /** На связи ли станция (по OCPP-websocket к SteVe). Гасится свипом при отсутствии heartbeat. */
    @Column(name = "online", nullable = false)
    private Boolean online = true;

    /** Время последнего сигнала от станции (CONNECTED/HEARTBEAT). */
    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    // Геолокация поле
    @Column(name = "geolocation", columnDefinition = "geometry(Point,4326)")
//    @JdbcTypeCode(SqlTypes.POINT)
    private Point geolocation;

    @Version
    @Column(name = "version")
    private Long version;

    @OneToMany(mappedBy = "chargeBox", fetch = FetchType.EAGER)
    private List<ConnectorEntity> connectors;

    @OneToMany(mappedBy = "chargeBox")
    private List<TransactionEntity> transactions;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "address_id")
    private AddressEntity address;


    // Методы для удобной работы с координатами
    public Double getLatitude() {
        return geolocation != null ? geolocation.getY() : null;
    }

    public Double getLongitude() {
        return geolocation != null ? geolocation.getX() : null;
    }

    public void setCoordinates(Double longitude, Double latitude) {
        if (longitude != null && latitude != null) {
            this.geolocation = GEOMETRY_FACTORY.createPoint(
                    new Coordinate(longitude, latitude)
            );
        }
    }

    private static final GeometryFactory GEOMETRY_FACTORY =
            new GeometryFactory(new PrecisionModel(), 4326);

    private Point createPoint(Double longitude, Double latitude) {
        // Это создаст объект Point с правильной SRID (4326)
        // Вам может понадобиться GeometryFactory в зависимости от реализации
        org.locationtech.jts.geom.Coordinate coordinate =
                new org.locationtech.jts.geom.Coordinate(longitude, latitude);
        org.locationtech.jts.geom.GeometryFactory factory =
                new org.locationtech.jts.geom.GeometryFactory();
        return factory.createPoint(coordinate);
    }
}