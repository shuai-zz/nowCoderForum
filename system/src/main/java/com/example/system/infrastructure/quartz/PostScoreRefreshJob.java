package com.example.system.infrastructure.quartz;

import com.example.interaction.application.service.LikeService;
import com.example.post.application.service.DiscussPostService;
import com.example.post.domain.entity.DiscussPost;
import com.example.post.infrastructure.mapper.DiscussPostMapper;
import com.example.search.application.service.ElasticSearchService;
import com.example.shared.utils.RedisKeyUtil;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.data.redis.core.BoundSetOperations;
import org.springframework.data.redis.core.RedisTemplate;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import static com.example.shared.constant.ForumConstant.ENTITY_TYPE_POST;


/**
 * @author zhaoshuai
 */
@Slf4j
public record PostScoreRefreshJob(RedisTemplate<String, Object> redisTemplate, DiscussPostService discussPostService,
                                  LikeService likeService, ElasticSearchService elasticSearchService) implements Job {
    private static final Date EPOCH;

    static {
        try {
            EPOCH = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse("2014-08-01 00:00:00");
        } catch (ParseException e) {
            throw new RuntimeException("nowCoder epoch init error", e);
        }
    }

    @Override
    public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {
        String redisKey = RedisKeyUtil.getPostScore();
        BoundSetOperations<String, Object> operations = redisTemplate.boundSetOps(redisKey);

        if (operations.size() == 0) {
            log.info("[任务取消] 没有需要刷新的帖子");
            return;
        }
        log.info("[任务开始] 正在刷新帖子分数" + operations.size());
        while (operations.size() > 0) {
            this.refresh((Integer) operations.pop());
        }
        log.info("[任务结束] 帖子分数刷新完毕");
    }

    private void refresh(int postId) {
        DiscussPost post = discussPostService.findDiscussPostById(postId, 0).discussPost();
        if (post == null) {
            log.error("该帖子不存在：id={}", postId);
            return;
        }
        // 是否精华
        boolean wonderful = post.isWonderful();
        // 评论数量
        int commentCount = post.getCommentCount();
        // 点赞数量
        long likeCount = likeService.findEntityLikeCount(ENTITY_TYPE_POST, postId);
        // 计算权重
        double w = (wonderful ? 75 : 0) + commentCount * 10L + likeCount * 2;
        // 分数 = 帖子权重 + 距离天数
        double score = Math.log10(Math.max(w, 1))
                + (double) (post.getCreateTime().getTime() - EPOCH.getTime()) / (1000 * 3600 * 24);
        // 更新帖子分数
        discussPostService.updateScore(postId, score);
        // 同步搜索数据
        post.updateScore(score);
        elasticSearchService.saveDiscussPost(post);
    }
}
