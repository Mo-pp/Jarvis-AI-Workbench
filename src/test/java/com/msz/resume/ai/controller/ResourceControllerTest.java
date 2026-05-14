package com.msz.resume.ai.integrations.openviking.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.msz.resume.ai.auth.entity.Account;
import com.msz.resume.ai.auth.support.CurrentAccountResolver;
import com.msz.resume.ai.integrations.openviking.core.dto.OpenVikingReadResponse;
import com.msz.resume.ai.integrations.openviking.core.model.OpenVikingIdentity;
import com.msz.resume.ai.integrations.openviking.core.service.OpenVikingIdentityResolver;
import com.msz.resume.ai.integrations.openviking.core.service.OpenVikingResourceService;
import com.msz.resume.ai.integrations.openviking.core.service.OpenVikingResourceService.CreateTextResourceResult;
import com.msz.resume.ai.integrations.openviking.core.service.OpenVikingResourceService.DeleteResourceResult;
import com.msz.resume.ai.integrations.openviking.core.service.OpenVikingResourceService.ImportUrlResult;
import com.msz.resume.ai.integrations.openviking.core.service.OpenVikingResourceService.ResourceDetail;
import com.msz.resume.ai.integrations.openviking.core.service.OpenVikingResourceService.UploadResourceResult;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * ResourceController 单元测试。
 */
@ExtendWith(MockitoExtension.class)
class ResourceControllerTest {

    @Mock
    private CurrentAccountResolver currentAccountResolver;

    @Mock
    private OpenVikingIdentityResolver openVikingIdentityResolver;

    @Mock
    private OpenVikingResourceService openVikingResourceService;

    @Mock
    private HttpServletRequest httpServletRequest;

    @InjectMocks
    private ResourceController resourceController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private Account testAccount;
    private OpenVikingIdentity testIdentity;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(resourceController).build();
        objectMapper = new ObjectMapper();

        testAccount = new Account();
        testAccount.setId(1);
        testAccount.setUsername("testuser");

        testIdentity = new OpenVikingIdentity("test-account", "test-user", "jarvis");
    }

    // ==================== 目录列表测试 ====================

    @Nested
    @DisplayName("目录列表测试")
    class ListResourcesTests {

        @Test
        @DisplayName("未认证用户返回错误")
        void unauthenticatedUserReturnsError() throws Exception {
            when(currentAccountResolver.requireCurrentAccount(any(), any()))
                    .thenThrow(new IllegalStateException("未认证用户"));

            mockMvc.perform(get("/api/resources")
                            .param("uri", "viking://resources/"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));
        }

        @Test
        @DisplayName("认证用户可以列出资源")
        void authenticatedUserCanListResources() throws Exception {
            when(currentAccountResolver.requireCurrentAccount(any(), any())).thenReturn(testAccount);
            when(openVikingIdentityResolver.resolve(testAccount)).thenReturn(testIdentity);

            OpenVikingReadResponse mockResponse = new OpenVikingReadResponse("ok", List.of(), null, null);
            when(openVikingResourceService.normalizeListUri("viking://resources/")).thenReturn("viking://resources/");
            when(openVikingResourceService.listResources(any(), any())).thenReturn(mockResponse);

            mockMvc.perform(get("/api/resources")
                            .param("uri", "viking://resources/"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(1));
        }

        @Test
        @DisplayName("OpenViking原始目录项会归一化为完整目录URI")
        void originalOpenVikingDirectoryItemIsNormalized() throws Exception {
            when(currentAccountResolver.requireCurrentAccount(any(), any())).thenReturn(testAccount);
            when(openVikingIdentityResolver.resolve(testAccount)).thenReturn(testIdentity);
            when(openVikingResourceService.normalizeListUri("viking://resources/")).thenReturn("viking://resources/");

            OpenVikingReadResponse mockResponse = new OpenVikingReadResponse("ok", List.of(Map.of(
                    "name", "测试文件1",
                    "size", 4096,
                    "isDir", true,
                    "modTime", "2026-05-12T10:00:00Z"
            )), null, null);
            when(openVikingResourceService.listResources(eq("viking://resources/"), eq(testIdentity))).thenReturn(mockResponse);

            mockMvc.perform(get("/api/resources")
                            .param("uri", "viking://resources/"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(1))
                    .andExpect(jsonPath("$.data[0].uri").value("viking://resources/测试文件1/"))
                    .andExpect(jsonPath("$.data[0].name").value("测试文件1"))
                    .andExpect(jsonPath("$.data[0].directory").value(true))
                    .andExpect(jsonPath("$.data[0].type").value("directory"))
                    .andExpect(jsonPath("$.data[0].updatedAt").value("2026-05-12T10:00:00Z"));
        }
    }

    // ==================== 资源详情测试 ====================

    @Nested
    @DisplayName("工作空间只读浏览测试")
    class WorkspaceBrowserTests {

        @Test
        @DisplayName("认证用户可以从OpenViking根目录列出工作空间")
        void authenticatedUserCanListWorkspaceFromRoot() throws Exception {
            when(currentAccountResolver.requireCurrentAccount(any(), any())).thenReturn(testAccount);
            when(openVikingIdentityResolver.resolve(testAccount)).thenReturn(testIdentity);
            when(openVikingResourceService.normalizeWorkspaceListUri("viking://")).thenReturn("viking://");

            OpenVikingReadResponse mockResponse = new OpenVikingReadResponse("ok", List.of(Map.of(
                    "name", "resources",
                    "isDir", true
            )), null, null);
            when(openVikingResourceService.listWorkspace(eq("viking://"), eq(testIdentity))).thenReturn(mockResponse);

            mockMvc.perform(get("/api/resources/workspace")
                            .param("uri", "viking://"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(1))
                    .andExpect(jsonPath("$.data[0].uri").value("viking://resources/"))
                    .andExpect(jsonPath("$.data[0].directory").value(true));

            verify(openVikingResourceService).listWorkspace("viking://", testIdentity);
            verify(openVikingResourceService, never()).listResources(any(), any());
        }

        @Test
        @DisplayName("工作空间详情允许非resources路径")
        void workspaceDetailAllowsNonResourcesPath() throws Exception {
            when(currentAccountResolver.requireCurrentAccount(any(), any())).thenReturn(testAccount);
            when(openVikingIdentityResolver.resolve(testAccount)).thenReturn(testIdentity);

            ResourceDetail mockDetail = new ResourceDetail(
                    "viking://user/test-user/memories/profile.md",
                    "profile.md",
                    120L,
                    false,
                    "file",
                    "2026-01-01T00:00:00Z",
                    "markdown",
                    "# Profile",
                    "Profile abstract",
                    "Profile overview"
            );
            when(openVikingResourceService.getWorkspaceDetail(any(), any())).thenReturn(mockDetail);

            mockMvc.perform(get("/api/resources/workspace/detail")
                            .param("uri", "viking://user/test-user/memories/profile.md"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(1))
                    .andExpect(jsonPath("$.data.uri").value("viking://user/test-user/memories/profile.md"))
                    .andExpect(jsonPath("$.data.previewKind").value("markdown"));

            verify(openVikingResourceService).getWorkspaceDetail(
                    "viking://user/test-user/memories/profile.md",
                    testIdentity
            );
            verify(openVikingResourceService, never()).getResourceDetail(any(), any());
        }
    }

    @Nested
    @DisplayName("资源详情测试")
    class ResourceDetailTests {

        @Test
        @DisplayName("获取Markdown文件详情")
        void getMarkdownFileDetail() throws Exception {
            when(currentAccountResolver.requireCurrentAccount(any(), any())).thenReturn(testAccount);
            when(openVikingIdentityResolver.resolve(testAccount)).thenReturn(testIdentity);

            ResourceDetail mockDetail = new ResourceDetail(
                    "viking://resources/test/test.md",
                    "test.md",
                    100L,
                    false,
                    "file",
                    "2026-01-01T00:00:00Z",
                    "markdown",
                    "# Test Content",
                    "Abstract text",
                    "Overview text"
            );
            when(openVikingResourceService.getResourceDetail(any(), any())).thenReturn(mockDetail);

            mockMvc.perform(get("/api/resources/detail")
                            .param("uri", "viking://resources/test/test.md"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(1))
                    .andExpect(jsonPath("$.data.uri").value("viking://resources/test/test.md"))
                    .andExpect(jsonPath("$.data.previewKind").value("markdown"));
        }
    }

    // ==================== 纯文本创建测试 ====================

    @Nested
    @DisplayName("纯文本创建测试")
    class CreateTextResourceTests {

        @Test
        @DisplayName("创建纯文本资源成功")
        void createTextResourceSuccess() throws Exception {
            when(currentAccountResolver.requireCurrentAccount(any(), any())).thenReturn(testAccount);
            when(openVikingIdentityResolver.resolve(testAccount)).thenReturn(testIdentity);

            CreateTextResourceResult mockResult = CreateTextResourceResult.success(
                    "测试笔记",
                    "viking://resources/测试笔记/测试笔记.md",
                    "viking://resources/测试笔记/"
            );
            when(openVikingResourceService.createTextResource(any(), any(), any())).thenReturn(mockResult);

            String requestBody = objectMapper.writeValueAsString(
                    new ResourceController.CreateTextResourceRequest("测试笔记", "这是测试内容"));

            mockMvc.perform(post("/api/resources/text")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(1))
                    .andExpect(jsonPath("$.data.status").value("success"));
        }

        @Test
        @DisplayName("创建冲突资源返回conflict状态")
        void createTextResourceConflict() throws Exception {
            when(currentAccountResolver.requireCurrentAccount(any(), any())).thenReturn(testAccount);
            when(openVikingIdentityResolver.resolve(testAccount)).thenReturn(testIdentity);

            CreateTextResourceResult mockResult = CreateTextResourceResult.conflict(
                    "已存在文件",
                    "viking://resources/已存在文件/已存在文件.md"
            );
            when(openVikingResourceService.createTextResource(any(), any(), any())).thenReturn(mockResult);

            String requestBody = objectMapper.writeValueAsString(
                    new ResourceController.CreateTextResourceRequest("已存在文件", "这是测试内容"));

            mockMvc.perform(post("/api/resources/text")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("conflict"));
        }
    }

    // ==================== URL导入测试 ====================

    @Nested
    @DisplayName("URL导入测试")
    class ImportUrlTests {

        @Test
        @DisplayName("导入GitHub仓库URL成功")
        void importGithubUrlSuccess() throws Exception {
            when(currentAccountResolver.requireCurrentAccount(any(), any())).thenReturn(testAccount);
            when(openVikingIdentityResolver.resolve(testAccount)).thenReturn(testIdentity);

            ImportUrlResult mockResult = ImportUrlResult.success(
                    "https://github.com/user/repo",
                    null,
                    "viking://resources/repo/"
            );
            when(openVikingResourceService.importFromUrl(any(), any())).thenReturn(mockResult);

            String requestBody = objectMapper.writeValueAsString(
                    new ResourceController.ImportUrlRequest("https://github.com/user/repo"));

            mockMvc.perform(post("/api/resources/url")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(1))
                    .andExpect(jsonPath("$.data.status").value("success"));
        }
    }

    // ==================== 删除资源测试 ====================

    @Nested
    @DisplayName("删除资源测试")
    class DeleteResourceTests {

        @Test
        @DisplayName("删除选中资源成功")
        void deleteSelectedResourceSuccess() throws Exception {
            when(currentAccountResolver.requireCurrentAccount(any(), any())).thenReturn(testAccount);
            when(openVikingIdentityResolver.resolve(testAccount)).thenReturn(testIdentity);

            DeleteResourceResult mockResult = DeleteResourceResult.success(
                    "viking://resources/测试文件1/测试文件1.md",
                    "viking://resources/测试文件1/"
            );
            when(openVikingResourceService.deleteResource(any(), any())).thenReturn(mockResult);

            mockMvc.perform(delete("/api/resources")
                            .param("uri", "viking://resources/测试文件1/测试文件1.md"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(1))
                    .andExpect(jsonPath("$.data.status").value("success"))
                    .andExpect(jsonPath("$.data.rootUri").value("viking://resources/测试文件1/"));

            verify(openVikingResourceService).deleteResource(
                    "viking://resources/测试文件1/测试文件1.md",
                    testIdentity
            );
        }

        @Test
        @DisplayName("删除资源根目录返回参数错误")
        void deleteResourceRootReturnsError() throws Exception {
            when(currentAccountResolver.requireCurrentAccount(any(), any())).thenReturn(testAccount);
            when(openVikingIdentityResolver.resolve(testAccount)).thenReturn(testIdentity);
            when(openVikingResourceService.deleteResource(any(), any()))
                    .thenThrow(new IllegalArgumentException("不能删除资源根目录"));

            mockMvc.perform(delete("/api/resources")
                            .param("uri", "viking://resources/"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));
        }
    }

    // ==================== 响应格式测试 ====================

    @Nested
    @DisplayName("响应格式测试")
    class ResponseFormatTests {

        @Test
        @DisplayName("导入结果包含必需字段")
        void importResultContainsRequiredFields() throws Exception {
            when(currentAccountResolver.requireCurrentAccount(any(), any())).thenReturn(testAccount);
            when(openVikingIdentityResolver.resolve(testAccount)).thenReturn(testIdentity);

            CreateTextResourceResult mockResult = CreateTextResourceResult.success(
                    "测试",
                    "viking://resources/测试/测试.md",
                    "viking://resources/测试/"
            );
            when(openVikingResourceService.createTextResource(any(), any(), any())).thenReturn(mockResult);

            String requestBody = objectMapper.writeValueAsString(
                    new ResourceController.CreateTextResourceRequest("测试", "内容"));

            mockMvc.perform(post("/api/resources/text")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.sourceType").value("text"))
                    .andExpect(jsonPath("$.data.sourceName").value("测试"))
                    .andExpect(jsonPath("$.data.targetUri").exists())
                    .andExpect(jsonPath("$.data.status").exists())
                    .andExpect(jsonPath("$.data.message").exists());
        }
    }
}
