package com.atguigu.tingshu.repository;

import com.atguigu.tingshu.model.search.AlbumInfoIndex;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Component;

@Component
//ElasticsearchRepository<实体类,实体类主键类型>
public interface AlbumInfoIndexRepository extends ElasticsearchRepository<AlbumInfoIndex, Long> {
}
