package com.example.travelfootprint.controller;

import com.example.travelfootprint.service.DestinationGuideService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class DestinationGuideController {

    private final DestinationGuideService guideService;

    public DestinationGuideController(DestinationGuideService guideService) {
        this.guideService = guideService;
    }

    @GetMapping("/guides")
    public String guides(
            @RequestParam(required = false) String province,
            @RequestParam(required = false) String q,
            Model model) {
        model.addAttribute("guide", guideService.build(province, q));
        return "destination-guides";
    }
}
