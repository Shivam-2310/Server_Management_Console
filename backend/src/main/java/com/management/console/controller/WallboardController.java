package com.management.console.controller;

import com.management.console.service.WallboardService;
import com.management.console.service.WallboardService.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for wallboard/big-screen monitoring endpoints.
 */
@RestController
@RequestMapping("/api/wallboard")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class WallboardController {

    private final WallboardService wallboardService;

    /**
     * Get comprehensive wallboard data
     */
    @GetMapping
    public ResponseEntity<WallboardData> getWallboardData() {
        return ResponseEntity.ok(wallboardService.getWallboardData());
    }

    /**
     * Get simplified status grid
     */
    @GetMapping("/grid")
    public ResponseEntity<StatusGrid> getStatusGrid() {
        return ResponseEntity.ok(wallboardService.getStatusGrid());
    }

    /**
     * Get incident summary
     */
    @GetMapping("/incidents")
    public ResponseEntity<IncidentSummary> getIncidentSummary() {
        return ResponseEntity.ok(wallboardService.getIncidentSummary());
    }

    /**
     * Get performance overview
     */
    @GetMapping("/performance")
    public ResponseEntity<PerformanceOverview> getPerformanceOverview() {
        return ResponseEntity.ok(wallboardService.getPerformanceOverview());
    }
}

