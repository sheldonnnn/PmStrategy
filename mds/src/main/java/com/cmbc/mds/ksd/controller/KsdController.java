package com.cmbc.mds.ksd.controller;

import com.cmbc.mds.ksd.service.KsdService;
import com.cmbc.mds.ksd.service.KsdService.GatewayResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ksd")
public class KsdController {

    private final KsdService ksdService;

    public KsdController(KsdService ksdService) {
        this.ksdService = ksdService;
    }

    @GetMapping("/status")
    public ResponseEntity<String> status() {
        return toResponse(ksdService.status());
    }

    @PostMapping("/start")
    public ResponseEntity<String> start() {
        return toResponse(ksdService.start());
    }

    @PostMapping("/reconnect")
    public ResponseEntity<String> reconnect() {
        return toResponse(ksdService.reconnect());
    }

    @PostMapping("/stop")
    public ResponseEntity<String> stop() {
        return toResponse(ksdService.stop());
    }

    @PostMapping("/logout")
    public ResponseEntity<String> logout(@RequestParam(required = false) String userID) {
        return toResponse(ksdService.logout(userID));
    }

    private ResponseEntity<String> toResponse(GatewayResponse response) {
        return ResponseEntity.status(response.statusCode()).body(response.body());
    }
}
