package cn.itcast.agent.ai.mapper;

/** 管理员额度总览查询的内部行对象。 */
public class WalletOverviewRow {

    private Long userId;
    private String username;
    private String nickname;
    private String role;
    private Long balanceMicros;
    private Long totalConsumedMicros;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public Long getBalanceMicros() { return balanceMicros; }
    public void setBalanceMicros(Long balanceMicros) { this.balanceMicros = balanceMicros; }
    public Long getTotalConsumedMicros() { return totalConsumedMicros; }
    public void setTotalConsumedMicros(Long totalConsumedMicros) { this.totalConsumedMicros = totalConsumedMicros; }
}