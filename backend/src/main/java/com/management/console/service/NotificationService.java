package com.management.console.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.management.console.domain.entity.Incident;
import com.management.console.domain.entity.ManagedService;
import com.management.console.domain.enums.HealthStatus;
import com.management.console.domain.enums.IncidentSeverity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Multi-channel notification service supporting Email, Slack, Discord, Microsoft Teams, and Webhooks.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    // Optional: Inject if email is configured
    // private final JavaMailSender mailSender;

    @Value("${app.notifications.slack.webhook-url:}")
    private String slackWebhookUrl;

    @Value("${app.notifications.discord.webhook-url:}")
    private String discordWebhookUrl;

    @Value("${app.notifications.teams.webhook-url:}")
    private String teamsWebhookUrl;

    @Value("${app.notifications.email.enabled:false}")
    private boolean emailEnabled;

    @Value("${app.notifications.email.recipients:}")
    private String emailRecipients;

    @Value("${app.notifications.email.from:noreply@management-console.local}")
    private String emailFrom;

    @Value("${app.notifications.custom-webhooks:}")
    private String customWebhooks;

    // Track notification history
    private final List<NotificationRecord> notificationHistory = Collections.synchronizedList(new ArrayList<>());
    private static final int MAX_HISTORY_SIZE = 1000;

    // Notification throttling to prevent spam
    private final Map<String, Long> lastNotificationTime = new ConcurrentHashMap<>();
    private static final long THROTTLE_INTERVAL_MS = 60000; // 1 minute

    // Notification channels
    private final Map<String, NotificationChannel> channels = new LinkedHashMap<>();

    @PostConstruct
    public void init() {
        // Register available channels
        if (slackWebhookUrl != null && !slackWebhookUrl.isEmpty()) {
            channels.put("slack", new SlackChannel(slackWebhookUrl));
            log.info("Slack notification channel configured");
        }
        if (discordWebhookUrl != null && !discordWebhookUrl.isEmpty()) {
            channels.put("discord", new DiscordChannel(discordWebhookUrl));
            log.info("Discord notification channel configured");
        }
        if (teamsWebhookUrl != null && !teamsWebhookUrl.isEmpty()) {
            channels.put("teams", new TeamsChannel(teamsWebhookUrl));
            log.info("Microsoft Teams notification channel configured");
        }
        if (emailEnabled) {
            channels.put("email", new EmailChannel());
            log.info("Email notification channel configured");
        }

        // Parse custom webhooks
        if (customWebhooks != null && !customWebhooks.isEmpty()) {
            Arrays.stream(customWebhooks.split(","))
                    .map(String::trim)
                    .filter(url -> !url.isEmpty())
                    .forEach(url -> {
                        String name = "webhook-" + (channels.size() + 1);
                        channels.put(name, new WebhookChannel(url));
                        log.info("Custom webhook channel configured: {}", name);
                    });
        }
    }

    /**
     * Send notification for service health change
     */
    @Async
    public void notifyHealthChange(ManagedService service, HealthStatus oldStatus, HealthStatus newStatus) {
        String throttleKey = "health:" + service.getId();
        if (isThrottled(throttleKey)) {
            log.debug("Notification throttled for service health: {}", service.getName());
            return;
        }

        String title = String.format("🔔 Service Health Alert: %s", service.getName());
        String message = String.format(
                "Service **%s** health status changed from **%s** to **%s**\n" +
                "Environment: %s\n" +
                "Time: %s",
                service.getName(),
                oldStatus,
                newStatus,
                service.getEnvironment(),
                LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        );

        NotificationSeverity severity = mapHealthToSeverity(newStatus);
        sendToAllChannels(title, message, severity, "health_change", service.getName());
    }

    /**
     * Send notification for new incident
     */
    @Async
    public void notifyIncident(Incident incident) {
        String throttleKey = "incident:" + incident.getId();
        if (isThrottled(throttleKey)) {
            return;
        }

        String emoji = getIncidentEmoji(incident.getSeverity());
        String title = String.format("%s Incident: %s", emoji, incident.getTitle());
        String serviceName = incident.getService() != null ? incident.getService().getName() : "Unknown";
        String message = String.format(
                "**Severity:** %s\n" +
                "**Service:** %s\n" +
                "**Description:** %s\n" +
                "**Time:** %s",
                incident.getSeverity(),
                serviceName,
                incident.getDescription(),
                incident.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        );

        NotificationSeverity severity = mapIncidentToSeverity(incident.getSeverity());
        sendToAllChannels(title, message, severity, "incident", serviceName);
    }

    /**
     * Send notification for lifecycle action
     */
    @Async
    public void notifyLifecycleAction(ManagedService service, String action, String result, String performedBy) {
        String title = String.format("⚙️ Lifecycle Action: %s on %s", action, service.getName());
        String message = String.format(
                "**Action:** %s\n" +
                "**Service:** %s\n" +
                "**Environment:** %s\n" +
                "**Result:** %s\n" +
                "**Performed by:** %s\n" +
                "**Time:** %s",
                action,
                service.getName(),
                service.getEnvironment(),
                result,
                performedBy,
                LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        );

        sendToAllChannels(title, message, NotificationSeverity.INFO, "lifecycle", service.getName());
    }

    /**
     * Send custom notification
     */
    @Async
    public void sendCustomNotification(String title, String message, NotificationSeverity severity, 
                                        String category, List<String> targetChannels) {
        if (targetChannels == null || targetChannels.isEmpty()) {
            sendToAllChannels(title, message, severity, category, null);
        } else {
            for (String channelName : targetChannels) {
                NotificationChannel channel = channels.get(channelName);
                if (channel != null) {
                    sendToChannel(channel, channelName, title, message, severity, category, null);
                }
            }
        }
    }

    /**
     * Test notification channel
     */
    public NotificationTestResult testChannel(String channelName) {
        NotificationChannel channel = channels.get(channelName);
        if (channel == null) {
            return NotificationTestResult.builder()
                    .success(false)
                    .channel(channelName)
                    .message("Channel not found or not configured")
                    .build();
        }

        try {
            String testTitle = "🧪 Test Notification";
            String testMessage = "This is a test notification from the Management Console.\n" +
                    "Time: " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            
            boolean success = channel.send(testTitle, testMessage, NotificationSeverity.INFO);
            
            return NotificationTestResult.builder()
                    .success(success)
                    .channel(channelName)
                    .message(success ? "Test notification sent successfully" : "Failed to send test notification")
                    .timestamp(System.currentTimeMillis())
                    .build();
        } catch (Exception e) {
            return NotificationTestResult.builder()
                    .success(false)
                    .channel(channelName)
                    .message("Error: " + e.getMessage())
                    .timestamp(System.currentTimeMillis())
                    .build();
        }
    }

    /**
     * Get available notification channels
     */
    public List<ChannelInfo> getAvailableChannels() {
        List<ChannelInfo> channelInfos = new ArrayList<>();
        channels.forEach((name, channel) -> {
            ChannelInfo info = new ChannelInfo();
            info.setName(name);
            info.setType(channel.getType());
            info.setEnabled(true);
            channelInfos.add(info);
        });
        return channelInfos;
    }

    /**
     * Get notification history
     */
    public List<NotificationRecord> getNotificationHistory(int limit) {
        int size = notificationHistory.size();
        int start = Math.max(0, size - limit);
        return new ArrayList<>(notificationHistory.subList(start, size));
    }

    /**
     * Get notification statistics
     */
    public NotificationStats getStats() {
        NotificationStats stats = new NotificationStats();
        stats.setTotalSent(notificationHistory.size());
        
        Map<String, Long> byChannel = new HashMap<>();
        Map<String, Long> bySeverity = new HashMap<>();
        Map<String, Long> byCategory = new HashMap<>();
        long successCount = 0;
        
        for (NotificationRecord record : notificationHistory) {
            byChannel.merge(record.getChannel(), 1L, Long::sum);
            bySeverity.merge(record.getSeverity().name(), 1L, Long::sum);
            if (record.getCategory() != null) {
                byCategory.merge(record.getCategory(), 1L, Long::sum);
            }
            if (record.isSuccess()) {
                successCount++;
            }
        }
        
        stats.setByChannel(byChannel);
        stats.setBySeverity(bySeverity);
        stats.setByCategory(byCategory);
        stats.setSuccessRate(notificationHistory.isEmpty() ? 0 : 
                (double) successCount / notificationHistory.size() * 100);
        
        return stats;
    }

    // Private methods

    private void sendToAllChannels(String title, String message, NotificationSeverity severity, 
                                   String category, String serviceName) {
        channels.forEach((name, channel) -> 
            sendToChannel(channel, name, title, message, severity, category, serviceName));
    }

    private void sendToChannel(NotificationChannel channel, String channelName, String title, 
                               String message, NotificationSeverity severity, String category, String serviceName) {
        try {
            boolean success = channel.send(title, message, severity);
            recordNotification(channelName, title, severity, category, serviceName, success, null);
            if (success) {
                log.debug("Notification sent to {}: {}", channelName, title);
            } else {
                log.warn("Failed to send notification to {}: {}", channelName, title);
            }
        } catch (Exception e) {
            log.error("Error sending notification to {}: {}", channelName, e.getMessage());
            recordNotification(channelName, title, severity, category, serviceName, false, e.getMessage());
        }
    }

    private void recordNotification(String channel, String title, NotificationSeverity severity,
                                   String category, String serviceName, boolean success, String error) {
        NotificationRecord record = new NotificationRecord();
        record.setChannel(channel);
        record.setTitle(title);
        record.setSeverity(severity);
        record.setCategory(category);
        record.setServiceName(serviceName);
        record.setSuccess(success);
        record.setError(error);
        record.setTimestamp(System.currentTimeMillis());
        
        notificationHistory.add(record);
        
        // Trim history if too large
        while (notificationHistory.size() > MAX_HISTORY_SIZE) {
            notificationHistory.remove(0);
        }
    }

    private boolean isThrottled(String key) {
        Long lastTime = lastNotificationTime.get(key);
        long now = System.currentTimeMillis();
        
        if (lastTime != null && (now - lastTime) < THROTTLE_INTERVAL_MS) {
            return true;
        }
        
        lastNotificationTime.put(key, now);
        return false;
    }

    private NotificationSeverity mapHealthToSeverity(HealthStatus status) {
        return switch (status) {
            case HEALTHY -> NotificationSeverity.INFO;
            case DEGRADED -> NotificationSeverity.WARNING;
            case DOWN, CRITICAL -> NotificationSeverity.CRITICAL;
            case UNKNOWN -> NotificationSeverity.INFO;
        };
    }

    private NotificationSeverity mapIncidentToSeverity(IncidentSeverity severity) {
        return switch (severity) {
            case CRITICAL -> NotificationSeverity.CRITICAL;
            case HIGH -> NotificationSeverity.ERROR;
            case MEDIUM -> NotificationSeverity.WARNING;
            case LOW -> NotificationSeverity.INFO;
        };
    }

    private String getIncidentEmoji(IncidentSeverity severity) {
        return switch (severity) {
            case CRITICAL -> "🚨";
            case HIGH -> "⚠️";
            case MEDIUM -> "⚡";
            case LOW -> "ℹ️";
        };
    }

    // Channel implementations

    private interface NotificationChannel {
        boolean send(String title, String message, NotificationSeverity severity);
        String getType();
    }

    private class SlackChannel implements NotificationChannel {
        private final String webhookUrl;

        SlackChannel(String webhookUrl) {
            this.webhookUrl = webhookUrl;
        }

        @Override
        public boolean send(String title, String message, NotificationSeverity severity) {
            try {
                Map<String, Object> payload = new HashMap<>();
                
                List<Map<String, Object>> blocks = new ArrayList<>();
                
                // Header block
                Map<String, Object> header = new HashMap<>();
                header.put("type", "header");
                header.put("text", Map.of("type", "plain_text", "text", title, "emoji", true));
                blocks.add(header);
                
                // Message block
                Map<String, Object> section = new HashMap<>();
                section.put("type", "section");
                section.put("text", Map.of("type", "mrkdwn", "text", message));
                blocks.add(section);
                
                payload.put("blocks", blocks);
                payload.put("text", title);

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(payload), headers);
                
                ResponseEntity<String> response = restTemplate.exchange(webhookUrl, HttpMethod.POST, entity, String.class);
                return response.getStatusCode().is2xxSuccessful();
            } catch (Exception e) {
                log.error("Slack notification failed: {}", e.getMessage());
                return false;
            }
        }

        @Override
        public String getType() {
            return "Slack";
        }
    }

    private class DiscordChannel implements NotificationChannel {
        private final String webhookUrl;

        DiscordChannel(String webhookUrl) {
            this.webhookUrl = webhookUrl;
        }

        @Override
        public boolean send(String title, String message, NotificationSeverity severity) {
            try {
                Map<String, Object> payload = new HashMap<>();
                
                // Embed
                Map<String, Object> embed = new HashMap<>();
                embed.put("title", title);
                embed.put("description", message);
                embed.put("color", getDiscordColor(severity));
                embed.put("timestamp", LocalDateTime.now().toString());
                
                payload.put("embeds", Collections.singletonList(embed));

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(payload), headers);
                
                ResponseEntity<String> response = restTemplate.exchange(webhookUrl, HttpMethod.POST, entity, String.class);
                return response.getStatusCode().is2xxSuccessful();
            } catch (Exception e) {
                log.error("Discord notification failed: {}", e.getMessage());
                return false;
            }
        }

        private int getDiscordColor(NotificationSeverity severity) {
            return switch (severity) {
                case CRITICAL -> 0xFF0000; // Red
                case ERROR -> 0xFFA500;    // Orange
                case WARNING -> 0xFFFF00;  // Yellow
                case INFO -> 0x00FF00;     // Green
            };
        }

        @Override
        public String getType() {
            return "Discord";
        }
    }

    private class TeamsChannel implements NotificationChannel {
        private final String webhookUrl;

        TeamsChannel(String webhookUrl) {
            this.webhookUrl = webhookUrl;
        }

        @Override
        public boolean send(String title, String message, NotificationSeverity severity) {
            try {
                Map<String, Object> payload = new HashMap<>();
                payload.put("@type", "MessageCard");
                payload.put("@context", "http://schema.org/extensions");
                payload.put("themeColor", getTeamsColor(severity));
                payload.put("summary", title);
                payload.put("title", title);
                payload.put("text", message.replace("\n", "<br>"));

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(payload), headers);
                
                ResponseEntity<String> response = restTemplate.exchange(webhookUrl, HttpMethod.POST, entity, String.class);
                return response.getStatusCode().is2xxSuccessful();
            } catch (Exception e) {
                log.error("Teams notification failed: {}", e.getMessage());
                return false;
            }
        }

        private String getTeamsColor(NotificationSeverity severity) {
            return switch (severity) {
                case CRITICAL -> "FF0000";
                case ERROR -> "FFA500";
                case WARNING -> "FFFF00";
                case INFO -> "00FF00";
            };
        }

        @Override
        public String getType() {
            return "Microsoft Teams";
        }
    }

    private class EmailChannel implements NotificationChannel {
        @Override
        public boolean send(String title, String message, NotificationSeverity severity) {
            // Email implementation would go here
            // Requires JavaMailSender configuration
            log.info("Email notification (mock): {} - {}", title, message);
            return true;
        }

        @Override
        public String getType() {
            return "Email";
        }
    }

    private class WebhookChannel implements NotificationChannel {
        private final String webhookUrl;

        WebhookChannel(String webhookUrl) {
            this.webhookUrl = webhookUrl;
        }

        @Override
        public boolean send(String title, String message, NotificationSeverity severity) {
            try {
                Map<String, Object> payload = new HashMap<>();
                payload.put("title", title);
                payload.put("message", message);
                payload.put("severity", severity.name());
                payload.put("timestamp", System.currentTimeMillis());

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(payload), headers);
                
                ResponseEntity<String> response = restTemplate.exchange(webhookUrl, HttpMethod.POST, entity, String.class);
                return response.getStatusCode().is2xxSuccessful();
            } catch (Exception e) {
                log.error("Webhook notification failed: {}", e.getMessage());
                return false;
            }
        }

        @Override
        public String getType() {
            return "Webhook";
        }
    }

    // Enums and DTOs

    public enum NotificationSeverity {
        INFO, WARNING, ERROR, CRITICAL
    }

    @lombok.Data
    public static class NotificationRecord {
        private String channel;
        private String title;
        private NotificationSeverity severity;
        private String category;
        private String serviceName;
        private boolean success;
        private String error;
        private long timestamp;
    }

    @lombok.Data
    @lombok.Builder
    public static class NotificationTestResult {
        private boolean success;
        private String channel;
        private String message;
        private long timestamp;
    }

    @lombok.Data
    public static class ChannelInfo {
        private String name;
        private String type;
        private boolean enabled;
    }

    @lombok.Data
    public static class NotificationStats {
        private long totalSent;
        private double successRate;
        private Map<String, Long> byChannel;
        private Map<String, Long> bySeverity;
        private Map<String, Long> byCategory;
    }
}

