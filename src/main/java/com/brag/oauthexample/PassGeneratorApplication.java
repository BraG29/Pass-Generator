package com.brag.oauthexample;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.*;
import java.util.Properties;

@SpringBootApplication
public class PassGeneratorApplication {

    public static void main(String[] args) {
        ApplicationContext context = SpringApplication.run(PassGeneratorApplication.class, args);

    }

}
