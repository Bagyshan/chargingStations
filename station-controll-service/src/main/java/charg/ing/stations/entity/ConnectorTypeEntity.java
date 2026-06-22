package charg.ing.stations.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "connector_type")
public class ConnectorTypeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "connector_type_name", nullable = false)
    private String connectorTypeName;

    @Column(name = "connector_type_icon")
    private String connectorTypeIcon;

    @OneToMany(mappedBy = "connectorType", fetch = FetchType.LAZY)
    private List<ConnectorEntity> connectors;
}