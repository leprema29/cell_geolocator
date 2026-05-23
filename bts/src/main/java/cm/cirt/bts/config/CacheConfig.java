package cm.cirt.bts.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cm = new CaffeineCacheManager("reverseGeocode", "areaPolygon");
        cm.setCaffeine(Caffeine.newBuilder()
                .maximumSize(5_000)
                .expireAfterWrite(1, TimeUnit.HOURS));
        return cm;
    }
}
