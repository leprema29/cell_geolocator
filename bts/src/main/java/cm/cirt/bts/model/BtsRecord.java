package cm.cirt.bts.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * In-memory record parsed from orangebts.properties or mtnbts.properties.
 *
 * Orange format: cellId = siteName-departement-localite,REGION,lon,lat
 * MTN    format: MCC-MNC-LAC-CI = siteName;localisation;lon;lat;azimuth
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BtsRecord {
    private String provider;      // "orange" or "mtn"
    private String btsKey;        // raw key from the file
    private String mcc;
    private String mnc;
    private String lac;
    private String cellId;
    private String siteName;
    private String departement;
    private String localite;
    private String region;
    private String fullLocation;  // raw localisation string (for area search)
    private Double latitude;
    private Double longitude;
    private Double azimuth;
}
