package charg.ing.stations.service;

import charg.ing.stations.client.StationControllClient;
import charg.ing.stations.dto.request.ConnectorPatchRequest;
import charg.ing.stations.dto.request.ConnectorRequest;
import charg.ing.stations.dto.response.ConnectorResponse;
import charg.ing.stations.entity.ChargeBoxEntity;
import charg.ing.stations.entity.ConnectorEntity;
import charg.ing.stations.repository.ChargeBoxRepository;
import charg.ing.stations.repository.ConnectorRepository;
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
public class ConnectorService {

    private final ConnectorRepository connectorRepository;
    private final ChargeBoxRepository chargeBoxRepository;
    private final StationControllClient stationControllClient;

    public Flux<ConnectorResponse> getAll(Jwt jwt) {
        if (JwtUtils.isContractor(jwt)) {
            return chargeBoxRepository.findByOwnerId(JwtUtils.getUserId(jwt))
                    .map(ChargeBoxEntity::getChargeBoxId)
                    .collectList()
                    .flatMapMany(ids -> ids.isEmpty() ? Flux.empty() : connectorRepository.findByChargeBoxIdIn(ids))
                    .map(this::toResponse);
        }
        return connectorRepository.findAll().map(this::toResponse);
    }

    public Mono<ConnectorResponse> getById(Integer id, Jwt jwt) {
        return connectorRepository.findById(id)
                .flatMap(entity -> canAccess(entity, jwt)
                        .filter(Boolean::booleanValue)
                        .thenReturn(entity))
                .map(this::toResponse);
    }

    public Flux<ConnectorResponse> getByChargeBoxId(String chargeBoxId, Jwt jwt) {
        if (JwtUtils.isContractor(jwt)) {
            return chargeBoxRepository.findByChargeBoxId(chargeBoxId)
                    .filter(cb -> JwtUtils.getUserId(jwt).equals(cb.getOwnerId()))
                    .flatMapMany(cb -> connectorRepository.findByChargeBoxId(chargeBoxId))
                    .map(this::toResponse);
        }
        return connectorRepository.findByChargeBoxId(chargeBoxId).map(this::toResponse);
    }

    public Mono<ConnectorResponse> getByChargeBoxIdAndConnectorId(String chargeBoxId, Integer connectorId, Jwt jwt) {
        return connectorRepository.findByChargeBoxIdAndConnectorId(chargeBoxId, connectorId)
                .flatMap(entity -> canAccess(entity, jwt)
                        .filter(Boolean::booleanValue)
                        .thenReturn(entity))
                .map(this::toResponse);
    }

    public Mono<ConnectorResponse> create(ConnectorRequest request, Jwt jwt) {
        if (JwtUtils.isContractor(jwt)) {
            return chargeBoxRepository.findByChargeBoxId(request.getChargeBoxId())
                    .filter(cb -> JwtUtils.getUserId(jwt).equals(cb.getOwnerId()))
                    .flatMap(cb -> saveNew(request))
                    .map(this::toResponse);
        }
        return saveNew(request).map(this::toResponse);
    }

    public Mono<ConnectorResponse> update(Integer id, ConnectorRequest request, Jwt jwt) {
        return connectorRepository.findById(id)
                .flatMap(entity -> canAccess(entity, jwt)
                        .filter(Boolean::booleanValue)
                        .flatMap(ok -> {
                            // Сначала применяем правку в station-controll (источник истины);
                            // локальную БД трогаем только если там успех — иначе данные разъедутся.
                            ConnectorPatchRequest patch = buildConnectorPatch(request);
                            return stationControllClient.patchConnector(entity.getChargeBoxId(), entity.getConnectorId(), patch, "Bearer " + jwt.getTokenValue())
                                    .then(Mono.defer(() -> {
                                        applyPatch(entity, request);
                                        return connectorRepository.save(entity);
                                    }));
                        }))
                .map(this::toResponse);
    }

    private ConnectorPatchRequest buildConnectorPatch(ConnectorRequest request) {
        ConnectorPatchRequest patch = new ConnectorPatchRequest();
        if (request.getInfo() != null) patch.setInfo(request.getInfo());
        if (request.getVendorId() != null) patch.setVendorId(request.getVendorId());
        if (request.getConnectorTypeId() != null) patch.setConnectorTypeId(request.getConnectorTypeId());
        return patch;
    }

    public Mono<Void> delete(Integer id, Jwt jwt) {
        return connectorRepository.findById(id)
                .flatMap(entity -> canAccess(entity, jwt)
                        .filter(Boolean::booleanValue)
                        .flatMap(ok -> connectorRepository.deleteById(entity.getId())));
    }

    private Mono<ConnectorEntity> saveNew(ConnectorRequest request) {
        ConnectorEntity entity = new ConnectorEntity();
        applyRequest(entity, request);
        entity.setCreatedAt(Instant.now());
        return connectorRepository.save(entity);
    }

    private Mono<Boolean> canAccess(ConnectorEntity connector, Jwt jwt) {
        if (JwtUtils.isAdminOrSpecialist(jwt)) return Mono.just(true);
        return chargeBoxRepository.findByChargeBoxId(connector.getChargeBoxId())
                .map(cb -> JwtUtils.getUserId(jwt).equals(cb.getOwnerId()))
                .defaultIfEmpty(false);
    }

    private void applyRequest(ConnectorEntity entity, ConnectorRequest req) {
        entity.setChargeBoxId(req.getChargeBoxId());
        entity.setConnectorId(req.getConnectorId());
        entity.setInfo(req.getInfo());
        entity.setVendorId(req.getVendorId());
        entity.setStatus(req.getStatus());
        entity.setConnectorTypeId(req.getConnectorTypeId());
    }

    private void applyPatch(ConnectorEntity entity, ConnectorRequest req) {
        if (req.getChargeBoxId() != null) entity.setChargeBoxId(req.getChargeBoxId());
        if (req.getConnectorId() != null) entity.setConnectorId(req.getConnectorId());
        if (req.getInfo() != null) entity.setInfo(req.getInfo());
        if (req.getVendorId() != null) entity.setVendorId(req.getVendorId());
        if (req.getStatus() != null) entity.setStatus(req.getStatus());
        if (req.getConnectorTypeId() != null) entity.setConnectorTypeId(req.getConnectorTypeId());
    }

    private ConnectorResponse toResponse(ConnectorEntity e) {
        return ConnectorResponse.builder()
                .id(e.getId())
                .chargeBoxId(e.getChargeBoxId())
                .connectorId(e.getConnectorId())
                .info(e.getInfo())
                .createdAt(e.getCreatedAt())
                .vendorId(e.getVendorId())
                .status(e.getStatus())
                .connectorTypeId(e.getConnectorTypeId())
                .build();
    }
}
