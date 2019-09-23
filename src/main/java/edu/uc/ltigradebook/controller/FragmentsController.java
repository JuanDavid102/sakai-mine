package edu.uc.ltigradebook.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class FragmentsController {

    @GetMapping("/head")
    public String headFragment() {
        return "head.html";
    }

    @GetMapping("/sidebar")
    public String sidebarFragment() {
        return "sidebar.html";
    }

    @GetMapping("/footer")
    public String footerFragment() {
        return "footer.html";
    }
    
    @GetMapping("/topbar")
    public String topbarFragment() {
        return "topbar.html";
    }

    @GetMapping("/custom")
    public String customFragment() {
        return "custom.html";
    }

    @GetMapping("/scroll")
    public String scrollFragment() {
        return "scroll.html";
    }

    @GetMapping("/logout")
    public String logoutFragment() {
        return "logout.html";
    }

}
