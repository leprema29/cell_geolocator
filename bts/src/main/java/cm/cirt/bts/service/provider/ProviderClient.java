package cm.cirt.bts.service.provider;

import cm.cirt.bts.model.GeolocationRequest;
import cm.cirt.bts.model.GeolocationResponse;

public interface ProviderClient {
    String getProviderName();
    GeolocationResponse resolve(GeolocationRequest request);
}
