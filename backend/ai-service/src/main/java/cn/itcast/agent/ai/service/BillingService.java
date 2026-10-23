package cn.itcast.agent.ai.service;

import cn.itcast.agent.ai.dto.RechargeRequest;
import cn.itcast.agent.ai.vo.UsageRecordVO;
import cn.itcast.agent.ai.vo.UsageSummaryVO;
import cn.itcast.agent.ai.vo.WalletOverviewVO;
import cn.itcast.agent.ai.vo.WalletVO;
import org.springframework.ai.chat.model.ChatResponse;

import java.util.List;

/**
 * 平台模型额度与 Token 计费。
 *
 * <p>只有「普通用户 + 平台 Token」才需要余额；管理员和用户自带 API Key 都不扣费。</p>
 */
public interface BillingService {

    /** 查询钱包，不存在时自动创建一个零余额钱包。 */
    WalletVO wallet(Long userId);

    /** 管理员充值，返回充值后的钱包。 */
    WalletVO recharge(Long operatorId, RechargeRequest request);

    /** 当前用户的 Token 使用记录，最新在前。 */
    List<UsageRecordVO> usage(Long userId, int limit);

    /** 指定天数内的 Token 用量、扣费与耗时汇总。 */
    UsageSummaryVO usageSummary(Long userId, int days);

    /** 管理员查看所有用户的余额与累计消耗。 */
    List<WalletOverviewVO> overview();

    /**
     * 判断用户是否为管理员。
     *
     * <p>优先读取数据库中的当前角色，使角色调整后无需等待旧 JWT 过期；
     * 用户 ID 缺失时才回退到网关注入的角色头。</p>
     */
    boolean isAdmin(Long userId, String authenticatedRole);

    /** 管理员接口权限校验，规则同 {@link #isAdmin(Long, String)}。 */
    void requireAdmin(Long userId, String authenticatedRole);

    /**
     * 模型调用前的额度检查。
     *
     * @param userId       当前用户，允许为空仅用于自定义模型场景
     * @param admin        是否管理员
     * @param platformToken 本次是否使用平台 Token
     */
    void requirePlatformAccess(Long userId, boolean admin, boolean platformToken);

    /**
     * 模型返回后记录实际 Token 并扣费。记录失败只打日志，不影响已经拿到的回答。
     */
    void recordUsage(Long userId, boolean admin, boolean platformToken,
                     String scene, ChatResponse response, long durationMs);
}
