package psy.staybooking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class StayBookingApplication {

    public static void main(String[] args) {
        SpringApplication.run(StayBookingApplication.class, args);
    }

}
