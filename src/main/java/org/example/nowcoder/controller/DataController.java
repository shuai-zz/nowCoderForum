package org.example.nowcoder.controller;

import lombok.RequiredArgsConstructor;
import org.example.nowcoder.service.DataService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.Date;

/**
 * @author zhaoshuai
 */
@Controller
@RequiredArgsConstructor
public class DataController {
    private final DataService dataService;

    @RequestMapping(path = "/data", method = {RequestMethod.GET, RequestMethod.POST})
    public String getData() {
        return "/site/admin/data";
    }

    @PostMapping("/data/uv")
    public String getUv(@DateTimeFormat(pattern = "yyyy-MM-dd") Date start,
                        @DateTimeFormat(pattern = "yyyy-MM-dd") Date end,
                        Model model){
        long uv = dataService.calculateUv(start, end);
        System.out.println(uv);
        model.addAttribute("uvResult", uv);
        model.addAttribute("uvStartDate", start);
        model.addAttribute("uvEndDate", end);
        return "forward:/data";
    }

    @PostMapping("/data/dau")
    public String getDau(@DateTimeFormat(pattern = "yyyy-MM-dd") Date start,
                         @DateTimeFormat(pattern = "yyyy-MM-dd") Date end,
                         Model model){
        long dau = dataService.calculateDau(start, end);
        model.addAttribute("dauResult", dau);
        model.addAttribute("dauStartDate", start);
        model.addAttribute("dauEndDate", end);
        return "forward:/data";
    }

}
