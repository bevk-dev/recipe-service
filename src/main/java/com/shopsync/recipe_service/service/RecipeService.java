package com.shopsync.recipe_service.service;

import com.shopsync.recipe_service.entity.Recipe;
import com.shopsync.recipe_service.repository.RecipeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecipeService {

    @Value("${openai.api.key}")
    private String apiKey;

    // Dodana manjkajoča polja, da @RequiredArgsConstructor deluje
    private final RecipeRepository recipeRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    private final WebClient webClient = WebClient.builder()
            .baseUrl("https://api.openai.com/v1/chat/completions")
            .build();

    @Transactional
    public Recipe importRecipeFromUrl(String url, String userEmail) throws IOException {
        log.info("Uvažam recept: {}", url);

        // 1. Scraping
        Document doc = Jsoup.connect(url).get();
        String title = doc.select("h1").text();
        String rawText = doc.body().text();
        if (rawText.length() > 4000) rawText = rawText.substring(0, 4000);

        // 2. Prompt
        String prompt = "Iz spodnjega besedila recepta izlušči samo sestavine. " +
                "Vrni samo seznam sestavin, kjer so sestavine ločene s podpičjem (;). " +
                "Ne dodajaj uvodnega besedila. " +
                "Besedilo: " + rawText;

        // 3. AI klic
        String responseContent = callChatGPT(prompt);

        // 4. Procesiranje odgovora
        List<String> ingredients = Arrays.stream(responseContent.split(";"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());

        // 5. Shranjevanje
        Recipe recipe = Recipe.builder()
                .name(title)
                .sourceUrl(url)
                .createdByEmail(userEmail)
                .ingredients(ingredients)
                .build();

        Recipe savedRecipe = recipeRepository.save(recipe);

        // 6. Kafka Event
        String message = "RECIPE_IMPORTED|" + userEmail + "|" + title + "|" + String.join(", ", ingredients);
        kafkaTemplate.send("recipe-events", message);

        return savedRecipe;
    }

    private String callChatGPT(String prompt) {
        Map<String, Object> body = Map.of(
                "model", "gpt-4o-mini",
                "messages", List.of(Map.of("role", "user", "content", prompt)),
                "temperature", 0.1
        );

        try {
            Map response = webClient.post()
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response == null || !response.containsKey("choices")) {
                throw new RuntimeException("Prazen odgovor od OpenAI");
            }

            List choices = (List) response.get("choices");
            Map firstChoice = (Map) choices.get(0);
            Map message = (Map) firstChoice.get("message");
            return (String) message.get("content");
        } catch (Exception e) {
            log.error("Napaka pri klicu OpenAI: {}", e.getMessage());
            throw new RuntimeException("AI uvoz ni uspel: " + e.getMessage());
        }
    }
}