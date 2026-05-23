package cm.cirt.bts.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class WebController {

    @GetMapping({"/", "/console"})
    public String index(Model model) {
        model.addAttribute("apiBase", "/api/v1");
        return "console";
    }
}
