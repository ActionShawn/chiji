package com.chiji.module.clinic.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chiji.entity.ClinicVisit;
import com.chiji.entity.User;
import com.chiji.module.auth.mapper.UserMapper;
import com.chiji.module.clinic.mapper.ClinicVisitMapper;
import com.chiji.module.clinic.service.ClinicReminderService;
import com.chiji.module.clinic.service.strategy.BookingRemindStrategy;
import com.chiji.enums.ClinicVisitStatusEnum;
import com.chiji.enums.WearReminderTypeEnum;
import com.chiji.module.message.service.NotificationSettingService;
import com.chiji.module.message.service.ReminderNotifyService;
import com.chiji.module.message.service.SubscribeQuotaService;
import com.chiji.framework.wechat.WxSubscribeClient;
import com.chiji.module.wear.support.WearTimes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 小齿档案提醒扫描实现。
 * <p>
 * 去重三层：① notify 的 (user,type,sceneDate) 唯一（同一天重复扫描不重发）；
 * ② 就诊提醒取「提醒日最早」的一条 PLANNED；③ 预约提醒以窗口锚点日为 sceneDate，
 * 换副开新周期自然产生新 sceneDate（每阶段一次）。
 * 订阅消息仅额度 &gt; 0 时下发（沿用 byte[] 防 412 链路），失败仅记日志、站内照发。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClinicReminderServiceImpl implements ClinicReminderService {

    private static final DateTimeFormatter CN_DATE = DateTimeFormatter.ofPattern("M月d日");
    /** 就诊提醒扫描回看天数上限（提前天数 0~3） */
    private static final int VISIT_LOOKAHEAD_DAYS = 3;

    private final ClinicVisitMapper visitMapper;
    private final UserMapper userMapper;
    private final NotificationSettingService notificationSettingService;
    private final ReminderNotifyService reminderNotifyService;
    private final SubscribeQuotaService subscribeQuotaService;
    private final WxSubscribeClient wxSubscribeClient;
    private final List<BookingRemindStrategy> bookingStrategies;

    @Override
    public boolean visitRemind(Long userId) {
        if (!notificationSettingService.isReminderAllowed(userId, WearReminderTypeEnum.CLINIC_VISIT_REMIND)) {
            return false;
        }
        LocalDate today = WearTimes.today();
        // 拉未来 0~3 天内的 PLANNED，内存按「visit_date − 提前天数 == 今天」过滤
        // （per-visit remind_offset_days 优先，null 用用户默认配置；避免对 DATE 列做函数运算）
        List<ClinicVisit> candidates = visitMapper.selectList(new LambdaQueryWrapper<ClinicVisit>()
                .eq(ClinicVisit::getUserId, userId)
                .eq(ClinicVisit::getStatus, ClinicVisitStatusEnum.PLANNED.name())
                .ge(ClinicVisit::getVisitDate, today)
                .le(ClinicVisit::getVisitDate, today.plusDays(VISIT_LOOKAHEAD_DAYS)));
        int defaultOffset = notificationSettingService.clinicVisitOffset(userId);
        ClinicVisit hit = candidates.stream()
                .filter(v -> today.equals(v.getVisitDate().minusDays(resolveOffset(v, defaultOffset))))
                .min(java.util.Comparator.comparing(ClinicVisit::getVisitDate))
                .orElse(null);
        if (hit == null) {
            return false;
        }
        String when = hit.getVisitDate().equals(today) ? "今天" : hit.getVisitDate().format(CN_DATE);
        String title = "复诊提醒：约了" + when + "复诊 🦷";
        StringBuilder body = new StringBuilder("别忘记")
                .append(hit.getVisitDate().equals(today) ? "今天" : when)
                .append("的复诊行程");
        if (hit.getClinicName() != null) {
            body.append("，地点：").append(hit.getClinicName());
        }
        if (hit.getDoctorName() != null) {
            body.append("（").append(hit.getDoctorName()).append("医生）");
        }
        body.append("。完成后记得回来补录就诊记录～");

        boolean stored = reminderNotifyService.notify(
                userId, WearReminderTypeEnum.CLINIC_VISIT_REMIND, hit.getVisitDate(), title, body.toString());
        if (stored) {
            pushClinicVisit(userId, hit);
        }
        return stored;
    }

    @Override
    public boolean bookRemind(Long userId) {
        if (!notificationSettingService.isReminderAllowed(userId, WearReminderTypeEnum.CLINIC_BOOK_REMIND)) {
            return false;
        }
        int offset = notificationSettingService.clinicBookOffset(userId);
        LocalDate remindDate = bookingStrategies.stream()
                .filter(s -> s.supports(currentTreatmentType(userId)))
                .findFirst()
                .map(s -> s.computeRemindDate(userId, offset))
                .orElse(null);
        if (remindDate == null || !WearTimes.today().equals(remindDate)) {
            return false;
        }
        // 去重边界：已存在未来 PLANNED 日程说明预约已落实，不再催
        Long planned = visitMapper.selectCount(new LambdaQueryWrapper<ClinicVisit>()
                .eq(ClinicVisit::getUserId, userId)
                .eq(ClinicVisit::getStatus, ClinicVisitStatusEnum.PLANNED.name())
                .ge(ClinicVisit::getVisitDate, WearTimes.today()));
        if (planned != null && planned > 0) {
            return false;
        }
        boolean stored = reminderNotifyService.notify(
                userId, WearReminderTypeEnum.CLINIC_BOOK_REMIND, remindDate,
                "最后一副快戴完啦，记得预约复诊 📅",
                "最后一副牙套预计 " + remindDate.plusDays(offset).format(CN_DATE)
                        + " 戴完，记得提前预约复诊，确认进度并准备保持器。约好时间后到小齿档案记下日程吧～");
        if (stored) {
            pushClinicBook(userId, remindDate, offset);
        }
        return stored;
    }

    // ───────────────────────── 订阅消息下发 ─────────────────────────

    /**
     * 就诊提醒订阅消息：额度 &gt; 0 才发，成功扣额度并回写推送状态；失败仅留站内。
     */
    private void pushClinicVisit(Long userId, ClinicVisit visit) {
        if (!wxSubscribeClient.isClinicVisitEnabled()) {
            return;
        }
        String scene = WearReminderTypeEnum.CLINIC_VISIT_REMIND.getCode();
        if (subscribeQuotaService.remain(userId, scene) <= 0) {
            return;
        }
        String openid = openidOf(userId);
        if (openid == null) {
            return;
        }
        // 模板 571「日程提醒」关键词：thing2 提醒内容 / date4 日程时间 / thing10 地点（就诊·预约两场景共用）
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("thing2", Map.of("value", truncate("复诊提醒", 20)));
        data.put("date4", Map.of("value", visit.getVisitDate().format(DateTimeFormatter.ofPattern("yyyy年M月d日"))));
        String place = visit.getClinicName() == null ? "线上预留" : visit.getClinicName();
        if (visit.getDoctorName() != null) {
            place += " · " + visit.getDoctorName();
        }
        data.put("thing10", Map.of("value", truncate(place, 20)));
        if (!wxSubscribeClient.sendClinicVisitMessage(openid, data)) {
            return;
        }
        subscribeQuotaService.consumeIfAvailable(userId, scene);
        reminderNotifyService.markPushed(userId, WearReminderTypeEnum.CLINIC_VISIT_REMIND, visit.getVisitDate());
    }

    /**
     * 预约提醒订阅消息：额度 &gt; 0 才发，成功扣额度并回写推送状态；失败仅留站内。
     */
    private void pushClinicBook(Long userId, LocalDate remindDate, int offset) {
        if (!wxSubscribeClient.isClinicBookEnabled()) {
            return;
        }
        String scene = WearReminderTypeEnum.CLINIC_BOOK_REMIND.getCode();
        if (subscribeQuotaService.remain(userId, scene) <= 0) {
            return;
        }
        String openid = openidOf(userId);
        if (openid == null) {
            return;
        }
        // 模板 571「日程提醒」关键词：thing2 提醒内容 / date4 日程时间（预计戴完日）/ thing11 备注（建议语）
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("thing2", Map.of("value", truncate("最后一副临近戴完，记得预约复诊", 20)));
        data.put("date4", Map.of("value",
                remindDate.plusDays(offset).format(DateTimeFormatter.ofPattern("yyyy年M月d日"))));
        data.put("thing11", Map.of("value", truncate("确认进度并准备保持器", 20)));
        if (!wxSubscribeClient.sendClinicBookMessage(openid, data)) {
            return;
        }
        subscribeQuotaService.consumeIfAvailable(userId, scene);
        reminderNotifyService.markPushed(userId, WearReminderTypeEnum.CLINIC_BOOK_REMIND, remindDate);
    }

    /** 用户 openid（查不到或为空返回 null）。 */
    private String openidOf(Long userId) {
        User user = userMapper.selectById(userId);
        String openid = user == null ? null : user.getOpenid();
        return openid == null || openid.isBlank() ? null : openid;
    }

    /** 当前用户治疗类型（查不到用户返回 null，策略全部 miss）。 */
    private String currentTreatmentType(Long userId) {
        User user = userMapper.selectById(userId);
        return user == null ? null : user.getTreatmentType();
    }

    /** 单条日程的提醒提前量：per-visit 配置优先，null 回退用户默认。 */
    private int resolveOffset(ClinicVisit visit, int defaultOffset) {
        Integer per = visit.getRemindOffsetDays();
        return per != null && per >= 0 && per <= 3 ? per : defaultOffset;
    }

    /** 订阅消息 thing 类关键词截断（微信限 20 字符）。 */
    private String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
