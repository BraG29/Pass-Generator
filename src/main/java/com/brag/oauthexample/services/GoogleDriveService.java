package com.brag.oauthexample.services;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.InputStreamContent;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Date;
import java.util.Objects;

@Service
public class GoogleDriveService {

    private final OAuth2AuthorizedClientService authorizedClientService;
    private Drive driveService;

    private final String fileName = "0pass-generator.json";

    public GoogleDriveService(OAuth2AuthorizedClientService authorizedClientService) {
        this.authorizedClientService = authorizedClientService;
    }

    public void initDriveService(OAuth2AuthenticationToken authentication) throws GeneralSecurityException, IOException {
        OAuth2AuthorizedClient client = authorizedClientService.loadAuthorizedClient(
                authentication.getAuthorizedClientRegistrationId(),
                authentication.getName());

        GoogleCredentials credentials = GoogleCredentials.create(
                AccessToken.newBuilder()
                        .setTokenValue(client.getAccessToken().getTokenValue())
                        .setExpirationTime(Date.from(Objects.requireNonNull(client.getAccessToken().getExpiresAt())))
                        .build()
        );

        driveService = new Drive.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                new HttpCredentialsAdapter(credentials)
        ).setApplicationName("Pass-Generator").build();

    }

    public String getUsername() throws IOException{
        return driveService.about()
                .get()
                .setFields("user")
                .execute()
                .getUser()
                .getEmailAddress();
    }

    private FileList getFileList(String query, String... fields) throws IOException{

        String fieldsToString = String.join(",", fields);

        return driveService.files().list()
                .setOrderBy("name")
                .setQ(query)
                .setFields(String.format("nextPageToken, files(%s)", fieldsToString))
                .execute();
    }

    public InputStream getFile(String fileId) throws IOException{
        return driveService.files().get(fileId).executeMediaAsInputStream();
    }

    public String getFileId() throws IOException{
        String query = String.format("name contains '%s'", fileName);
        FileList result = getFileList(query, "id");

        if(result.getFiles().isEmpty()){
            InputStreamContent content = new InputStreamContent("application/json",
                    new ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8)));

            File fileMetadata = new File().setName(fileName);

            return driveService.files()
                    .create(fileMetadata, content)
                    .execute()
                    .getId();
        }

        return result.getFiles()
                .getFirst()
                .getId();

    }

    public String updateFile(String fileId, ByteArrayOutputStream outputStream) throws IOException{
        File fileMetadata = new File().setName(fileName);

        InputStreamContent mediaContent = new InputStreamContent("application/json",
                new ByteArrayInputStream(outputStream.toByteArray()));

        return driveService.files()
                .update(fileId, fileMetadata, mediaContent)
                .execute()
                .getId();
    }
}
