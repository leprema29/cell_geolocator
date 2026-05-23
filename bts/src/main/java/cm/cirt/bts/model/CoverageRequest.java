package cm.cirt.bts.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class CoverageRequest {
    @NotBlank
    private String area;
    @Positive
    private double radiusMeters;
    private String provider = "orange";
}
