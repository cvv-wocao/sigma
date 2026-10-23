package cn.itcast.agent.ai.controller;

import cn.itcast.agent.ai.dto.RechargeRequest;
import cn.itcast.agent.ai.service.BillingService;
import cn.itcast.agent.ai.vo.UsageRecordVO;
import cn.itcast.agent.ai.vo.UsageSummaryVO;
import cn.itcast.agent.ai.vo.WalletOverviewVO;
import cn.itcast.agent.ai.vo.WalletVO;
import cn.itcast.agent.common.core.result.Result;
import cn.itcast.agent.common.security.AuthHeaders;
import cn.itcast.agent.common.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 平台额度、Token 消费记录与管理员充值。
 *
 * <p>用户 ID 与角色均来自网关注入的可信请求头；充值和管理总览只允许 ADMIN。</p>
 */
@RestController
@RequestMapping("/ai/wallet")
public class WalletController {

    private final BillingService billingService;

    public WalletController(BillingService billingService) {
        this.billingService = billingService;
    }

    @GetMapping
    public Result<WalletVO> wallet(
            @RequestHeader(value = AuthHeaders.USER_ID, required = false) String authenticatedUserId) {
        return Result.success(billingService.wallet(CurrentUser.requireId(authenticatedUserId)));
    }

    @GetMapping("/usage")
    public Result<List<UsageRecordVO>> usage(
            @RequestParam(name = "limit", defaultValue = "50") int limit,
            @RequestHeader(value = AuthHeaders.USER_ID, required = false) String authenticatedUserId) {
        return Result.success(billingService.usage(CurrentUser.requireId(authenticatedUserId), limit));
    }

    @GetMapping("/usage/summary")
    public Result<UsageSummaryVO> usageSummary(
            @RequestParam(name = "days", defaultValue = "30") int days,
            @RequestHeader(value = AuthHeaders.USER_ID, required = false) String authenticatedUserId) {
        return Result.success(billingService.usageSummary(CurrentUser.requireId(authenticatedUserId), days));
    }

    @GetMapping("/admin/overview")
    public Result<List<WalletOverviewVO>> overview(
            @RequestHeader(value = AuthHeaders.USER_ID, required = false) String authenticatedUserId,
            @RequestHeader(value = AuthHeaders.USER_ROLE, required = false) String authenticatedRole) {
        billingService.requireAdmin(CurrentUser.requireId(authenticatedUserId), authenticatedRole);
        return Result.success(billingService.overview());
    }

    @PostMapping("/recharge")
    public Result<WalletVO> recharge(
            @Valid @RequestBody RechargeRequest request,
            @RequestHeader(value = AuthHeaders.USER_ID, required = false) String authenticatedUserId,
            @RequestHeader(value = AuthHeaders.USER_ROLE, required = false) String authenticatedRole) {
        Long operatorId = CurrentUser.requireId(authenticatedUserId);
        billingService.requireAdmin(operatorId, authenticatedRole);
        return Result.success(billingService.recharge(operatorId, request));
    }
}
