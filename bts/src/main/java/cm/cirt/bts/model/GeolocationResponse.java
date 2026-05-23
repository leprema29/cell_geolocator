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
public class GeolocationResponse {
    private Double latitude;
    private Double longitude;
    private Double accuracy;
    private String providerUsed;
    private String cellId;
    private String originalRequestedCellId;
    private Boolean fallbackUsed;
    private String technoCell;
    private String frequenceCell;
    private String address;
    private AddressDetail addressDetail;
}
