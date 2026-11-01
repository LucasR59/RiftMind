package com.riftmind.backend.controller;

import java.time.Instant;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.riftmind.backend.dto.PingResponse;

/** Endpoint de test utilisé par le frontend pour vérifier que l'API répond. */
@RestController
@RequestMapping("/api")
public class PingController {

    @GetMapping("/ping")
    public PingResponse ping() {
        return new PingResponse("ok", "riftmind-backend", Instant.now());
    }
}
