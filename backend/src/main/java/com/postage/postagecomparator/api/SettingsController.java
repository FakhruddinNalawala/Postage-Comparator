package com.postage.postagecomparator.api;

import com.postage.postagecomparator.model.OriginSettings;
import com.postage.postagecomparator.model.ThemePreferenceRequest;
import com.postage.postagecomparator.service.SettingsService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private final SettingsService settingsService;

    public SettingsController(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping("/origin")
    public ResponseEntity<OriginSettings> getOrigin() {
        OriginSettings origin = settingsService.getOriginSettings();
        if (origin == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(origin);
    }

    @PutMapping("/origin")
    public ResponseEntity<OriginSettings> updateOrigin(@RequestBody @Valid OriginSettings settings) {
        OriginSettings updated = settingsService.updateOriginSettings(settings);
        return ResponseEntity.ok(updated);
    }

    @PutMapping("/theme")
    public ResponseEntity<OriginSettings> updateTheme(@RequestBody @Valid ThemePreferenceRequest request) {
        OriginSettings updated = settingsService.updateThemePreference(request.themePreference());
        return ResponseEntity.ok(updated);
    }
}
