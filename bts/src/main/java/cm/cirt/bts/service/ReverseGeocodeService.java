package cm.cirt.bts.service;

import cm.cirt.bts.model.AddressDetail;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

@Service
public class ReverseGeocodeService {

    private static final Logger log = LoggerFactory.getLogger(ReverseGeocodeService.class);

    @Value("${locationiq.api.key:}")
    private String locationIqKey;

    private final WebClient locationIq;
    private final WebClient nominatim;

    public ReverseGeocodeService(WebClient.Builder builder) {
        this.locationIq = builder.clone().baseUrl("https://us1.locationiq.com/v1").build();
        this.nominatim = builder.clone()
                .baseUrl("https://nominatim.openstreetmap.org")
                .defaultHeader("User-Agent", "bts-cirt/1.0")
                .build();
    }

    @Cacheable(value = "reverseGeocode", key = "T(java.lang.String).format('%.5f_%.5f', #lat, #lon)")
    public Map<String, Object> reverse(double lat, double lon) {
        if (locationIqKey != null && !locationIqKey.isBlank()) {
            try {
                JsonNode resp = locationIq.get()
                        .uri(uri -> uri.path("/reverse")
                                .queryParam("key", locationIqKey)
                                .queryParam("lat", lat)
                                .queryParam("lon", lon)
                                .queryParam("format", "json")
                                .build())
                        .retrieve()
                        .bodyToMono(JsonNode.class)
                        .timeout(Duration.ofSeconds(5))
                        .onErrorResume(e -> { log.warn("LocationIQ failed: {}", e.getMessage()); return Mono.empty(); })
                        .block();
                if (resp != null && resp.has("address")) {
                    return toMap(resp.get("display_name").asText(""), resp.get("address"));
                }
            } catch (Exception e) {
                log.warn("LocationIQ exception: {}", e.getMessage());
            }
        }

        try {
            JsonNode resp = nominatim.get()
                    .uri(uri -> uri.path("/reverse")
                            .queryParam("lat", lat)
                            .queryParam("lon", lon)
                            .queryParam("format", "json")
                            .build())
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .timeout(Duration.ofSeconds(5))
                    .onErrorResume(e -> { log.warn("Nominatim failed: {}", e.getMessage()); return Mono.empty(); })
                    .block();
            if (resp != null && resp.has("address")) {
                return toMap(resp.get("display_name").asText(""), resp.get("address"));
            }
        } catch (Exception e) {
            log.warn("Nominatim exception: {}", e.getMessage());
        }

        return Map.of();
    }

    private Map<String, Object> toMap(String displayName, JsonNode address) {
        AddressDetail detail = AddressDetail.builder()
                .country(text(address, "country"))
                .stateOrRegion(firstNonNull(text(address, "state"), text(address, "region")))
                .cityOrTown(firstNonNull(text(address, "city"), text(address, "town"), text(address, "village"), text(address, "county")))
                .postalCode(text(address, "postcode"))
                .street(firstNonNull(text(address, "road"), text(address, "neighbourhood"), text(address, "suburb")))
                .build();
        return Map.of("address", displayName, "detail", detail);
    }

    private String text(JsonNode n, String f) {
        return n != null && n.has(f) ? n.get(f).asText(null) : null;
    }

    private String firstNonNull(String... vs) {
        for (String v : vs) if (v != null && !v.isBlank()) return v;
        return null;
    }
}
