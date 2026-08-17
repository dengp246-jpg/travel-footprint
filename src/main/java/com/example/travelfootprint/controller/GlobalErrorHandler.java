package com.example.travelfootprint.controller;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@ControllerAdvice
public class GlobalErrorHandler {

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public String notFound(HttpServletResponse response, Model model) {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        model.addAttribute("status", HttpServletResponse.SC_NOT_FOUND);
        return "error";
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String uploadTooLarge(HttpServletRequest request, RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute(
                "errorMessage",
                "上传内容总量过大。请压缩或减少图片、视频后重试。");
        String path = request.getRequestURI();
        if ("/settings".equals(path)) {
            return "redirect:/settings";
        }
        if (path != null && path.matches("/posts/\\d+/edit")) {
            return "redirect:" + path;
        }
        return "redirect:/posts/new";
    }
}
