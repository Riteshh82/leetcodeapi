package com.leetcode.demo.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class LeetCodeService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public LeetCodeService(WebClient.Builder builder, ObjectMapper objectMapper) {
        this.webClient = builder
                .baseUrl("https://leetcode.com")
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.REFERER, "https://leetcode.com")
                .defaultHeader(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build();

        this.objectMapper = objectMapper;
    }

    public Mono<String> searchUser(String keyword) {
        // Generate possible username variations
        List<String> usernamesToTry = generatePossibleUsernames(keyword);

        return Flux.fromIterable(usernamesToTry)
                .flatMap(this::tryGetUser, 10) // Try 10 at a time for better performance
                .filter(user -> user != null)
                .distinct(user -> user.get("username")) // Remove duplicates by username
                .collectList()
                .map(users -> {
                    try {
                        return objectMapper.writeValueAsString(Map.of(
                                "data", Map.of("userSearchList", users)
                        ));
                    } catch (Exception e) {
                        return "{\"data\":{\"userSearchList\":[]}}";
                    }
                });
    }

    private List<String> generatePossibleUsernames(String keyword) {
        List<String> usernames = new ArrayList<>();
        String lower = keyword.toLowerCase().trim();
        String upper = keyword.toUpperCase().trim();
        String capitalized = keyword.substring(0, 1).toUpperCase() + keyword.substring(1).toLowerCase();

        // Add exact matches
        usernames.add(keyword);
        usernames.add(lower);
        usernames.add(upper);
        usernames.add(capitalized);

        // Add variations without spaces
        if (keyword.contains(" ")) {
            String noSpace = keyword.replaceAll("\\s+", "");
            usernames.add(noSpace);
            usernames.add(noSpace.toLowerCase());
            usernames.add(noSpace.toUpperCase());

            String underscore = keyword.replaceAll("\\s+", "_");
            usernames.add(underscore);
            usernames.add(underscore.toLowerCase());

            String dash = keyword.replaceAll("\\s+", "-");
            usernames.add(dash);
            usernames.add(dash.toLowerCase());
        }

        // Add number variations (0-100)
        for (int i = 0; i <= 100; i++) {
            usernames.add(lower + i);
            usernames.add(capitalized + i);
            if (i < 10) {
                usernames.add(lower + "0" + i);
            }
        }

        // Add year variations (1990-2025)
        for (int year = 1990; year <= 2025; year++) {
            usernames.add(lower + year);
        }

        // Add underscore and dash patterns
        usernames.add(lower + "_" + lower);
        usernames.add(lower + "-" + lower);
        usernames.add(lower + "_123");
        usernames.add(lower + "_" + lower.substring(0, Math.min(4, lower.length())));

        // Add common suffixes
        String[] suffixes = {"_dev", "_code", "_coder", "_leetcode", "_algo", "_cp", "123", "456", "789"};
        for (String suffix : suffixes) {
            usernames.add(lower + suffix);
        }

        // Add first few chars patterns
        if (keyword.length() >= 4) {
            String prefix = lower.substring(0, 4);
            for (int i = 0; i < 20; i++) {
                usernames.add(prefix + i);
            }
        }

        return usernames.stream().distinct().limit(200).toList();
    }

    private Mono<Map<String, Object>> tryGetUser(String username) {
        String query = """
            query userPublicProfile($username: String!) {
              matchedUser(username: $username) {
                username
                profile {
                  ranking
                  userAvatar
                  realName
                  reputation
                }
              }
            }
        """;

        Map<String, Object> body = Map.of(
                "query", query,
                "variables", Map.of("username", username)
        );

        return webClient.post()
                .uri("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .flatMap(response -> {
                    try {
                        JsonNode root = objectMapper.readTree(response);
                        JsonNode matchedUser = root.path("data").path("matchedUser");

                        if (!matchedUser.isMissingNode() && !matchedUser.isNull()) {
                            // Generate a unique ID from username
                            String id = Integer.toHexString(matchedUser.path("username").asText().hashCode());

                            Map<String, Object> userMap = Map.of(
                                    "_id", id,
                                    "username", matchedUser.path("username").asText(),
                                    "realName", matchedUser.path("profile").path("realName").asText(""),
                                    "userAvatar", matchedUser.path("profile").path("userAvatar").asText(""),
                                    "ranking", matchedUser.path("profile").path("ranking").asInt(0),
                                    "reputation", matchedUser.path("profile").path("reputation").asInt(0)
                            );
                            return Mono.just(userMap);
                        }
                        return Mono.empty();
                    } catch (Exception e) {
                        return Mono.empty();
                    }
                })
                .onErrorResume(e -> Mono.empty());
    }

    public Mono<String> getUserProfile(String username) {
        String query = """
            query userPublicProfile($username: String!) {
              matchedUser(username: $username) {
                username
                profile {
                  ranking
                  userAvatar
                  realName
                  reputation
                  websites
                  countryName
                  skillTags
                  company
                  school
                  starRating
                  aboutMe
                  solutionCount
                  postViewCount
                }
                submitStats {
                  acSubmissionNum {
                    difficulty
                    count
                    submissions
                  }
                  totalSubmissionNum {
                    difficulty
                    count
                    submissions
                  }
                }
              }
            }
        """;

        Map<String, Object> body = Map.of(
                "query", query,
                "variables", Map.of("username", username)
        );

        return webClient.post()
                .uri("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .onErrorResume(WebClientResponseException.class, e -> {
                    String errorMsg = String.format("LeetCode API error: %s - %s",
                            e.getStatusCode(), e.getResponseBodyAsString());
                    return Mono.error(new RuntimeException(errorMsg));
                })
                .onErrorResume(Exception.class, e -> {
                    return Mono.error(new RuntimeException("Failed to get user profile: " + e.getMessage()));
                });
    }
}