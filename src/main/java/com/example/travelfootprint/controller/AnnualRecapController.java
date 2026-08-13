package com.example.travelfootprint.controller;

import com.example.travelfootprint.model.TravelPost;
import com.example.travelfootprint.model.User;
import com.example.travelfootprint.repository.TravelPostRepository;
import com.example.travelfootprint.service.CurrentUserService;
import com.example.travelfootprint.service.TravelReportService;
import jakarta.servlet.http.HttpSession;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AnnualRecapController {

    private final CurrentUserService currentUserService;
    private final TravelReportService reportService;
    private final TravelPostRepository postRepository;

    public AnnualRecapController(
            CurrentUserService currentUserService,
            TravelReportService reportService,
            TravelPostRepository postRepository) {
        this.currentUserService = currentUserService;
        this.reportService = reportService;
        this.postRepository = postRepository;
    }

    @GetMapping("/recap")
    public String recap(
            @RequestParam(required = false) Integer year,
            HttpSession session,
            Model model,
            RedirectAttributes redirectAttributes) {
        User currentUser = currentUserService.getCurrentUser(session);
        if (currentUser == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "请先登录，再打开年度旅行回忆。");
            return "redirect:/login";
        }
        List<TravelPost> posts = postRepository.findByAuthorIdOrderByCreatedAtDesc(currentUser.getId());
        List<Integer> availableYears = posts.stream().map(TravelPost::getTravelDate).filter(Objects::nonNull)
                .map(LocalDate::getYear).distinct().sorted(Comparator.reverseOrder()).toList();
        int selectedYear = year != null && availableYears.contains(year)
                ? year
                : availableYears.stream().findFirst().orElse(LocalDate.now().getYear());
        model.addAttribute("recap", reportService.build(
                currentUser, TravelReportService.ReportPeriod.YEAR, LocalDate.of(selectedYear, 6, 1)));
        model.addAttribute("selectedYear", selectedYear);
        model.addAttribute("recapDate", selectedYear + "-06-01");
        model.addAttribute("availableYears", availableYears);
        return "annual-recap";
    }
}
