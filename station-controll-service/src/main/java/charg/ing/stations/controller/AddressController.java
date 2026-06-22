package charg.ing.stations.controller;


import charg.ing.stations.dto.address.AddressRequest;
import charg.ing.stations.dto.address.AddressResponse;
import charg.ing.stations.service.AddressService;
import charg.ing.stations.service.ConnectorTypeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@Slf4j
@RequestMapping("/api/address")
@RequiredArgsConstructor
public class AddressController {

    private final AddressService addressService;

    @GetMapping
    public Flux<AddressResponse> getAll() {
        return addressService.getAllAddresses();
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<AddressResponse>> getById(@PathVariable Integer id) {
        return addressService.getAddressById(id)
                .map(ResponseEntity::ok)
                .onErrorResume(IllegalArgumentException.class, e ->
                        Mono.just(ResponseEntity.notFound().build()));
    }

    @PostMapping
    public Mono<ResponseEntity<AddressResponse>> create(
            @RequestBody AddressRequest addressRequest
    ) {
        return addressService.createAddress(addressRequest.getAddressName())
                .map(ResponseEntity::ok)
                .onErrorResume(IllegalArgumentException.class, e ->
                        Mono.just(ResponseEntity.badRequest().build()));
    }

    @PutMapping("/{id}")
    public Mono<ResponseEntity<AddressResponse>> update(
            @PathVariable Integer id,
            @RequestBody AddressRequest addressRequest
    ) {
        return addressService.updateAddress(id, addressRequest.getAddressName())
                .map(ResponseEntity::ok)
                .onErrorResume(IllegalArgumentException.class, e ->
                        Mono.just(ResponseEntity.badRequest().build()));
    }

    @DeleteMapping("/{id}")
    public Mono<Void> delete(@PathVariable Integer id) {
        return addressService.deleteAddress(id)
                .onErrorResume(IllegalArgumentException.class, e -> Mono.empty());
    }
}
