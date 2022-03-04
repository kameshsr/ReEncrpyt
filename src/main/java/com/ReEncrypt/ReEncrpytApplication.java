package com.ReEncrypt;

import com.ReEncrypt.service.ReEncrypt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;


@SpringBootApplication(scanBasePackages = {"com.ReEncrypt.*" })

public class ReEncrpytApplication implements CommandLineRunner {

    //private static final Logger LOGGER = KeymanagerLogger.getLogger(ReEncrpytApplication.class);

    @Autowired
    ReEncrypt reEncrypt;
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }


    public static void main(String[] args) throws Exception {
        ConfigurableApplicationContext run = SpringApplication.run(ReEncrpytApplication.class, args);
        SpringApplication.exit(run);
    }

    @Override
    public void run(String... args) throws Exception {

        //LOGGER.info(" started......" );
        reEncrypt.start();
        //LOGGER.info("  Completed......" );
    }
}
