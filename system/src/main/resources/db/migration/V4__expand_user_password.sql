-- S1.1 把密码哈希从 MD5(32 字符) + salt 改成 BCrypt 之后，password 列的 VARCHAR(50) 装不下
-- BCrypt 输出的 `$2a$10$<22 字符 salt><31 字符 hash>` = 60 字符。
-- 旧 MySQL（非严格 sql_mode）会静默截断 → login 永远 matches=false 的隐藏 bug；
-- 新 MySQL (STRICT_TRANS_TABLES) 直接抛 Data truncation。
-- 72 字符 buffer：兼容未来 DelegatingPasswordEncoder 切 Argon2id / scrypt（前缀更长）。
ALTER TABLE `user` MODIFY COLUMN `password` VARCHAR(72) NOT NULL;
