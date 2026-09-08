package com.chiji.module.message.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chiji.common.core.exception.BusinessException;
import com.chiji.common.core.exception.ErrorCode;
import com.chiji.entity.Message;
import com.chiji.entity.UserNotificationConfig;
import com.chiji.entity.UserSetting;
import com.chiji.enums.NotificationPresetEnum;
import com.chiji.enums.WearReminderTypeEnum;
import com.chiji.module.message.dto.NotificationUpdateRequest;
import com.chiji.module.message.mapper.MessageMapper;
import com.chiji.module.message.mapper.UserNotificationConfigMapper;
import com.chiji.module.message.mapper.UserSettingMapper;
import com.chiji.module.message.service.NotificationSettingService;
import com.chiji.module.message.vo.DndSettingVO;
import com.chiji.module.message.vo.NotificationSettingsVO;
import com.chiji.module.message.vo.TypeSwitchVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 通知设置服务实现。
 * <p>
 * 默认值（与 DB 列 DEFAULT 一致）：总开关/弹窗/红点开、预设 STANDARD、勿扰关闭 22:00–08:00。
 * {@code user_setting} 行缺失时按默认值对外呈现、不写库；仅更新时懒创建（唯一键冲突幂等回读）。
 * {@code user_notification_config} 未落行的类型按 {@link WearReminderTypeEnum#isDefaultOn()} 呈现，
 * 首次应用预设/手工拨动时写入。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationSettingServiceImpl implements NotificationSettingService {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");
    private static final LocalTime DEFAULT_DND_START = LocalTime.of(22, 0);
    private static final LocalTime DEFAULT_DND_END = LocalTime.of(8, 0);
    /** 订阅消息能力恒未开通（前端灰置订阅通道）。 */
    private static final boolean SUBSCRIBE_AVAILABLE = false;

    private final UserSettingMapper userSettingMapper;
    private final UserNotificationConfigMapper configMapper;
    private final MessageMapper messageMapper;

    @Override
    public NotificationSettingsVO getSettings(Long userId) {
        UserSetting s = userSettingMapper.selectOne(new LambdaQueryWrapper<UserSetting>()
                .eq(UserSetting::getUserId, userId));
        Map<String, Boolean> enabled = effectiveSwitches(userId, loadSwitches(userId));
        String preset = s != null && s.getPresetMode() != null ? s.getPresetMode() : NotificationPresetEnum.STANDARD.getCode();
        return toVO(s, preset, enabled);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public NotificationSettingsVO updateSettings(Long userId, NotificationUpdateRequest req) {
        UserSetting s = ensureSetting(userId);
        if (req == null) {
            return getSettings(userId);
        }
        // 1. 单值偏好（局部更新，null 表示不修改）
        if (req.master() != null) {
            s.setNotifMaster(req.master());
        }
        if (req.popup() != null) {
            s.setNotifPopup(req.popup());
        }
        if (req.badge() != null) {
            s.setNotifBadge(req.badge());
        }
        if (req.dnd() != null) {
            NotificationUpdateRequest.DndUpdate dnd = req.dnd();
            if (dnd.enabled() != null) {
                s.setDndEnabled(dnd.enabled());
            }
            if (dnd.start() != null) {
                s.setDndStart(parseDndTime(dnd.start()));
            }
            if (dnd.end() != null) {
                s.setDndEnd(parseDndTime(dnd.end()));
            }
        }

        // 2. 一键应用预设：覆盖 8 类开关明细
        if (req.preset() != null && !req.preset().isBlank()) {
            NotificationPresetEnum preset = NotificationPresetEnum.getByCode(req.preset());
            if (preset == null || preset == NotificationPresetEnum.CUSTOM) {
                throw new BusinessException(ErrorCode.NOTIFICATION_PARAM_INVALID, "预设模式不合法");
            }
            s.setPresetMode(preset.getCode());
            applyTemplate(userId, preset);
            userSettingMapper.updateById(s);
            return getSettings(userId);
        }

        // 3. 逐类型开关：覆盖后重新推导预设（与模板完全一致则回模板，否则 CUSTOM）
        if (req.types() != null && !req.types().isEmpty()) {
            Map<String, Boolean> applied = loadSwitches(userId);
            for (NotificationUpdateRequest.TypeSwitchUpdate item : req.types()) {
                WearReminderTypeEnum type = requireType(item.type());
                if (item.enabled() == null) {
                    throw new BusinessException(ErrorCode.NOTIFICATION_PARAM_INVALID, "开关取值缺失");
                }
                applied.put(type.getCode(), item.enabled());
            }
            upsertSwitches(userId, applied);
            s.setPresetMode(derivePreset(applied).getCode());
        }

        userSettingMapper.updateById(s);
        return getSettings(userId);
    }

    @Override
    public boolean isReminderAllowed(Long userId, WearReminderTypeEnum type) {
        UserSetting s = userSettingMapper.selectOne(new LambdaQueryWrapper<UserSetting>()
                .eq(UserSetting::getUserId, userId));
        boolean master = s == null || s.getNotifMaster() == null || s.getNotifMaster();
        return master && isTypeEnabled(userId, type);
    }

    @Override
    public boolean existsSceneMessage(Long userId, WearReminderTypeEnum type, LocalDate sceneDate) {
        if (type == null || sceneDate == null) {
            return false;
        }
        Long count = messageMapper.selectCount(new LambdaQueryWrapper<Message>()
                .eq(Message::getUserId, userId)
                .eq(Message::getReminderType, type.getCode())
                .eq(Message::getSceneDate, sceneDate));
        return count != null && count > 0;
    }

    // ───────────────────────────── 内部工具 ─────────────────────────────

    /** 行缺失时按默认值装配整读 VO（不写库）。 */
    private NotificationSettingsVO toVO(UserSetting s, String preset, Map<String, Boolean> enabled) {
        boolean master = s == null || s.getNotifMaster() == null || s.getNotifMaster();
        boolean popup = s == null || s.getNotifPopup() == null || s.getNotifPopup();
        boolean badge = s == null || s.getNotifBadge() == null || s.getNotifBadge();
        boolean dndOn = s != null && Boolean.TRUE.equals(s.getDndEnabled());
        LocalTime dndStart = s != null && s.getDndStart() != null ? s.getDndStart() : DEFAULT_DND_START;
        LocalTime dndEnd = s != null && s.getDndEnd() != null ? s.getDndEnd() : DEFAULT_DND_END;

        List<TypeSwitchVO> types = new ArrayList<>();
        for (WearReminderTypeEnum t : WearReminderTypeEnum.values()) {
            boolean on = enabled.getOrDefault(t.getCode(), t.isDefaultOn());
            types.add(new TypeSwitchVO(t.getCode(), t.getDesc(), t.getGroupName(), on));
        }
        return new NotificationSettingsVO(
                master, popup, badge, preset,
                new DndSettingVO(dndOn, dndStart.format(HH_MM), dndEnd.format(HH_MM)),
                SUBSCRIBE_AVAILABLE, types);
    }

    /** 读取已落行的开关明细；未落行不在 map 中（由呈现层按默认值兜底）。 */
    private Map<String, Boolean> loadSwitches(Long userId) {
        Map<String, Boolean> map = new LinkedHashMap<>();
        List<UserNotificationConfig> rows = configMapper.selectList(new LambdaQueryWrapper<UserNotificationConfig>()
                .eq(UserNotificationConfig::getUserId, userId));
        for (UserNotificationConfig row : rows) {
            map.put(row.getReminderType(), Boolean.TRUE.equals(row.getEnabled()));
        }
        return map;
    }

    /** 合并不落行类型的默认值，得到全量开关（8 类）。 */
    private Map<String, Boolean> effectiveSwitches(Long userId, Map<String, Boolean> stored) {
        Map<String, Boolean> map = new LinkedHashMap<>(stored);
        for (WearReminderTypeEnum t : WearReminderTypeEnum.values()) {
            map.putIfAbsent(t.getCode(), t.isDefaultOn());
        }
        return map;
    }

    /** 单类型生效开关（无落行按预设默认）。 */
    private boolean isTypeEnabled(Long userId, WearReminderTypeEnum type) {
        if (type == null) {
            return false;
        }
        UserNotificationConfig row = configMapper.selectOne(new LambdaQueryWrapper<UserNotificationConfig>()
                .eq(UserNotificationConfig::getUserId, userId)
                .eq(UserNotificationConfig::getReminderType, type.getCode()));
        return row == null ? type.isDefaultOn() : Boolean.TRUE.equals(row.getEnabled());
    }

    /** 应用预设模板：覆盖全部 8 类开关明细（删旧插新）。 */
    private void applyTemplate(Long userId, NotificationPresetEnum preset) {
        Map<String, Boolean> template = templateOf(preset);
        List<UserNotificationConfig> old = configMapper.selectList(new LambdaQueryWrapper<UserNotificationConfig>()
                .eq(UserNotificationConfig::getUserId, userId));
        for (UserNotificationConfig row : old) {
            configMapper.deleteById(row.getId());
        }
        upsertSwitches(userId, template);
    }

    /** 全量 upsert 8 类开关（唯一键冲突回读幂等）。 */
    private void upsertSwitches(Long userId, Map<String, Boolean> switches) {
        for (Map.Entry<String, Boolean> entry : switches.entrySet()) {
            UserNotificationConfig row = configMapper.selectOne(new LambdaQueryWrapper<UserNotificationConfig>()
                    .eq(UserNotificationConfig::getUserId, userId)
                    .eq(UserNotificationConfig::getReminderType, entry.getKey()));
            if (row != null) {
                row.setEnabled(entry.getValue());
                configMapper.updateById(row);
            } else {
                UserNotificationConfig created = new UserNotificationConfig();
                created.setUserId(userId);
                created.setReminderType(entry.getKey());
                created.setEnabled(entry.getValue());
                try {
                    configMapper.insert(created);
                } catch (DuplicateKeyException e) {
                    UserNotificationConfig existing = configMapper.selectOne(new LambdaQueryWrapper<UserNotificationConfig>()
                            .eq(UserNotificationConfig::getUserId, userId)
                            .eq(UserNotificationConfig::getReminderType, entry.getKey()));
                    if (existing != null) {
                        existing.setEnabled(entry.getValue());
                        configMapper.updateById(existing);
                    }
                }
            }
        }
    }

    /** 预设模板：STANDARD = 各类型默认；IMPORTANT_ONLY = 仅 ALIGNER_CHANGE + WEAR_SETTLE。 */
    private Map<String, Boolean> templateOf(NotificationPresetEnum preset) {
        Map<String, Boolean> map = new LinkedHashMap<>();
        for (WearReminderTypeEnum t : WearReminderTypeEnum.values()) {
            boolean on = preset == NotificationPresetEnum.IMPORTANT_ONLY
                    ? (t == WearReminderTypeEnum.ALIGNER_CHANGE || t == WearReminderTypeEnum.WEAR_SETTLE)
                    : t.isDefaultOn();
            map.put(t.getCode(), on);
        }
        return map;
    }

    /** 全量开关推导预设：与某模板完全一致回该模板，否则 CUSTOM。 */
    private NotificationPresetEnum derivePreset(Map<String, Boolean> full) {
        Map<String, Boolean> eff = new LinkedHashMap<>();
        for (WearReminderTypeEnum t : WearReminderTypeEnum.values()) {
            eff.put(t.getCode(), full.getOrDefault(t.getCode(), t.isDefaultOn()));
        }
        if (eff.equals(templateOf(NotificationPresetEnum.STANDARD))) {
            return NotificationPresetEnum.STANDARD;
        }
        if (eff.equals(templateOf(NotificationPresetEnum.IMPORTANT_ONLY))) {
            return NotificationPresetEnum.IMPORTANT_ONLY;
        }
        return NotificationPresetEnum.CUSTOM;
    }

    /** 懒创建 user_setting 行（唯一键冲突幂等回读）。 */
    private UserSetting ensureSetting(Long userId) {
        UserSetting s = userSettingMapper.selectOne(new LambdaQueryWrapper<UserSetting>()
                .eq(UserSetting::getUserId, userId));
        if (s != null) {
            return s;
        }
        UserSetting created = new UserSetting();
        created.setUserId(userId);
        try {
            userSettingMapper.insert(created);
        } catch (DuplicateKeyException e) {
            // 并发首次创建，另一线程已插入，直接回读
        }
        return userSettingMapper.selectOne(new LambdaQueryWrapper<UserSetting>()
                .eq(UserSetting::getUserId, userId));
    }

    private LocalTime parseDndTime(String hhmm) {
        if (hhmm == null || hhmm.isBlank()) {
            throw new BusinessException(ErrorCode.NOTIFICATION_PARAM_INVALID, "勿扰时间格式应为 HH:mm");
        }
        try {
            return LocalTime.parse(hhmm.trim(), HH_MM);
        } catch (RuntimeException e) {
            throw new BusinessException(ErrorCode.NOTIFICATION_PARAM_INVALID, "勿扰时间格式应为 HH:mm");
        }
    }

    private WearReminderTypeEnum requireType(String code) {
        WearReminderTypeEnum type = WearReminderTypeEnum.getByCode(code);
        if (type == null) {
            throw new BusinessException(ErrorCode.NOTIFICATION_PARAM_INVALID, "提醒类型不存在");
        }
        return type;
    }
}
