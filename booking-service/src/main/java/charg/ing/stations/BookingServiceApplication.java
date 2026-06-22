package charg.ing.stations;

import charg.ing.stations.service.KafkaRequestReplyService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BookingServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(BookingServiceApplication.class, args);
	}

//	@Bean
//	public CommandLineRunner initKafkaListeners(KafkaRequestReplyService kafkaRequestReplyService) {
//		return args -> {
//			kafkaRequestReplyService.registerResponseListener("booking.payment.responses");
//			kafkaRequestReplyService.registerResponseListener("booking.station.responses");
//		};
//	}
}
