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
public class UnwiredLabsClient implements ProviderClient {

    private static final Logger log = LoggerFactory.getLogger(UnwiredLabsClient.class);

    @Value("${unwired_labs.api.key:}")
    private String apiKey;

    private final WebClient client;
    private final ReverseGeocodeService reverseGeocodeService;

    public UnwiredLabsClient(WebClient.Builder builder, ReverseGeocodeService reverseGeocodeService) {
        this.client = builder.clone().baseUrl("https://us1.unwiredlabs.com").build();
        this.reverseGeocodeService = reverseGeocodeService;
    }

    @Override
    public String getProviderName() { return "UnwiredLabs"; }

    @Override
    public GeolocationResponse resolve(GeolocationRequest req) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("UnwiredLabs API key not configured");
            return GeolocationResponse.builder().providerUsed(getProviderName()).build();
        }

        Map<String, Object> payload = Map.of(
                "token", apiKey,
                "radio", "gsm",
                "mcc", parseIntSafe(req.getMcc()),
                "mnc", parseIntSafe(req.getMnc()),
                "cells", List.of(Map.of(
                        "lac", parseIntSafe(req.getLac()),
                        "cid", parseIntSafe(req.getCellId())
                )),
                "address", 1
        );

        try {
            JsonNode body = client.post()
                    .uri("/v2/process")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .timeout(Duration.ofSeconds(8))
                    .block();

            if (body == null || !"ok".equalsIgnoreCase(text(body, "status"))) {
                return GeolocationResponse.builder().providerUsed(getProviderName()).build();
            }
            double lat = body.get("lat").asDouble();
            double lon = body.get("lon").asDouble();
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
            log.warn("UnwiredLabs failed: {}", e.getMessage());
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

    private String text(JsonNode n, String f) {
        return n.has(f) ? n.get(f).asText(null) : null;
    }
}
