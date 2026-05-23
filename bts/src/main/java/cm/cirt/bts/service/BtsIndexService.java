package cm.cirt.bts.service;

import cm.cirt.bts.model.BtsRecord;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads orangebts.properties and mtnbts.properties at startup into in-memory indexes.
 *
 * Orange line  : cellId=siteName-departement-localite,REGION,lon,lat
 * MTN    line  : MCC-MNC-LAC-CI=siteName;localisation;lon;lat;azimuth
 */
@Service
public class BtsIndexService {

    private static final Logger log = LoggerFactory.getLogger(BtsIndexService.class);

    @Value("${bts.data.orange.path}")
    private String orangePath;

    @Value("${bts.data.mtn.path}")
    private String mtnPath;

    @Getter
    private final Map<String, BtsRecord> orangeByCellId = new ConcurrentHashMap<>();

    /** key = MCC-MNC-LAC-CI */
    @Getter
    private final Map<String, BtsRecord> mtnByFullKey = new ConcurrentHashMap<>();

    /** Secondary index: normalized text token -> set of record ids for area search. */
    private final Map<String, List<BtsRecord>> orangeByText = new HashMap<>();
    private final Map<String, List<BtsRecord>> mtnByText = new HashMap<>();

    @PostConstruct
    public void load() {
        loadOrange();
        loadMtn();
        log.info("BTS index ready — Orange: {} entries, MTN: {} entries",
                orangeByCellId.size(), mtnByFullKey.size());
    }

    private void loadOrange() {
        Path p = Path.of(orangePath);
        if (!Files.exists(p)) {
            log.warn("Orange BTS file not found at {} — index empty", orangePath);
            return;
        }
        try (BufferedReader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
            String line;
            int count = 0;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#") || line.startsWith("!")) continue;
                int eq = line.indexOf('=');
                if (eq <= 0) continue;
                String key = line.substring(0, eq).trim();
                String value = line.substring(eq + 1).trim();
                BtsRecord rec = parseOrangeLine(key, value);
                if (rec != null) {
                    orangeByCellId.put(key, rec);
                    indexText(orangeByText, rec);
                    count++;
                }
            }
            log.info("Loaded {} Orange BTS records from {}", count, orangePath);
        } catch (IOException e) {
            log.error("Failed to load Orange BTS file: {}", e.getMessage());
        }
    }

    private void loadMtn() {
        Path p = Path.of(mtnPath);
        if (!Files.exists(p)) {
            log.warn("MTN BTS file not found at {} — index empty", mtnPath);
            return;
        }
        try (BufferedReader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
            String line;
            int count = 0;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#") || line.startsWith("!")) continue;
                int eq = line.indexOf('=');
                if (eq <= 0) continue;
                String key = line.substring(0, eq).trim();
                String value = line.substring(eq + 1).trim();
                BtsRecord rec = parseMtnLine(key, value);
                if (rec != null) {
                    mtnByFullKey.put(key, rec);
                    indexText(mtnByText, rec);
                    count++;
                }
            }
            log.info("Loaded {} MTN BTS records from {}", count, mtnPath);
        } catch (IOException e) {
            log.error("Failed to load MTN BTS file: {}", e.getMessage());
        }
    }

    private BtsRecord parseOrangeLine(String key, String value) {
        // value: siteName-departement-localite,REGION,lon,lat
        String[] parts = value.split(",", -1);
        if (parts.length < 4) {
            log.debug("Malformed Orange line — key={} value={}", key, value);
            return null;
        }
        String siteFull = parts[0].trim();
        String region = parts[1].trim();
        Double lon = parseDouble(parts[2]);
        Double lat = parseDouble(parts[3]);
        if (lat == null || lon == null) return null;

        String[] sites = siteFull.split("-", 3);
        String siteName = sites.length > 0 ? sites[0] : siteFull;
        String departement = sites.length > 1 ? sites[1] : null;
        String localite = sites.length > 2 ? sites[2] : null;

        return BtsRecord.builder()
                .provider("orange")
                .btsKey(key)
                .cellId(key)
                .siteName(siteName)
                .departement(departement)
                .localite(localite)
                .region(region)
                .fullLocation(siteFull)
                .latitude(lat)
                .longitude(lon)
                .build();
    }

    private BtsRecord parseMtnLine(String key, String value) {
        // key: MCC-MNC-LAC-CI
        String[] keyParts = key.split("-", -1);
        if (keyParts.length < 4) {
            log.debug("Malformed MTN key — key={}", key);
            return null;
        }

        // value: siteName;localisation;lon;lat;azimuth
        String[] parts = value.split(";", -1);
        if (parts.length < 4) {
            log.debug("Malformed MTN value — key={} value={}", key, value);
            return null;
        }
        String siteName = parts[0].trim();
        String location = parts[1].trim();
        Double lon = parseDouble(parts[2]);
        Double lat = parseDouble(parts[3]);
        Double azimuth = parts.length > 4 ? parseDouble(parts[4]) : null;
        if (lat == null || lon == null) return null;

        return BtsRecord.builder()
                .provider("mtn")
                .btsKey(key)
                .mcc(keyParts[0])
                .mnc(keyParts[1])
                .lac(keyParts[2])
                .cellId(keyParts[3])
                .siteName(siteName)
                .fullLocation(location)
                .latitude(lat)
                .longitude(lon)
                .azimuth(azimuth)
                .build();
    }

    private void indexText(Map<String, List<BtsRecord>> index, BtsRecord rec) {
        Set<String> tokens = new HashSet<>();
        addTokens(tokens, rec.getSiteName());
        addTokens(tokens, rec.getRegion());
        addTokens(tokens, rec.getDepartement());
        addTokens(tokens, rec.getLocalite());
        addTokens(tokens, rec.getFullLocation());
        for (String t : tokens) {
            index.computeIfAbsent(t, k -> new ArrayList<>()).add(rec);
        }
    }

    private void addTokens(Set<String> bag, String s) {
        if (s == null || s.isBlank()) return;
        String n = normalize(s);
        if (!n.isBlank()) bag.add(n);
        for (String tok : n.split("[\\s\\-_/]+")) {
            if (tok.length() >= 3) bag.add(tok);
        }
    }

    public static String normalize(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .toLowerCase(Locale.ROOT)
                .trim();
        return n;
    }

    private Double parseDouble(String s) {
        try {
            return Double.parseDouble(s.trim());
        } catch (Exception e) {
            return null;
        }
    }

    /* ----- Lookup API ----- */

    public Optional<BtsRecord> findOrangeByCellId(String cellId) {
        return Optional.ofNullable(orangeByCellId.get(cellId));
    }

    public Optional<BtsRecord> findMtn(String mcc, String mnc, String lac, String cellId) {
        String key = mcc + "-" + mnc + "-" + lac + "-" + cellId;
        return Optional.ofNullable(mtnByFullKey.get(key));
    }

    public List<BtsRecord> searchByArea(String query, String provider) {
        if (query == null || query.isBlank()) return List.of();
        String q = normalize(query);

        Map<String, List<BtsRecord>> index;
        Collection<BtsRecord> all;
        if ("mtn".equalsIgnoreCase(provider)) {
            index = mtnByText;
            all = mtnByFullKey.values();
        } else {
            index = orangeByText;
            all = orangeByCellId.values();
        }

        // Exact token match
        List<BtsRecord> exact = index.get(q);
        if (exact != null && !exact.isEmpty()) {
            return new ArrayList<>(exact);
        }

        // Substring fallback across normalized text
        List<BtsRecord> matches = new ArrayList<>();
        for (BtsRecord r : all) {
            String hay = normalize(
                    safe(r.getSiteName()) + " " +
                    safe(r.getRegion()) + " " +
                    safe(r.getDepartement()) + " " +
                    safe(r.getLocalite()) + " " +
                    safe(r.getFullLocation())
            );
            if (hay.contains(q)) matches.add(r);
        }
        return matches;
    }

    private String safe(String s) { return s == null ? "" : s; }

    public Collection<BtsRecord> allOrange() { return orangeByCellId.values(); }
    public Collection<BtsRecord> allMtn() { return mtnByFullKey.values(); }
}
