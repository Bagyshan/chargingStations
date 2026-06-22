package charg.ing.stations.repository;

import charg.ing.stations.entity.ConnectorTypeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

//@Repository
//public interface ConnectorTypeRepository extends JpaRepository<ConnectorTypeEntity, Integer> {
//
//    boolean existsByConnectorTypeName(String name);
//
//    boolean existsByConnectorTypeNameAndIdNot(String name, Integer id);
//

//}

@Repository
public interface ConnectorTypeRepository extends JpaRepository<ConnectorTypeEntity, Integer> {
    boolean existsByConnectorTypeName(String name);
    boolean existsByConnectorTypeNameAndIdNot(String name, Integer id);

    @Query("SELECT ct FROM ConnectorTypeEntity ct LEFT JOIN FETCH ct.connectors WHERE ct.id = :id")
    Optional<ConnectorTypeEntity> findByIdWithConnectors(@Param("id") Integer id);
}