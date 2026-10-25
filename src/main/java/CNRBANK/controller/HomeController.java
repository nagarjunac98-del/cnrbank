package CNRBANK.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HomeController {

    @GetMapping("/")
    public String home() {
        return "CNRBANK Spring Boot Backend is running successfully!";
    }

    @GetMapping("/api/health")
    public Map<String, String> health() {
        return Map.of(
                "application", "CNRBANK",
                "status", "UP",
                "message", "Backend API is working successfully"
        );
    }

    @GetMapping("/api/accounts")
    public Map<String, Object> getAccounts() {
        return Map.of(
                "application", "CNRBANK",
                "accounts", new String[]{
                        "Savings Account",
                        "Credit Account"
                },
                "message", "Demo account categories"
        );
    }
}