package com.example.travelfootprint.controller;

import com.example.travelfootprint.model.User;
import com.example.travelfootprint.service.CurrentUserService;
import com.example.travelfootprint.service.DestinationMapService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/location")
public class ArrivalLocationController {

    private final CurrentUserService currentUserService;
    private final DestinationMapService destinationMapService;

    public ArrivalLocationController(
            CurrentUserService currentUserService,
            DestinationMapService destinationMapService) {
        this.currentUserService = currentUserService;
        this.destinationMapService = destinationMapService;
    }

    @GetMapping("/arrival-match")
    public ResponseEntity<?> arrivalMatch(
            @RequestParam double latitude,
            @RequestParam double longitude,
            HttpSession session) {
        User currentUser = currentUserService.getCurrentUser(session);
        if (currentUser == null) {
            return ResponseEntity.status(401).body(new ArrivalError("请先登录后再使用到访提醒。"));
        }
        try {
            return ResponseEntity.ok(destinationMapService.matchArrival(longitude, latitude));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(new ArrivalError(exception.getMessage()));
        }
    }

    private record ArrivalError(String message) {
    }
}
