package com.example.user.interfaces.rest;

import com.example.interaction.application.service.FollowService;
import com.example.interaction.application.service.LikeService;
import com.example.shared.common.exception.ResourceNotFoundException;
import com.example.shared.common.exception.ValidationException;
import com.example.shared.common.result.Result;
import com.example.shared.common.utils.ForumUtil;
import com.example.user.application.service.UserService;
import com.example.user.domain.User;
import com.example.user.infrastructure.utils.SecurityUtil;
import com.example.user.interfaces.vo.UserProfileVO;
import com.example.user.interfaces.vo.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import org.example.nowcoder.interfaces.dto.ChangePasswordRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static com.example.shared.common.constant.ForumConstant.ENTITY_TYPE_USER;

/**
 * @author zhaoshuai
 */
@Tag(name = "User", description = "用户主页 / 头像 / 改密")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {
    final Logger log= LoggerFactory.getLogger(getClass());

    private static final Set<String> SUPPORTED_AVATAR_EXT = Set.of(".jpg", ".jpeg", ".png");

    private final UserService userService;
    private final LikeService likeService;
    private final FollowService followService;


    @Value("${nowcoder.path.upload}")
    private String uploadPath;

    @Value("${nowcoder.path.domain}")
    private String domain;

    @Operation(summary = "用户主页：基础信息 + 计数 + hasFollowed")
    @GetMapping("/{id}")
    public Result<UserProfileVO> profile(@PathVariable int id) {
        User user = userService.getById(id);
        if (user == null) {
            throw new ResourceNotFoundException("User not found: " + id);
        }
        long likeCount = likeService.findEntityLikeCount(ENTITY_TYPE_USER, id);
        long followeeCount = followService.findFolloweeCount(id, ENTITY_TYPE_USER);
        long followerCount = followService.findFollowerCount(ENTITY_TYPE_USER, id);
        User me = SecurityUtil.getCurrentUser();
        boolean hasFollowed = me != null && followService.hasFollowed(me.getId(), ENTITY_TYPE_USER, id);

        return Result.ok(new UserProfileVO(
                UserVO.from(user), likeCount, followeeCount, followerCount, hasFollowed));
    }

    @Operation(summary = "上传当前用户头像，返回头像 URL")
    @PostMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<String> uploadAvatar(@RequestPart("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ValidationException("Please select an image");
        }
        String original = file.getOriginalFilename();
        String ext = original == null ? "" : original.substring(original.lastIndexOf(".")).toLowerCase();
        if (StringUtils.isBlank(ext) || !SUPPORTED_AVATAR_EXT.contains(ext)) {
            throw new ValidationException("Only jpg/jpeg/png are supported");
        }

        String filename = ForumUtil.generateUuid() + ext;
        Path dest = Path.of(uploadPath, filename);
        try {
            Files.createDirectories(dest.getParent());
            file.transferTo(dest.toFile());
        } catch (IOException e) {
            log.error("Avatar upload failed: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to upload avatar", e);
        }

        User me = SecurityUtil.getCurrentUser();
        String avatarUrl = domain + "/api/v1/users/avatar/" + filename;
        userService.updateAvatar(me.getId(), avatarUrl);
        return Result.ok(avatarUrl);
    }

    @Operation(summary = "读取头像图片（二进制）")
    @GetMapping("/avatar/{filename}")
    public void avatar(@PathVariable String filename, HttpServletResponse response) throws IOException {
        File file = new File(uploadPath, filename);
        if (!file.exists() || !file.isFile()) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        String ext = filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
        response.setContentType("image/" + ext);
        try (OutputStream os = response.getOutputStream();
             FileInputStream fis = new FileInputStream(file)) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = fis.read(buf)) != -1) {
                os.write(buf, 0, n);
            }
        }
    }

    @Operation(summary = "修改当前用户密码")
    @PatchMapping("/me/password")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordRequest req) {
        if (!req.newPassword().equals(req.confirmPassword())) {
            throw new ValidationException("Passwords do not match");
        }
        User me = SecurityUtil.getCurrentUser();
        userService.updatePassword(me.getId(), req.oldPassword(), req.newPassword());
        return Result.ok();
    }
}
