package com.leetcode.demo.controller;

import com.leetcode.demo.service.LeetCodeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/leetcode")
public class LeetCodeController {

    private final LeetCodeService leetCodeService;

    public LeetCodeController(LeetCodeService leetCodeService) {
        this.leetCodeService = leetCodeService;
    }

    @GetMapping("/search")
    public Mono<ResponseEntity<String>> searchUsers(@RequestParam String name) {
        return leetCodeService.searchUser(name)
                .map(ResponseEntity::ok)
                .onErrorResume(e -> Mono.just(
                        ResponseEntity.badRequest()
                                .body("{\"error\": \"" + e.getMessage() + "\"}")
                ));
    }

    @GetMapping("/profile/{username}")
    public Mono<ResponseEntity<String>> getUserProfile(@PathVariable String username) {
        return leetCodeService.getUserProfile(username)
                .map(ResponseEntity::ok)
                .onErrorResume(e -> Mono.just(
                        ResponseEntity.badRequest()
                                .body("{\"error\": \"" + e.getMessage() + "\"}")
                ));
    }
}