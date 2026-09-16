package com.enterpriseapp.procureflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class ProcureFlowApplication {

  public static void main(String[] args) {
    SpringApplication.run(ProcureFlowApplication.class, args);
  }
}
