package com.shopsync.recipe_service.controller;

import com.shopsync.recipe_service.entity.Recipe;
import com.shopsync.recipe_service.repository.RecipeRepository;
import com.shopsync.recipe_service.service.RecipeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/recipes")
@RequiredArgsConstructor
public class RecipeController {

    private final RecipeService recipeService;
    private final RecipeRepository recipeRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @PostMapping("/import")
    public String importRecipe(@RequestBody String url, @AuthenticationPrincipal Jwt jwt) {
        try {
            // Iz Auth0 tokena potegnemo email
            String userEmail = jwt.getClaimAsString("https://shopsync-api.com/email");
            if (userEmail == null) userEmail = jwt.getClaimAsString("email");

            Recipe recipe = recipeService.importRecipeFromUrl(url, userEmail);

            return "Uspešno uvožen recept: " + recipe.getName() +
                    " s " + recipe.getIngredients().size() + " sestavinami.";
        } catch (Exception e) {
            return "Napaka pri uvozu: " + e.getMessage();
        }
    }

    @GetMapping
    public ResponseEntity<List<Recipe>> getAllRecipes(@AuthenticationPrincipal Jwt jwt) {
        // Uporabimo isto logiko za email kot pri importu
        String userEmail = jwt.getClaimAsString("https://shopsync-api.com/email");
        if (userEmail == null) userEmail = jwt.getClaimAsString("email");

        System.out.println("Iščem recepte za email: " + userEmail); // Log za debug

        List<Recipe> recipes = recipeRepository.findByCreatedByEmail(userEmail);
        return ResponseEntity.ok(recipes);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Recipe> getRecipe(@PathVariable Long id) {
        return recipeRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRecipe(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        String userEmail = jwt.getClaimAsString("https://shopsync-api.com/email");
        if (userEmail == null) userEmail = jwt.getClaimAsString("email");

        Recipe recipe = recipeRepository.findById(id).orElse(null);

        if (recipe == null) {
            return ResponseEntity.notFound().build();
        }

        if (!recipe.getCreatedByEmail().equals(userEmail)) {
            return ResponseEntity.status(403).build();
        }

        recipeRepository.delete(recipe);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/create-list")
    public ResponseEntity<String> createShoppingListFromRecipe(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        // Auth0 ID se v JWT vedno nahaja v claimu "sub"
        String auth0Id = jwt.getSubject();

        return recipeRepository.findById(id).map(recipe -> {
            String message = "CREATE_LIST_FROM_RECIPE~" + auth0Id + "~" + recipe.getName() + "~" + String.join(";", recipe.getIngredients());

            kafkaTemplate.send("recipe-events", message);
            return ResponseEntity.ok("Zahteva poslana za uporabnika: " + auth0Id);
        }).orElse(ResponseEntity.notFound().build());
    }
}