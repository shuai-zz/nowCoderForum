package com.example.system.infrastructure.quartz;

import com.example.interaction.application.service.LikeService;
import com.example.post.application.service.DiscussPostService;
import com.example.post.domain.entity.DiscussPost;
import com.example.search.application.service.ElasticSearchService;
import com.example.shared.utils.RedisKeyUtil;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.data.redis.core.BoundSetOperations;
import org.springframework.data.redis.core.RedisTemplate;

import static com.example.shared.constant.ForumConstant.ENTITY_TYPE_POST;


/**
 * @author zhaoshuai
 */
@Slf4j
public class PostScoreRefreshJob implements Job {

    private final RedisTemplate<String, Object> redisTemplate;
    private final DiscussPostService discussPostService;
    private final LikeService likeService;
    private final ElasticSearchService elasticSearchService;

    public PostScoreRefreshJob(RedisTemplate<String, Object> redisTemplate,
                               DiscussPostService discussPostService,
                               LikeService likeService,
                               ElasticSearchService elasticSearchService) {
        this.redisTemplate = redisTemplate;
        this.discussPostService = discussPostService;
        this.likeService = likeService;
        this.elasticSearchService = elasticSearchService;
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
//        DiscussPost post = discussPostService.findDiscussPostById(postId, 0).discussPost();
        DiscussPost post = discussPostService.getRawPost(postId);
        if (post == null) {
            log.error("该帖子不存在：id={}", postId);
            return;
        }
        long likeCount = likeService.findEntityLikeCount(ENTITY_TYPE_POST, postId);
        double score = DiscussPost.calculateScore(
                post.isWonderful(),
                post.getCommentCount(),
                likeCount,
                post.getCreateTime()
        );
        // 更新帖子分数
        discussPostService.updateScore(postId, score);
        // 同步搜索数据
        post.updateScore(score);
        elasticSearchService.saveDiscussPost(post);
    }
}
