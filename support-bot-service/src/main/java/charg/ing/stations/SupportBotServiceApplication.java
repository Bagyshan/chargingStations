package charg.ing.stations;

import charg.ing.stations.config.TelegramBotProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(TelegramBotProperties.class)
public class SupportBotServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SupportBotServiceApplication.class, args);
    }
}
