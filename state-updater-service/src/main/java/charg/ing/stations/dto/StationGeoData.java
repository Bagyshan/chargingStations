package charg.ing.stations.dto;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StationGeoData {
    private String chargeBoxId;
    private Integer id; // числовой ID станции
    private Double latitude;
    private Double longitude;
    private String coordinates;
    private Integer version;

    public static StationGeoData fromStationDTO(StationDTO stationDTO) {
        StationGeoData geoData = new StationGeoData();
        geoData.setChargeBoxId(stationDTO.getChargeBoxId());
        geoData.setId(Integer.parseInt(stationDTO.getId()));
        geoData.setVersion(stationDTO.getVersion());

        if (stationDTO.getGeolocation() != null) {
            geoData.setLatitude(stationDTO.getGeolocation().getLatitude());
            geoData.setLongitude(stationDTO.getGeolocation().getLongitude());
            geoData.setCoordinates(stationDTO.getGeolocation().getCoordinates());
        }

        return geoData;
    }

    public boolean hasValidCoordinates() {
        return latitude != null && longitude != null &&
                !Double.isNaN(latitude) && !Double.isNaN(longitude);
    }
}