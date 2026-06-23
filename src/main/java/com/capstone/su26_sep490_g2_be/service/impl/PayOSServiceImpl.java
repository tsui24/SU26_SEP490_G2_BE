package com.capstone.su26_sep490_g2_be.service.impl;

import com.capstone.su26_sep490_g2_be.config.PayOSProperties;
import com.capstone.su26_sep490_g2_be.enums.ErrorCode;
import com.capstone.su26_sep490_g2_be.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class PayOSServiceImpl implements com.capstone.su26_sep490_g2_be.service.PayOSService {

    private final PayOSProperties payOSProperties;
    // ObjectMapper được khởi tạo trực tiếp để tránh dependency injection issue
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public String createPaymentLink(long orderCode, long amountVnd, String description) {
        String desc = description.length() > 25 ? description.substring(0, 25) : description;
        String returnUrl = payOSProperties.getReturnUrl();
        String cancelUrl = payOSProperties.getCancelUrl();

        String signature = buildCreateSignature(orderCode, amountVnd, desc, returnUrl, cancelUrl);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("orderCode", orderCode);
        body.put("amount", amountVnd);
        body.put("description", desc);
        body.put("cancelUrl", cancelUrl);
        body.put("returnUrl", returnUrl);
        body.put("signature", signature);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-client-id", payOSProperties.getClientId());
        headers.set("x-api-key", payOSProperties.getApiKey());

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    payOSProperties.getApiUrl() + "/v2/payment-requests",
                    request,
                    String.class);

            JsonNode root = objectMapper.readTree(response.getBody());
            String code = root.path("code").asText();
            if (!"00".equals(code)) {
                log.error("PayOS error: code={} desc={}", code, root.path("desc").asText());
                throw new BusinessException(ErrorCode.PAYMENT_CREATE_FAILED);
            }
            return root.path("data").path("checkoutUrl").asText();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("PayOS createPaymentLink error: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.PAYMENT_CREATE_FAILED);
        }
    }

    @Override
    public boolean verifyWebhookSignature(String rawBody) {
        try {
            JsonNode root = objectMapper.readTree(rawBody);
            String receivedSig = root.path("signature").asText();
            JsonNode data = root.path("data");

            TreeMap<String, String> fields = new TreeMap<>();
            for (Map.Entry<String, JsonNode> entry : data.properties()) {
                if (!entry.getValue().isNull() && !entry.getValue().isMissingNode()) {
                    fields.put(entry.getKey(), entry.getValue().asText());
                }
            }

            StringBuilder sb = new StringBuilder();
            fields.forEach((k, v) -> {
                if (!sb.isEmpty()) sb.append("&");
                sb.append(k).append("=").append(v);
            });

            String computed = hmacSha256(sb.toString(), payOSProperties.getChecksumKey());
            return computed.equalsIgnoreCase(receivedSig);
        } catch (Exception e) {
            log.error("Webhook signature verify error: {}", e.getMessage());
            return false;
        }
    }

    private String buildCreateSignature(
            long orderCode, long amount, String description,
            String returnUrl, String cancelUrl) {
        String data = "amount=" + amount
                + "&cancelUrl=" + cancelUrl
                + "&description=" + description
                + "&orderCode=" + orderCode
                + "&returnUrl=" + returnUrl;
        return hmacSha256(data, payOSProperties.getChecksumKey());
    }

    private String hmacSha256(String data, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException("HMAC-SHA256 error", e);
        }
    }
}
