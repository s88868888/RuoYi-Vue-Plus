package org.dromara.resource.service.agent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 模板填充Agent
 * 负责将公司信息填充到模板中的占位符
 *
 * @author ruoyi
 * @date 2026-02-26
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TemplateFillAgent {

    /**
     * 填充模板
     *
     * @param template 模板内容（包含占位符）
     * @param companyInfo 公司信息（占位符->值的映射）
     * @return 填充后的内容
     */
    public String fillTemplate(String template, Map<String, String> companyInfo) {
        log.debug("开始填充模板，占位符数量: {}", companyInfo.size());

        if (template == null || template.isEmpty()) {
            return "";
        }

        String result = template;

        // 替换所有占位符
        for (Map.Entry<String, String> entry : companyInfo.entrySet()) {
            String placeholder = entry.getKey();
            String value = entry.getValue() != null ? entry.getValue() : "";

            // 支持多种占位符格式
            // 格式1: {{company_name}}
            result = result.replace(placeholder, value);

            // 格式2: {company_name}
            String simplePlaceholder = placeholder.replace("{{", "{").replace("}}", "}");
            result = result.replace(simplePlaceholder, value);

            // 格式3: 下划线格式 ________
            // 如果占位符后面跟着下划线，替换下划线
            result = replaceUnderlineFormat(result, placeholder, value);
        }

        log.debug("模板填充完成");
        return result;
    }

    /**
     * 替换下划线格式的占位符
     * 例如：公司名称：________ -> 公司名称：某某科技有限公司
     */
    private String replaceUnderlineFormat(String content, String placeholder, String value) {
        // 提取占位符的中文描述
        String fieldDesc = getFieldDescription(placeholder);

        if (fieldDesc != null) {
            // 匹配 "字段描述：____" 格式
            Pattern pattern = Pattern.compile(fieldDesc + "[：:][_\\s]{2,}");
            Matcher matcher = pattern.matcher(content);

            if (matcher.find()) {
                content = matcher.replaceAll(fieldDesc + "：" + value);
            }
        }

        return content;
    }

    /**
     * 获取字段的中文描述
     */
    private String getFieldDescription(String placeholder) {
        Map<String, String> descMap = Map.of(
            "{{company_name}}", "公司名称",
            "{{register_capital}}", "注册资本",
            "{{establish_date}}", "成立日期",
            "{{legal_person}}", "法定代表人",
            "{{business_scope}}", "经营范围",
            "{{company_address}}", "公司地址",
            "{{contact_phone}}", "联系电话",
            "{{employee_count}}", "员工人数"
        );

        return descMap.get(placeholder);
    }

    /**
     * 批量填充多个模板
     */
    public Map<String, String> batchFill(
        Map<String, String> templates,
        Map<String, String> companyInfo) {

        Map<String, String> result = new java.util.HashMap<>();

        for (Map.Entry<String, String> entry : templates.entrySet()) {
            String key = entry.getKey();
            String template = entry.getValue();
            String filled = fillTemplate(template, companyInfo);
            result.put(key, filled);
        }

        return result;
    }

    /**
     * 检查模板中的占位符是否都已填充
     */
    public boolean isAllPlaceholdersFilled(String content) {
        // 检查是否还有未填充的占位符
        Pattern pattern = Pattern.compile("\\{\\{[^}]+\\}\\}");
        Matcher matcher = pattern.matcher(content);
        return !matcher.find();
    }

    /**
     * 提取模板中的所有占位符
     */
    public java.util.List<String> extractPlaceholders(String template) {
        java.util.List<String> placeholders = new java.util.ArrayList<>();

        Pattern pattern = Pattern.compile("\\{\\{([^}]+)\\}\\}");
        Matcher matcher = pattern.matcher(template);

        while (matcher.find()) {
            placeholders.add(matcher.group(0)); // 包含{{}}
        }

        return placeholders;
    }

}
