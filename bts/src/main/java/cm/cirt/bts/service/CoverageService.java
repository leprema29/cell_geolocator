package cm.cirt.bts.service;

import cm.cirt.bts.model.BtsRecord;
import cm.cirt.bts.model.CoverageResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.io.geojson.GeoJsonReader;
import org.locationtech.jts.io.geojson.GeoJsonWriter;
import org.locationtech.jts.operation.union.UnaryUnionOp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Penetration / coverage calculation using JTS in Java pure.
 *
 * Steps:
 *  1. Resolve area polygon via Nominatim (GeoJSON).
 *  2. Buffer each BTS antenna point with radiusMeters → disk in degrees (approximated).
 *  3. Union all disks → coverage polygon.
 *  4. Intersect with area polygon → covered region.
 *  5. penetrationRate = areaOf(intersection) / areaOf(region) * 100.
 */
@Service
public class CoverageService {

    private static final Logger log = LoggerFactory.getLogger(CoverageService.class);

    private static final GeometryFactory GF = new GeometryFactory(new PrecisionModel(), 4326);

    private final BtsIndexService btsIndex;
    private final WebClient nominatim;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CoverageService(BtsIndexService btsIndex, WebClient.Builder builder) {
        this.btsIndex = btsIndex;
        this.nominatim = builder.clone()
                .baseUrl("https://nominatim.openstreetmap.org")
                .defaultHeader("User-Agent", "bts-cirt/1.0")
                .build();
    }

    public CoverageResponse calculateCoverage(String area, double radiusMeters, String provider) {
        try {
            Geometry areaGeom = resolveAreaGeometry(area);
            if (areaGeom == null) {
                return CoverageResponse.builder()
                        .message("Area not found via Nominatim: " + area)
                        .penetrationRate(0.0)
                        .classification("Low")
                        .coveragePolygonsGeoJson(List.of())
                        .build();
            }

            Envelope env = areaGeom.getEnvelopeInternal();
            double centerLat = (env.getMinY() + env.getMaxY()) / 2.0;
            double radiusDegLat = radiusMeters / 111_320.0;
            double radiusDegLon = radiusMeters / (111_320.0 * Math.cos(Math.toRadians(centerLat)));

            Collection<BtsRecord> source = "mtn".equalsIgnoreCase(provider) ? btsIndex.allMtn() : btsIndex.allOrange();

            List<Geometry> disks = new ArrayList<>();
            for (BtsRecord r : source) {
                if (r.getLatitude() == null || r.getLongitude() == null) continue;
                if (!env.contains(r.getLongitude(), r.getLatitude())
                        && env.distance(new Envelope(r.getLongitude(), r.getLongitude(), r.getLatitude(), r.getLatitude()))
                            > Math.max(radiusDegLat, radiusDegLon)) {
                    continue;
                }
                Point p = GF.createPoint(new Coordinate(r.getLongitude(), r.getLatitude()));
                // Approximate ellipse-disk via buffer scaled in lon
                Geometry disk = p.buffer(radiusDegLat, 16);
                disks.add(disk);
            }

            if (disks.isEmpty()) {
                return CoverageResponse.builder()
                        .totalAreaKm2(areaKm2(areaGeom, centerLat))
                        .coveredAreaKm2(0.0)
                        .penetrationRate(0.0)
                        .classification("Low")
                        .message("No BTS antennas found in area for provider " + provider)
                        .coveragePolygonsGeoJson(List.of())
                        .build();
            }

            Geometry union = UnaryUnionOp.union(disks);
            Geometry intersection = areaGeom.intersection(union);

            double totalKm2 = areaKm2(areaGeom, centerLat);
            double coveredKm2 = areaKm2(intersection, centerLat);
            double rate = totalKm2 > 0 ? Math.min(100.0, (coveredKm2 / totalKm2) * 100.0) : 0.0;

            GeoJsonWriter writer = new GeoJsonWriter();
            List<String> polygons = new ArrayList<>();
            if (intersection instanceof MultiPolygon mp) {
                for (int i = 0; i < mp.getNumGeometries(); i++) {
                    polygons.add(writer.write(mp.getGeometryN(i)));
                }
            } else if (!intersection.isEmpty()) {
                polygons.add(writer.write(intersection));
            }

            return CoverageResponse.builder()
                    .totalAreaKm2(round(totalKm2))
                    .coveredAreaKm2(round(coveredKm2))
                    .penetrationRate(round(rate))
                    .classification(classify(rate))
                    .message(String.format("Computed using %d antennas (provider=%s)", disks.size(), provider))
                    .coveragePolygonsGeoJson(polygons)
                    .build();

        } catch (Exception e) {
            log.error("Coverage calculation failed", e);
            return CoverageResponse.builder()
                    .message("Coverage computation error: " + e.getMessage())
                    .penetrationRate(0.0)
                    .classification("Low")
                    .coveragePolygonsGeoJson(List.of())
                    .build();
        }
    }

    @Cacheable(value = "areaPolygon", key = "#area.toLowerCase()")
    public Geometry resolveAreaGeometry(String area) {
        try {
            JsonNode arr = nominatim.get()
                    .uri(uri -> uri.path("/search")
                            .queryParam("q", area)
                            .queryParam("format", "json")
                            .queryParam("polygon_geojson", 1)
                            .queryParam("limit", 1)
                            .build())
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();
            if (arr == null || !arr.isArray() || arr.size() == 0) return null;
            JsonNode first = arr.get(0);
            if (!first.has("geojson")) return null;
            String geoJson = objectMapper.writeValueAsString(first.get("geojson"));
            return new GeoJsonReader(GF).read(geoJson);
        } catch (Exception e) {
            log.warn("Nominatim area lookup failed for '{}': {}", area, e.getMessage());
            return null;
        }
    }

    private double areaKm2(Geometry g, double centerLat) {
        if (g == null || g.isEmpty()) return 0.0;
        double degArea = g.getArea();
        double kmPerDegLat = 111.320;
        double kmPerDegLon = 111.320 * Math.cos(Math.toRadians(centerLat));
        return degArea * kmPerDegLat * kmPerDegLon;
    }

    private String classify(double rate) {
        if (rate < 50.0) return "Low";
        if (rate <= 80.0) return "Medium";
        return "High";
    }

    private double round(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
