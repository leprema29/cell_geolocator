package cm.cirt.bts.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PriorityGeolocationResult {
    private GeolocationResponse chosen;
    private Map<String, GeolocationResponse> allResponses;
    private Map<String, Double> distances;
    private String shortestPair;
}
