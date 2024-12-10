package com.brag.oauthexample.controllers;
import com.brag.oauthexample.config.SecurityConfig;
import com.brag.oauthexample.models.Form;
import com.brag.oauthexample.services.GoogleDriveService;
import com.brag.oauthexample.services.PasswordService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.net.HttpHeaders;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.*;

@Controller
public class SingleController {

    private final GoogleDriveService googleDriveService;
    private final PasswordService passwordService;
    private final SecurityConfig securityConfig;

    private String userEmail;

    //Guarda los ID's de los archivos teniendo cada un como clave el email del usuario
    private final Map<String, String> filesId;

    //Map que guarda las contraseñas generadas, es mapeado a JSON para ser guardado en el drive.
    private Map<String, String> passwordsMap = new HashMap<>();

    private final ObjectMapper objectMapper;

    public SingleController(GoogleDriveService driveService, PasswordService passwordService, SecurityConfig securityConfig) {
        this.googleDriveService = driveService;
        this.passwordService = passwordService;
        this.securityConfig = securityConfig;
        this.filesId = new HashMap<>();
        this.objectMapper = new ObjectMapper();
    }

    @GetMapping("/")
    public String auth(OAuth2AuthenticationToken authentication, Model model){

        try {
            googleDriveService.initDriveService(authentication);

            userEmail = googleDriveService.getUsername();

            if(!filesId.containsKey(userEmail)){
                String fileId = googleDriveService.getFileId();

                try (InputStream fileMetadata = googleDriveService.getFile(fileId)){
                    passwordsMap = objectMapper.readValue(fileMetadata, Map.class);

                }catch (IOException e) {
                    System.err.println("Error: " + e.getMessage());
                    return "error";
                }

                filesId.put(userEmail, fileId);
            }
        } catch (GeneralSecurityException | IOException e) {
            throw new RuntimeException(e);
        }

        loadModel(model);

        return "home";
    }

    @PostMapping("/generate")
    public ResponseEntity<String> generatePassword(@RequestBody Form form){
        String fileId = filesId.get(userEmail);
        String generatedPass = passwordService.generatePassword(form.getPasswordLength());

        passwordsMap.put(form.getSite(), passwordService.encryptPassword(generatedPass));

        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            objectMapper.writeValue(outputStream, passwordsMap);

            googleDriveService.updateFile(fileId, outputStream);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return ResponseEntity.ok().body(generatedPass);
    }

    @GetMapping("/password/{site}")
    public ResponseEntity<String> getPassword(@PathVariable String site){
        site = site.replace("\"", "");

        return ResponseEntity.ok()
                .body(passwordService.decryptPassword(passwordsMap.get(site)));
    }

    @GetMapping("/download/{args}")
    public ResponseEntity<Resource> downloadPasswords(@PathVariable String args){

        args = args.replace("\"", "").toUpperCase();

        StringBuilder fileContentBuilder = new StringBuilder()
                .append(String.format("Contraseñas guardadas en el drive de %s\n", userEmail));

        switch (args){
            case "ALL":
                appendKey(fileContentBuilder);
                appendPasswords(fileContentBuilder);
                break;

            case "KEY":
                appendKey(fileContentBuilder);
                break;

            case "PASSWORDS":
                appendPasswords(fileContentBuilder);
                break;

            default:
                return ResponseEntity.notFound().build();
        }


        byte[] fileContentBytes = fileContentBuilder.toString()
                .getBytes(StandardCharsets.UTF_8);

        Resource resource = new ByteArrayResource(fileContentBytes);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=passwords.txt")
                .contentType(MediaType.TEXT_PLAIN)
                .contentLength(fileContentBytes.length)
                .body(resource);
    }

    private void appendKey(StringBuilder stringBuilder){
        stringBuilder.append(String.format("Encriptadas mediante algoritmo AES con clave secreta: %s\n",
                passwordService.getStringKey()));
    }

    private void appendPasswords(StringBuilder stringBuilder){
        String splitter = "=".repeat(100);

        stringBuilder.append(String.format("%s\n", splitter));
        stringBuilder.append("Contraseñas:\n");

        for(Map.Entry<String, String> entry : passwordsMap.entrySet()){

            String decryptedPass = passwordService.decryptPassword(entry.getValue());
            stringBuilder.append(String.format("%s: %s\n", entry.getKey(), decryptedPass));
        }

        stringBuilder.append(String.format("%s\n", splitter));
    }

    @PostMapping("/change-key")
    public ResponseEntity<String> changeKey(@RequestBody String key){
        try {
            securityConfig.changeKey(key.replace("\"", ""));
            return ResponseEntity.ok().build();

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    private void loadModel(Model model){
        List<String> sites = passwordsMap.keySet().stream().toList();
        model.addAttribute("sites", sites);

    }
}
