package com.cloudrun.calling.controller;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import com.cloudrun.calling.interceptor.GoogleAuthInterceptor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;

@RestController
public class CallingController {

  @Value("${RECEIVING_URL}")
  private String serviceUrl;

  @GetMapping(value = "/")
  public String getReceiving() {
      RestTemplate restTemplate = new RestTemplate();
      restTemplate.getInterceptors().add(new GoogleAuthInterceptor());
      ResponseEntity<String> response = restTemplate.getForEntity(serviceUrl, String.class);

    return "Receiving service says: \"" + response.getBody() + "\"";
  }

}
