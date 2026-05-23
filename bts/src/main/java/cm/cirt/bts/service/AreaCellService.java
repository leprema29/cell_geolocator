package cm.cirt.bts.service;

import cm.cirt.bts.model.BtsRecord;
import cm.cirt.bts.model.CellInfo;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AreaCellService {

    private final BtsIndexService btsIndex;

    public AreaCellService(BtsIndexService btsIndex) {
        this.btsIndex = btsIndex;
    }

    public List<CellInfo> getCellsByArea(String query, String provider) {
        String p = provider == null ? "orange" : provider.toLowerCase();
        return btsIndex.searchByArea(query, p).stream()
                .map(this::toCellInfo)
                .toList();
    }

    private CellInfo toCellInfo(BtsRecord r) {
        return CellInfo.builder()
                .latitude(r.getLatitude())
                .longitude(r.getLongitude())
                .lac(r.getLac())
                .ci(r.getCellId())
                .site_name(r.getSiteName())
                .bts_id(r.getBtsKey())
                .localite(r.getLocalite())
                .quartier(r.getFullLocation())
                .departement(r.getDepartement())
                .region_terr(r.getRegion())
                .region_bus(r.getRegion())
                .techno_cell("GSM")
                .frequence_cell(null)
                .build();
    }
}
