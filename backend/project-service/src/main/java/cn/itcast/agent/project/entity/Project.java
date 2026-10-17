package cn.itcast.agent.project.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 项目收藏实体，对应 infrastructure/mysql/init/04-project.sql 的 project 表。
 *
 * <p>语言、Star、Fork、默认分支是「收藏时刻的快照」，不随 Gitee 变化自动刷新。</p>
 */
@TableName("project")
public class Project implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 状态：关注中 */
    public static final int STATUS_ACTIVE = 1;
    /** 状态：已归档 */
    public static final int STATUS_ARCHIVED = 0;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 所属用户 ID，来自网关注入的 X-User-Id */
    private Long userId;

    /** 仓库全名 owner/repo，同一用户下唯一 */
    private String fullName;

    private String repoOwner;

    private String repoName;

    private String description;

    private String language;

    private Integer stars;

    private Integer forks;

    private String defaultBranch;

    private String htmlUrl;

    /** 用户备注：用途、接入计划等 */
    private String note;

    /** 状态：1 关注中，0 已归档 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getRepoOwner() {
        return repoOwner;
    }

    public void setRepoOwner(String repoOwner) {
        this.repoOwner = repoOwner;
    }

    public String getRepoName() {
        return repoName;
    }

    public void setRepoName(String repoName) {
        this.repoName = repoName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public Integer getStars() {
        return stars;
    }

    public void setStars(Integer stars) {
        this.stars = stars;
    }

    public Integer getForks() {
        return forks;
    }

    public void setForks(Integer forks) {
        this.forks = forks;
    }

    public String getDefaultBranch() {
        return defaultBranch;
    }

    public void setDefaultBranch(String defaultBranch) {
        this.defaultBranch = defaultBranch;
    }

    public String getHtmlUrl() {
        return htmlUrl;
    }

    public void setHtmlUrl(String htmlUrl) {
        this.htmlUrl = htmlUrl;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
}