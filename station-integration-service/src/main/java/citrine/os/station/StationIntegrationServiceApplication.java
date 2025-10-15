package citrine.os.station;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;



@SpringBootApplication
public class StationIntegrationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(StationIntegrationServiceApplication.class, args);
    }

//    public static void main(String[] args) {
//        new SpringApplicationBuilder(FeedbackServiceApplication.class)
//                .properties("spring.config.name=application-standalone")
//                .run(args);
//    }
}
