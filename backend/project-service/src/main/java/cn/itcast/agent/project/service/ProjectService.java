package cn.itcast.agent.project.service;

import cn.itcast.agent.project.dto.ProjectSaveRequest;
import cn.itcast.agent.project.dto.ProjectUpdateRequest;
import cn.itcast.agent.project.vo.ProjectVO;

import java.util.List;

/**
 * 项目收藏业务接口。
 *
 * <p>所有方法都要求传入当前登录用户 ID，查询与修改一律带 user_id 条件，
 * 因此用户之间不可能读到或改到对方的项目。</p>
 */
public interface ProjectService {

    /** 收藏一个项目；同一用户重复收藏同一仓库时抛业务异常 */
    ProjectVO create(Long userId, ProjectSaveRequest request);

    /**
     * 查询当前用户的项目列表。
     *
     * @param keyword 可选，匹配仓库全名或简介
     * @param status  可选，1 关注中 / 0 已归档
     */
    List<ProjectVO> list(Long userId, String keyword, Integer status);

    /** 查询项目详情；不存在或不属于当前用户时抛业务异常 */
    ProjectVO get(Long userId, Long id);

    /** 更新简介、备注或状态；返回更新后的数据 */
    ProjectVO update(Long userId, Long id, ProjectUpdateRequest request);

    /** 取消收藏（物理删除）；不存在或不属于当前用户时抛业务异常 */
    void delete(Long userId, Long id);
}