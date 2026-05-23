package cm.cirt.bts.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CoverageResponse {
    private Double totalAreaKm2;
    private Double coveredAreaKm2;
    private Double penetrationRate;
    private String classification;
    private String message;
    private List<String> coveragePolygonsGeoJson;
}
