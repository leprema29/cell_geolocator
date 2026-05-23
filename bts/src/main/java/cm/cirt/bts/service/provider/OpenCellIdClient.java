package cm.cirt.bts.service.provider;

import cm.cirt.bts.model.AddressDetail;
import cm.cirt.bts.model.GeolocationRequest;
import cm.cirt.bts.model.GeolocationResponse;
import cm.cirt.bts.service.ReverseGeocodeService;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Map;

@Component
public class OpenCellIdClient implements ProviderClient {

    private static final Logger log = LoggerFactory.getLogger(OpenCellIdClient.class);

    @Value("${opencellid.api.key:}")
    private String apiKey;

    private final WebClient client;
    private final ReverseGeocodeService reverseGeocodeService;

    public OpenCellIdClient(WebClient.Builder builder, ReverseGeocodeService reverseGeocodeService) {
        this.client = builder.clone().baseUrl("https://opencellid.org").build();
        this.reverseGeocodeService = reverseGeocodeService;
    }

    @Override
    public String getProviderName() { return "OpenCellID"; }

    @Override
    public GeolocationResponse resolve(GeolocationRequest req) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("OpenCellID API key not configured");
            return GeolocationResponse.builder().providerUsed(getProviderName()).build();
        }

        try {
            JsonNode body = client.get()
                    .uri(uri -> uri.path("/cell/get")
                            .queryParam("key", apiKey)
                            .queryParam("mcc", req.getMcc())
                            .queryParam("mnc", req.getMnc())
                            .queryParam("lac", req.getLac())
                            .queryParam("cellid", req.getCellId())
                            .queryParam("format", "json")
                            .build())
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .timeout(Duration.ofSeconds(8))
                    .block();

            if (body == null || !body.has("lat") || !body.has("lon")) {
                return GeolocationResponse.builder().providerUsed(getProviderName()).build();
            }
            double lat = body.get("lat").asDouble();
            double lon = body.get("lon").asDouble();
            Double accuracy = body.has("range") ? body.get("range").asDouble() : null;

            return enrich(GeolocationResponse.builder()
                    .latitude(lat)
                    .longitude(lon)
                    .accuracy(accuracy)
                    .providerUsed(getProviderName())
                    .cellId(req.getCellId())
                    .originalRequestedCellId(req.getCellId())
                    .fallbackUsed(false)
                    .build(), lat, lon);

        } catch (Exception e) {
            log.warn("OpenCellID failed: {}", e.getMessage());
            return GeolocationResponse.builder().providerUsed(getProviderName()).build();
        }
    }

    private GeolocationResponse enrich(GeolocationResponse r, double lat, double lon) {
        try {
            Map<String, Object> geo = reverseGeocodeService.reverse(lat, lon);
            if (!geo.isEmpty()) {
                r.setAddress((String) geo.get("address"));
                r.setAddressDetail((AddressDetail) geo.get("detail"));
            }
        } catch (Exception e) {
            log.debug("Reverse geocode skipped: {}", e.getMessage());
        }
        return r;
    }
}
