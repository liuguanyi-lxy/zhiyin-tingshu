package com.atguigu.tingshu.model.search;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.CompletionField;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.core.suggest.Completion;

/**
 * 自动生成getter/setter的注解
 */
@Data
/**
 * 用于指定Elasticsearch中文档的索引名称为"suggestinfo"
 */
@Document(indexName = "suggestinfo")
/**
 * 用于JSON序列化/反序列化时忽略未知属性，防止因未识别字段导致的错误
 */
@JsonIgnoreProperties(ignoreUnknown = true)//目的：防止json字符串转成实体对象时因未识别字段报错
public class SuggestIndex {

    /**
     * Elasticsearch文档的唯一标识符
     */
    @Id
    private String id;

    /**
     * Elasticsearch中文本类型的字段，使用"standard"分析器进行分词
     */
    @Field(type = FieldType.Text, analyzer = "standard")
    private String title;

    /**
     * Elasticsearch中的自动补全字段，使用"standard"分析器，最大输入长度限制为20
     */
    @CompletionField(analyzer = "standard", searchAnalyzer = "standard", maxInputLength = 20)
    private Completion keyword;

    /**
     * Elasticsearch中的拼音自动补全字段，使用"standard"分析器，最大输入长度限制为20
     */
    @CompletionField(analyzer = "standard", searchAnalyzer = "standard", maxInputLength = 20)
    private Completion keywordPinyin;

    /**
     * Elasticsearch中的拼音缩写自动补全字段，使用"standard"分析器，最大输入长度限制为20
     */
    @CompletionField(analyzer = "standard", searchAnalyzer = "standard", maxInputLength = 20)
    private Completion keywordSequence;

}
