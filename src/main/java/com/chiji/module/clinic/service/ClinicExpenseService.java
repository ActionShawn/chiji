package com.chiji.module.clinic.service;

import com.chiji.module.clinic.dto.ExpenseSaveRequest;
import com.chiji.module.clinic.vo.ClinicExpenseVO;
import com.chiji.module.clinic.vo.ExpenseStatsVO;

import java.util.List;

/**
 * 矫正花费流水服务。
 */
public interface ClinicExpenseService {

    /**
     * 新增一笔花费。
     *
     * @param userId 用户 ID
     * @param req    保存请求（分类须存在：内置或本人自定义）
     * @return 新建流水
     */
    ClinicExpenseVO create(Long userId, ExpenseSaveRequest req);

    /**
     * 编辑一笔花费。
     *
     * @param userId 用户 ID
     * @param id     流水 ID
     * @param req    保存请求
     * @return 更新后流水
     */
    ClinicExpenseVO update(Long userId, Long id, ExpenseSaveRequest req);

    /**
     * 删除一笔花费（逻辑删）。
     *
     * @param userId 用户 ID
     * @param id     流水 ID
     */
    void delete(Long userId, Long id);

    /**
     * 流水列表（按月 + 可选分类筛选，日期倒序）。
     *
     * @param userId     用户 ID
     * @param month      月份 yyyy-MM（可空=全部）
     * @param categoryId 分类 ID（可空=全部分类）
     * @return 流水列表
     */
    List<ClinicExpenseVO> list(Long userId, String month, Long categoryId);

    /**
     * 花费统计：累计总额 + 分类占比（金额降序）+ 近 12 个月趋势。
     *
     * @param userId 用户 ID
     * @return 统计视图
     */
    ExpenseStatsVO stats(Long userId);
}
