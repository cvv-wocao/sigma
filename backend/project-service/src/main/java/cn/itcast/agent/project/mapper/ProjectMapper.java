package cn.itcast.agent.project.mapper;

import cn.itcast.agent.project.entity.Project;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 项目收藏表 Mapper。
 * 单表增删改查直接使用 BaseMapper 提供的方法；
 * 复杂 SQL 按约定写在 resources/mapper/ProjectMapper.xml 中，并在此接口声明方法。
 */
@Mapper
public interface ProjectMapper extends BaseMapper<Project> {
}