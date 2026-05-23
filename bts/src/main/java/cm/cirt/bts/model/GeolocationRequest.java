package cm.cirt.bts.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class GeolocationRequest {
    @NotBlank
    private String mcc;
    @NotBlank
    private String mnc;
    @NotBlank
    private String lac;
    @NotBlank
    private String cellId;
}
