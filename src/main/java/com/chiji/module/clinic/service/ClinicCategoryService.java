package com.chiji.module.clinic.service;

import com.chiji.module.clinic.dto.CategoryCreateRequest;
import com.chiji.module.clinic.dto.CategorySortRequest;
import com.chiji.module.clinic.vo.ClinicCategoryVO;

import java.util.List;

/**
 * 花费分类服务。
 * <p>
 * 列表 = 系统内置五类（user_id=0，不可改删）+ 用户自定义分类；
 * 删除自定义分类时其流水保留（categoryId 变为悬空引用，展示侧兜底「已删分类」）。
 */
public interface ClinicCategoryService {

    /**
     * 查询分类列表（内置排前，自定义按用户排序）。
     *
     * @param userId 用户 ID
     * @return 分类列表
     */
    List<ClinicCategoryVO> list(Long userId);

    /**
     * 新增自定义分类。
     *
     * @param userId 用户 ID
     * @param req    创建请求（名称同用户下唯一，含内置名）
     * @return 新建分类
     */
    ClinicCategoryVO create(Long userId, CategoryCreateRequest req);

    /**
     * 删除自定义分类（内置分类拒绝删除）。
     *
     * @param userId 用户 ID
     * @param id     分类 ID
     */
    void delete(Long userId, Long id);

    /**
     * 自定义分类全量排序（按传入 ID 顺序重写 sort_order）。
     *
     * @param userId 用户 ID
     * @param req    排序请求
     */
    void sort(Long userId, CategorySortRequest req);
}
