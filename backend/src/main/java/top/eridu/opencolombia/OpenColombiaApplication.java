package top.eridu.opencolombia;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class OpenColombiaApplication {
    public static void main(String[] args) {
        SpringApplication.run(OpenColombiaApplication.class, args);
    }
}
