package com.msz.resume.ai.integrations.openviking.api;

import com.msz.resume.ai.auth.entity.Account;
import com.msz.resume.ai.auth.support.CurrentAccountResolver;
import com.msz.resume.ai.shared.response.Result;
import com.msz.resume.ai.integrations.openviking.api.dto.SkillListItemResponse;
import com.msz.resume.ai.integrations.openviking.api.dto.SkillUploadResponse;
import com.msz.resume.ai.integrations.openviking.core.dto.OpenVikingSkillAddResponse;
import com.msz.resume.ai.integrations.openviking.core.dto.OpenVikingReadResponse;
import com.msz.resume.ai.integrations.openviking.core.model.OpenVikingIdentity;
import com.msz.resume.ai.integrations.openviking.core.service.OpenVikingSkillService.SkillCatalogItem;
import com.msz.resume.ai.integrations.openviking.core.service.OpenVikingIdentityResolver;
import com.msz.resume.ai.integrations.openviking.core.service.OpenVikingSkillService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Skill 正式业务接口。
 */
@Slf4j
@RestController
@RequestMapping("/api/skills")
@RequiredArgsConstructor
public class SkillController {

    private final CurrentAccountResolver currentAccountResolver;
    private final OpenVikingIdentityResolver openVikingIdentityResolver;
    private final OpenVikingSkillService openVikingSkillService;

    @GetMapping
    public Result<List<SkillListItemResponse>> listSkills(HttpServletRequest httpServletRequest) {
        try {
            ResolvedSkillRequestContext context = resolveRequestContext(httpServletRequest, "skills");
            log.info("[SkillController] 查询用户私有 Skill 列表, username={}", context.account().getUsername());

            List<SkillCatalogItem> skills = openVikingSkillService.listSkillCatalog(context.identity());
            List<SkillListItemResponse> data = skills.stream()
                    .map(this::toSkillListItemResponse)
                    .toList();
            Result<List<SkillListItemResponse>> result = Result.success(data);
            result.setMsg("success");
            return result;
        } catch (IllegalArgumentException e) {
            log.warn("[SkillController] Skill 列表请求参数非法: {}", e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("[SkillController] Skill 列表查询失败", e);
            return Result.error("Skill 列表查询失败: " + e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public Result<SkillListItemResponse> getSkill(
            HttpServletRequest httpServletRequest,
            @PathVariable String id) {
        try {
            ResolvedSkillRequestContext context = resolveRequestContext(httpServletRequest, "skills/" + id);
            log.info("[SkillController] 查询用户私有 Skill 详情, username={}, id={}", context.account().getUsername(), id);

            SkillCatalogItem item = openVikingSkillService.getSkillCatalogItem(id, context.identity());
            Result<SkillListItemResponse> result = Result.success(toSkillListItemResponse(item));
            result.setMsg("success");
            return result;
        } catch (IllegalArgumentException e) {
            log.warn("[SkillController] Skill 详情请求参数非法: {}", e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("[SkillController] Skill 详情查询失败", e);
            String message = e.getMessage() != null ? e.getMessage() : "unknown error";
            if (message.contains("HTTP 404")) {
                return Result.error("Skill 不存在或无权限访问: " + id);
            }
            return Result.error("Skill 详情查询失败: " + message);
        }
    }

    @PostMapping("/upload")
    public Result<SkillUploadResponse> uploadSkill(
            HttpServletRequest httpServletRequest,
            @RequestParam("file") MultipartFile file) {
        try {
            ResolvedSkillRequestContext context = resolveRequestContext(httpServletRequest, "skills/upload");

            log.info("[SkillController] 上传用户私有 Skill, username={}, filename={}, size={}",
                    context.account().getUsername(),
                    file != null ? file.getOriginalFilename() : null,
                    file != null ? file.getSize() : null);

            OpenVikingSkillAddResponse response = openVikingSkillService.uploadSkill(file, context.identity());
            SkillUploadResponse data = SkillUploadResponse.builder()
                    .fileName(file != null ? file.getOriginalFilename() : null)
                    .fileSize(file != null ? file.getSize() : null)
                    .account(context.identity().account())
                    .user(context.identity().user())
                    .agent(context.identity().agent())
                    .status(response.status())
                    .message("Skill 上传成功")
                    .result(response.result())
                    .build();
            Result<SkillUploadResponse> result = Result.success(data);
            result.setMsg("success");
            return result;
        } catch (IllegalArgumentException e) {
            log.warn("[SkillController] Skill 上传参数非法: {}", e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("[SkillController] Skill 上传失败", e);
            return Result.error("Skill 上传失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public Result<Boolean> deleteSkill(
            HttpServletRequest httpServletRequest,
            @PathVariable String id) {
        try {
            ResolvedSkillRequestContext context = resolveRequestContext(httpServletRequest, "skills/" + id);
            log.info("[SkillController] 删除用户私有 Skill, username={}, id={}", context.account().getUsername(), id);

            OpenVikingReadResponse abstractResponse = openVikingSkillService.readSkillAbstract(id, context.identity());
            if (abstractResponse == null || !"ok".equalsIgnoreCase(abstractResponse.status())) {
                return Result.error("Skill 不存在或无权限访问: " + id);
            }

            openVikingSkillService.deleteSkill(id, context.identity());
            Result<Boolean> result = Result.success(Boolean.TRUE);
            result.setMsg("删除成功");
            return result;
        } catch (IllegalArgumentException e) {
            log.warn("[SkillController] Skill 删除参数非法: {}", e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("[SkillController] Skill 删除失败", e);
            String message = e.getMessage() != null ? e.getMessage() : "unknown error";
            if (message.contains("HTTP 404")) {
                return Result.error("Skill 不存在或无权限访问: " + id);
            }
            return Result.error("Skill 删除失败: " + message);
        }
    }

    private SkillListItemResponse toSkillListItemResponse(SkillCatalogItem item) {
        return SkillListItemResponse.builder()
                .id(item.id())
                .name(item.name())
                .path(item.path())
                .abstractText(item.abstractText())
                .updatedAt(item.updatedAt())
                .build();
    }

    private ResolvedSkillRequestContext resolveRequestContext(HttpServletRequest request, String endpoint) {
        Account currentAccount = currentAccountResolver.requireCurrentAccount(request, endpoint);
        OpenVikingIdentity identity = openVikingIdentityResolver.resolve(currentAccount);
        return new ResolvedSkillRequestContext(currentAccount, identity);
    }

    private record ResolvedSkillRequestContext(
            Account account,
            OpenVikingIdentity identity
    ) {
    }
}
