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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final ManagedServiceRepository serviceRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        initializeUsers();
        // Sample services initialization disabled - only show actually running services
        // initializeSampleServices();
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

