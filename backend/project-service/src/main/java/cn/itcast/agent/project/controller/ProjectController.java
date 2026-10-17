package cn.itcast.agent.project.controller;

import cn.itcast.agent.common.core.result.Result;
import cn.itcast.agent.common.security.AuthHeaders;
import cn.itcast.agent.common.security.CurrentUser;
import cn.itcast.agent.project.dto.ProjectSaveRequest;
import cn.itcast.agent.project.dto.ProjectUpdateRequest;
import cn.itcast.agent.project.service.ProjectService;
import cn.itcast.agent.project.vo.ProjectVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 项目管理对外接口。
 * 对外路径由网关统一加 /api 前缀，网关 StripPrefix 后到达本服务的是 /project/**。
 *
 * <p>当前登录用户从网关写入的 X-User-Id 请求头读取，所有操作都限定在自己的项目范围内。</p>
 */
@RestController
@RequestMapping("/project")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    /** 收藏项目。对外：POST /api/project */
    @PostMapping
    public Result<ProjectVO> create(@Valid @RequestBody ProjectSaveRequest request,
                                    @RequestHeader(value = AuthHeaders.USER_ID, required = false) String authenticatedUserId) {
        return Result.success(projectService.create(CurrentUser.requireId(authenticatedUserId), request));
    }

    /** 项目列表。对外：GET /api/project?keyword=&status= */
    @GetMapping
    public Result<List<ProjectVO>> list(@RequestParam(value = "keyword", required = false) String keyword,
                                        @RequestParam(value = "status", required = false) Integer status,
                                        @RequestHeader(value = AuthHeaders.USER_ID, required = false) String authenticatedUserId) {
        return Result.success(projectService.list(CurrentUser.requireId(authenticatedUserId), keyword, status));
    }

    /** 项目详情。对外：GET /api/project/{id} */
    @GetMapping("/{id}")
    public Result<ProjectVO> get(@PathVariable("id") Long id,
                                @RequestHeader(value = AuthHeaders.USER_ID, required = false) String authenticatedUserId) {
        return Result.success(projectService.get(CurrentUser.requireId(authenticatedUserId), id));
    }

    /** 更新简介 / 备注 / 状态。对外：PUT /api/project/{id} */
    @PutMapping("/{id}")
    public Result<ProjectVO> update(@PathVariable("id") Long id,
                                    @Valid @RequestBody ProjectUpdateRequest request,
                                    @RequestHeader(value = AuthHeaders.USER_ID, required = false) String authenticatedUserId) {
        return Result.success(projectService.update(CurrentUser.requireId(authenticatedUserId), id, request));
    }

    /** 取消收藏。对外：DELETE /api/project/{id} */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id,
                              @RequestHeader(value = AuthHeaders.USER_ID, required = false) String authenticatedUserId) {
        projectService.delete(CurrentUser.requireId(authenticatedUserId), id);
        return Result.success();
    }
}