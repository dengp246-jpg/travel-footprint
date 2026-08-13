package com.example.travelfootprint.controller;

import com.example.travelfootprint.model.User;
import com.example.travelfootprint.service.CurrentUserService;
import com.example.travelfootprint.service.PublicAttractionImportService;
import com.example.travelfootprint.service.PublicAttractionImportService.ImportResult;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class DataImportController {

    private final CurrentUserService currentUserService;
    private final PublicAttractionImportService importService;

    public DataImportController(CurrentUserService currentUserService, PublicAttractionImportService importService) {
        this.currentUserService = currentUserService;
        this.importService = importService;
    }

    @PostMapping("/imports/baidu-scenic-descriptions")
    public String importBaiduScenicDescriptions(HttpSession session, RedirectAttributes redirectAttributes) {
        User currentUser = currentUserService.getCurrentUser(session);
        if (currentUser == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "请先登录，再导入百度景点描述数据。");
            return "redirect:/login";
        }

        try {
            ImportResult result = importService.importBaiduScenicDescriptions();
            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "百度景点描述导入完成：新增 " + result.importedCount() + " 条，跳过 " + result.skippedCount() + " 条。");
        } catch (IllegalStateException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        }
        return "redirect:/";
    }
}
