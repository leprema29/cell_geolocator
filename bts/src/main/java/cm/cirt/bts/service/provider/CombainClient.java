package cm.cirt.bts.service.provider;

import cm.cirt.bts.model.AddressDetail;
import cm.cirt.bts.model.GeolocationRequest;
import cm.cirt.bts.model.GeolocationResponse;
import cm.cirt.bts.service.ReverseGeocodeService;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
public class CombainClient implements ProviderClient {

    private static final Logger log = LoggerFactory.getLogger(CombainClient.class);

    @Value("${combain.api.key:}")
    private String apiKey;

    private final WebClient client;
    private final ReverseGeocodeService reverseGeocodeService;

    public CombainClient(WebClient.Builder builder, ReverseGeocodeService reverseGeocodeService) {
        this.client = builder.clone().baseUrl("https://apiv2.combain.com").build();
        this.reverseGeocodeService = reverseGeocodeService;
    }

    @Override
    public String getProviderName() { return "Combain"; }

    @Override
    public GeolocationResponse resolve(GeolocationRequest req) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Combain API key not configured");
            return GeolocationResponse.builder().providerUsed(getProviderName()).build();
        }

        Map<String, Object> payload = Map.of(
                "cellTowers", List.of(Map.of(
                        "mobileCountryCode", parseIntSafe(req.getMcc()),
                        "mobileNetworkCode", parseIntSafe(req.getMnc()),
                        "locationAreaCode", parseIntSafe(req.getLac()),
                        "cellId", parseIntSafe(req.getCellId())
                ))
        );

        try {
            JsonNode body = client.post()
                    .uri(uri -> uri.path("/").queryParam("key", apiKey).build())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .timeout(Duration.ofSeconds(8))
                    .block();

            if (body == null || !body.has("location")) {
                return GeolocationResponse.builder().providerUsed(getProviderName()).build();
            }
            JsonNode loc = body.get("location");
            double lat = loc.get("lat").asDouble();
            double lon = loc.get("lng").asDouble();
            Double accuracy = body.has("accuracy") ? body.get("accuracy").asDouble() : null;

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
            log.warn("Combain failed: {}", e.getMessage());
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
        } catch (Exception ignored) {}
        return r;
    }

    private int parseIntSafe(String s) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return 0; }
    }
}
