package com.jarvis.controller;

import com.jarvis.model.JarvisSettings;
import com.jarvis.service.SettingsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    @Autowired
    private SettingsService settingsService;

    @GetMapping
    public ResponseEntity<JarvisSettings> getSettings() {
        return ResponseEntity.ok(settingsService.getSafeSettings("default"));
    }

    @PutMapping
    public ResponseEntity<JarvisSettings> updateSettings(@RequestBody JarvisSettings update) {
        settingsService.updateSettings("default", update);
        return ResponseEntity.ok(settingsService.getSafeSettings("default"));
    }
}
