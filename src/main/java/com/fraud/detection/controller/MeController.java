package com.fraud.detection.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
// @RequestMapping("/api")
public class MeController {

    @GetMapping("/api/me")
    public String me(Authentication authentication) {
        return authentication.getName();
    }
}
