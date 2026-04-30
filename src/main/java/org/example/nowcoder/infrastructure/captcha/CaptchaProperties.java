package org.example.nowcoder.infrastructure.captcha;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 验证码相关配置。通过 {@code nowcoder-captcha.*} 绑定。
 *
 * <p>腾讯云验证码字段仅在 {@code provider=tencent} 时生效。凭据建议用 env 注入：
 * <pre>
 *   nowcoder-captcha.tencent.secret-id: ${TENCENT_SECRET_ID}
 *   nowcoder-captcha.tencent.secret-key: ${TENCENT_SECRET_KEY}
 *   nowcoder-captcha.tencent.captcha-app-id: ${TENCENT_CAPTCHA_APP_ID}
 *   nowcoder-captcha.tencent.app-secret-key: ${TENCENT_CAPTCHA_APP_SECRET_KEY}
 * </pre>
 */
@Data
@ConfigurationProperties(prefix = "nowcoder-captcha")
public class CaptchaProperties {

    /** kaptcha（默认）或 tencent */
    private String provider = "kaptcha";

    private Tencent tencent = new Tencent();

    @Data
    public static class Tencent {
        private String secretId;
        private String secretKey;
        private Long captchaAppId;
        private String appSecretKey;
        /** 9 = 行为验证码（滑块/点选） */
        private Long captchaType = 9L;
    }
}
