package com.cloudrun.receiving.controller;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;


@RestController
public class ReceivingController {

  @Value("${REGION:World}")
  private String region;
  
  @GetMapping(value="/")
  public String getMethodName() {
      return "Hello from " + region;
  }
  
}
