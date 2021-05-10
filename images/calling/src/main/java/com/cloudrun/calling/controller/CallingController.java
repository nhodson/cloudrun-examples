package com.cloudrun.calling.controller;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.IdTokenCredentials;
import com.google.auth.oauth2.IdTokenProvider;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;

@RestController
public class CallingController {

  @Value("${RECEIVING_URL}")
  private String serviceUrl;

  private Logger logger = LogManager.getLogger(CallingController.class);

  @GetMapping(value = "/")
  public String getReceiving() {
    String responseString;
    try {
      GoogleCredentials credentials = GoogleCredentials.getApplicationDefault();
      IdTokenCredentials tokenCredentials = IdTokenCredentials.newBuilder()
          .setIdTokenProvider((IdTokenProvider) credentials).setTargetAudience(serviceUrl).build();

      String token = tokenCredentials.refreshAccessToken().getTokenValue();

      RestTemplate restTemplate = new RestTemplate();
      HttpHeaders headers = new HttpHeaders();
      headers.setBearerAuth(token);
      ResponseEntity<String> response = restTemplate.exchange(serviceUrl, HttpMethod.GET, new HttpEntity<>(headers), String.class);

      responseString = response.getBody();
    } catch (IOException e) {
      responseString = e.getMessage();
      logger.error(e);
    }

    return "Receiving service says: \"" +responseString + "\"";
  }

}
