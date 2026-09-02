package com.chiji.module.feedback.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chiji.entity.Feedback;
import org.apache.ibatis.annotations.Mapper;

/**
 * 意见反馈表 Mapper。
 */
@Mapper
public interface FeedbackMapper extends BaseMapper<Feedback> {
}
