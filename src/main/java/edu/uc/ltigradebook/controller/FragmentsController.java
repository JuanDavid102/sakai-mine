package edu.uc.ltigradebook.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class FragmentsController {

    @GetMapping("/head")
    public String headFragment() {
        return "head.html";
    }
    
    @GetMapping("/topbar")
    public String topbarFragment() {
        return "topbar.html";
    }

}
