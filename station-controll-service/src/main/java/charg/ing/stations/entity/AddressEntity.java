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
@Table(name = "address")
public class AddressEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "address_name", nullable = false, length = 500)
    private String addressName;

    @OneToMany(mappedBy = "address", fetch = FetchType.LAZY)
    private List<ChargeBoxEntity> chargeBoxes;
}