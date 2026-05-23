package cm.cirt.bts.service;

import cm.cirt.bts.model.*;
import cm.cirt.bts.service.provider.ProviderClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Strategy:
 *  1. Try local BTS index (Orange / MTN) — if found, return immediately (skip external providers).
 *  2. Otherwise, query OpenCellID, UnwiredLabs and Combain in parallel.
 *  3. Compute pairwise Haversine distances, pick the shortest pair, then resolve ties by priority.
 */
@Service
public class GeolocationService {

    private static final Logger log = LoggerFactory.getLogger(GeolocationService.class);

    private final BtsIndexService btsIndex;
    private final ReverseGeocodeService reverseGeocodeService;
    private final List<ProviderClient> providers;
    private final PriorityService priorityService;

    public GeolocationService(BtsIndexService btsIndex,
                              ReverseGeocodeService reverseGeocodeService,
                              List<ProviderClient> providers,
                              PriorityService priorityService) {
        this.btsIndex = btsIndex;
        this.reverseGeocodeService = reverseGeocodeService;
        this.providers = providers;
        this.priorityService = priorityService;
    }

    public PriorityGeolocationResult resolveWithPriority(GeolocationRequest request) {
        log.info("Geolocation request — MCC={} MNC={} LAC={} CELL={}",
                request.getMcc(), request.getMnc(), request.getLac(), request.getCellId());

        // 1. Local BTS index
        GeolocationResponse local = tryLocal(request);
        if (local != null) {
            log.info("Local BTS hit — provider={}", local.getProviderUsed());
            return PriorityGeolocationResult.builder()
                    .chosen(local)
                    .allResponses(Map.of(local.getProviderUsed(), local))
                    .distances(Map.of())
                    .shortestPair("LOCAL_ONLY")
                    .build();
        }

        // 2. External providers in parallel
        Map<String, CompletableFuture<GeolocationResponse>> futures = new LinkedHashMap<>();
        for (ProviderClient p : providers) {
            futures.put(p.getProviderName(),
                    CompletableFuture.supplyAsync(() -> p.resolve(request))
                            .exceptionally(e -> {
                                log.error("Provider {} failed: {}", p.getProviderName(), e.getMessage());
                                return GeolocationResponse.builder().providerUsed(p.getProviderName()).build();
                            }));
        }
        CompletableFuture.allOf(futures.values().toArray(new CompletableFuture[0])).join();

        Map<String, GeolocationResponse> results = new LinkedHashMap<>();
        futures.forEach((k, f) -> results.put(k, f.join()));

        // 3. Distance matrix
        List<String> names = new ArrayList<>(results.keySet());
        Map<String, Double> distances = new LinkedHashMap<>();
        double bestDist = Double.MAX_VALUE;
        String bestPair = null;
        String bestA = null, bestB = null;
        for (int i = 0; i < names.size(); i++) {
            for (int j = i + 1; j < names.size(); j++) {
                String a = names.get(i), b = names.get(j);
                double d = distanceIfValid(results.get(a), results.get(b));
                distances.put(a + "-" + b, d);
                if (d < bestDist) {
                    bestDist = d;
                    bestPair = a + "-" + b;
                    bestA = a; bestB = b;
                }
            }
        }

        GeolocationResponse chosen;
        if (bestPair != null && bestDist != Double.MAX_VALUE) {
            chosen = pickByPriorityWithFallback(results.get(bestA), results.get(bestB),
                    fallback(results, bestA, bestB));
        } else {
            chosen = firstValidByPriority(results);
        }

        if (chosen == null || chosen.getLatitude() == null) {
            chosen = GeolocationResponse.builder()
                    .providerUsed("None")
                    .address("No provider matched")
                    .build();
        }

        return PriorityGeolocationResult.builder()
                .chosen(chosen)
                .allResponses(results)
                .distances(distances)
                .shortestPair(bestPair == null ? "NONE" : bestPair)
                .build();
    }

    /* ---------------- local lookup ---------------- */

    private GeolocationResponse tryLocal(GeolocationRequest req) {
        Optional<BtsRecord> mtn = btsIndex.findMtn(req.getMcc(), req.getMnc(), req.getLac(), req.getCellId());
        if (mtn.isPresent()) return toResponse(mtn.get(), req);

        Optional<BtsRecord> orange = btsIndex.findOrangeByCellId(req.getCellId());
        return orange.map(r -> toResponse(r, req)).orElse(null);
    }

    private GeolocationResponse toResponse(BtsRecord rec, GeolocationRequest req) {
        AddressDetail localDetail = AddressDetail.builder()
                .country("Cameroon")
                .stateOrRegion(rec.getRegion())
                .cityOrTown(rec.getLocalite() != null ? rec.getLocalite() : rec.getSiteName())
                .street(rec.getSiteName())
                .build();

        String address = rec.getFullLocation();
        AddressDetail detail = localDetail;
        try {
            Map<String, Object> geo = reverseGeocodeService.reverse(rec.getLatitude(), rec.getLongitude());
            if (!geo.isEmpty()) {
                address = (String) geo.getOrDefault("address", address);
                AddressDetail remote = (AddressDetail) geo.get("detail");
                if (remote != null) {
                    detail = AddressDetail.builder()
                            .country(remote.getCountry() != null ? remote.getCountry() : "Cameroon")
                            .stateOrRegion(remote.getStateOrRegion() != null ? remote.getStateOrRegion() : rec.getRegion())
                            .cityOrTown(remote.getCityOrTown() != null ? remote.getCityOrTown() :
                                    (rec.getLocalite() != null ? rec.getLocalite() : rec.getSiteName()))
                            .postalCode(remote.getPostalCode())
                            .street(rec.getSiteName())
                            .build();
                }
            }
        } catch (Exception ignored) {}

        return GeolocationResponse.builder()
                .latitude(rec.getLatitude())
                .longitude(rec.getLongitude())
                .accuracy(0.0)
                .providerUsed("LOCAL_DB(" + rec.getProvider() + ")")
                .cellId(rec.getCellId() != null ? rec.getCellId() : req.getCellId())
                .originalRequestedCellId(req.getCellId())
                .fallbackUsed(false)
                .technoCell("GSM")
                .frequenceCell(null)
                .address(address)
                .addressDetail(detail)
                .build();
    }

    /* ---------------- helpers ---------------- */

    private GeolocationResponse fallback(Map<String, GeolocationResponse> all, String a, String b) {
        for (Map.Entry<String, GeolocationResponse> e : all.entrySet()) {
            if (!e.getKey().equals(a) && !e.getKey().equals(b) && isValid(e.getValue())) return e.getValue();
        }
        return null;
    }

    private GeolocationResponse firstValidByPriority(Map<String, GeolocationResponse> results) {
        for (String pname : priorityService.getProviderPriorities()) {
            GeolocationResponse r = results.get(pname);
            if (isValid(r)) return r;
        }
        for (GeolocationResponse r : results.values()) if (isValid(r)) return r;
        return null;
    }

    private GeolocationResponse pickByPriorityWithFallback(GeolocationResponse r1, GeolocationResponse r2,
                                                           GeolocationResponse fb) {
        GeolocationResponse pref = pickByPriority(r1, r2);
        if (isValid(pref)) return pref;
        GeolocationResponse alt = (pref == r1) ? r2 : r1;
        if (isValid(alt)) return alt;
        return isValid(fb) ? fb : null;
    }

    private GeolocationResponse pickByPriority(GeolocationResponse r1, GeolocationResponse r2) {
        if (r1 == null) return r2;
        if (r2 == null) return r1;
        List<String> prios = priorityService.getProviderPriorities();
        int i1 = prios.indexOf(r1.getProviderUsed());
        int i2 = prios.indexOf(r2.getProviderUsed());
        if (i1 == -1 && i2 == -1) return r1;
        if (i1 == -1) return r2;
        if (i2 == -1) return r1;
        return i1 < i2 ? r1 : r2;
    }

    private boolean isValid(GeolocationResponse r) {
        return r != null && r.getLatitude() != null && r.getLongitude() != null;
    }

    private double distanceIfValid(GeolocationResponse a, GeolocationResponse b) {
        if (!isValid(a) || !isValid(b)) return Double.MAX_VALUE;
        return haversineKm(a.getLatitude(), a.getLongitude(), b.getLatitude(), b.getLongitude());
    }

    public static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
