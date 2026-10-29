package com.atguigu.tingshu.comment.config;

import com.atguigu.tingshu.common.util.SensitiveWordFilterUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.FileCopyUtils;

import jakarta.annotation.PostConstruct;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 敏感词库装配：应用启动时从 classpath 加载 sensitive-words.txt 构建 DFA。
 * 词库更新后重启生效；后续可改为 Nacos 动态下发（监听配置变更重新 buildDFAModel）。
 */
@Slf4j
@Configuration
public class SensitiveWordConfig {

	@PostConstruct
	public void initSensitiveWordModel() {
		List<String> words = new ArrayList<>();
		try {
			String text = FileCopyUtils.copyToString(new InputStreamReader(
					getClass().getClassLoader().getResourceAsStream("sensitive-words.txt"), StandardCharsets.UTF_8));
			Arrays.stream(text.split("\\r?\\n"))
					.map(String::trim)
					.filter(line -> !line.isEmpty() && !line.startsWith("#"))
					.forEach(words::add);
		} catch (Exception e) {
			log.warn("[评论] 敏感词库文件缺失或读取失败，评论内容将不做过滤", e);
		}
		SensitiveWordFilterUtil.buildDFAModel(words);
		log.info("[评论] 敏感词库加载完成，词条数={}", words.size());
	}
}
