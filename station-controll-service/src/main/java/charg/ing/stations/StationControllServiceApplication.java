package charg.ing.stations;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class StationControllServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(StationControllServiceApplication.class, args);
    }
}

