package charg.ing.stations.dto.analytics;

public enum EnergyGroupBy {
    TOTAL,    // одна серия для всех станций
    STATION,  // отдельная серия на каждую станцию
    OWNER     // отдельная серия на каждого контрагента
}
