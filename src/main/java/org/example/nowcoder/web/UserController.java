package org.example.nowcoder.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.example.nowcoder.entity.User;
import org.example.nowcoder.exception.ResourceNotFoundException;
import org.example.nowcoder.exception.ValidationException;
import org.example.nowcoder.service.FollowService;
import org.example.nowcoder.service.LikeService;
import org.example.nowcoder.service.UserService;
import org.example.nowcoder.utils.ForumUtil;
import org.example.nowcoder.utils.HostHolder;
import org.example.nowcoder.web.common.Result;
import org.example.nowcoder.web.dto.ChangePasswordRequest;
import org.example.nowcoder.web.vo.UserProfileVO;
import org.example.nowcoder.web.vo.UserVO;
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
import java.util.Map;
import java.util.Set;

import static org.example.nowcoder.utils.ForumConstant.ENTITY_TYPE_USER;

@Tag(name = "User", description = "用户主页 / 头像 / 改密")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Slf4j
public class UserController {

    private static final Set<String> SUPPORTED_AVATAR_EXT = Set.of(".jpg", ".jpeg", ".png");

    private final UserService userService;
    private final LikeService likeService;
    private final FollowService followService;
    private final HostHolder hostHolder;

    @Value("${nowCoder.path.upload}")
    private String uploadPath;

    @Value("${nowCoder.path.domain}")
    private String domain;

    @Value("${server.servlet.context-path}")
    private String contextPath;

    @Operation(summary = "用户主页：基础信息 + 计数 + hasFollowed")
    @GetMapping("/{id}")
    public Result<UserProfileVO> profile(@PathVariable int id) {
        User user = userService.findUserById(id);
        if (user == null) {
            throw new ResourceNotFoundException("User not found: " + id);
        }
        long likeCount = likeService.findEntityLikeCount(ENTITY_TYPE_USER, id);
        long followeeCount = followService.findFolloweeCount(id, ENTITY_TYPE_USER);
        long followerCount = followService.findFollowerCount(ENTITY_TYPE_USER, id);
        User me = hostHolder.getUser();
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

        User me = hostHolder.getUser();
        String avatarUrl = domain + contextPath + "/api/v1/users/avatar/" + filename;
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
        User me = hostHolder.getUser();
        Map<String, Object> errors = userService.updatePassword(me.getId(), req.oldPassword(), req.newPassword());
        if (errors != null && !errors.isEmpty()) {
            throw new ValidationException(String.join("; ",
                    errors.values().stream().map(Object::toString).toList()));
        }
        return Result.ok();
    }
}
