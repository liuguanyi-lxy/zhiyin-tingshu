package com.atguigu.tingshu.repository;

import com.atguigu.tingshu.model.search.SuggestIndex;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

/**
 * SuggestIndexRepository 接口，用于提供与 Elasticsearch 交互的仓库方法
 * 该接口继承自 ElasticsearchRepository，实现了对索引库的基本CRUD 操作
 *
 */
public interface SuggestIndexRepository extends ElasticsearchRepository<SuggestIndex, String> {
}
