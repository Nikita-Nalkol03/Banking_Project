
package com.smartbank.ai;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
public class AiTestController {

    private final GeminiService geminiService;

    public AiTestController(GeminiService geminiService) {
        this.geminiService = geminiService;
    }

    @GetMapping("/test")
    public ResponseEntity<Map<String, String>> testGemini() {
        String response = geminiService.generateResponse(
                "Reply with exactly: SmartBank Gemini integration successful");

        return ResponseEntity.ok(Map.of("response", response));
    }
}