package charg.ing.stations.service;

import charg.ing.stations.client.StationControllClient;
import charg.ing.stations.dto.request.ChargeBoxRequest;
import charg.ing.stations.dto.request.StationPatchRequest;
import charg.ing.stations.dto.response.ChargeBoxResponse;
import charg.ing.stations.entity.ChargeBoxEntity;
import charg.ing.stations.repository.ChargeBoxRepository;
import charg.ing.stations.security.JwtUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChargeBoxService {

    private final ChargeBoxRepository chargeBoxRepository;
    private final StationControllClient stationControllClient;

    public Flux<ChargeBoxResponse> getAll(Jwt jwt) {
        if (JwtUtils.isContractor(jwt)) {
            return chargeBoxRepository.findByOwnerId(JwtUtils.getUserId(jwt)).map(this::toResponse);
        }
        return chargeBoxRepository.findAll().map(this::toResponse);
    }

    public Mono<ChargeBoxResponse> getById(Integer id, Jwt jwt) {
        return chargeBoxRepository.findById(id)
                .filter(entity -> canAccess(entity, jwt))
                .map(this::toResponse);
    }

    public Mono<ChargeBoxResponse> getByChargeBoxId(String chargeBoxId, Jwt jwt) {
        return chargeBoxRepository.findByChargeBoxId(chargeBoxId)
                .filter(entity -> canAccess(entity, jwt))
                .map(this::toResponse);
    }

    public Mono<ChargeBoxResponse> create(ChargeBoxRequest request, Jwt jwt) {
        ChargeBoxEntity entity = new ChargeBoxEntity();
        applyRequest(entity, request);
        if (JwtUtils.isContractor(jwt)) {
            entity.setOwnerId(JwtUtils.getUserId(jwt));
        }
        entity.setCreatedAt(Instant.now());
        return chargeBoxRepository.save(entity).map(this::toResponse);
    }

    public Mono<ChargeBoxResponse> update(Integer id, ChargeBoxRequest request, Jwt jwt) {
        return chargeBoxRepository.findById(id)
                .filter(entity -> canAccess(entity, jwt))
                .flatMap(entity -> {
                    // station-controll — источник истины каталога: сначала применяем правку там.
                    // Если она не прошла — НЕ трогаем локальную БД, чтобы данные двух сервисов
                    // не разъехались (правка должна примениться в обоих или ни в одном).
                    StationPatchRequest patch = buildStationPatch(entity, request, jwt);
                    return stationControllClient.patchChargeBox(entity.getChargeBoxId(), patch, "Bearer " + jwt.getTokenValue())
                            .then(Mono.defer(() -> {
                                applyPatch(entity, request, jwt);
                                return chargeBoxRepository.save(entity);
                            }));
                })
                .map(this::toResponse);
    }

    private StationPatchRequest buildStationPatch(ChargeBoxEntity entity, ChargeBoxRequest request, Jwt jwt) {
        StationPatchRequest patch = new StationPatchRequest();
        patch.setChargeBoxId(entity.getChargeBoxId());
        if (request.getOcppProtocol() != null) patch.setOcppProtocol(request.getOcppProtocol());
        if (request.getChargePointVendor() != null) patch.setChargePointVendor(request.getChargePointVendor());
        if (request.getChargePointModel() != null) patch.setChargePointModel(request.getChargePointModel());
        if (request.getChargePointSerialNumber() != null) patch.setChargePointSerialNumber(request.getChargePointSerialNumber());
        if (request.getChargeBoxSerialNumber() != null) patch.setChargeBoxSerialNumber(request.getChargeBoxSerialNumber());
        if (request.getFirmwareVersion() != null) patch.setFirmwareVersion(request.getFirmwareVersion());
        if (request.getIccid() != null) patch.setIccid(request.getIccid());
        if (request.getImsi() != null) patch.setImsi(request.getImsi());
        if (request.getMeterType() != null) patch.setMeterType(request.getMeterType());
        if (request.getMeterSerialNumber() != null) patch.setMeterSerialNumber(request.getMeterSerialNumber());
        if (request.getOcppTag() != null) patch.setOcppTag(request.getOcppTag());
        if (request.getOwnerId() != null && JwtUtils.isAdminOrSpecialist(jwt)) patch.setOwnerId(request.getOwnerId());
        if (request.getPower() != null) patch.setPower(request.getPower());
        if (request.getKwCost() != null) patch.setKwCost(request.getKwCost());
        if (request.getBookingMinuteCost() != null) patch.setBookingMinuteCost(request.getBookingMinuteCost());
        if (request.getAddressId() != null) patch.setAddressId(request.getAddressId());
        if (request.getLatitude() != null && request.getLongitude() != null) {
            patch.setGeolocation(new StationPatchRequest.GeoLocationDTO(
                    request.getLatitude() + "," + request.getLongitude()
            ));
        }
        return patch;
    }

    public Mono<Void> delete(Integer id, Jwt jwt) {
        return chargeBoxRepository.findById(id)
                .filter(entity -> canAccess(entity, jwt))
                .flatMap(entity -> chargeBoxRepository.deleteById(entity.getId()));
    }

    private boolean canAccess(ChargeBoxEntity entity, Jwt jwt) {
        return JwtUtils.isAdminOrSpecialist(jwt) || JwtUtils.getUserId(jwt).equals(entity.getOwnerId());
    }

    private void applyRequest(ChargeBoxEntity entity, ChargeBoxRequest req) {
        entity.setChargeBoxId(req.getChargeBoxId());
        entity.setOcppProtocol(req.getOcppProtocol());
        entity.setChargePointVendor(req.getChargePointVendor());
        entity.setChargePointModel(req.getChargePointModel());
        entity.setChargePointSerialNumber(req.getChargePointSerialNumber());
        entity.setChargeBoxSerialNumber(req.getChargeBoxSerialNumber());
        entity.setFirmwareVersion(req.getFirmwareVersion());
        entity.setIccid(req.getIccid());
        entity.setImsi(req.getImsi());
        entity.setMeterType(req.getMeterType());
        entity.setMeterSerialNumber(req.getMeterSerialNumber());
        entity.setOcppTag(req.getOcppTag());
        entity.setOwnerId(req.getOwnerId());
        entity.setPower(req.getPower());
        entity.setKwCost(req.getKwCost());
        entity.setBookingMinuteCost(req.getBookingMinuteCost());
        entity.setAddressId(req.getAddressId());
    }

    private void applyPatch(ChargeBoxEntity entity, ChargeBoxRequest req, Jwt jwt) {
        if (req.getChargeBoxId() != null) entity.setChargeBoxId(req.getChargeBoxId());
        if (req.getOcppProtocol() != null) entity.setOcppProtocol(req.getOcppProtocol());
        if (req.getChargePointVendor() != null) entity.setChargePointVendor(req.getChargePointVendor());
        if (req.getChargePointModel() != null) entity.setChargePointModel(req.getChargePointModel());
        if (req.getChargePointSerialNumber() != null) entity.setChargePointSerialNumber(req.getChargePointSerialNumber());
        if (req.getChargeBoxSerialNumber() != null) entity.setChargeBoxSerialNumber(req.getChargeBoxSerialNumber());
        if (req.getFirmwareVersion() != null) entity.setFirmwareVersion(req.getFirmwareVersion());
        if (req.getIccid() != null) entity.setIccid(req.getIccid());
        if (req.getImsi() != null) entity.setImsi(req.getImsi());
        if (req.getMeterType() != null) entity.setMeterType(req.getMeterType());
        if (req.getMeterSerialNumber() != null) entity.setMeterSerialNumber(req.getMeterSerialNumber());
        if (req.getOcppTag() != null) entity.setOcppTag(req.getOcppTag());
        if (req.getPower() != null) entity.setPower(req.getPower());
        if (req.getKwCost() != null) entity.setKwCost(req.getKwCost());
        if (req.getBookingMinuteCost() != null) entity.setBookingMinuteCost(req.getBookingMinuteCost());
        if (req.getAddressId() != null) entity.setAddressId(req.getAddressId());
        // ownerId only changeable by ADMIN/SPECIALIST
        if (req.getOwnerId() != null && JwtUtils.isAdminOrSpecialist(jwt)) {
            entity.setOwnerId(req.getOwnerId());
        }
    }

    private ChargeBoxResponse toResponse(ChargeBoxEntity e) {
        return ChargeBoxResponse.builder()
                .id(e.getId())
                .chargeBoxId(e.getChargeBoxId())
                .ocppProtocol(e.getOcppProtocol())
                .chargePointVendor(e.getChargePointVendor())
                .chargePointModel(e.getChargePointModel())
                .chargePointSerialNumber(e.getChargePointSerialNumber())
                .chargeBoxSerialNumber(e.getChargeBoxSerialNumber())
                .firmwareVersion(e.getFirmwareVersion())
                .iccid(e.getIccid())
                .imsi(e.getImsi())
                .meterType(e.getMeterType())
                .meterSerialNumber(e.getMeterSerialNumber())
                .ocppTag(e.getOcppTag())
                .createdAt(e.getCreatedAt())
                .ownerId(e.getOwnerId())
                .power(e.getPower())
                .kwCost(e.getKwCost())
                .bookingMinuteCost(e.getBookingMinuteCost())
                .addressId(e.getAddressId())
                .build();
    }
}
