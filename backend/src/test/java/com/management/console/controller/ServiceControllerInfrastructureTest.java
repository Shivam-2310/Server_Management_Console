package com.management.console.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.management.console.domain.entity.ManagedService;
import com.management.console.domain.enums.ServiceType;
import com.management.console.repository.ManagedServiceRepository;
import com.management.console.service.EnvironmentService;
import com.management.console.service.InfrastructureMonitoringService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration test for Infrastructure and Configuration endpoints.
 * Tests the actual HTTP responses to identify serialization issues.
 */
@SpringBootTest
@AutoConfigureMockMvc
public class ServiceControllerInfrastructureTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private InfrastructureMonitoringService infrastructureMonitoringService;

    @MockBean
    private EnvironmentService environmentService;

    @Autowired
    private ManagedServiceRepository serviceRepository;

    private Long testServiceId;

    @BeforeEach
    void setUp() {
        // Create a test service
        ManagedService service = new ManagedService();
        service.setName("Test Service");
        service.setServiceType(ServiceType.BACKEND);
        service.setHost("localhost");
        service.setPort(8500);
        service.setBaseUrl("http://localhost:8500");
        service.setActuatorBasePath("/actuator");
        service = serviceRepository.save(service);
        testServiceId = service.getId();
    }

    @Test
    @WithMockUser(username = "test", roles = {"VIEWER"})
    void testInfrastructureEndpoint_ShouldReturnJson() throws Exception {
        // Create mock infrastructure info
        InfrastructureMonitoringService.InfrastructureInfo mockInfra = new InfrastructureMonitoringService.InfrastructureInfo();
        mockInfra.setServiceId(testServiceId);
        mockInfra.setServiceName("Test Service");
        mockInfra.setOsName("Windows");
        mockInfra.setOsVersion("10");
        mockInfra.setOsArch("x86_64");
        mockInfra.setJvmName("OpenJDK");
        mockInfra.setJvmVersion("17");
        mockInfra.setJvmVendor("Eclipse Adoptium");
        mockInfra.setSystemCpuLoad(25.5);
        mockInfra.setProcessCpuLoad(15.3);
        mockInfra.setAvailableProcessors(8);
        mockInfra.setTotalPhysicalMemory(16L * 1024 * 1024 * 1024); // 16GB
        mockInfra.setFreePhysicalMemory(8L * 1024 * 1024 * 1024); // 8GB

        // Mock the service to return the infrastructure info
        when(infrastructureMonitoringService.getRemoteInfrastructureInfo(testServiceId))
                .thenReturn(Mono.just(mockInfra));

        // Perform the request
        MvcResult result = mockMvc.perform(get("/api/services/{id}/infrastructure", testServiceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();

        // Verify response is not empty
        String responseBody = result.getResponse().getContentAsString();
        System.out.println("=== INFRASTRUCTURE RESPONSE ===");
        System.out.println("Status: " + result.getResponse().getStatus());
        System.out.println("Content-Type: " + result.getResponse().getContentType());
        System.out.println("Response Length: " + responseBody.length());
        System.out.println("Response Body: " + responseBody);
        System.out.println("================================");

        // Assertions
        assertNotNull(responseBody, "Response body should not be null");
        assertFalse(responseBody.isEmpty(), "Response body should not be empty");
        assertTrue(responseBody.length() > 0, "Response body should have content");
        assertTrue(responseBody.contains("serviceId") || responseBody.contains("serviceName"), 
                "Response should contain service information");
        
        // Try to parse as JSON
        try {
            InfrastructureMonitoringService.InfrastructureInfo parsed = 
                    objectMapper.readValue(responseBody, InfrastructureMonitoringService.InfrastructureInfo.class);
            assertNotNull(parsed, "Response should be valid JSON");
            assertEquals(testServiceId, parsed.getServiceId(), "Service ID should match");
        } catch (Exception e) {
            fail("Response should be valid JSON: " + e.getMessage());
        }
    }

    @Test
    @WithMockUser(username = "test", roles = {"VIEWER"})
    void testConfigurationEndpoint_ShouldReturnJson() throws Exception {
        // Create mock environment info
        EnvironmentService.EnvironmentInfo mockConfig = new EnvironmentService.EnvironmentInfo();
        mockConfig.setTimestamp(System.currentTimeMillis());
        mockConfig.setActiveProfiles(java.util.Arrays.asList("dev", "test"));
        
        EnvironmentService.PropertySourceInfo propSource = new EnvironmentService.PropertySourceInfo();
        propSource.setName("application.properties");
        propSource.setType("Application Properties");
        propSource.setProperties(java.util.Map.of("server.port", "8080", "spring.application.name", "test-app"));
        propSource.setPropertyCount(2);
        
        mockConfig.setPropertySources(java.util.Arrays.asList(propSource));

        // Mock the service to return the environment info
        when(environmentService.getRemoteEnvironment(testServiceId))
                .thenReturn(Mono.just(mockConfig));

        // Perform the request
        MvcResult result = mockMvc.perform(get("/api/services/{id}/configuration", testServiceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();

        // Verify response is not empty
        String responseBody = result.getResponse().getContentAsString();
        System.out.println("=== CONFIGURATION RESPONSE ===");
        System.out.println("Status: " + result.getResponse().getStatus());
        System.out.println("Content-Type: " + result.getResponse().getContentType());
        System.out.println("Response Length: " + responseBody.length());
        System.out.println("Response Body: " + responseBody);
        System.out.println("================================");

        // Assertions
        assertNotNull(responseBody, "Response body should not be null");
        assertFalse(responseBody.isEmpty(), "Response body should not be empty");
        assertTrue(responseBody.length() > 0, "Response body should have content");
        assertTrue(responseBody.contains("timestamp") || responseBody.contains("propertySources"), 
                "Response should contain configuration information");
        
        // Try to parse as JSON
        try {
            EnvironmentService.EnvironmentInfo parsed = 
                    objectMapper.readValue(responseBody, EnvironmentService.EnvironmentInfo.class);
            assertNotNull(parsed, "Response should be valid JSON");
            assertNotNull(parsed.getPropertySources(), "Property sources should not be null");
        } catch (Exception e) {
            fail("Response should be valid JSON: " + e.getMessage());
        }
    }

    @Test
    @WithMockUser(username = "test", roles = {"VIEWER"})
    void testInfrastructureEndpoint_WithRealService() throws Exception {
        // Test with actual service ID 225 (if it exists)
        Long serviceId = 225L;
        
        try {
            MvcResult result = mockMvc.perform(get("/api/services/{id}/infrastructure", serviceId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .accept(MediaType.APPLICATION_JSON))
                    .andDo(print())
                    .andReturn();

            String responseBody = result.getResponse().getContentAsString();
            System.out.println("=== REAL INFRASTRUCTURE RESPONSE (Service 225) ===");
            System.out.println("Status: " + result.getResponse().getStatus());
            System.out.println("Content-Type: " + result.getResponse().getContentType());
            System.out.println("Response Length: " + responseBody.length());
            System.out.println("Response Body: " + responseBody);
            System.out.println("==================================================");

            // Check if response is empty
            if (responseBody == null || responseBody.isEmpty() || responseBody.trim().isEmpty()) {
                System.err.println("ERROR: Response body is EMPTY!");
                System.err.println("This indicates a serialization issue.");
            } else {
                System.out.println("SUCCESS: Response body contains data");
            }
        } catch (Exception e) {
            System.err.println("Error testing real service: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Test
    @WithMockUser(username = "test", roles = {"VIEWER"})
    void testConfigurationEndpoint_WithRealService() throws Exception {
        // Test with actual service ID 225 (if it exists)
        Long serviceId = 225L;
        
        try {
            MvcResult result = mockMvc.perform(get("/api/services/{id}/configuration", serviceId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .accept(MediaType.APPLICATION_JSON))
                    .andDo(print())
                    .andReturn();

            String responseBody = result.getResponse().getContentAsString();
            System.out.println("=== REAL CONFIGURATION RESPONSE (Service 225) ===");
            System.out.println("Status: " + result.getResponse().getStatus());
            System.out.println("Content-Type: " + result.getResponse().getContentType());
            System.out.println("Response Length: " + responseBody.length());
            System.out.println("Response Body: " + responseBody);
            System.out.println("==================================================");

            // Check if response is empty
            if (responseBody == null || responseBody.isEmpty() || responseBody.trim().isEmpty()) {
                System.err.println("ERROR: Response body is EMPTY!");
                System.err.println("This indicates a serialization issue.");
            } else {
                System.out.println("SUCCESS: Response body contains data");
            }
        } catch (Exception e) {
            System.err.println("Error testing real service: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

