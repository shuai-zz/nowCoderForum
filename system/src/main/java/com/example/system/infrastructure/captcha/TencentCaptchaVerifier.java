package com.example.system.infrastructure.captcha;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.example.nowcoder.exception.ValidationException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;

/**
 * 腾讯云验证码（Captcha）实现，调用 {@code DescribeCaptchaResult} 接口校验前端提交的 ticket/randstr。
 *
 * <p>使用 TC3-HMAC-SHA256 鉴权（官方签名协议），无外部 SDK 依赖。
 *
 * <p>注意：需要在 {@link CaptchaProperties} 中配置有效凭据才能工作；否则校验会直接失败。
 */
@Component
@ConditionalOnProperty(prefix = "nowCoder.captcha", name = "provider", havingValue = "tencent")
@RequiredArgsConstructor
@Slf4j
public class TencentCaptchaVerifier implements CaptchaVerifier {

    private static final String HOST = "captcha.tencentcloudapi.com";
    private static final String SERVICE = "captcha";
    private static final String ACTION = "DescribeCaptchaResult";
    private static final String VERSION = "2019-07-22";
    private static final String ALGORITHM = "TC3-HMAC-SHA256";

    private final CaptchaProperties props;
    private final ObjectMapper objectMapper;
    private final RestClient restClient = RestClient.create("https://" + HOST);

    @Override
    public void verify(CaptchaContext ctx) {
        if (StringUtils.isBlank(ctx.tencentTicket()) || StringUtils.isBlank(ctx.tencentRandstr())) {
            throw new ValidationException("Captcha ticket is required");
        }
        if (!isConfigured()) {
            log.error("Tencent captcha provider selected but credentials are not configured");
            throw new ValidationException("Captcha backend not configured");
        }
        try {
            Map<String, Object> payload = Map.of(
                    "CaptchaType", props.getTencent().getCaptchaType(),
                    "Ticket", ctx.tencentTicket(),
                    "Randstr", ctx.tencentRandstr(),
                    "UserIp", ctx.userIp() == null ? "" : ctx.userIp(),
                    "CaptchaAppId", props.getTencent().getCaptchaAppId(),
                    "AppSecretKey", props.getTencent().getAppSecretKey()
            );
            String body = objectMapper.writeValueAsString(payload);

            String response = restClient.post()
                    .uri("/")
                    .headers(h -> buildSignedHeaders(h::add, body))
                    .body(body)
                    .retrieve()
                    .body(String.class);

            Map<String, Object> parsed = objectMapper.readValue(response, new TypeReference<>() {});
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = (Map<String, Object>) parsed.get("Response");
            if (resp == null) {
                throw new ValidationException("Captcha service returned invalid response");
            }
            Object code = resp.get("CaptchaCode");
            if (code == null || ((Number) code).intValue() != 1) {
                log.warn("Tencent captcha verify failed: {}", resp);
                throw new ValidationException("Captcha verification failed");
            }
        } catch (ValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Tencent captcha verify error", e);
            throw new ValidationException("Captcha verification error");
        }
    }

    private boolean isConfigured() {
        CaptchaProperties.Tencent t = props.getTencent();
        return StringUtils.isNotBlank(t.getSecretId())
                && StringUtils.isNotBlank(t.getSecretKey())
                && t.getCaptchaAppId() != null
                && StringUtils.isNotBlank(t.getAppSecretKey());
    }

    private void buildSignedHeaders(HeaderPutter put, String body) {
        try {
            long ts = Instant.now().getEpochSecond();
            String date = LocalDate.ofInstant(Instant.ofEpochSecond(ts), ZoneOffset.UTC).toString();

            String hashedBody = sha256Hex(body);
            String canonicalRequest = "POST\n/\n\n"
                    + "content-type:application/json; charset=utf-8\n"
                    + "host:" + HOST + "\n"
                    + "x-tc-action:" + ACTION.toLowerCase() + "\n"
                    + "\n"
                    + "content-type;host;x-tc-action\n"
                    + hashedBody;

            String credentialScope = date + "/" + SERVICE + "/tc3_request";
            String stringToSign = ALGORITHM + "\n" + ts + "\n" + credentialScope + "\n" + sha256Hex(canonicalRequest);

            byte[] kDate = hmacSha256(("TC3" + props.getTencent().getSecretKey()).getBytes(StandardCharsets.UTF_8), date);
            byte[] kService = hmacSha256(kDate, SERVICE);
            byte[] kSigning = hmacSha256(kService, "tc3_request");
            String signature = hex(hmacSha256(kSigning, stringToSign));

            String authorization = ALGORITHM
                    + " Credential=" + props.getTencent().getSecretId() + "/" + credentialScope
                    + ", SignedHeaders=content-type;host;x-tc-action"
                    + ", Signature=" + signature;

            put.add("Authorization", authorization);
            put.add("Content-Type", "application/json; charset=utf-8");
            put.add("Host", HOST);
            put.add("X-TC-Action", ACTION);
            put.add("X-TC-Timestamp", String.valueOf(ts));
            put.add("X-TC-Version", VERSION);
        } catch (Exception e) {
            throw new RuntimeException("Failed to build TC3 signature", e);
        }
    }

    private static String sha256Hex(String s) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        return hex(md.digest(s.getBytes(StandardCharsets.UTF_8)));
    }

    private static byte[] hmacSha256(byte[] key, String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    @FunctionalInterface
    private interface HeaderPutter {
        void add(String name, String value);
    }
}
