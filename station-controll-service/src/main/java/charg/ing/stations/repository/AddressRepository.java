package charg.ing.stations.repository;


import charg.ing.stations.entity.AddressEntity;
import charg.ing.stations.entity.ConnectorTypeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AddressRepository extends JpaRepository<AddressEntity, Integer> {
    boolean existsByAddressName(String name);
    boolean existsByAddressNameAndIdNot(String name, Integer id);

    @Query("SELECT a FROM AddressEntity a LEFT JOIN FETCH a.chargeBoxes WHERE a.id = :id")
    Optional<AddressEntity> findByIdWithChargeBoxes(@Param("id") Integer id);
}


