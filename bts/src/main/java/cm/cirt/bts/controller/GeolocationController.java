package cm.cirt.bts.controller;

import cm.cirt.bts.model.*;
import cm.cirt.bts.service.AreaCellService;
import cm.cirt.bts.service.CoverageService;
import cm.cirt.bts.service.GeolocationService;
import io.github.bucket4j.Bucket;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Geolocation", description = "Cell tower geolocation, area search and coverage")
public class GeolocationController {

    private final GeolocationService geolocationService;
    private final AreaCellService areaCellService;
    private final CoverageService coverageService;
    private final Bucket rateLimiterBucket;

    public GeolocationController(GeolocationService geolocationService,
                                 AreaCellService areaCellService,
                                 CoverageService coverageService,
                                 Bucket rateLimiterBucket) {
        this.geolocationService = geolocationService;
        this.areaCellService = areaCellService;
        this.coverageService = coverageService;
        this.rateLimiterBucket = rateLimiterBucket;
    }

    @Operation(summary = "Resolve cell geolocation (priority + distance)")
    @PostMapping("/geolocate/priority")
    public ResponseEntity<Map<String, Object>> geolocatePriority(@Valid @RequestBody GeolocationRequest req) {
        if (!rateLimiterBucket.tryConsume(1)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }
        PriorityGeolocationResult result = geolocationService.resolveWithPriority(req);
        return ResponseEntity.ok(Map.of(
                "priorityResults", result,
                "relatedCells", List.of()
        ));
    }

    @Operation(summary = "Get all cells within a named area")
    @GetMapping("/cells/by-area")
    public ResponseEntity<List<CellInfo>> cellsByArea(
            @RequestParam String query,
            @RequestParam(defaultValue = "orange") String provider) {
        if (!rateLimiterBucket.tryConsume(1)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }
        return ResponseEntity.ok(areaCellService.getCellsByArea(query, provider));
    }

    @Operation(summary = "Compute cell coverage / penetration rate over a named area")
    @PostMapping("/coverage/penetration")
    public ResponseEntity<CoverageResponse> coverage(@Valid @RequestBody CoverageRequest req) {
        if (!rateLimiterBucket.tryConsume(1)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }
        return ResponseEntity.ok(
                coverageService.calculateCoverage(req.getArea(), req.getRadiusMeters(), req.getProvider()));
    }
}
