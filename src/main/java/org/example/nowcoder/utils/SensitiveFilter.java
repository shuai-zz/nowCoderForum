package org.example.nowcoder.utils;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.CharUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

/**
 * @author zhaoshuai
 */
@Component
@Slf4j
public class SensitiveFilter {
    // 替换符
    private static final String REPLACEMENT = "***";
    // 根节点
    private final TrieNode rootNode = new TrieNode();

    @PostConstruct
    public void init() {
        try (
                InputStream is = this.getClass().getClassLoader().getResourceAsStream("sensitive-words.txt");
                BufferedReader reader = new BufferedReader(new InputStreamReader(is))
        ) {
            String keyword;
            while ((keyword = reader.readLine()) != null) {
                // 添加到前缀树
                this.addKeyword(keyword);
            }
        } catch (Exception e) {
            log.error("Failed to load sensitive word text:{}", e.getMessage());
        }
    }


    // add one sensitive word to TrieNode
    private void addKeyword(String keyword) {
        TrieNode node = rootNode;
        for (int i = 0; i < keyword.length(); ++i) {
            char c = keyword.charAt(i);
            TrieNode subNode = node.getSubNode(c);

            // 没有该前缀时
            if (subNode == null) {
                // 初始化子节点
                subNode = new TrieNode();
                node.addSubNode(c, subNode);
            }

            // 指向子节点，进入下一个循环
            node = subNode;
            // 设置结束标识
            if (i == keyword.length() - 1) {
                node.setCompleteWord(true);
            }
        }
    }

    /**
     * 过滤敏感词
     *
     * @param text 待过滤的文本
     * @return 过滤后的文本
     */
    public String filter(String text) {
        if (StringUtils.isBlank(text)) {
            return null;
        }
        TrieNode tempNode = rootNode;
        int begin = 0;
        int position = 0;
        StringBuilder stringBuilder = new StringBuilder();

        while (begin < text.length()) {
            if (position < text.length()) {
                char c = text.charAt(position);
                Character lowerCaseC = Character.toLowerCase(c);
                //  跳过符号
                if (isSymbol(lowerCaseC)) {
                    if (tempNode == rootNode) {
                        // 符号在中间，跳过符号继续匹配
                        ++begin;
                        stringBuilder.append(lowerCaseC);
                    }
                    ++position;
                    continue;
                }
                // 检查下级节点
                tempNode = tempNode.getSubNode(lowerCaseC);
                if (tempNode == null) {
                    // 以begin开头的字符串不是敏感词
                    stringBuilder.append(text.charAt(begin));
                    // 进入下一个位置
                    position = ++begin;
                    // 重新指向根节点
                    tempNode = rootNode;
                } else if (tempNode.isCompleteWord()) {
                    // 发现敏感词
                    stringBuilder.append(REPLACEMENT);
                    begin = ++position;
                } else {
                    // 检查下一个字符
                    ++position;
                }

            } else {
                stringBuilder.append(text.charAt(begin));
                position = ++begin;
                tempNode = rootNode;
            }
        }
        return stringBuilder.toString();
    }


    // 判断是否为符号
    private boolean isSymbol(Character c) {
        // 0x2E80 - 0x9FFF 是东亚文字范围
        return !CharUtils.isAsciiAlphanumeric(c) && (c < 0x2E80 || c > 0x9FFF);
    }

    // 前缀树
    private static class TrieNode {
        // 关键词结束标志
        private boolean isCompleteWord = false;
        // 子节点
        private final Map<Character, TrieNode> subNode = new HashMap<>();

        public boolean isCompleteWord() {
            return isCompleteWord;
        }

        public void setCompleteWord(boolean completeWord) {
            isCompleteWord = completeWord;
        }

        // 添加子节点
        public void addSubNode(Character c, TrieNode node) {
            subNode.put(c, node);
        }

        // 获取子节点
        public TrieNode getSubNode(Character c) {
            return subNode.get(c);
        }

    }
}
