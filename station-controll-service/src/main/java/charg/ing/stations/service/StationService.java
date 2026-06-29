package charg.ing.stations.service;

import charg.ing.stations.dto.StationPatchDTO;
import charg.ing.stations.entity.AddressEntity;
import charg.ing.stations.entity.ChargeBoxEntity;
import charg.ing.stations.dto.StationDTO;
import charg.ing.stations.enums.ServiceStatus;
import charg.ing.stations.repository.AddressRepository;
import charg.ing.stations.repository.StationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StationService {

    private final StationRepository stationRepository;
    private final AddressRepository addressRepository;
    private final StationStateService stationStateService;

    public List<StationDTO> getAllStations() {
        log.info("Fetching all stations from database");
        return stationRepository.findAll().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public StationDTO getStationById(String stationId) {
        log.info("Fetching station by id: {}", stationId);
        ChargeBoxEntity station = stationRepository.findByChargeBoxId(stationId)
                .orElseThrow(() -> new RuntimeException("Station not found: " + stationId));
        return convertToDTO(station);
    }

    /**
     * Возвращает ocppTag, привязанный к станции в каталоге. Старт/стоп зарядки больше не
     * принимают ocppTag от клиента — он подставляется в OCPP-запрос отсюда. Источник истины
     * каталога (в т.ч. для ocppTag) — эта БД; правки приходят из contractor-admin через patch.
     */
    public String getOcppTag(String chargeBoxId) {
        return stationRepository.findByChargeBoxId(chargeBoxId)
                .map(ChargeBoxEntity::getOcppTag)
                .orElseThrow(() -> new IllegalStateException("Station not found: " + chargeBoxId));
    }

    public List<StationDTO> getStationsPaginated(int page, int size) {
        log.info("Fetching stations page: {}, size: {}", page, size);
        Pageable pageable = PageRequest.of(page, size);
        return stationRepository.findAll(pageable).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }


    @Transactional
    public StationPatchDTO patchStation(String chargeBoxId, StationPatchDTO dto) {

        ChargeBoxEntity entity = stationRepository
                .findByChargeBoxId(chargeBoxId)
                .orElseThrow(() -> new IllegalStateException("Station not found"));

        if (dto.getOcppProtocol() != null) entity.setOcppProtocol(dto.getOcppProtocol());
        if (dto.getChargePointVendor() != null) entity.setChargePointVendor(dto.getChargePointVendor());
        if (dto.getChargePointModel() != null) entity.setChargePointModel(dto.getChargePointModel());
        if (dto.getChargePointSerialNumber() != null) entity.setChargePointSerialNumber(dto.getChargePointSerialNumber());
        if (dto.getChargeBoxSerialNumber() != null) entity.setChargeBoxSerialNumber(dto.getChargeBoxSerialNumber());
        if (dto.getFirmwareVersion() != null) entity.setFirmwareVersion(dto.getFirmwareVersion());
        if (dto.getIccid() != null) entity.setIccid(dto.getIccid());
        if (dto.getImsi() != null) entity.setImsi(dto.getImsi());
        if (dto.getMeterType() != null) entity.setMeterType(dto.getMeterType());
        if (dto.getMeterSerialNumber() != null) entity.setMeterSerialNumber(dto.getMeterSerialNumber());
        if (dto.getOcppTag() != null) entity.setOcppTag(dto.getOcppTag());
        if (dto.getOwnerId() != null) entity.setOwnerId(dto.getOwnerId());
        if (dto.getPower() != null) entity.setPower(dto.getPower());
        if (dto.getKwCost() != null) entity.setKwCost(dto.getKwCost());
        if (dto.getBookingMinuteCost() != null) entity.setBookingMinuteCost(dto.getBookingMinuteCost());
        if (dto.getAddressId() != null) {
            entity.setAddress(addressRepository.getReferenceById(dto.getAddressId()));
        }

        if (dto.getGeolocation() != null && dto.getGeolocation().getCoordinates() != null) {
            Double lat = dto.getGeolocation().getLatitude();
            Double lng = dto.getGeolocation().getLongitude();
            validateCoordinates(lat, lng);
            entity.setCoordinates(lng, lat);
        }

        long newVersion = (entity.getVersion() != null ? entity.getVersion() : 0L) + 1;
        entity.setVersion(newVersion);
        stationRepository.save(entity);

        // Публикуем снимок, чтобы правка (в т.ч. сделанная через contractor-admin) дошла до
        // Redis-кэша, карты и обратной синхронизации contractor-admin (StationSyncConsumer).
        stationStateService.publishStationSnapshot(chargeBoxId, newVersion);

        return mapToPatchDto(entity);
    }

    @Transactional
    public StationDTO updateServiceStatus(String chargeBoxId, ServiceStatus serviceStatus) {
        if (serviceStatus == null) {
            throw new IllegalArgumentException("serviceStatus must not be null");
        }
        ChargeBoxEntity entity = stationRepository
                .findByChargeBoxId(chargeBoxId)
                .orElseThrow(() -> new IllegalStateException("Station not found: " + chargeBoxId));
        entity.setServiceStatus(serviceStatus);
        long newVersion = (entity.getVersion() != null ? entity.getVersion() : 0L) + 1;
        entity.setVersion(newVersion);
        stationRepository.save(entity);

        // Публикуем снимок, чтобы смена статуса дошла до Redis-кэша и фильтра доступности на карте.
        stationStateService.publishStationSnapshot(chargeBoxId, newVersion);
        log.info("Station {} service status set to {} (version {})", chargeBoxId, serviceStatus, newVersion);
        return convertToDTO(entity);
    }

    private StationDTO convertToDTO(ChargeBoxEntity station) {
        StationDTO dto = new StationDTO();
        dto.setId(String.valueOf(station.getId()));
        dto.setChargeBoxId(station.getChargeBoxId());
        dto.setVersion(Math.toIntExact(station.getVersion()));
        dto.setLastUpdated(Instant.now());
        dto.setOwnerId(station.getOwnerId());
        dto.setPower(station.getPower());
        dto.setKwCost(station.getKwCost());
        dto.setBookingMinuteCost(station.getBookingMinuteCost());
        dto.setServiceStatus(station.getServiceStatus() != null ? station.getServiceStatus().name() : null);
        dto.setOnline(station.getOnline());

        // Конвертируем коннекторы
        dto.setConnectors(station.getConnectors().stream()
                .map(connector -> {
                    StationDTO.ConnectorDTO connectorDTO = new StationDTO.ConnectorDTO();
                    connectorDTO.setConnectorId(connector.getConnectorId());
                    connectorDTO.setStatus(connector.getStatus());
                    connectorDTO.setVersion(Math.toIntExact(connector.getVersion()));
                    connectorDTO.setLastUpdated(Instant.now());
                    if (connector.getConnectorType() != null) {
                        connectorDTO.setConnectorType(new StationDTO.ConnectorTypeDTO(
                                connector.getConnectorType().getId(),
                                connector.getConnectorType().getConnectorTypeName(),
                                connector.getConnectorType().getConnectorTypeIcon()
                        ));
                    }
                    return connectorDTO;
                })
                .collect(Collectors.toList()));

        StationDTO.GeoLocationDTO geolocationDTO = new StationDTO.GeoLocationDTO();
        geolocationDTO.setLatitude(station.getLatitude());
        geolocationDTO.setLongitude(station.getLongitude());
        dto.setGeolocation(geolocationDTO);

        if (station.getAddress() != null) {
            dto.setAddress(new StationDTO.AddressDTO(
                    station.getAddress().getId(),
                    station.getAddress().getAddressName()
            ));
        }

        return dto;
    }

    private void validateCoordinates(Double lat, Double lng) {
        if (lat == null || lng == null) {
            throw new IllegalArgumentException("Coordinates must not be null");
        }
        if (lat < -90 || lat > 90) {
            throw new IllegalArgumentException("Latitude out of range");
        }
        if (lng < -180 || lng > 180) {
            throw new IllegalArgumentException("Longitude out of range");
        }
    }

    private StationPatchDTO mapToPatchDto(ChargeBoxEntity entity) {

        StationPatchDTO dto = new StationPatchDTO();

        dto.setId(String.valueOf(entity.getId()));
        dto.setChargeBoxId(entity.getChargeBoxId());
        dto.setOcppProtocol(entity.getOcppProtocol());
        dto.setChargePointVendor(entity.getChargePointVendor());
        dto.setChargePointModel(entity.getChargePointModel());
        dto.setChargePointSerialNumber(entity.getChargePointSerialNumber());
        dto.setChargeBoxSerialNumber(entity.getChargeBoxSerialNumber());
        dto.setFirmwareVersion(entity.getFirmwareVersion());
        dto.setIccid(entity.getIccid());
        dto.setImsi(entity.getImsi());
        dto.setMeterType(entity.getMeterType());
        dto.setMeterSerialNumber(entity.getMeterSerialNumber());
        dto.setOcppTag(entity.getOcppTag());
        dto.setOwnerId(entity.getOwnerId());
        dto.setPower(entity.getPower());
        dto.setKwCost(entity.getKwCost());
        dto.setBookingMinuteCost(entity.getBookingMinuteCost());
        if (entity.getAddress() != null) dto.setAddressId(entity.getAddress().getId());

        if (entity.getGeolocation() != null) {
            StationPatchDTO.GeoLocationDTO geo = new StationPatchDTO.GeoLocationDTO();
            geo.setCoordinates(
                    entity.getLatitude() + "," + entity.getLongitude()
            );
            dto.setGeolocation(geo);
        }

        return dto;
    }
}
