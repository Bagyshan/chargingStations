package charg.ing.stations.controller;

import charg.ing.stations.dto.ConnectorPatchDTO;
import charg.ing.stations.service.ConnectorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/connectors")
@RequiredArgsConstructor
public class ConnectorController {

    private final ConnectorService connectorService;

    @PatchMapping("/{chargeBoxId}/{connectorId}")
    public ResponseEntity<ConnectorPatchDTO> patchConnector(
            @PathVariable String chargeBoxId,
            @PathVariable int connectorId,
            @RequestBody ConnectorPatchDTO dto
    ) {
        return ResponseEntity.ok(connectorService.patchConnector(chargeBoxId, connectorId, dto));
    }
}
