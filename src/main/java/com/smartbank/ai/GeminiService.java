
package com.smartbank.ai;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class GeminiService {

    private final Client geminiClient;
    private final String model;

    public GeminiService(
            Client geminiClient,
            @Value("${gemini.api.model}") String model) {
        this.geminiClient = geminiClient;
        this.model = model;
    }


public String generateResponse(String prompt) {
    if (prompt == null || prompt.isBlank()) {
        throw new IllegalArgumentException(
                "Prompt must not be empty");
    }

    try {
        GenerateContentResponse response =
                geminiClient.models.generateContent(
                        model,
                        prompt,
                        null);

        String result = response.text();

        if (result == null || result.isBlank()) {
            throw new IllegalStateException(
                    "Gemini returned an empty response");
        }

        return result.trim();

    } catch (RuntimeException ex) {
        System.err.println(
                "Gemini request failed: " + ex.getClass().getName());
        ex.printStackTrace();
        throw ex;
    }
}


}