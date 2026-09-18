package com.chiji.framework.weather;

/**
 * 首页天气展示数据。
 * <p>
 * 仅承载展示所需的最小字段，不含任何坐标（隐私：模糊定位坐标不落地、不回传）。
 *
 * @param city      城市（和风坐标反查 adm2，如「合肥市」）
 * @param district  区县（和风坐标反查 name，如「包河区」）
 * @param text      天气文案（如「晴」）
 * @param temp      温度（摄氏度字符串，如「26」）
 * @param icon      和风 icon code（对应前端 assets/weather/{icon}.svg）
 * @param updatedAt 数据获取时间（yyyy-MM-dd HH:mm:ss；命中缓存时为写入缓存的时刻）
 */
public record WeatherVO(String city, String district, String text, String temp, String icon, String updatedAt) {
}
