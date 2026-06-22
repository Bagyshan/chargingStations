package charg.ing.stations.service;


import charg.ing.stations.dto.connector_type.ConnectorTypeRequest;
import charg.ing.stations.dto.connector_type.ConnectorTypeResponse;
import charg.ing.stations.entity.ConnectorTypeEntity;
import charg.ing.stations.repository.ConnectorTypeRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
@Slf4j
public class ConnectorTypeService {

    private final ConnectorTypeRepository connectorTypeRepository;
    private final IconStorageService iconStorageService;

    public ConnectorTypeService(ConnectorTypeRepository connectorTypeRepository,
                                IconStorageService iconStorageService) {
        this.connectorTypeRepository = connectorTypeRepository;
        this.iconStorageService = iconStorageService;
    }

    public Flux<ConnectorTypeResponse> getAllConnectorTypes() {
        return Flux.defer(() -> Flux.fromIterable(connectorTypeRepository.findAll()))
                .subscribeOn(Schedulers.boundedElastic())
                .map(this::toResponse);
    }

    public Mono<ConnectorTypeResponse> getConnectorTypeById(Integer id) {
        return Mono.fromCallable(() -> connectorTypeRepository.findById(id)
                        .orElseThrow(() -> new IllegalArgumentException("Connector type not found with id: " + id)))
                .subscribeOn(Schedulers.boundedElastic())
                .map(this::toResponse);
    }

    public Mono<ConnectorTypeResponse> createConnectorType(String name, FilePart iconFilePart) {
        return Mono.fromCallable(() -> {
                    // Проверка уникальности имени
                    if (connectorTypeRepository.existsByConnectorTypeName(name)) {
                        throw new IllegalArgumentException("Connector type with name '" + name + "' already exists");
                    }
                    // Создаем сущность
                    ConnectorTypeEntity entity = new ConnectorTypeEntity();
                    entity.setConnectorTypeName(name);
                    return connectorTypeRepository.save(entity);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(entity -> {
                    if (iconFilePart == null) {
                        return Mono.just(toResponse(entity));
                    }
                    // Сохраняем иконку
                    return iconStorageService.saveIcon(iconFilePart, entity.getId())
                            .flatMap(iconPath -> {
                                entity.setConnectorTypeIcon(iconPath);
                                return Mono.fromCallable(() -> connectorTypeRepository.save(entity))
                                        .subscribeOn(Schedulers.boundedElastic());
                            })
                            .map(this::toResponse);
                });
    }

    public Mono<ConnectorTypeResponse> updateConnectorType(Integer id, String name, FilePart iconFilePart) {
        return Mono.fromCallable(() -> {
                    ConnectorTypeEntity entity = connectorTypeRepository.findById(id)
                            .orElseThrow(() -> new IllegalArgumentException("Connector type not found with id: " + id));
                    // Проверка уникальности имени
                    if (!entity.getConnectorTypeName().equals(name) &&
                            connectorTypeRepository.existsByConnectorTypeNameAndIdNot(name, id)) {
                        throw new IllegalArgumentException("Connector type with name '" + name + "' already exists");
                    }
                    entity.setConnectorTypeName(name);
                    return entity;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(entity -> {
                    if (iconFilePart == null) {
                        return Mono.fromCallable(() -> connectorTypeRepository.save(entity))
                                .subscribeOn(Schedulers.boundedElastic())
                                .map(this::toResponse);
                    }
                    // Удаляем старую иконку и сохраняем новую
                    return iconStorageService.deleteIcon(entity.getConnectorTypeIcon())
                            .then(iconStorageService.saveIcon(iconFilePart, entity.getId()))
                            .flatMap(iconPath -> {
                                entity.setConnectorTypeIcon(iconPath);
                                return Mono.fromCallable(() -> connectorTypeRepository.save(entity))
                                        .subscribeOn(Schedulers.boundedElastic());
                            })
                            .map(this::toResponse);
                });
    }

    public Mono<Void> deleteConnectorType(Integer id) {
        return Mono.fromCallable(() -> connectorTypeRepository.findByIdWithConnectors(id)
                        .orElseThrow(() -> new IllegalArgumentException("Connector type not found with id: " + id)))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(entity -> {
                    if (entity.getConnectors() != null && !entity.getConnectors().isEmpty()) {
                        return Mono.error(new IllegalStateException(
                                "Cannot delete connector type that has associated connectors. Count: " +
                                        entity.getConnectors().size()));
                    }
                    return iconStorageService.deleteIcon(entity.getConnectorTypeIcon())
                            .then(Mono.fromRunnable(() -> connectorTypeRepository.delete(entity))
                                    .subscribeOn(Schedulers.boundedElastic()));
                })
                .then();
    }

//    private ConnectorTypeResponse toResponse(ConnectorTypeEntity entity) {
//        return ConnectorTypeResponse.builder()
//                .id(entity.getId())
//                .connectorTypeName(entity.getConnectorTypeName())
//                .connectorTypeIcon(entity.getConnectorTypeIcon())
//                .connectorsCount(entity.getConnectors() != null ? entity.getConnectors().size() : 0)
//                .build();
//    }
    private ConnectorTypeResponse toResponse(ConnectorTypeEntity entity) {
        return ConnectorTypeResponse.builder()
                .id(entity.getId())
                .connectorTypeName(entity.getConnectorTypeName())
                .connectorTypeIcon(entity.getConnectorTypeIcon())
                .connectorsCount(0)  // или уберите поле из DTO
                .build();
}
}