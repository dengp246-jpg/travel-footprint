package com.example.travelfootprint.controller;

import com.example.travelfootprint.model.DestinationWish;
import com.example.travelfootprint.model.DestinationWishPriority;
import com.example.travelfootprint.model.DestinationWishStatus;
import com.example.travelfootprint.model.TripPlan;
import com.example.travelfootprint.model.TripPlanStatus;
import com.example.travelfootprint.model.User;
import com.example.travelfootprint.repository.DestinationWishRepository;
import com.example.travelfootprint.repository.TripPlanRepository;
import com.example.travelfootprint.service.CurrentUserService;
import jakarta.servlet.http.HttpSession;
import java.time.LocalDate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class DestinationWishController {

    private final DestinationWishRepository wishRepository;
    private final TripPlanRepository planRepository;
    private final CurrentUserService currentUserService;

    public DestinationWishController(
            DestinationWishRepository wishRepository,
            TripPlanRepository planRepository,
            CurrentUserService currentUserService) {
        this.wishRepository = wishRepository;
        this.planRepository = planRepository;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/wishlist")
    public String wishlist(HttpSession session, Model model, RedirectAttributes redirectAttributes) {
        User currentUser = currentUserService.getCurrentUser(session);
        if (currentUser == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "请先登录，再管理想去清单。");
            return "redirect:/login";
        }
        var wishes = wishRepository.findByOwnerIdOrderByCreatedAtDesc(currentUser.getId());
        model.addAttribute("wishes", wishes);
        model.addAttribute("priorities", DestinationWishPriority.values());
        model.addAttribute("wishCount", wishes.stream().filter(item -> item.getStatus() == DestinationWishStatus.WISH).count());
        model.addAttribute("plannedCount", wishes.stream().filter(item -> item.getStatus() == DestinationWishStatus.PLANNED).count());
        model.addAttribute("visitedCount", wishes.stream().filter(item -> item.getStatus() == DestinationWishStatus.VISITED).count());
        model.addAttribute("currentYear", LocalDate.now().getYear());
        return "destination-wishlist";
    }

    @PostMapping("/wishlist")
    public String create(
            @RequestParam String destination,
            @RequestParam(required = false) String province,
            @RequestParam(required = false) String note,
            @RequestParam DestinationWishPriority priority,
            @RequestParam(required = false) Integer targetYear,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        User currentUser = currentUserService.getCurrentUser(session);
        if (currentUser == null) return "redirect:/login";
        String normalizedDestination = destination.trim();
        String normalizedNote = note == null ? "" : note.trim();
        if (normalizedDestination.isBlank() || normalizedDestination.length() > 100 || normalizedNote.length() > 500) {
            redirectAttributes.addFlashAttribute("errorMessage", "目的地不能为空且最多 100 字，备注最多 500 字。");
            return "redirect:/wishlist";
        }
        int thisYear = LocalDate.now().getYear();
        if (targetYear != null && (targetYear < thisYear || targetYear > thisYear + 20)) {
            redirectAttributes.addFlashAttribute("errorMessage", "目标年份应在未来 20 年内。");
            return "redirect:/wishlist";
        }
        DestinationWish wish = new DestinationWish();
        wish.setOwner(currentUser);
        wish.setDestination(normalizedDestination);
        wish.setProvince(province == null ? "" : province.trim());
        wish.setNote(normalizedNote);
        wish.setPriority(priority);
        wish.setTargetYear(targetYear);
        wish.setStatus(DestinationWishStatus.WISH);
        wishRepository.save(wish);
        redirectAttributes.addFlashAttribute("successMessage", "已加入想去清单。");
        return "redirect:/wishlist";
    }

    @PostMapping("/wishlist/{id}/status")
    public String updateStatus(
            @PathVariable Long id,
            @RequestParam DestinationWishStatus status,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        User currentUser = currentUserService.getCurrentUser(session);
        DestinationWish wish = ownedWish(id, currentUser);
        if (wish == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "只能修改自己的想去清单。");
            return "redirect:/wishlist";
        }
        wish.setStatus(status);
        wishRepository.save(wish);
        return "redirect:/wishlist";
    }

    @PostMapping("/wishlist/{id}/convert")
    public String convertToPlan(
            @PathVariable Long id,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        User currentUser = currentUserService.getCurrentUser(session);
        DestinationWish wish = ownedWish(id, currentUser);
        if (wish == null) return "redirect:/wishlist";
        if (wish.getTripPlan() != null) {
            return "redirect:/plans/" + wish.getTripPlan().getId();
        }
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            redirectAttributes.addFlashAttribute("errorMessage", "结束日期不能早于开始日期。");
            return "redirect:/wishlist";
        }
        TripPlan plan = new TripPlan();
        plan.setOwner(currentUser);
        plan.setTitle(wish.getDestination() + "旅行计划");
        plan.setDestination((wish.getProvince() == null || wish.getProvince().isBlank() ? "" : wish.getProvince() + " · ")
                + wish.getDestination());
        plan.setStartDate(startDate);
        plan.setEndDate(endDate);
        plan.setStatus(TripPlanStatus.PLANNED);
        plan.setNotes(wish.getNote() == null ? "" : wish.getNote());
        plan = planRepository.save(plan);
        wish.setTripPlan(plan);
        wish.setStatus(DestinationWishStatus.PLANNED);
        wishRepository.save(wish);
        redirectAttributes.addFlashAttribute("successMessage", "想去目的地已转换为旅行计划。");
        return "redirect:/plans/" + plan.getId();
    }

    @PostMapping("/wishlist/{id}/delete")
    public String delete(@PathVariable Long id, HttpSession session, RedirectAttributes redirectAttributes) {
        DestinationWish wish = ownedWish(id, currentUserService.getCurrentUser(session));
        if (wish != null) {
            wishRepository.delete(wish);
            redirectAttributes.addFlashAttribute("successMessage", "已从想去清单移除。");
        }
        return "redirect:/wishlist";
    }

    private DestinationWish ownedWish(Long id, User user) {
        if (user == null) return null;
        return wishRepository.findById(id).filter(item -> item.getOwner().getId().equals(user.getId())).orElse(null);
    }
}
