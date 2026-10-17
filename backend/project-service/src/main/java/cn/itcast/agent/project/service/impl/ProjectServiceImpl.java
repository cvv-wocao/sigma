package cn.itcast.agent.project.service.impl;

import cn.itcast.agent.common.core.exception.BusinessException;
import cn.itcast.agent.common.core.result.ErrorCode;
import cn.itcast.agent.project.dto.ProjectSaveRequest;
import cn.itcast.agent.project.dto.ProjectUpdateRequest;
import cn.itcast.agent.project.entity.Project;
import cn.itcast.agent.project.mapper.ProjectMapper;
import cn.itcast.agent.project.service.ProjectService;
import cn.itcast.agent.project.vo.ProjectVO;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 项目收藏业务实现。
 *
 * <p>重复收藏既做「先查后插」的友好提示，也捕获唯一键冲突，
 * 保证并发提交同一条收藏时不会写入两条记录。</p>
 */
@Service
public class ProjectServiceImpl implements ProjectService {

    private static final Logger log = LoggerFactory.getLogger(ProjectServiceImpl.class);

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ProjectMapper projectMapper;

    public ProjectServiceImpl(ProjectMapper projectMapper) {
        this.projectMapper = projectMapper;
    }

    @Override
    public ProjectVO create(Long userId, ProjectSaveRequest request) {
        String repoOwner = request.repoOwner().trim();
        String repoName = request.repoName().trim();
        String fullName = repoOwner + "/" + repoName;

        Long exists = projectMapper.selectCount(Wrappers.<Project>lambdaQuery()
                .eq(Project::getUserId, userId)
                .eq(Project::getFullName, fullName));
        if (exists != null && exists > 0) {
            throw new BusinessException(ErrorCode.PROJECT_ALREADY_SAVED);
        }

        Project project = new Project();
        project.setUserId(userId);
        project.setFullName(fullName);
        project.setRepoOwner(repoOwner);
        project.setRepoName(repoName);
        project.setDescription(trimToNull(request.description()));
        project.setLanguage(trimToNull(request.language()));
        project.setStars(request.stars());
        project.setForks(request.forks());
        project.setDefaultBranch(trimToNull(request.defaultBranch()));
        project.setHtmlUrl(trimToNull(request.htmlUrl()));
        project.setNote(trimToNull(request.note()));
        project.setStatus(Project.STATUS_ACTIVE);

        try {
            projectMapper.insert(project);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.PROJECT_ALREADY_SAVED);
        }
        log.info("收藏成功：userId={}, fullName={}", userId, fullName);
        // 重新查询一次，让返回值带上数据库生成的 create_time / update_time，与列表接口保持一致
        return toVO(requireOwned(userId, project.getId()));
    }

    @Override
    public List<ProjectVO> list(Long userId, String keyword, Integer status) {
        String trimmedKeyword = trimToNull(keyword);
        List<Project> projects = projectMapper.selectList(Wrappers.<Project>lambdaQuery()
                .eq(Project::getUserId, userId)
                .eq(status != null, Project::getStatus, status)
                .and(trimmedKeyword != null, wrapper -> wrapper
                        .like(Project::getFullName, trimmedKeyword)
                        .or()
                        .like(Project::getDescription, trimmedKeyword))
                .orderByDesc(Project::getUpdateTime)
                .orderByDesc(Project::getId));

        return projects.stream().map(this::toVO).toList();
    }

    @Override
    public ProjectVO get(Long userId, Long id) {
        return toVO(requireOwned(userId, id));
    }

    @Override
    public ProjectVO update(Long userId, Long id, ProjectUpdateRequest request) {
        Project project = requireOwned(userId, id);

        Project patch = new Project();
        patch.setId(project.getId());
        patch.setDescription(trimToNull(request.description()));
        patch.setNote(trimToNull(request.note()));
        patch.setStatus(request.status());
        // updateById 默认忽略 null 字段，因此只提交了备注时不会把简介、状态清空
        projectMapper.updateById(patch);

        return toVO(requireOwned(userId, id));
    }

    @Override
    public void delete(Long userId, Long id) {
        int rows = projectMapper.delete(Wrappers.<Project>lambdaQuery()
                .eq(Project::getId, id)
                .eq(Project::getUserId, userId));
        if (rows == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "项目不存在");
        }
        log.info("取消收藏：userId={}, projectId={}", userId, id);
    }

    /** 按「主键 + 所属用户」查询，查不到统一按不存在处理，不泄露他人项目是否存在 */
    private Project requireOwned(Long userId, Long id) {
        Project project = projectMapper.selectOne(Wrappers.<Project>lambdaQuery()
                .eq(Project::getId, id)
                .eq(Project::getUserId, userId));
        if (project == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "项目不存在");
        }
        return project;
    }

    private ProjectVO toVO(Project project) {
        return new ProjectVO(
                project.getId(),
                project.getFullName(),
                project.getRepoOwner(),
                project.getRepoName(),
                project.getDescription(),
                project.getLanguage(),
                project.getStars(),
                project.getForks(),
                project.getDefaultBranch(),
                project.getHtmlUrl(),
                project.getNote(),
                project.getStatus(),
                format(project.getCreateTime()),
                format(project.getUpdateTime()));
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String format(LocalDateTime dateTime) {
        return dateTime == null ? null : DATE_TIME_FORMATTER.format(dateTime);
    }
}