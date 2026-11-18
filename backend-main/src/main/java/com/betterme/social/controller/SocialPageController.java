package com.betterme.social.controller;

import com.betterme.social.service.SocialService;

import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class SocialPageController {
    private final SocialService social;
    public SocialPageController(SocialService social) { this.social = social; }

    @GetMapping("/social")
    public String page(Authentication authentication, Model model) {
        model.addAttribute("socialMe", social.me(authentication.getName()));
        return "social";
    }

    @GetMapping("/social/posts/{id}/photo")
    @ResponseBody
    public ResponseEntity<byte[]> photo(Authentication authentication, @PathVariable("id") Long id) {
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .body(social.photo(authentication.getName(), id));
    }
}
