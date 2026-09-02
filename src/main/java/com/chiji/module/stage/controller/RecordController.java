package com.chiji.module.stage.controller;

import com.chiji.common.core.result.R;
import com.chiji.module.auth.util.SecurityUtil;
import com.chiji.module.stage.dto.CreateRecordRequest;
import com.chiji.module.stage.service.RecordService;
import com.chiji.module.stage.vo.RecordVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 时光轴记录接口。
 * <p>
 * 受 Sa-Token 保护，需携带 Authorization 请求头。
 * 提供记录的创建与查询能力，供「记录此刻」弹层与时间轴页使用。
 */
@Tag(name = "记录", description = "时光轴记录的创建与查询")
@RestController
@RequestMapping("/api/records")
@RequiredArgsConstructor
public class RecordController {

    private final RecordService recordService;

    /**
     * 创建一条记录。
     * <p>
     * 保存正文与标签，自动关联阶段与牙套副。自定义标签会自动创建。
     *
     * @param request 创建请求
     * @return 保存后的记录 VO
     */
    @Operation(summary = "创建记录", description = "保存文字 + 标签，自动关联阶段与牙套副")
    @PostMapping
    public R<RecordVO> createRecord(@Valid @RequestBody CreateRecordRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        RecordVO vo = recordService.createRecord(userId, request);
        return R.ok(vo, "记录已保存");
    }

    /**
     * 查询记录列表。
     * <p>
     * 支持按阶段或按牙套副过滤；都不传时返回该用户全部记录。
     *
     * @param stageId   阶段 ID（可选，「查看全部」按当前阶段过滤）
     * @param alignerId 牙套副 ID（可选）
     * @return 记录列表（按记录日期倒序）
     */
    @Operation(summary = "查询记录列表", description = "可按阶段/牙套副过滤，都不传则查全部，按日期倒序")
    @GetMapping
    public R<List<RecordVO>> listRecords(@RequestParam(required = false) Long stageId,
                                         @RequestParam(required = false) Long alignerId) {
        Long userId = SecurityUtil.getCurrentUserId();
        List<RecordVO> records;
        if (stageId != null) {
            records = recordService.listByStage(userId, stageId);
        } else if (alignerId != null) {
            records = recordService.listByAligner(userId, alignerId);
        } else {
            records = recordService.listByUser(userId);
        }
        return R.ok(records);
    }

    /**
     * 查询单条记录详情。
     * <p>
     * 首页「最近记录」缩略图点击后，前端据此拉取完整记录并弹出与时光轴一致的预览弹窗。
     *
     * @param id 记录 ID
     * @return 记录 VO
     */
    @Operation(summary = "查询记录详情", description = "按 ID 查询单条记录，仅允许查看自己的记录")
    @GetMapping("/{id}")
    public R<RecordVO> getRecord(@PathVariable Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        return R.ok(recordService.getRecord(userId, id));
    }

    /**
     * 删除一条记录（逻辑删除）。
     *
     * @param id 记录 ID
     * @return 操作结果
     */
    @Operation(summary = "删除记录", description = "逻辑删除记录，仅允许删除自己的记录")
    @DeleteMapping("/{id}")
    public R<Void> deleteRecord(@PathVariable Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        recordService.deleteRecord(userId, id);
        return R.ok(null, "记录已删除");
    }
}
