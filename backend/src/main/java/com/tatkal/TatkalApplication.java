package com.tatkal;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class TatkalApplication {

    public static void main(String[] args) {
        // Java on some Windows machines reports India's time zone as "Asia/Calcutta"
        // (the old name). Newer PostgreSQL rejects that name and refuses to connect.
        // Using UTC everywhere avoids the problem, and it is what the server will use too.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

        SpringApplication.run(TatkalApplication.class, args);
    }
}
