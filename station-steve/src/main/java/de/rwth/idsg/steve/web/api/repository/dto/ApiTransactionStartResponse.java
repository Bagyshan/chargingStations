package de.rwth.idsg.steve.web.api.repository.dto;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.joda.time.DateTime;

import java.io.Serializable;


@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class ApiTransactionStartResponse implements Serializable {

    private Integer transactionId;
    private DateTime startTimestamp;
}
