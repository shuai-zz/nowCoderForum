package org.example.nowcoder.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.example.nowcoder.application.service.DataService;
import org.example.nowcoder.interfaces.common.Result;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

@Tag(name = "Admin Stats", description = "管理端：UV / DAU 统计")
@RestController
@RequestMapping("/api/v1/admin/stats")
@RequiredArgsConstructor
public class DataController {

    private final DataService dataService;

    @Operation(summary = "区间 UV（独立访客）")
    @GetMapping("/uv")
    public Result<Long> uv(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end
    ) {
        return Result.ok(dataService.calculateUv(toDate(start), toDate(end)));
    }

    @Operation(summary = "区间 DAU（日活用户）")
    @GetMapping("/dau")
    public Result<Long> dau(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end
    ) {
        return Result.ok(dataService.calculateDau(toDate(start), toDate(end)));
    }

    private Date toDate(LocalDate ld) {
        return Date.from(ld.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }
}
