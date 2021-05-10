package com.cloudrun.receiving.controller;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;


@RestController
public class ReceivingController {
  
  @GetMapping(value="/")
  public String getMethodName() {
      return "Hello World";
  }
  
}
