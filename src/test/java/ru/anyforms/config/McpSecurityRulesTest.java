package ru.anyforms.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import ru.anyforms.model.Role;
import ru.anyforms.service.auth.JwtTokenService;
import ru.anyforms.service.auth.UserAccess;
import ru.anyforms.service.auth.UserAccessService;

import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringJUnitWebConfig(classes = {WebSecurityConfig.class, JwtAuthFilter.class, McpSecurityRulesTest.Beans.class})
@TestPropertySource(properties = "allowed.origins=http://localhost")
class McpSecurityRulesTest {

    private static final String MCP_TOKEN = "mcp.token";
    private static final String ADMIN_TOKEN = "admin.token";
    private static final String MANAGER_TOKEN = "manager.token";
    private static final String MCP_TOKEN_OF_MANAGER = "mcp.manager.token";

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private FilterChainProxy springSecurityFilterChain;
    @Autowired
    private JwtTokenService jwtTokenService;
    @Autowired
    private UserAccessService userAccessService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        reset(jwtTokenService, userAccessService);
        mockMvc = MockMvcBuilders.webAppContextSetup(context).addFilters(springSecurityFilterChain).build();
        token(MCP_TOKEN, "boss@anyforms.ru", true, Role.ADMIN);
        token(ADMIN_TOKEN, "boss@anyforms.ru", false, Role.ADMIN);
        token(MANAGER_TOKEN, "manager@anyforms.ru", false, Role.SALES_MANAGER);
        token(MCP_TOKEN_OF_MANAGER, "manager2@anyforms.ru", true, Role.SALES_MANAGER);
    }

    private void token(String token, String email, boolean mcp, Role role) {
        when(jwtTokenService.isValid(token)).thenReturn(true);
        when(jwtTokenService.isMcpReadToken(token)).thenReturn(mcp);
        when(jwtTokenService.getUsername(token)).thenReturn(email);
        when(userAccessService.resolve(email)).thenReturn(Optional.of(new UserAccess(email, "Имя", role, false, null, null)));
    }

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, String token) {
        return request.header("Authorization", "Bearer " + token);
    }

    @Test
    void mcpTokenOfAdminReadsOnlyMcp() throws Exception {
        mockMvc.perform(as(post("/api/mcp/server"), MCP_TOKEN)).andExpect(status().isOk());
        mockMvc.perform(as(get("/api/mcp/custom-orders"), MCP_TOKEN)).andExpect(status().isOk());

        mockMvc.perform(as(delete("/api/mcp/custom-items/1"), MCP_TOKEN)).andExpect(status().isForbidden());
        mockMvc.perform(as(post("/api/custom-product-items"), MCP_TOKEN)).andExpect(status().isForbidden());
        mockMvc.perform(as(get("/api/orders/custom"), MCP_TOKEN)).andExpect(status().isForbidden());
        mockMvc.perform(as(get("/api/auth/me"), MCP_TOKEN)).andExpect(status().isForbidden());
    }

    @Test
    void mcpTokenOfNonAdminIsRejected() throws Exception {
        mockMvc.perform(as(post("/api/mcp/server"), MCP_TOKEN_OF_MANAGER)).andExpect(status().isUnauthorized());
    }

    @Test
    void onlyAdminPanelTokenOfAdminReachesMcp() throws Exception {
        mockMvc.perform(as(post("/api/mcp/server"), ADMIN_TOKEN)).andExpect(status().isOk());
        mockMvc.perform(as(post("/api/mcp/server"), MANAGER_TOKEN)).andExpect(status().isForbidden());
        mockMvc.perform(as(get("/api/mcp/custom-orders"), MANAGER_TOKEN)).andExpect(status().isForbidden());
        mockMvc.perform(as(get("/api/orders/custom"), MANAGER_TOKEN)).andExpect(status().isOk());
    }

    @Test
    void loginEndpointsAreOpenAndEverythingElseNeedsToken() throws Exception {
        mockMvc.perform(post("/api/mcp/auth/request-code")).andExpect(status().isOk());
        mockMvc.perform(post("/api/mcp/auth/verify-code")).andExpect(status().isOk());
        mockMvc.perform(post("/api/mcp/server")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/mcp/custom-orders")).andExpect(status().isUnauthorized());
    }

    @Configuration
    @EnableWebMvc
    static class Beans {

        @Bean
        JwtTokenService jwtTokenService() {
            return mock(JwtTokenService.class);
        }

        @Bean
        UserAccessService userAccessService() {
            return mock(UserAccessService.class);
        }

        @Bean
        StubController stubController() {
            return new StubController();
        }
    }

    @RestController
    static class StubController {

        @PostMapping({"/api/mcp/server", "/api/mcp/auth/request-code", "/api/mcp/auth/verify-code", "/api/custom-product-items"})
        String post() {
            return "ok";
        }

        @GetMapping({"/api/mcp/custom-orders", "/api/orders/custom", "/api/auth/me"})
        String get() {
            return "ok";
        }

        @DeleteMapping("/api/mcp/custom-items/1")
        String delete() {
            return "ok";
        }
    }
}
