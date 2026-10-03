package com.neonvibe.controller;

import com.neonvibe.dto.PushKeyResponse;
import com.neonvibe.dto.PushSubscriptionRequest;
import com.neonvibe.dto.PushTestResponse;
import com.neonvibe.dto.PushUnsubscribeRequest;
import com.neonvibe.security.SecurityUtils;
import com.neonvibe.security.UserPrincipal;
import com.neonvibe.service.PushNotificationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Web Push subscription endpoints for native notifications.
 */
@RestController
@RequestMapping("/api/v1/push")
public class PushController {

    private final PushNotificationService pushService;

    public PushController(PushNotificationService pushService) {
        this.pushService = pushService;
    }

    @GetMapping("/public-key")
    public ResponseEntity<PushKeyResponse> publicKey() {
        return ResponseEntity.ok(new PushKeyResponse(pushService.publicKey(), pushService.isConfigured()));
    }

    @PostMapping("/subscribe")
    public ResponseEntity<Void> subscribe(@Valid @RequestBody PushSubscriptionRequest request) {
        UserPrincipal user = SecurityUtils.currentUser();
        pushService.subscribe(user.id(), request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/unsubscribe")
    public ResponseEntity<Void> unsubscribe(@Valid @RequestBody PushUnsubscribeRequest request) {
        UserPrincipal user = SecurityUtils.currentUser();
        pushService.unsubscribe(user.id(), request.endpoint());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/test")
    public ResponseEntity<PushTestResponse> test() {
        if (!pushService.isConfigured()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        UserPrincipal user = SecurityUtils.currentUser();
        return ResponseEntity.ok(new PushTestResponse(pushService.sendTest(user.id())));
    }
}
