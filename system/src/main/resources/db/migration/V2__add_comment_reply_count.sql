ALTER TABLE `comment`
ADD COLUMN `reply_count` INT NOT NULL DEFAULT 0 COMMENT '回复数（事件驱动维护）';

-- 回填：MySQL 不允许 UPDATE 同表 SELECT 子查询，必须 JOIN 派生表
UPDATE `comment` c
JOIN (
    SELECT entity_id, COUNT(*) AS cnt
    FROM `comment`
    WHERE entity_type=2 AND status=0
    GROUP BY entity_id
) sub ON c.id=sub.entity_id
SET c.reply_count=sub.cnt;