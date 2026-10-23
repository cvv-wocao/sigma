package cn.itcast.agent.ai.service.impl;

import cn.itcast.agent.ai.config.BillingProperties;
import cn.itcast.agent.ai.dto.RechargeRequest;
import cn.itcast.agent.ai.entity.RechargeRecord;
import cn.itcast.agent.ai.entity.TokenUsage;
import cn.itcast.agent.ai.entity.UserWallet;
import cn.itcast.agent.ai.mapper.RechargeRecordMapper;
import cn.itcast.agent.ai.mapper.TokenUsageMapper;
import cn.itcast.agent.ai.mapper.TokenUsageSummaryRow;
import cn.itcast.agent.ai.mapper.UserWalletMapper;
import cn.itcast.agent.ai.mapper.WalletOverviewRow;
import cn.itcast.agent.ai.service.BillingService;
import cn.itcast.agent.ai.vo.UsageRecordVO;
import cn.itcast.agent.ai.vo.UsageSummaryVO;
import cn.itcast.agent.ai.vo.WalletOverviewVO;
import cn.itcast.agent.ai.vo.WalletVO;
import cn.itcast.agent.common.core.exception.BusinessException;
import cn.itcast.agent.common.core.result.ErrorCode;
import cn.itcast.agent.common.security.CurrentUser;
import cn.itcast.agent.common.security.jwt.LoginUser;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** 平台 Token 额度、扣费与充值实现。 */
@Service
public class BillingServiceImpl implements BillingService {

    private static final Logger log = LoggerFactory.getLogger(BillingServiceImpl.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final BigDecimal MICROS_PER_YUAN = new BigDecimal("1000000");
    private static final int MAX_USAGE_LIMIT = 200;
    private static final int MAX_SUMMARY_DAYS = 365;

    private final UserWalletMapper walletMapper;
    private final TokenUsageMapper usageMapper;
    private final RechargeRecordMapper rechargeMapper;
    private final BillingProperties properties;

    public BillingServiceImpl(UserWalletMapper walletMapper,
                              TokenUsageMapper usageMapper,
                              RechargeRecordMapper rechargeMapper,
                              BillingProperties properties) {
        this.walletMapper = walletMapper;
        this.usageMapper = usageMapper;
        this.rechargeMapper = rechargeMapper;
        this.properties = properties;
    }

    @Override
    public WalletVO wallet(Long userId) {
        UserWallet wallet = wallet(userId, true);
        return toVO(wallet);
    }

    @Override
    @Transactional
    public WalletVO recharge(Long operatorId, RechargeRequest request) {
        if (request.userId() == null || request.userId() <= 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "充值用户不正确");
        }
        if (walletMapper.countUser(request.userId()) == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "充值用户不存在");
        }
        long amountMicros = request.amountYuan().multiply(MICROS_PER_YUAN)
                .setScale(0, RoundingMode.HALF_UP).longValueExact();
        if (amountMicros <= 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "充值金额必须大于 0");
        }

        walletMapper.ensureExists(request.userId());
        walletMapper.addBalance(request.userId(), amountMicros);

        RechargeRecord record = new RechargeRecord();
        record.setUserId(request.userId());
        record.setOperatorId(operatorId);
        record.setAmountMicros(amountMicros);
        record.setRemark(StringUtils.hasText(request.remark()) ? request.remark().trim() : null);
        rechargeMapper.insert(record);

        log.info("管理员充值成功：operatorId={}, userId={}, amountYuan={}",
                operatorId, request.userId(), request.amountYuan());
        return toVO(wallet(request.userId(), false));
    }

    @Override
    public List<UsageRecordVO> usage(Long userId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, MAX_USAGE_LIMIT));
        return usageMapper.selectList(Wrappers.<TokenUsage>lambdaQuery()
                        .eq(TokenUsage::getUserId, userId)
                        .orderByDesc(TokenUsage::getCreateTime)
                        .orderByDesc(TokenUsage::getId)
                        .last("LIMIT " + safeLimit))
                .stream()
                .map(this::toUsageVO)
                .toList();
    }

    @Override
    public UsageSummaryVO usageSummary(Long userId, int days) {
        int safeDays = Math.max(1, Math.min(days, MAX_SUMMARY_DAYS));
        TokenUsageSummaryRow row = usageMapper.summarize(userId, LocalDateTime.now().minusDays(safeDays));
        if (row == null) {
            row = new TokenUsageSummaryRow();
        }
        return new UsageSummaryVO(
                nvl(row.getRequestCount()),
                nvl(row.getPromptTokens()),
                nvl(row.getCompletionTokens()),
                nvl(row.getTotalTokens()),
                yuan(row.getInputCostMicros()),
                yuan(row.getOutputCostMicros()),
                yuan(row.getUsageCostMicros()),
                yuan(row.getPlatformUsageCostMicros()),
                yuan(row.getSelfKeyUsageCostMicros()),
                yuan(row.getCostMicros()),
                nvl(row.getChargedRequestCount()),
                nvl(row.getPlatformRequestCount()),
                nvl(row.getSelfKeyRequestCount()),
                row.getAvgDurationMs() == null ? 0L
                        : row.getAvgDurationMs().setScale(0, RoundingMode.HALF_UP).longValueExact(),
                nvl(row.getMaxDurationMs()),
                format(row.getFirstTime()),
                format(row.getLastTime()));
    }

    @Override
    public List<WalletOverviewVO> overview() {
        return walletMapper.listOverview().stream()
                .map(this::toOverviewVO)
                .toList();
    }

    @Override
    public boolean isAdmin(Long userId, String authenticatedRole) {
        if (userId == null) {
            return CurrentUser.isAdmin(authenticatedRole);
        }
        String role = walletMapper.selectRole(userId);
        return StringUtils.hasText(role) && LoginUser.ROLE_ADMIN.equalsIgnoreCase(role.trim());
    }

    @Override
    public void requireAdmin(Long userId, String authenticatedRole) {
        if (!isAdmin(userId, authenticatedRole)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "该操作需要管理员权限");
        }
    }

    @Override
    public void requirePlatformAccess(Long userId, boolean admin, boolean platformToken) {
        if (!platformToken || admin) {
            return;
        }
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "请登录后使用平台模型");
        }
        UserWallet wallet = walletMapper.selectById(userId);
        if (wallet == null || wallet.getBalanceMicros() == null || wallet.getBalanceMicros() <= 0) {
            throw new BusinessException(ErrorCode.AI_BALANCE_INSUFFICIENT,
                    "平台额度不足，请先充值，或在模型设置中配置自己的 API Key");
        }
    }

    @Override
    public void recordUsage(Long userId, boolean admin, boolean platformToken,
                            String scene, ChatResponse response, long durationMs) {
        if (userId == null) {
            return;
        }
        try {
            Usage usage = response == null || response.getMetadata() == null
                    ? null : response.getMetadata().getUsage();
            int promptTokens = usage == null || usage.getPromptTokens() == null ? 0 : usage.getPromptTokens();
            int completionTokens = usage == null || usage.getCompletionTokens() == null ? 0 : usage.getCompletionTokens();
            int totalTokens = usage == null || usage.getTotalTokens() == null
                    ? promptTokens + completionTokens : usage.getTotalTokens();
            String model = response == null || response.getMetadata() == null
                    ? null : response.getMetadata().getModel();

            CostBreakdown cost = costBreakdown(promptTokens, completionTokens);
            long inputCostMicros = cost.inputMicros();
            long outputCostMicros = cost.outputMicros();
            long usageCostMicros = cost.totalMicros();
            long costMicros = platformToken && !admin ? usageCostMicros : 0L;
            if (costMicros > 0) {
                walletMapper.ensureExists(userId);
                walletMapper.charge(userId, costMicros);
            }
            if (usage == null) {
                log.warn("模型响应未返回 Token 用量，本次无法精确计费：userId={}, scene={}, model={}",
                        userId, scene, model);
            }

            TokenUsage record = new TokenUsage();
            record.setUserId(userId);
            record.setScene(scene);
            record.setModel(model);
            record.setPromptTokens(promptTokens);
            record.setCompletionTokens(completionTokens);
            record.setTotalTokens(totalTokens);
            record.setInputCostMicros(inputCostMicros);
            record.setOutputCostMicros(outputCostMicros);
            record.setCostMicros(costMicros);
            record.setDurationMs(Math.max(0L, durationMs));
            record.setChargeMode(resolveChargeMode(admin, platformToken, totalTokens, costMicros));
            record.setPlatformToken(platformToken);
            usageMapper.insert(record);
        } catch (Exception e) {
            log.error("Token 用量记录失败：userId={}, scene={}", userId, scene, e);
        }
    }

    private UserWallet wallet(Long userId, boolean createIfAbsent) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "请登录后查看额度");
        }
        UserWallet wallet = walletMapper.selectById(userId);
        if (wallet == null && createIfAbsent) {
            walletMapper.ensureExists(userId);
            wallet = walletMapper.selectById(userId);
        }
        return wallet;
    }

    private CostBreakdown costBreakdown(int promptTokens, int completionTokens) {
        long inputMicros = tokenCostMicros(promptTokens, properties.inputPricePerMillionYuan());
        long outputMicros = tokenCostMicros(completionTokens, properties.outputPricePerMillionYuan());
        return new CostBreakdown(inputMicros, outputMicros);
    }

    private long tokenCostMicros(int tokens, BigDecimal pricePerMillionYuan) {
        return BigDecimal.valueOf(Math.max(0, tokens))
                .multiply(pricePerMillionYuan)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    private String resolveChargeMode(boolean admin, boolean platformToken, int totalTokens, long costMicros) {
        if (!platformToken) {
            return "SELF_KEY";
        }
        if (admin) {
            return "ADMIN_EXEMPT";
        }
        if (totalTokens <= 0 || costMicros <= 0) {
            return "NO_CHARGE";
        }
        return "PLATFORM_CHARGED";
    }

    private WalletVO toVO(UserWallet wallet) {
        if (wallet == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "钱包不存在");
        }
        return new WalletVO(
                wallet.getUserId(),
                yuan(wallet.getBalanceMicros()),
                yuan(wallet.getTotalRechargedMicros()),
                yuan(wallet.getTotalConsumedMicros()),
                properties.inputPricePerMillionYuan(),
                properties.outputPricePerMillionYuan(),
                format(wallet.getUpdateTime()));
    }

    private UsageRecordVO toUsageVO(TokenUsage usage) {
        return new UsageRecordVO(
                usage.getId(),
                usage.getScene(),
                usage.getModel(),
                nvl(usage.getPromptTokens()),
                nvl(usage.getCompletionTokens()),
                nvl(usage.getTotalTokens()),
                yuan(usage.getInputCostMicros()),
                yuan(usage.getOutputCostMicros()),
                yuan(usage.getCostMicros()),
                nvl(usage.getDurationMs()),
                usage.getChargeMode(),
                Boolean.TRUE.equals(usage.getPlatformToken()),
                format(usage.getCreateTime()));
    }

    private WalletOverviewVO toOverviewVO(WalletOverviewRow row) {
        String role = StringUtils.hasText(row.getRole()) ? row.getRole() : "USER";
        return new WalletOverviewVO(
                row.getUserId(),
                row.getUsername(),
                row.getNickname(),
                role,
                yuan(row.getBalanceMicros()),
                yuan(row.getTotalConsumedMicros()),
                "ADMIN".equalsIgnoreCase(role));
    }

    private BigDecimal yuan(Long micros) {
        return BigDecimal.valueOf(micros == null ? 0L : micros, 6);
    }

    private int nvl(Integer value) {
        return value == null ? 0 : value;
    }

    private long nvl(Long value) {
        return value == null ? 0L : value;
    }

    private String format(LocalDateTime time) {
        return time == null ? null : TIME_FORMATTER.format(time);
    }

    private record CostBreakdown(long inputMicros, long outputMicros) {
        private long totalMicros() {
            return inputMicros + outputMicros;
        }
    }
}
