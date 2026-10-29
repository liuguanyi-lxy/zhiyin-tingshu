-- 【压测配套】把专辑数据凑到 10 万级，用于验证简历中"10 万级曲库检索"的性能声明
-- 使用：mysql -uroot -p < pad_albums_100k.sql（需 MySQL 8，WITH RECURSIVE）
-- 原理：以 id<=1500 的真实专辑为模板复制 66 轮，标题追加序号，分类/用户沿用真实值（保证 ES 上架链路可用）
USE tingshu_album;

SELECT COUNT(*) AS before_count FROM album_info;

INSERT INTO album_info (user_id, album_title, category3_id, is_open, status)
SELECT s.user_id,
       CONCAT(s.album_title, '·听', n.n),
       s.category3_id,
       '1',
       '0301'
FROM (SELECT id, user_id, album_title, category3_id FROM album_info WHERE id <= 1500) s
CROSS JOIN (
    WITH RECURSIVE seq AS (SELECT 1 AS n UNION ALL SELECT n + 1 FROM seq WHERE n < 66)
    SELECT n FROM seq
) n;

SELECT COUNT(*) AS after_count FROM album_info;

-- ES 侧灌数：服务启动后调用（100000 按实际 after_count 调整，预计 10~30 分钟）：
-- curl http://localhost:8502/api/search/albumInfo/batchUpperAlbum/100500
