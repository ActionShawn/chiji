// D:\Java Work Place\personal-develop\teeth-trace\server\src\main\java\com\chiji\common\core\page\CursorPage.java
package com.chiji.common.core.page;

import java.util.List;

/**
 * 游标分页响应。
 * <p>
 * 游标分页相比页码分页更适合无限滚动场景：客户端携带上一页最后一条数据的 id 作为下一次查询起点，
 * 避免新增数据导致的页码漂移。
 *
 * @param items      当前页数据
 * @param nextLastId 下一页游标（无更多数据时为 null）
 * @param hasMore    是否还有下一页
 * @param <T>        数据类型
 */
public record CursorPage<T>(List<T> items, Long nextLastId, Boolean hasMore) {

    /**
     * 构造空的分页结果。
     *
     * @param <T> 数据类型
     * @return 空分页结果
     */
    public static <T> CursorPage<T> empty() {
        return new CursorPage<>(List.of(), null, false);
    }
}
