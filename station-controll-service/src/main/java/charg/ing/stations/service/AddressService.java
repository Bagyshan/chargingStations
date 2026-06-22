package charg.ing.stations.service;


import charg.ing.stations.dto.address.AddressResponse;
import charg.ing.stations.dto.connector_type.ConnectorTypeResponse;
import charg.ing.stations.entity.AddressEntity;
import charg.ing.stations.entity.ConnectorTypeEntity;
import charg.ing.stations.repository.AddressRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.amqp.RabbitConnectionDetails;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
@RequiredArgsConstructor
@Slf4j
public class AddressService {

    private final AddressRepository addressRepository;


    public Flux<AddressResponse> getAllAddresses() {
        return Flux.defer(() -> Flux.fromIterable(addressRepository.findAll()))
                .subscribeOn(Schedulers.boundedElastic())
                .map(this::toResponse);
    }

    public Mono<AddressResponse> getAddressById(Integer id) {
        return Mono.fromCallable(() -> addressRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Address not found with id: " + id)))
                .subscribeOn(Schedulers.boundedElastic())
                .map(this::toResponse);
    }

    public Mono<AddressResponse> createAddress(String name) {
        return Mono.fromCallable(() -> {
                    // Проверка уникальности имени
                    if (addressRepository.existsByAddressName(name)) {
                        throw new IllegalArgumentException("Connector type with name '" + name + "' already exists");
                    }
                    // Создаем сущность
                    AddressEntity entity = new AddressEntity();
                    entity.setAddressName(name);
                    return addressRepository.save(entity);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .map(this::toResponse);
    }

    public Mono<AddressResponse> updateAddress(Integer id, String name) {
        return Mono.fromCallable(() -> {
                    AddressEntity entity = addressRepository.findById(id)
                            .orElseThrow(() -> new IllegalArgumentException("Address not found with id: " + id));
                    // Проверка уникальности имени
                    if (!entity.getAddressName().equals(name) &&
                            addressRepository.existsByAddressNameAndIdNot(name, id)) {
                        throw new IllegalArgumentException("Address with name '" + name + "' already exists");
                    }
                    entity.setAddressName(name);
                    addressRepository.save(entity);
                    return entity;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .map(this::toResponse);
    }

    public Mono<Void> deleteAddress(Integer id) {
        return Mono.fromCallable(() -> addressRepository.findByIdWithChargeBoxes(id)
                .orElseThrow(() -> new IllegalArgumentException("Connector type not found with id: " + id)))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(entity -> {
                    if (entity.getChargeBoxes() != null && !entity.getChargeBoxes().isEmpty()) {
                        return Mono.error(new IllegalStateException(
                                "Cannot delete address that has associated connectors. Count: " +
                                        entity.getChargeBoxes().size()));
                    }
                    return Mono.fromRunnable(() -> addressRepository
                                    .delete(entity))
                                    .subscribeOn(Schedulers.boundedElastic());
                })
                .then();
    }


    private AddressResponse toResponse(AddressEntity address) {
        return AddressResponse.builder()
                .id(address.getId())
                .addressName(address.getAddressName())
                .chargeBoxCount(0)
                .build();
    }
}
