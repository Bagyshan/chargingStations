package charg.ing.stations;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class ContractorAdminServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ContractorAdminServiceApplication.class, args);
    }

}
