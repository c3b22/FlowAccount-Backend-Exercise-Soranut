package com.flowaccount.productmanagement.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    /** แยก Clock ออกมาเป็น bean เพื่อให้เทสต์กำหนดเวลาเองได้ */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
