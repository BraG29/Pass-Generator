package com.brag.oauthexample.controllers;
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

    private String userEmail;

    //Guarda los ID's de los archivos teniendo cada un como clave el email del usuario
    private final Map<String, String> filesId;

    //Map que guarda las contraseñas generadas, es mapeado a JSON para ser guardado en el drive.
    private Map<String, String> passwordsMap = new HashMap<>();

    private final ObjectMapper objectMapper;

    public SingleController(GoogleDriveService driveService, PasswordService passwordService) {
        this.googleDriveService = driveService;
        this.passwordService = passwordService;
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
        System.out.println("Site: " + site);

        return ResponseEntity.ok()
                .body(passwordService.decryptPassword(passwordsMap.get(site)));
    }

    @GetMapping("/download")
    public ResponseEntity<Resource> downloadPasswords(){

        StringBuilder fileContentBuilder = new StringBuilder()
                .append(String.format("Contraseñas guardadas en el drive de %s\nTu clave secreta es: %s\n",
                        userEmail, passwordService.getStringKey()));

        for(Map.Entry<String, String> entry : passwordsMap.entrySet()){
            String decryptedPass = passwordService.decryptPassword(entry.getValue());
            fileContentBuilder.append(String.format("%s: %s\n", entry.getKey(), decryptedPass));
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

    private void loadModel(Model model){
        List<String> sites = passwordsMap.keySet().stream().toList();
        model.addAttribute("sites", sites);

    }
}
