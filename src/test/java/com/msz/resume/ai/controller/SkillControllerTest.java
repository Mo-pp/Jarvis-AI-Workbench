package com.msz.resume.ai.integrations.openviking.api;

import com.msz.resume.ai.auth.entity.Account;
import com.msz.resume.ai.auth.support.CurrentAccountResolver;
import com.msz.resume.ai.integrations.openviking.core.dto.OpenVikingSkillAddResponse;
import com.msz.resume.ai.integrations.openviking.core.model.OpenVikingIdentity;
import com.msz.resume.ai.integrations.openviking.core.service.OpenVikingIdentityResolver;
import com.msz.resume.ai.integrations.openviking.core.service.OpenVikingSkillService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SkillControllerTest {

    @Test
    @DisplayName("查询已有 skill 列表时应返回前端所需字段")
    void shouldListSkillsForCurrentUser() throws Exception {
        CurrentAccountResolver currentAccountResolver = mock(CurrentAccountResolver.class);
        OpenVikingIdentityResolver identityResolver = mock(OpenVikingIdentityResolver.class);
        OpenVikingSkillService skillService = mock(OpenVikingSkillService.class);
        SkillController controller = new SkillController(currentAccountResolver, identityResolver, skillService);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        Account account = new Account(1, "alice", null, null, null, null, null);
        OpenVikingIdentity identity = new OpenVikingIdentity("alice", "alice", "jarvis");
        when(currentAccountResolver.requireCurrentAccount(org.mockito.ArgumentMatchers.any(), eq("skills"))).thenReturn(account);
        when(identityResolver.resolve(account)).thenReturn(identity);
        when(skillService.listSkillCatalog(identity)).thenReturn(List.of(
                new OpenVikingSkillService.SkillCatalogItem(
                        "codemap",
                        "Code Review Assistant",
                        "viking://agent/jarvis/skills/codemap/",
                        "用于代码审查场景，聚焦风险、回归点、测试缺口和可维护性问题。",
                        "2026-05-10T12:00Z"
                )
        ));

        mockMvc.perform(get("/api/skills"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.msg").value("success"))
                .andExpect(jsonPath("$.data[0].id").value("codemap"))
                .andExpect(jsonPath("$.data[0].name").value("Code Review Assistant"))
                .andExpect(jsonPath("$.data[0].path").value("viking://agent/jarvis/skills/codemap/"))
                .andExpect(jsonPath("$.data[0].abstract").value("用于代码审查场景，聚焦风险、回归点、测试缺口和可维护性问题。"))
                .andExpect(jsonPath("$.data[0].updatedAt").value("2026-05-10T12:00Z"));
    }

    @Test
    @DisplayName("查询单个 skill 详情时应返回前端所需字段")
    void shouldGetSkillDetailForCurrentUser() throws Exception {
        CurrentAccountResolver currentAccountResolver = mock(CurrentAccountResolver.class);
        OpenVikingIdentityResolver identityResolver = mock(OpenVikingIdentityResolver.class);
        OpenVikingSkillService skillService = mock(OpenVikingSkillService.class);
        SkillController controller = new SkillController(currentAccountResolver, identityResolver, skillService);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        Account account = new Account(1, "alice", null, null, null, null, null);
        OpenVikingIdentity identity = new OpenVikingIdentity("alice", "alice", "jarvis");
        when(currentAccountResolver.requireCurrentAccount(org.mockito.ArgumentMatchers.any(), eq("skills/codemap"))).thenReturn(account);
        when(identityResolver.resolve(account)).thenReturn(identity);
        when(skillService.getSkillCatalogItem("codemap", identity)).thenReturn(
                new OpenVikingSkillService.SkillCatalogItem(
                        "codemap",
                        "Code Review Assistant",
                        "viking://agent/jarvis/skills/codemap/",
                        "用于代码审查场景，聚焦风险、回归点、测试缺口和可维护性问题。",
                        null
                )
        );

        mockMvc.perform(get("/api/skills/codemap"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.msg").value("success"))
                .andExpect(jsonPath("$.data.id").value("codemap"))
                .andExpect(jsonPath("$.data.name").value("Code Review Assistant"))
                .andExpect(jsonPath("$.data.path").value("viking://agent/jarvis/skills/codemap/"))
                .andExpect(jsonPath("$.data.abstract").value("用于代码审查场景，聚焦风险、回归点、测试缺口和可维护性问题。"));
    }

    @Test
    @DisplayName("上传 skill 时应按当前登录用户 identity 写入私有空间")
    void shouldUploadSkillForCurrentUserIdentity() throws Exception {
        CurrentAccountResolver currentAccountResolver = mock(CurrentAccountResolver.class);
        OpenVikingIdentityResolver identityResolver = mock(OpenVikingIdentityResolver.class);
        OpenVikingSkillService skillService = mock(OpenVikingSkillService.class);
        SkillController controller = new SkillController(currentAccountResolver, identityResolver, skillService);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        Account account = new Account(1, "alice", null, null, null, null, null);
        OpenVikingIdentity identity = new OpenVikingIdentity("alice", "alice", "jarvis");
        when(currentAccountResolver.requireCurrentAccount(org.mockito.ArgumentMatchers.any(), eq("skills/upload"))).thenReturn(account);
        when(identityResolver.resolve(account)).thenReturn(identity);
        when(skillService.uploadSkill(org.mockito.ArgumentMatchers.any(), eq(identity)))
                .thenReturn(new OpenVikingSkillAddResponse("ok", Map.of("name", "demo-skill"), null, null));

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "demo.skill",
                "application/octet-stream",
                "fake-zip".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/skills/upload").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.fileName").value("demo.skill"))
                .andExpect(jsonPath("$.data.account").value("alice"))
                .andExpect(jsonPath("$.data.user").value("alice"))
                .andExpect(jsonPath("$.data.agent").value("jarvis"))
                .andExpect(jsonPath("$.data.status").value("ok"))
                .andExpect(jsonPath("$.data.result.name").value("demo-skill"));

        verify(skillService).uploadSkill(org.mockito.ArgumentMatchers.any(), eq(identity));
    }

    @Test
    @DisplayName("未登录时应返回错误")
    void shouldReturnErrorWhenCurrentAccountMissing() throws Exception {
        CurrentAccountResolver currentAccountResolver = mock(CurrentAccountResolver.class);
        OpenVikingIdentityResolver identityResolver = mock(OpenVikingIdentityResolver.class);
        OpenVikingSkillService skillService = mock(OpenVikingSkillService.class);
        SkillController controller = new SkillController(currentAccountResolver, identityResolver, skillService);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        when(currentAccountResolver.requireCurrentAccount(org.mockito.ArgumentMatchers.any(), eq("skills/upload")))
                .thenThrow(new IllegalArgumentException("未登录，无法调用 /api/claude/skills/upload"));

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "demo.skill",
                "application/octet-stream",
                "fake-zip".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/skills/upload").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value("未登录，无法调用 /api/claude/skills/upload"));
    }

    @Test
    @DisplayName("非法扩展名时应返回业务错误")
    void shouldReturnValidationErrorForUnsupportedExtension() throws Exception {
        CurrentAccountResolver currentAccountResolver = mock(CurrentAccountResolver.class);
        OpenVikingIdentityResolver identityResolver = mock(OpenVikingIdentityResolver.class);
        OpenVikingSkillService skillService = mock(OpenVikingSkillService.class);
        SkillController controller = new SkillController(currentAccountResolver, identityResolver, skillService);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        Account account = new Account(1, "alice", null, null, null, null, null);
        OpenVikingIdentity identity = new OpenVikingIdentity("alice", "alice", "jarvis");
        when(currentAccountResolver.requireCurrentAccount(org.mockito.ArgumentMatchers.any(), eq("skills/upload"))).thenReturn(account);
        when(identityResolver.resolve(account)).thenReturn(identity);
        when(skillService.uploadSkill(org.mockito.ArgumentMatchers.any(), eq(identity)))
                .thenThrow(new IllegalArgumentException("OpenViking skill upload failed: only .zip or .skill files are supported."));

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "demo.txt",
                "text/plain",
                "text".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/skills/upload").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value("OpenViking skill upload failed: only .zip or .skill files are supported."));
    }

    @Test
    @DisplayName("删除 skill 时应按当前登录用户 identity 删除")
    void shouldDeleteSkillForCurrentUser() throws Exception {
        CurrentAccountResolver currentAccountResolver = mock(CurrentAccountResolver.class);
        OpenVikingIdentityResolver identityResolver = mock(OpenVikingIdentityResolver.class);
        OpenVikingSkillService skillService = mock(OpenVikingSkillService.class);
        SkillController controller = new SkillController(currentAccountResolver, identityResolver, skillService);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        Account account = new Account(1, "alice", null, null, null, null, null);
        OpenVikingIdentity identity = new OpenVikingIdentity("alice", "alice", "jarvis");
        when(currentAccountResolver.requireCurrentAccount(org.mockito.ArgumentMatchers.any(), eq("skills/codemap"))).thenReturn(account);
        when(identityResolver.resolve(account)).thenReturn(identity);
        when(skillService.readSkillAbstract("codemap", identity))
                .thenReturn(new com.msz.resume.ai.integrations.openviking.core.dto.OpenVikingReadResponse("ok", "desc", null, null));

        mockMvc.perform(delete("/api/skills/codemap"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.msg").value("删除成功"))
                .andExpect(jsonPath("$.data").value(true));

        verify(skillService).deleteSkill("codemap", identity);
    }
}
