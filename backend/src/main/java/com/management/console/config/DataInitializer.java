package com.management.console.config;

import com.management.console.domain.entity.ManagedService;
import com.management.console.domain.entity.User;
import com.management.console.domain.enums.HealthStatus;
import com.management.console.domain.enums.ServiceType;
import com.management.console.domain.enums.UserRole;
import com.management.console.repository.ManagedServiceRepository;
import com.management.console.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final ManagedServiceRepository serviceRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        migrateDatabaseSchema();
        initializeUsers();
        // Sample services initialization disabled - only show actually running services
        // initializeSampleServices();
    }
    
    /**
     * Migrate database schema to fix authentication_token column length.
     * Hibernate's ddl-auto: update doesn't always alter existing columns,
     * so we manually ensure the column is updated to VARCHAR(128).
     */
    @Transactional
    public void migrateDatabaseSchema() {
        try {
            // Check if table exists first
            String checkTableSql = "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES " +
                                 "WHERE TABLE_NAME = 'MANAGED_SERVICES'";
            Integer tableExists = jdbcTemplate.queryForObject(checkTableSql, Integer.class);
            
            if (tableExists != null && tableExists > 0) {
                // Table exists, try to alter the column
                // H2 syntax: ALTER TABLE table_name ALTER COLUMN column_name VARCHAR(new_size)
                String alterSql = "ALTER TABLE managed_services ALTER COLUMN authentication_token VARCHAR(128)";
                jdbcTemplate.execute(alterSql);
                log.info("Successfully migrated authentication_token column to VARCHAR(128)");
            } else {
                log.debug("Table managed_services does not exist yet, will be created with correct schema");
            }
        } catch (Exception e) {
            // Column might already be correct size, or error occurred
            String errorMsg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
            if (errorMsg.contains("already") || errorMsg.contains("duplicate") || 
                errorMsg.contains("cannot alter")) {
                // Column might already be correct size, try to verify
                try {
                    String checkSql = "SELECT CHARACTER_MAXIMUM_LENGTH FROM INFORMATION_SCHEMA.COLUMNS " +
                                     "WHERE TABLE_SCHEMA = 'PUBLIC' AND TABLE_NAME = 'MANAGED_SERVICES' " +
                                     "AND COLUMN_NAME = 'AUTHENTICATION_TOKEN'";
                    Integer currentLength = jdbcTemplate.queryForObject(checkSql, Integer.class);
                    if (currentLength != null && currentLength >= 128) {
                        log.debug("Column already has correct size: {}", currentLength);
                    } else if (currentLength != null) {
                        log.warn("Column exists with size {} but could not be altered. " +
                                "Please manually run: ALTER TABLE managed_services ALTER COLUMN authentication_token VARCHAR(128)",
                                currentLength);
                    }
                } catch (Exception e2) {
                    log.debug("Could not verify column size (this is OK if table doesn't exist yet)");
                }
            } else {
                log.debug("Schema migration completed (error may be expected): {}", e.getMessage());
            }
        }
    }

    private void initializeUsers() {
        if (userRepository.count() == 0) {
            log.info("Initializing default users...");

            // Admin user
            User admin = User.builder()
                    .username("admin")
                    .email("admin@management.local")
                    .password(passwordEncoder.encode("admin123"))
                    .fullName("System Administrator")
                    .role(UserRole.ADMIN)
                    .enabled(true)
                    .accountNonLocked(true)
                    .build();
            userRepository.save(admin);

            // Operator user
            User operator = User.builder()
                    .username("operator")
                    .email("operator@management.local")
                    .password(passwordEncoder.encode("operator123"))
                    .fullName("Operations Team Member")
                    .role(UserRole.OPERATOR)
                    .enabled(true)
                    .accountNonLocked(true)
                    .build();
            userRepository.save(operator);

            // Viewer user
            User viewer = User.builder()
                    .username("viewer")
                    .email("viewer@management.local")
                    .password(passwordEncoder.encode("viewer123"))
                    .fullName("Read-only User")
                    .role(UserRole.VIEWER)
                    .enabled(true)
                    .accountNonLocked(true)
                    .build();
            userRepository.save(viewer);

            log.info("Created default users: admin, operator, viewer");
        }
    }

    private void initializeSampleServices() {
        if (serviceRepository.count() == 0) {
            log.info("Initializing sample services...");

            // Backend Service 1: Order Service
            ManagedService orderService = ManagedService.builder()
                    .name("order-service")
                    .description("Handles order processing and management")
                    .serviceType(ServiceType.BACKEND)
                    .healthStatus(HealthStatus.UNKNOWN)
                    .host("localhost")
                    .port(8081)
                    .actuatorBasePath("/actuator")
                    .healthEndpoint("/actuator/health")
                    .metricsEndpoint("/actuator/metrics")
                    .startCommand("java -jar order-service.jar")
                    .stopCommand("kill -TERM $(cat order-service.pid)")
                    .restartCommand("./restart-order-service.sh")
                    .workingDirectory("/opt/services/order-service")
                    .environment("DEV")
                    .tags(List.of("core", "orders", "critical"))
                    .enabled(true)
                    .isRunning(false)
                    .instanceCount(1)
                    .stabilityScore(100)
                    .riskScore(0)
                    .build();
            serviceRepository.save(orderService);

            // Backend Service 2: User Service
            ManagedService userService = ManagedService.builder()
                    .name("user-service")
                    .description("Manages user authentication and profiles")
                    .serviceType(ServiceType.BACKEND)
                    .healthStatus(HealthStatus.UNKNOWN)
                    .host("localhost")
                    .port(8082)
                    .actuatorBasePath("/actuator")
                    .healthEndpoint("/actuator/health")
                    .metricsEndpoint("/actuator/metrics")
                    .startCommand("java -jar user-service.jar")
                    .stopCommand("kill -TERM $(cat user-service.pid)")
                    .environment("DEV")
                    .tags(List.of("core", "auth", "users"))
                    .enabled(true)
                    .isRunning(false)
                    .instanceCount(1)
                    .stabilityScore(100)
                    .riskScore(0)
                    .build();
            serviceRepository.save(userService);

            // Backend Service 3: Payment Service
            ManagedService paymentService = ManagedService.builder()
                    .name("payment-service")
                    .description("Processes payments and transactions")
                    .serviceType(ServiceType.BACKEND)
                    .healthStatus(HealthStatus.UNKNOWN)
                    .host("localhost")
                    .port(8083)
                    .actuatorBasePath("/actuator")
                    .healthEndpoint("/actuator/health")
                    .metricsEndpoint("/actuator/metrics")
                    .startCommand("java -jar payment-service.jar")
                    .stopCommand("kill -TERM $(cat payment-service.pid)")
                    .environment("DEV")
                    .tags(List.of("core", "payments", "critical", "pci"))
                    .enabled(true)
                    .isRunning(false)
                    .instanceCount(2)
                    .stabilityScore(100)
                    .riskScore(0)
                    .build();
            serviceRepository.save(paymentService);

            // Backend Service 4: Notification Service
            ManagedService notificationService = ManagedService.builder()
                    .name("notification-service")
                    .description("Handles email, SMS, and push notifications")
                    .serviceType(ServiceType.BACKEND)
                    .healthStatus(HealthStatus.UNKNOWN)
                    .host("localhost")
                    .port(8084)
                    .actuatorBasePath("/actuator")
                    .healthEndpoint("/actuator/health")
                    .metricsEndpoint("/actuator/metrics")
                    .startCommand("java -jar notification-service.jar")
                    .environment("DEV")
                    .tags(List.of("notifications", "messaging"))
                    .enabled(true)
                    .isRunning(false)
                    .instanceCount(1)
                    .stabilityScore(100)
                    .riskScore(0)
                    .build();
            serviceRepository.save(notificationService);

            // Frontend Service 1: Main Dashboard
            ManagedService dashboardFrontend = ManagedService.builder()
                    .name("dashboard-frontend")
                    .description("Main customer-facing dashboard application")
                    .serviceType(ServiceType.FRONTEND)
                    .healthStatus(HealthStatus.UNKNOWN)
                    .host("localhost")
                    .port(3000)
                    .frontendTechnology("React")
                    .servingTechnology("Node.js")
                    .healthEndpoint("/health")
                    .startCommand("npm start")
                    .stopCommand("npm stop")
                    .workingDirectory("/opt/frontend/dashboard")
                    .environment("DEV")
                    .tags(List.of("frontend", "react", "customer-facing"))
                    .enabled(true)
                    .isRunning(false)
                    .instanceCount(1)
                    .stabilityScore(100)
                    .riskScore(0)
                    .build();
            serviceRepository.save(dashboardFrontend);

            // Frontend Service 2: Admin Panel
            ManagedService adminPanel = ManagedService.builder()
                    .name("admin-panel")
                    .description("Internal administration interface")
                    .serviceType(ServiceType.FRONTEND)
                    .healthStatus(HealthStatus.UNKNOWN)
                    .host("localhost")
                    .port(3001)
                    .frontendTechnology("React")
                    .servingTechnology("Nginx")
                    .healthEndpoint("/")
                    .startCommand("serve -s build -l 3001")
                    .environment("DEV")
                    .tags(List.of("frontend", "admin", "internal"))
                    .enabled(true)
                    .isRunning(false)
                    .instanceCount(1)
                    .stabilityScore(100)
                    .riskScore(0)
                    .build();
            serviceRepository.save(adminPanel);

            // Frontend Service 3: Mobile API Gateway
            ManagedService mobileApi = ManagedService.builder()
                    .name("mobile-api-gateway")
                    .description("API Gateway for mobile applications")
                    .serviceType(ServiceType.BACKEND)
                    .healthStatus(HealthStatus.UNKNOWN)
                    .host("localhost")
                    .port(8085)
                    .actuatorBasePath("/actuator")
                    .healthEndpoint("/actuator/health")
                    .startCommand("java -jar mobile-gateway.jar")
                    .environment("DEV")
                    .tags(List.of("api", "mobile", "gateway"))
                    .enabled(true)
                    .isRunning(false)
                    .instanceCount(1)
                    .stabilityScore(100)
                    .riskScore(0)
                    .build();
            serviceRepository.save(mobileApi);

            log.info("Created {} sample services", 7);
        }
    }
}

