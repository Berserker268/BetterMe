package com.betterme.controller;

import com.betterme.user.PasswordResetService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class PasswordResetController {

    private final PasswordResetService service;

    public PasswordResetController(PasswordResetService service) {
        this.service = service;
    }

    @GetMapping("/forgot-password")
    public String forgotPage() {
        return "forgot-password";
    }

    @PostMapping("/forgot-password")
    public String forgot(@RequestParam("email") String email, Model model) {
        service.sendResetLink(email.trim());
        model.addAttribute("sent", true);
        return "forgot-password";
    }

    @GetMapping("/reset-password")
    public String resetPage(@RequestParam("token") String token, Model model) {
        model.addAttribute("token", token);
        model.addAttribute("valid", service.isValid(token));
        return "reset-password";
    }

    @PostMapping("/reset-password")
    public String reset(@RequestParam("token") String token,
                        @RequestParam("password") String password,
                        @RequestParam("confirm") String confirm,
                        Model model) {
        model.addAttribute("token", token);
        model.addAttribute("valid", true);

        if (password.length() < 8) {
            model.addAttribute("error", "Password must be at least 8 characters.");
            return "reset-password";
        }
        if (!password.equals(confirm)) {
            model.addAttribute("error", "Passwords do not match.");
            return "reset-password";
        }
        if (!service.resetPassword(token, password)) {
            model.addAttribute("valid", false);
            return "reset-password";
        }
        return "redirect:/login?reset";
    }
}