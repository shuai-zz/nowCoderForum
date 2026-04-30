package org.example.nowcoder.infrastructure.quartz;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.nowcoder.domain.entity.DiscussPost;
import org.example.nowcoder.application.service.DiscussPostService;
import org.example.nowcoder.application.service.ElasticSearchService;
import org.example.nowcoder.application.service.LikeService;
import org.example.nowcoder.infrastructure.util.RedisKeyUtil;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.data.redis.core.BoundSetOperations;
import org.springframework.data.redis.core.RedisTemplate;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import static org.example.nowcoder.infrastructure.util.ForumConstant.ENTITY_TYPE_POST;

/**
 * @author zhaoshuai
 */
@Slf4j
@RequiredArgsConstructor
@SuppressWarnings("unchecked")
public class PostScoreRefreshJob implements Job {
    private final RedisTemplate redisTemplate;
    private final DiscussPostService discussPostService;
    private final LikeService likeService;
    private final ElasticSearchService elasticSearchService;
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
        BoundSetOperations operations = redisTemplate.boundSetOps(redisKey);

        if(operations.size()==0) {
            log.info("[任务取消] 没有需要刷新的帖子");
            return;
        }
        log.info("[任务开始] 正在刷新帖子分数"+operations.size());
        while (operations.size()>0){
            this.refresh((Integer) operations.pop());
        }
        log.info("[任务结束] 帖子分数刷新完毕");
    }
    private void refresh(int postId) {
        DiscussPost post = discussPostService.findDiscussPostById(postId);
        if(post==null){
            log.error("该帖子不存在：id={}",postId);
            return;
        }
        // 是否精华
        boolean wonderful = post.getStatus()==1;
        // 评论数量
        int commentCount = post.getCommentCount();
        // 点赞数量
        long likeCount = likeService.findEntityLikeCount(ENTITY_TYPE_POST, postId);
        // 计算权重
        double w = (wonderful ? 75 : 0) + commentCount * 10L + likeCount*2;
        // 分数 = 帖子权重 + 距离天数
        double score = Math.log10(Math.max(w, 1))
                + (double) (post.getCreateTime().getTime() - EPOCH.getTime()) / (1000*3600*24);
        // 更新帖子分数
        discussPostService.updateScore(postId,score);
        // 同步搜索数据
        post.setScore(score);
        elasticSearchService.saveDiscussPost(post);
    }
}
