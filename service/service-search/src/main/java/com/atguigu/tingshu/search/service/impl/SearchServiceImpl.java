package com.atguigu.tingshu.search.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.TopHitsAggregate;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.NestedQuery;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.CompletionSuggest;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.Suggestion;
import com.alibaba.fastjson.JSON;
import java.util.ArrayList;
import java.util.List;
import com.atguigu.tingshu.album.client.AlbumInfoFeignClient;
import com.atguigu.tingshu.album.client.CategoryFeignClient;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.common.util.PinYinUtils;
import com.atguigu.tingshu.model.album.*;
import com.atguigu.tingshu.model.search.AlbumInfoIndex;
import com.atguigu.tingshu.model.search.AttributeValueIndex;
import com.atguigu.tingshu.model.search.SuggestIndex;
import com.atguigu.tingshu.query.search.AlbumIndexQuery;
import com.atguigu.tingshu.repository.AlbumInfoIndexRepository;
import com.atguigu.tingshu.repository.SuggestIndexRepository;
import com.atguigu.tingshu.search.service.SearchService;
import com.atguigu.tingshu.user.client.UserInfoFeignClient;
import com.atguigu.tingshu.vo.album.AlbumStatVo;
import com.atguigu.tingshu.vo.search.AlbumInfoIndexVo;
import com.atguigu.tingshu.vo.search.AlbumSearchResponseVo;
import com.atguigu.tingshu.vo.user.UserInfoVo;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.suggest.Completion;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;


@Slf4j
@Service
@SuppressWarnings({"all"})
public class SearchServiceImpl implements SearchService {
    @Autowired
    private AlbumInfoIndexRepository albumInfoIndexRepository;
    @Autowired
    private ThreadPoolExecutor threadPoolExecutor;
    @Autowired
    private AlbumInfoFeignClient albumInfoFeignClient;
    @Autowired
    private CategoryFeignClient categoryFeignClient;
    @Autowired
    private UserInfoFeignClient userInfoFeignClient;
    //使用原生的ElasticSearch的API进行检索
    @Autowired
    private ElasticsearchClient elasticsearchClient;
    @Autowired
    private SuggestIndexRepository suggestIndexRepository;
    @Autowired
    private RedisTemplate redisTemplate;

    @Override
    public List<AlbumInfoIndexVo> findRankingList(Long category1Id, String dimension) {
        //根据一级分类ID和排序字段从Redis中获取对应的排行榜中的专辑数据列表
        // 从Redis中获取数据，由于使用的是JSON序列化，需要先获取原始值
        Object albumObj = redisTemplate.boundHashOps("ranking:" + category1Id).get(dimension);
        List<AlbumInfoIndex> albuminfoList = new ArrayList<>();
        
        // 检查获取到的数据是否为null
        if (albumObj != null) {
            // 如果是字符串类型，说明可能是JSON格式
            if (albumObj instanceof String) {
                // 尝试将JSON字符串解析为List
                try {
                    albuminfoList = JSON.parseArray(albumObj.toString(), AlbumInfoIndex.class);
                } catch (Exception e) {
                    // 解析失败，记录错误日志并返回空列表
                    log.error("解析Redis中的JSON数据失败: " + albumObj.toString(), e);
                }
            } 
            // 如果已经是List类型，直接转换
            else if (albumObj instanceof List) {
                albuminfoList = (List<AlbumInfoIndex>) albumObj;
            }
        }
        //将AlbumInfoIndex类型的列表转换成返回给前端的AlbumInfoIndexVo类型的列表
        List<AlbumInfoIndexVo> albumInfoIndexVoList = albuminfoList.stream().map(albumInfoIndex -> {
            AlbumInfoIndexVo albumInfoIndexVo = new AlbumInfoIndexVo();
            BeanUtils.copyProperties(albumInfoIndex, albumInfoIndexVo);
            return albumInfoIndexVo;
        }).collect(Collectors.toList());
        return albumInfoIndexVoList;
    }

    //更新专辑排行榜
//    GET /albuminfo/_search
//{
//  "query": {
//    "term": {
//      "category1Id": {
//        "value": "1"
//      }
//    }
//  }
//  ,"sort": [
//    {
//      "playStatNum": {
//        "order": "desc"
//      }
//    }
//  ],
//  "size": 10
//}
    @SneakyThrows
    @Override
    public void updateLatelyAlbumRanking() {
        //1.根据远程调用接口查询mysql数据库获取所有一级分类对应的列表
        Result<List<BaseCategory1>> result = categoryFeignClient.findAllCategory1();
        List<BaseCategory1> baseCategory1List = result.getData();
        Assert.notNull(baseCategory1List, "一级分类列表为空");
        //定义排序字段数组
        String[] sortFields = {"hotScore", "playStatNum", "subscribeStatNum", "buyStatNum", "commentStatNum"};
        //2.对一级分类列表进行遍历，获取一级分类然后排序字段数组嵌套循序遍历，构建对应的DSL语句然后在ES中进行查询，得到排序后的专辑列表
        baseCategory1List.stream().forEach(baseCategory1 -> {
            //遍历排序字段，构建DSL语句
            for(String sortField : sortFields) {
                //构建查询的DSL请求语句
                SearchRequest.Builder request = new SearchRequest.Builder();
                request.index("albuminfo")
                        .query(q->q.term(t->t.field("category1Id").value(baseCategory1.getId())))
                        .sort(s->s.field(o->o.field(sortField).order(SortOrder.Desc)))
                        .size(10);
                //根据构建好的DSL语句对ES进行查询
                try {
                    SearchResponse<AlbumInfoIndex> searchResponse = elasticsearchClient.search(request.build(), AlbumInfoIndex.class);
                    //从es中的查询结果中获取对应的专辑列表
                    List<AlbumInfoIndex> albuminfoList = searchResponse.hits().hits().stream().map(hit -> hit.source()).collect(Collectors.toList());
                    //3.将得到的专辑列表放入Redis的Hash中，key为一级分类，filed是排序字段，value是排序后的列表
                    // (像排行榜这种不经常变化访问量大的就需要放到缓存中)
                    //hash中，key为ranking:一级分类，filed为排序字段，value为排序后的专辑列表
                    String key="ranking:" + baseCategory1.getId();
                    redisTemplate.opsForHash().put(key, sortField, JSON.toJSONString(albuminfoList));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }

    //根据输入的关键字返回自动补全的字符串列表
    @SneakyThrows
    @Override
    /*GET /suggestinfo/_search
{
  "suggest": {
    "suggestionKeyword": {
      "prefix": "",
      "completion": {
        "field": "keyword",
        "skip_duplicates": true,
        "size": 10,
        "fuzzy": {
          "fuzziness": "auto"
        }
      }
    },
     "suggestionkeywordPinyin": {
      "prefix": "sangui",
      "completion": {
        "field": "keywordPinyin",
        "skip_duplicates": true,
        "size": 10,
        "fuzzy": {
          "fuzziness": "auto"
        }
      }
    },
     "suggestionkeywordSequence": {
      "prefix": "",
      "completion": {
        "field": "keywordSequence",
        "skip_duplicates": true,
        "size": 10,
        "fuzzy": {
          "fuzziness": "auto"
        }
      }
    }
  }
}*/
    public List<String> completeSuggest(String keyword) {
        //构建查询的DSL请求语句
        SearchRequest.Builder request = new SearchRequest.Builder();
        request.index("suggestinfo")
                .suggest(s->s.suggesters("suggestionKeyword",
                        p->p.prefix(keyword)
                                .completion(f->f.field("keyword")
                                        .skipDuplicates(true)
                                        .size(10)
                                        .fuzzy(fu->fu.fuzziness("auto"))))
                        .suggesters("suggestionkeywordPinyin",
                                p->p.prefix(keyword)
                                .completion(f->f.field("keywordPinyin")
                                        .skipDuplicates(true)
                                        .size(10)
                                        .fuzzy(fu->fu.fuzziness("auto"))))
                        .suggesters("suggestionkeywordSequence",
                                p->p.prefix(keyword)
                                        .completion(f->f.field("keywordSequence")
                                                .skipDuplicates(true)
                                                .size(10)
                                                .fuzzy(fu->fu.fuzziness("auto")))));
        //执行查询
        SearchResponse<SuggestIndex> response = elasticsearchClient.search(request.build(), SuggestIndex.class);
        //将查询结果封装到HashSet中，无序且去重
        HashSet<String> titleSet = new HashSet<>();
        //根据关键词匹配类型封装对应类型的匹配字符串
        titleSet.addAll(this.parseResultData(response, "suggestionKeyword"));
        titleSet.addAll(this.parseResultData(response, "suggestionkeywordPinyin"));
        titleSet.addAll(this.parseResultData(response, "suggestionkeywordSequence"));
        //如果查询Completion没有达到10条匹配，根据title进行普通匹配补全
        if (titleSet.size() < 10) {
            SearchResponse<SuggestIndex> searchResponse = elasticsearchClient.search(s->s.index("suggestinfo")
                    .query(q -> q.match(m -> m.field("title").query(keyword))), SuggestIndex.class);
            for(Hit<SuggestIndex> hit : searchResponse.hits().hits()) {
                //获取数据结果
                SuggestIndex suggestIndex = hit.source();
                //将title添加到Set集合中
                titleSet.add(suggestIndex.getTitle());
                //如果Set集合大小达到10条，则退出循环
                if (titleSet.size() >= 10) {
                    break;
                }
            }
        }
        //将Set集合转换为List集合
        return new ArrayList<>(titleSet);
    }
    //处理查询关键字对应补全字符串结果
    private List<String> parseResultData(SearchResponse<SuggestIndex> response, String suggestionKeyword) {
        // 创建一个String类型的List集合，用于存储建议结果
        List<String> suggestList = new ArrayList<>();
        // 从响应中获取建议结果映射表，键为关键词类型，值为匹配的Suggestion列表
        Map<String, List<Suggestion<SuggestIndex>>> map = response.suggest();
        //根据名称获取值
        List<Suggestion<SuggestIndex>> suggestions = map.get(suggestionKeyword);
        suggestions.forEach(item -> {
            CompletionSuggest<SuggestIndex> completionSuggest =  item.completion();
            completionSuggest.options().forEach(it -> {
                SuggestIndex suggestIndex = it.source();
                suggestList.add(suggestIndex.getTitle());
            });
        });
        //  返回集合列表
        return suggestList;
    }

    /*从 albuminfo 索引中，只取出那些 category3Id 在指定列表中的文档。

    将这些文档按 category3Id 分组（最多 10 组）。

    对每个分组，从中挑选出热度（hotScore）最高的 6 个专辑，并返回这些专辑的完整文档数据。
    GET /albuminfo/_search
    {
      "query": {
        "terms": {字段匹配多个值
          "category3Id": [
            "1001",
            "1002",
            "1003",
            "1004",
            "1005",
            "1006",
            "1007"
          ]
        }
      },
      "aggs": {聚合查询
        "groupByCategory3IdAgg": {
          "terms": {根据字段category3Id进行分组,分十组
            "field": "category3Id",
            "size": 10
          },
          "aggs": {
            "topTenHotScoreAgg": {
              "top_hits": {//找出每组按照热度排序的前六个专辑数据
                "size": 6,
                "sort": [
                  {
                    "hotScore": {
                      "order": "desc"
                    }
                  }
                ]
              }
            }
          }
        }
      }
    1. 先根据一级分类Id获取到三级分类集合数据
    2. 获取到三级分类Id集合
    3. 根据三级分类Id 集合生成根据三级分类Id查询专辑数据
    4. 根据三级分类Id聚合并获取到前6条数据并按照热度排名进行降序排列
    5. 从聚合中获取到数据并封装到返回数据的集合中
    }*/
    @SneakyThrows
    @Override
    public List<Map<String, Object>> channel(Long category1Id) {
        //1.根据一级分类ID获取到置顶到频道页7个三级分类专辑数据集合
        Result<List<BaseCategory3>> topBaseCategory3 = categoryFeignClient.findTopBaseCategory3(category1Id);
        List<BaseCategory3> baseCategory3List = topBaseCategory3.getData();
        Assert.notNull(baseCategory3List, "一级分类下没有置顶频道页的数据");
        //建立map，key是三级分类ID，value是三级分类对象
        Map<Long, BaseCategory3> category3Map = baseCategory3List.stream().collect(Collectors.toMap(BaseCategory3::getId, baseCategory3 -> baseCategory3));
        //获取到三级分类ID集合
        List<Long> category3IdList = baseCategory3List.stream().map(BaseCategory3::getId).collect(Collectors.toList());
        //将三级分类ID集合转换成List<FieldValue>对象
        List<FieldValue> fieldValueList = category3IdList.stream().map(id->FieldValue.of(id)).collect(Collectors.toList());
        //2.编写对应的DSL语句查询es
        SearchRequest.Builder request = new SearchRequest.Builder();
        //指定category3Id匹配多个值，为前面查询到的7个三级分类ID
        request.index("albuminfo").query(q->q.terms(t1->t1.field("category3Id").terms(t2->t2.value(fieldValueList))));
        //3.指定聚合查询，根据category3Id进行分组，最多分10组，每组取热度最高的前6个三级分类数据
        request.aggregations("groupByCategory3IdAgg", a1->a1.terms(t1->t1.field("category3Id").size(10))
                .aggregations("topTenHotScoreAgg",
                        a2->a2.topHits(t1->t1.size(6).sort(s1->s1.field(f1->f1.field("hotScore").order(SortOrder.Desc))))));
        //3.执行查询
        SearchResponse<AlbumInfoIndex> response = elasticsearchClient.search(request.build(), AlbumInfoIndex.class);
        //4.解析查询结果,封装到map集合中
        /*示例数据"aggregations": {
    "groupByCategory3IdAgg": {
      "doc_count_error_upper_bound": 0,
      "sum_other_doc_count": 0,
      "buckets": [
        {
          "key": 1001,
          "doc_count": 1,
          "topTenHotScoreAgg": {
            "hits": {
              "total": {
                "value": 1,
                "relation": "eq"
              },
              "max_score": null,
              "hits": [
                {
                  "_index": "albuminfo",
                  "_id": "1001",
                  "_score": null,
                  "_source": {
                    "_class": "com.atguigu.tingshu.model.search.AlbumInfoIndex",
                    "id": 1001,
                    "albumTitle": "德云社丙申年封箱庆典 2017",
                    "albumIntro": "本专辑为喜马拉雅【音频独家】节目德云社丙申年封箱庆典 2017爆笑上线！为您带来《找搭档》《下象棋》《...",
                    "announcerName": "方源",
                    "coverUrl": "https://imagev2.xmcdn.com/group71/M03/D4/36/wKgOz14gCsGjRolkAAPL13Gd8aM385.jpg",
                    "includeTrackCount": 10,
                    "payType": "0101",
                    "createTime": "2023-03-03T15:32:35.000Z",
                    "category1Id": 1,
                    "category2Id": 101,
                    "category3Id": 1001,
                    "playStatNum": 0,
                    "subscribeStatNum": 0,
                    "buyStatNum": 0,
                    "commentStatNum": 0,
                    "hotScore": 0,
                    "attributeValueIndexList": []
                  },
                  "sort": [
                    0
                  ]
                }
              ]
            }
          }
        },*/
        //声明List集合，每个元素是一个map,第一个键值对是baseCategory3：指定三级分类ID的三级分类的数据，第二个键值对是list：该三级分类下的专辑数据集合
        List<Map<String,Object>> resultList = new ArrayList<>();
        //获取到聚合查询结果,得到七组三级分类及其分类下7个专辑数据的聚合结果
        Aggregate groupByCategory3IdAgg = response.aggregations().get("groupByCategory3IdAgg");
        //遍历分组后的三级分类集合，对每一组进行数据提取
        groupByCategory3IdAgg.lterms().buckets().array().forEach(item->{
            //创建存储三级分类下专辑数据的集合
            List<AlbumInfoIndex> albumInfoIndexList = new ArrayList<>();
            //获取三级分类ID
            Long category3Id = item.key();
            //获取该组下的置顶的7个专辑数据的集合,遍历每个专辑数据对象
            item.aggregations().get("topTenHotScoreAgg").topHits().hits().hits().forEach(hit->{
                //将专辑数据转换成JSON字符串
                String albuminfo = hit.source().toString();
                //将专辑数据对应的JSON字符串解析封装到AlbumInfoIndex对象中
                AlbumInfoIndex albumInfoIndex = JSON.parseObject(albuminfo, AlbumInfoIndex.class);
                //将该专辑数据添加到该三级分类下的集合中
                albumInfoIndexList.add(albumInfoIndex);
            });
            //创建map集合，存储三级分类下的专辑数据集合
            Map<String,Object> map = new HashMap<>();
            //存储三级分类的数据
            map.put("baseCategory3",category3Map.get(category3Id));
            //存储该三级分类下的专辑数据集合
            map.put("list",albumInfoIndexList);
            //将map集合添加到List集合中
            resultList.add(map);
        });
        //返回封装好的数据集合，用于前端首页显示一级分类下的前七个置顶三级分类名称和每个三级分类下的6个专辑信息
        return resultList;
    }
    //搜索框进行专辑检索
    /**
     * 根据前端传入的关键词和其他查询条件进行检索
     * @param albumIndexQuery
     * @return
     */
    @Override
    @SneakyThrows
    public AlbumSearchResponseVo search(AlbumIndexQuery albumIndexQuery) {
        //根据前端传入的检索条件构建标志DSL语句的SearchRequest对象
        SearchRequest request=this.buildQueryDSL(albumIndexQuery);
        //接收从ES中查询的结果
        SearchResponse<AlbumInfoIndex> response = null;
        //指定检索的条件和返回检索结果对应的封装类进行检索，然后接收查询结果
        response= elasticsearchClient.search(request, AlbumInfoIndex.class);
        //对查询结果进行解析,并设置每页显示数量以及当前页
        AlbumSearchResponseVo albumSearchResponseVo=this.parseSearchResult(response);
        albumSearchResponseVo.setPageSize(albumIndexQuery.getPageSize());
        albumSearchResponseVo.setPageNo(albumIndexQuery.getPageNo());
        //获取总页数,总页数=(总记录数+每页显示数-1)/每页显示数
        albumSearchResponseVo.setTotalPages((albumSearchResponseVo.getTotal()+albumSearchResponseVo.getPageSize()-1)/albumSearchResponseVo.getPageSize());
        return albumSearchResponseVo;
    }
    //ES查询后的结果封装到SearchResponse对象中，解析成AlbumSearchResponseVo对象
    private AlbumSearchResponseVo parseSearchResult(SearchResponse<AlbumInfoIndex> response) {
        AlbumSearchResponseVo albumSearchResponseVo=new AlbumSearchResponseVo();
        //获取查询结果中的总记录数
        albumSearchResponseVo.setTotal(response.hits().total().value());
        //获取查询结果中的数据列表
        List<Hit<AlbumInfoIndex>> hits = response.hits().hits();
        if (!CollectionUtils.isEmpty(hits)) {
            //将hits中的数据封装到AlbumInfoIndexVo对象中
            List<AlbumInfoIndexVo> albumInfoIndexVoList = hits.stream().map(hit -> {
                AlbumInfoIndexVo albumInfoIndexVo = new AlbumInfoIndexVo();
                //将hit中的数据封装到AlbumInfoIndexVo对象中
                BeanUtils.copyProperties(hit.source(), albumInfoIndexVo);
                //判断用户是否根据关键词进行检索
                if (hit.highlight() != null && hit.highlight().get("albumTitle") != null && 
                    !hit.highlight().get("albumTitle").isEmpty() && 
                    StringUtils.hasText(hit.highlight().get("albumTitle").get(0))) {
                    //获取高亮显示的标题
                    String albumTitle = hit.highlight().get("albumTitle").get(0);
                    //将高亮显示的标题设置到AlbumInfoIndexVo对象中
                    albumInfoIndexVo.setAlbumTitle(albumTitle);
                }
                return albumInfoIndexVo;
            }).collect(Collectors.toList());
            albumSearchResponseVo.setList(albumInfoIndexVoList);
        }
        return albumSearchResponseVo;
    }
    //根据前端传入的检索条件构建标志DSL语句的SearchRequest对象
    private SearchRequest buildQueryDSL(AlbumIndexQuery albumIndexQuery) {
        //创建用于ES进行检索的SearchRequest对象,构建者模式构建SearchRequest对象，形式是SearchRequest.Builder()......build()
        SearchRequest.Builder requestBuilder = new SearchRequest.Builder();
        //通过SearchRequest对象构建dsl语句
        //创建BoolQuery对象
        BoolQuery.Builder boolQuery = new BoolQuery.Builder();
        //将关键词跟专辑标题和介绍进行匹配
        /*"query": {
    "bool": {
      "should": [
        {
          "match": {
            "albumTitle": "小说"
          }
        },
        {
          "match": {
            "albumIntro": "小说"
          }
        }
      ],
      "filter": [
        {
          "term": {
            "category3Id": "1152"
          }
        }
      ]
    }*/
        //获取关键词
        String keyword = albumIndexQuery.getKeyword();
        if (StringUtils.hasText(keyword)) {
            boolQuery.should(m1 -> m1.match(f1 -> f1.field("albumTitle").query(keyword)));
            boolQuery.should(m2 -> m2.match(f2 -> f2.field("albumIntro").query(keyword)));
            /*"highlight": {
            "fields": {"albumTitle": {}},
            "pre_tags": ["<font color='red'>"],
            "post_tags": ["</font>"]*/
            //设置高亮
            requestBuilder.highlight(h -> h.fields("albumTitle", f -> f.preTags("<span style=color:red>").postTags("</span>")));
        }
        //根据分类ID进行过滤
        Long category3Id = albumIndexQuery.getCategory3Id();
        if (category3Id != null) {
            boolQuery.filter(f -> f.term(t -> t.field("category3Id").value(category3Id)));
        }
        Long category1Id = albumIndexQuery.getCategory1Id();
        if (category1Id != null) {
            boolQuery.filter(f -> f.term(t -> t.field("category1Id").value(category1Id)));
        }
        Long category2Id = albumIndexQuery.getCategory2Id();
        if (category2Id != null) {
            boolQuery.filter(f -> f.term(t -> t.field("category2Id").value(category2Id)));
        }
        //根据标签的ID和属性值ID进行筛选，标签ID:标签值ID
        /*"query": {
    "bool": {
      "filter": [
        {
          "nested": {
            "path": "attributeValueIndexList",
            "query": {
              "bool": {
                "must": [
                  {
                    "term": {
                      "attributeValueIndexList.attributeId": {
                        "value": "15"
                      }
                    }
                  },
                  {
                    "term": {
                      "attributeValueIndexList.valueId": {
                        "value": "32"
                      }
                    }
                  }
                ]
              }
            }*/
        List<String> attributeList = albumIndexQuery.getAttributeList();
        if (!CollectionUtils.isEmpty(attributeList)) {
            attributeList.stream().forEach(attribute -> {
                String[] split = attribute.split(":");
                if (split != null && split.length == 2) {
                    String attributeId = split[0];
                    String attributeValueId = split[1];
                    NestedQuery nestedQuery = NestedQuery.of(n -> n.path("attributeValueIndexList").query(b -> b.bool(m -> m.must(
                            t -> t.term(f -> f.field("attributeValueIndexList.attributeId").value(attributeId))
                    ).must(t -> t.term(f -> f.field("attributeValueIndexList.valueId").value(attributeValueId))))));
                    boolQuery.filter(f -> f.nested(nestedQuery));
                }
            });
        }
        //排序，（综合排序[1:desc] 播放量[2:desc] 发布时间[3:desc]；asc:升序 desc:降序）
        String order = albumIndexQuery.getOrder();
        if (StringUtils.hasText(order)) {
            String[] split = order.split(":");
            //定义排序字段
            String orderField = "";
            //定义排序规则
            String sort = "";
            if (split != null && split.length == 2) {
                switch (split[0]) {
                    case "1":
                        orderField = "hotScore";
                        break;
                    case "2":
                        orderField = "playStatNum";
                        break;
                    case "3":
                        orderField = "publishTime";
                        break;
                }
                sort = split[1];
                orderField=split[0];
                //设置排序dsl语句
            /*"sort": [
            {
              "playStatNum": {
                "order": "desc"
              }
            }
          ],*/
                //排序需要需要两个filed
                String finalOrderField = orderField;
                String finalSort = sort;
                requestBuilder.sort(s->s.field(f->f.field(finalOrderField).order(SortOrder.valueOf(finalSort.toUpperCase()))));
            }else{
                //按照默认排序方式
                requestBuilder.sort(s->s.field(f->f.field("hotScore").order(SortOrder.valueOf("DESC"))));
            }
        }
        //指定不返回字段
        requestBuilder.source(s->s.filter(f->f.excludes("attributeValueIndexList")));
        //指定分页条件
               /*"query": {
           "match": {
             "albumTitle": "小说"
           }
         },
         "from": 0,
         "size": 20,*/
        //计算分页从哪里开始,如果是从第二页开始，就需要算第二页的起始位置
        Integer from = (albumIndexQuery.getPageNo() - 1) * albumIndexQuery.getPageSize();
        //指定返回的检索结果从哪里开始显示
        requestBuilder.from(from);
        //指定返回的检索结果显示的数据量
        requestBuilder.size(albumIndexQuery.getPageSize());
        //指定索引库和组合查询条件,对哪个索引库进行检索
        requestBuilder.index("albuminfo").query(f->f.bool(boolQuery.build()));
        //requestBuilder进行build返回SearchRequest对象
        SearchRequest searchRequest = requestBuilder.build();
        log.info("searchRequest:{}", searchRequest);
        return searchRequest;
    }

    //下架专辑
    @Override
    public void lowerAlbum(Long albumId) {
        //根据专辑ID删除索引库中的数据
        albumInfoIndexRepository.deleteById(albumId);
    }
    //上架专辑到ES中
    @Override
    public void upperAlbum(Long albumId) {
        //创建es对应的索引对象,用于封装从mysql中查询的数据
        AlbumInfoIndex albumInfoIndex = new AlbumInfoIndex();
        //CompletableFuture结合线程池实现并发多任务执行
        //1.根据专辑ID获取专辑基本信息
        CompletableFuture<AlbumInfo> future1 = CompletableFuture.supplyAsync(() -> {
            //调用albumInfoFeignClient中的方法就是调用对应微服务器的接口从而获取专辑信息
            Result<AlbumInfo> result = albumInfoFeignClient.getAlbumInfo(albumId);
            AlbumInfo albumInfoData = result.getData();
            //查询得到的数据后续要用就得先判空
            Assert.notNull(albumInfoData, "专辑信息查询失败");
            //将专辑信息封装到es对应的索引对象中
            BeanUtils.copyProperties(albumInfoData, albumInfoIndex);
            //因为查询分类信息和查询用户信息需要用到该任务的结果，所以需要返回专辑信息给别的线程的任务
            return albumInfoData;
        }, threadPoolExecutor);//指定该线程从自定义线程池中获取
        //2.获取分类信息,在专辑信息任务线程后执行，接收返回结果并且异步执行
        CompletableFuture<Void> future2 = future1.thenAcceptAsync(albumInfo -> {
            //获取三级分类ID
            Long category3Id = albumInfo.getCategory3Id();
            //根据三级分类ID查询分类信息
            Result<BaseCategoryView> result = categoryFeignClient.getCategoryView(category3Id);
            BaseCategoryView categoryData = result.getData();
            Assert.notNull(categoryData, "分类信息查询失败");
            //将分类信息封装到es对应的索引对象中,注意这里需要排除id字段，因为albumInfoIndex的ID应该是专辑ID，在第一个线程中已经被赋值，这里的categoryData的ID是第三级分类ID
            BeanUtils.copyProperties(categoryData, albumInfoIndex, "id");
        }, threadPoolExecutor);
        //3.获取用户信息，在专辑信息任务线程后执行，接收返回结果并且异步执行
        CompletableFuture<Void> future3 = future1.thenAcceptAsync(albumInfo -> {
            //先从专辑信息中获取用户ID
            Long userId = albumInfo.getUserId();
            log.info("userid:{}",userId);
            //根据用户ID查询用户信息
            Result<UserInfoVo> result = userInfoFeignClient.getUserInfoVo(userId);
            UserInfoVo userInfoData = result.getData();
            Assert.notNull(userInfoData, "用户信息查询失败");
            //将用户信息封装到es对应的索引对象中
            albumInfoIndex.setAnnouncerName(userInfoData.getNickname());
        }, threadPoolExecutor);
        //4.获取专辑对应的标签数据,执行异步任务
        CompletableFuture<Void> future4 = CompletableFuture.runAsync(() -> {
            //根据专辑ID查询专辑对应的标签数据列表
            Result<List<AlbumAttributeValue>> result = albumInfoFeignClient.findAlbumAttributeValue(albumId);
            Assert.notNull(result, "专辑标签信息查询失败");
            List<AlbumAttributeValue> albumAttributeValueList = result.getData();
            //将AlbumAttributeValue类型的列表转换成AlbumAttributeValueIndex类型的列表
            List<AttributeValueIndex> attributeValueIndexList = albumAttributeValueList.stream().map(albumAttributeValue -> {
                AttributeValueIndex attributeValueIndex = new AttributeValueIndex();
                BeanUtils.copyProperties(albumAttributeValue, attributeValueIndex);
                return attributeValueIndex;
            }).collect(Collectors.toList());
            //将专辑对应的标签数据封装到es对应的索引对象中
            albumInfoIndex.setAttributeValueIndexList(attributeValueIndexList);
        }, threadPoolExecutor);
        //5.获取专辑的统计数据,执行异步任务
        CompletableFuture<Void> future5 = CompletableFuture.runAsync(() -> {
            //根据专辑ID查询专辑的统计数据
            Result<AlbumStatVo> result = albumInfoFeignClient.getAlbumInfoStat(albumId);
            AlbumStatVo albumStatVo =  result.getData();
            Assert.notNull(albumStatVo, "专辑统计信息查询失败");
            //将专辑的统计数据封装到es对应的索引对象中
            albumInfoIndex.setPlayStatNum((Integer) albumStatVo.getPlayStatNum());
            albumInfoIndex.setSubscribeStatNum((Integer) albumStatVo.getSubscribeStatNum());
            albumInfoIndex.setBuyStatNum((Integer) albumStatVo.getBuyStatNum());
            albumInfoIndex.setCommentStatNum((Integer) albumStatVo.getCommentStatNum());
        }, threadPoolExecutor);
        //等待上面的线程中任务执行完毕进行汇总
        CompletableFuture.allOf(future1, future2, future3,future4,future5).join();
        //将封装好的数据保存到es中
        albumInfoIndexRepository.save(albumInfoIndex);
        //添加数据到suggestinfo索引库，后面实现自动补全
        SuggestIndex suggestIndex = new SuggestIndex();
        //id
        suggestIndex.setId(UUID.randomUUID().toString().replaceAll("-",""));
        //专辑标题
        suggestIndex.setTitle(albumInfoIndex.getAlbumTitle());
        //标题中文
        suggestIndex.setKeyword(new Completion(new String[]{albumInfoIndex.getAlbumTitle()}));
        //中文的完整汉语拼音，比如xiaoshuo
        suggestIndex.setKeywordPinyin(new Completion(new String[]{PinYinUtils.toHanyuPinyin(albumInfoIndex.getAlbumTitle())}));
        //中文的汉语拼音首字母，比如xs
        suggestIndex.setKeywordSequence(new Completion(new String[]{PinYinUtils.getFirstLetter(albumInfoIndex.getAlbumTitle())}));
        //保存专辑标题对应的中文、拼音、拼音缩写到索引库中
        this.suggestIndexRepository.save(suggestIndex);
    }
}
