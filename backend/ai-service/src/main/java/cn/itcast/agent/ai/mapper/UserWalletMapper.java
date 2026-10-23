package cn.itcast.agent.ai.mapper;

import cn.itcast.agent.ai.entity.UserWallet;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/** 钱包表操作；金额全部使用微元。 */
@Mapper
public interface UserWalletMapper extends BaseMapper<UserWallet> {

    @Insert("INSERT IGNORE INTO ai_user_wallet(user_id, balance_micros, total_recharged_micros, total_consumed_micros) "
            + "VALUES(#{userId}, 0, 0, 0)")
    int ensureExists(@Param("userId") Long userId);

    @Insert("INSERT INTO ai_user_wallet(user_id, balance_micros, total_recharged_micros, total_consumed_micros) "
            + "VALUES(#{userId}, #{amountMicros}, #{amountMicros}, 0) "
            + "ON DUPLICATE KEY UPDATE balance_micros = balance_micros + #{amountMicros}, "
            + "total_recharged_micros = total_recharged_micros + #{amountMicros}, update_time = NOW()")
    int addBalance(@Param("userId") Long userId, @Param("amountMicros") Long amountMicros);

    @Update("UPDATE ai_user_wallet SET "
            + "total_consumed_micros = total_consumed_micros + LEAST(balance_micros, #{costMicros}), "
            + "balance_micros = GREATEST(balance_micros - #{costMicros}, 0), update_time = NOW() "
            + "WHERE user_id = #{userId}")
    int charge(@Param("userId") Long userId, @Param("costMicros") Long costMicros);

    @Select("SELECT u.id AS user_id, u.username, u.nickname, u.role, "
            + "COALESCE(w.balance_micros, 0) AS balance_micros, "
            + "COALESCE(w.total_consumed_micros, 0) AS total_consumed_micros "
            + "FROM `user` u LEFT JOIN ai_user_wallet w ON w.user_id = u.id ORDER BY u.id")
    List<WalletOverviewRow> listOverview();

    @Select("SELECT COUNT(*) FROM `user` WHERE id = #{userId}")
    int countUser(@Param("userId") Long userId);

    @Select("SELECT role FROM `user` WHERE id = #{userId}")
    String selectRole(@Param("userId") Long userId);
}
