package com.example.customer.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BuildController {

    @Value("${build.version:unknown}")
    private String buildVersion;

    @GetMapping("/version")
    public String getVersion() {
        return "Build Version: " + buildVersion;
    }
}
