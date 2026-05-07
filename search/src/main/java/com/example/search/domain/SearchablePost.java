package com.example.search.domain;

import com.example.post.domain.entity.DiscussPost;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.Setting;

import java.util.Date;

/**
 * 帖子的 Elasticsearch 文档视图，专属 search 模块。
 * <p>与 {@link DiscussPost} 字段对齐，但承担 ES 持久化身份；
 * 这样 {@code DiscussPost} 可以保持纯 domain 实体不被 ES 注解污染。
 * 搜索结果中的 highlight 写到本类的 title/content 字段，避免回写 domain。
 *
 * @author zhaoshuai
 */
@Document(indexName = "discusspost")
@Setting(shards = 6, replicas = 3)
public class SearchablePost {

    @Id
    @Field(type = FieldType.Integer)
    private int id;

    @Field(type = FieldType.Integer)
    private int userId;

    @Field(type = FieldType.Text, analyzer = "standard", searchAnalyzer = "standard")
    private String title;

    @Field(type = FieldType.Text, analyzer = "standard", searchAnalyzer = "standard")
    private String content;

    @Field(type = FieldType.Integer)
    private int type;

    @Field(type = FieldType.Integer)
    private int status;

    @Field(type = FieldType.Date)
    private Date createTime;

    @Field(type = FieldType.Integer)
    private int commentCount;

    @Field(type = FieldType.Integer)
    private int likeCount;

    @Field(type = FieldType.Double)
    private double score;

    public SearchablePost() {}

    /** 从 domain 实体投影到 ES 文档（写索引时用）。 */
    public static SearchablePost from(DiscussPost p) {
        SearchablePost s = new SearchablePost();
        s.id = p.getId();
        s.userId = p.getUserId();
        s.title = p.getTitle();
        s.content = p.getContent();
        s.type = p.getType();
        s.status = p.getStatus();
        s.createTime = p.getCreateTime();
        s.commentCount = p.getCommentCount();
        s.likeCount = p.getLikeCount();
        s.score = p.getScore();
        return s;
    }

    /** 转回 domain 实体（读索引时用）。注意 title/content 可能含 highlight 标签。 */
    public DiscussPost toDiscussPost() {
        return DiscussPost.builder()
                .id(id)
                .userId(userId)
                .title(title)
                .content(content)
                .type(type)
                .status(status)
                .createTime(createTime)
                .commentCount(commentCount)
                .likeCount(likeCount)
                .score(score)
                .build();
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public int getType() { return type; }
    public void setType(int type) { this.type = type; }

    public int getStatus() { return status; }
    public void setStatus(int status) { this.status = status; }

    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }

    public int getCommentCount() { return commentCount; }
    public void setCommentCount(int commentCount) { this.commentCount = commentCount; }

    public int getLikeCount() { return likeCount; }
    public void setLikeCount(int likeCount) { this.likeCount = likeCount; }

    public double getScore() { return score; }
    public void setScore(double score) { this.score = score; }
}
