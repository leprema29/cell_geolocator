package cm.cirt.bts.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CellInfo {
    private Double latitude;
    private Double longitude;
    private String lac;
    private String ci;
    private String site_name;
    private String bts_id;
    private String localite;
    private String quartier;
    private String departement;
    private String region_terr;
    private String region_bus;
    private String techno_cell;
    private String frequence_cell;
}
