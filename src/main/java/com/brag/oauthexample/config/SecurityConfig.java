package com.brag.oauthexample.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractAuthenticationFilterConfigurer;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.util.Base64;
import java.util.Properties;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final String filePath = "secrets.properties";

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> authorize
                        .anyRequest().authenticated())
                .oauth2Login(Customizer.withDefaults());
        return http.build();
    }

    @Bean
    public Cipher getCipher() throws Exception {

        return Cipher.getInstance("AES");
    }

    @Bean
    @Scope("prototype")
    public SecretKey getSecretKey() throws Exception {
        System.out.println("---- BUSCANDO CLAVE SECRETA EN SECURITY CONFIG ----");
        File secretKeyFile = new File(filePath);

        Properties properties = new Properties();

        if(!secretKeyFile.exists()) {
            KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
            keyGenerator.init(256);
            SecretKey secretKey = keyGenerator.generateKey();

            properties.setProperty("key", Base64.getEncoder().encodeToString(secretKey.getEncoded()));

            try(FileOutputStream secretKeyFileOutput = new FileOutputStream(filePath)) {
                properties.store(secretKeyFileOutput,
                        "Clave para desencriptar tus contraseñas: NO LA MUESTRES NI LA PIERDAS");
            }

            return secretKey;

        }else{
            try(FileInputStream secretKeyFileInput = new FileInputStream(filePath)) {
                properties.load(secretKeyFileInput);

                String key = properties.getProperty("key");

                byte[] decodedKey = Base64.getDecoder().decode(key);
                return new SecretKeySpec(decodedKey, 0, decodedKey.length, "AES");
            }
        }
    }

    public void changeKey(String secretKey) throws Exception{
        Properties properties = new Properties();
        properties.setProperty("key", secretKey);

        try(FileOutputStream secretKeyFileOutput = new FileOutputStream(filePath)) {
            properties.store(secretKeyFileOutput,
                    "Clave para desencriptar tus contraseñas: NO LA MUESTRES NI LA PIERDAS");

        }

    }
}