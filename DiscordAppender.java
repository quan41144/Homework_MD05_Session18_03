package com.storex.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.AppenderBase;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

public class DiscordAppender extends AppenderBase<ILoggingEvent> {

    private String webhookUrl;

    private String username = "DevOps Alert Bot";
    private String avatarUrl = "https://cdn-icons-png.flaticon.com/512/564/564619.png";
    private String applicationName = "Storex Application";

    private int alertColor = 15158332;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    @Override
    public void start() {
        if (webhookUrl == null || webhookUrl.trim().isEmpty()) {
            addWarn("Webhook URL chưa được cấu hình cho DiscordAppender! Cảnh báo sẽ không được gửi.");
        }
        super.start();
    }

    @Override
    protected void append(ILoggingEvent eventObject) {
        if (eventObject.getLevel().levelInt == Level.ERROR_INT) {
            sendWebhook(eventObject);
        }
    }

    private void sendWebhook(ILoggingEvent eventObject) {
        if (webhookUrl == null || webhookUrl.trim().isEmpty()) {
            return;
        }

        try {
            String payloadJson;
            if (webhookUrl.contains("slack.com")) {
                payloadJson = buildSlackPayload(eventObject);
            } else {
                payloadJson = buildDiscordPayload(eventObject);
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(payloadJson, StandardCharsets.UTF_8))
                    .timeout(Duration.ofSeconds(5))
                    .build();

            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        int statusCode = response.statusCode();
                        if (statusCode < 200 || statusCode >= 300) {
                            addWarn("Gửi webhook Discord thất bại! HTTP Status: " + statusCode + ", Body: " + response.body());
                        }
                    })
                    .exceptionally(ex -> {
                        addWarn("Lỗi mạng khi kết nối tới Webhook Discord: " + ex.getMessage());
                        return null;
                    });

        } catch (Exception e) {
            addWarn("Lỗi không xác định khi tạo hoặc gửi webhook Discord: " + e.getMessage());
        }
    }

    private String buildDiscordPayload(ILoggingEvent eventObject) {
        String logMessage = eventObject.getFormattedMessage();
        String loggerName = eventObject.getLoggerName();
        String threadName = eventObject.getThreadName();
        String formattedTime = DATE_FORMATTER.format(Instant.ofEpochMilli(eventObject.getTimeStamp()));

        String stackTraceInfo = "";
        IThrowableProxy throwableProxy = eventObject.getThrowableProxy();
        if (throwableProxy != null) {
            String fullTrace = ThrowableProxyUtil.asString(throwableProxy);
            if (fullTrace.length() > 900) {
                fullTrace = fullTrace.substring(0, 900) + "\n... [Đã cắt bớt do vượt quá độ dài]";
            }
            stackTraceInfo = fullTrace;
        }

        String requestId = "";
        Map<String, String> mdcMap = eventObject.getMDCPropertyMap();
        if (mdcMap != null) {
            if (mdcMap.containsKey("requestId")) {
                requestId = mdcMap.get("requestId");
            } else if (mdcMap.containsKey("traceId")) {
                requestId = mdcMap.get("traceId");
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"username\":\"").append(escapeJson(username)).append("\",");
        sb.append("\"avatar_url\":\"").append(escapeJson(avatarUrl)).append("\",");
        sb.append("\"content\":\"**[SYSTEM ALERT] Phát hiện log ERROR trên hệ thống!**\",");
        sb.append("\"embeds\":[{");
        sb.append("\"title\":\"CẢNH BÁO LỖI HỆ THỐNG (ERROR)\",");
        sb.append("\"description\":\"**Chi tiết:** ").append(escapeJson(logMessage)).append("\",");
        sb.append("\"color\":").append(alertColor).append(",");
        sb.append("\"fields\":[");

        sb.append("{\"name\":\"Mức độ\",\"value\":\"`ERROR`\",\"inline\":true},");
        sb.append("{\"name\":\"Logger\",\"value\":\"`").append(escapeJson(loggerName)).append("`\",\"inline\":true},");
        sb.append("{\"name\":\"Thread\",\"value\":\"`").append(escapeJson(threadName)).append("`\",\"inline\":true},");
        sb.append("{\"name\":\"Thời gian\",\"value\":\"").append(escapeJson(formattedTime)).append("\",\"inline\":true}");

        if (!requestId.isEmpty()) {
            sb.append(",{\"name\":\"ID Request / Trace ID\",\"value\":\"`").append(escapeJson(requestId)).append("`\",\"inline\":true}");
        }

        if (!stackTraceInfo.isEmpty()) {
            sb.append(",{\"name\":\"Exception Stacktrace\",\"value\":\"```java\\n")
              .append(escapeJson(stackTraceInfo))
              .append("\\n```\",\"inline\":false}");
        }

        sb.append("],");
        sb.append("\"footer\":{\"text\":\"").append(escapeJson(applicationName)).append(" • Real-time Monitoring\"},");
        sb.append("\"timestamp\":\"").append(Instant.ofEpochMilli(eventObject.getTimeStamp()).toString()).append("\"");
        sb.append("}]");
        sb.append("}");

        return sb.toString();
    }

    private String buildSlackPayload(ILoggingEvent eventObject) {
        String logMessage = eventObject.getFormattedMessage();
        String loggerName = eventObject.getLoggerName();
        String formattedTime = DATE_FORMATTER.format(Instant.ofEpochMilli(eventObject.getTimeStamp()));

        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"text\":\"*[ERROR ALERT]* ").append(escapeJson(logMessage)).append("\",");
        sb.append("\"attachments\":[{");
        sb.append("\"color\":\"#FF0000\",");
        sb.append("\"title\":\"Chi tiết lỗi hệ thống\",");
        sb.append("\"text\":\"*Logger:* `").append(escapeJson(loggerName))
          .append("`\\n*Thời gian:* ").append(escapeJson(formattedTime))
          .append("\\n*Thông báo:* ").append(escapeJson(logMessage)).append("\"");
        sb.append("}]");
        sb.append("}");
        return sb.toString();
    }
    public void sendViaRestTemplate(String webhookUrl, String payloadJson) {
        org.springframework.web.client.RestTemplate restTemplate = new org.springframework.web.client.RestTemplate();
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        org.springframework.http.HttpEntity<String> entity = new org.springframework.http.HttpEntity<>(payloadJson, headers);
        restTemplate.postForEntity(webhookUrl, entity, String.class);
    }
    */

    private String escapeJson(String input) {
        if (input == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : input.toCharArray()) {
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < ' ') {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }
    public String getWebhookUrl() {
        return webhookUrl;
    }

    public void setWebhookUrl(String webhookUrl) {
        this.webhookUrl = webhookUrl;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public String getApplicationName() {
        return applicationName;
    }

    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }

    public int getAlertColor() {
        return alertColor;
    }

    public void setAlertColor(int alertColor) {
        this.alertColor = alertColor;
    }
}
