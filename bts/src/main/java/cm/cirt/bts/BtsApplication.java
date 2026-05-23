package cm.cirt.bts;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class BtsApplication {

    public static void main(String[] args) {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        dotenv.entries().forEach(e -> {
            if (System.getProperty(e.getKey()) == null && System.getenv(e.getKey()) == null) {
                System.setProperty(e.getKey(), e.getValue());
            }
        });
        SpringApplication.run(BtsApplication.class, args);
    }
}
