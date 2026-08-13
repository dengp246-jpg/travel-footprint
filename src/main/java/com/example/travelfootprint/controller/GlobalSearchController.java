package com.example.travelfootprint.controller;

import com.example.travelfootprint.model.User;
import com.example.travelfootprint.service.CurrentUserService;
import com.example.travelfootprint.service.GlobalSearchService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class GlobalSearchController {

    private final CurrentUserService currentUserService;
    private final GlobalSearchService searchService;

    public GlobalSearchController(CurrentUserService currentUserService, GlobalSearchService searchService) {
        this.currentUserService = currentUserService;
        this.searchService = searchService;
    }

    @GetMapping("/search")
    public String search(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "all") String type,
            HttpSession session,
            Model model) {
        User currentUser = currentUserService.getCurrentUser(session);
        model.addAttribute("search", searchService.search(currentUser, q, type));
        return "global-search";
    }
}
