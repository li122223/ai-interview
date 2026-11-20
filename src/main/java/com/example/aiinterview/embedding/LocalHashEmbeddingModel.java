package com.example.aiinterview.embedding;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 轻量级本地向量化实现（EmbeddingModel 的本地/兼容接口实现）。
 *
 * <p>基于“字符 n-gram + 单词”的哈希向量化（hashing trick），纯 Java 实现：
 * <ul>
 *     <li>无需调用任何外部 Embedding API，不消耗 API 额度；</li>
 *     <li>无需下载 ONNX 模型或引入本地推理运行时，离线可用、结果确定；</li>
 *     <li>对中文使用相邻双字（bigram）作为特征，对英文技术词（Redis/JVM/MQ 等）使用单词特征；</li>
 *     <li>输出向量做 L2 归一化，使 SimpleVectorStore 的余弦相似度退化为“共享特征重合度”。</li>
 * </ul>
 */
public class LocalHashEmbeddingModel implements EmbeddingModel {

    private final int dimensions;

    public LocalHashEmbeddingModel(int dimensions) {
        if (dimensions <= 0) {
            throw new IllegalArgumentException("dimensions must be positive");
        }
        this.dimensions = dimensions;
    }

    @Override
    public float[] embed(String text) {
        float[] vector = new float[dimensions];
        if (text == null || text.isBlank()) {
            return vector;
        }
        String normalized = text.toLowerCase(Locale.ROOT);

        // 特征 1：相邻双字符 bigram（对中文尤其有效，如 "微服/服务/务架/架构"）
        for (int i = 0; i + 1 < normalized.length(); i++) {
            bump(vector, normalized.substring(i, i + 2));
        }

        // 特征 2：ASCII 单词（捕获 Redis、JVM、MQ、Kafka、G1、CMS 等英文技术词）
        for (String word : normalized.split("[^a-z0-9]+")) {
            if (!word.isEmpty()) {
                bump(vector, "w:" + word);
            }
        }

        l2Normalize(vector);
        return vector;
    }

    private void bump(float[] vector, String token) {
        // String.hashCode() 跨 JVM 稳定，Math.floorMod 保证索引非负
        int index = Math.floorMod(token.hashCode(), dimensions);
        vector[index] += 1.0f;
    }

    private static void l2Normalize(float[] vector) {
        double sum = 0.0;
        for (float v : vector) {
            sum += (double) v * v;
        }
        if (sum <= 0.0) {
            return;
        }
        double norm = Math.sqrt(sum);
        for (int i = 0; i < vector.length; i++) {
            vector[i] = (float) (vector[i] / norm);
        }
    }

    @Override
    public float[] embed(Document document) {
        return embed(document.getText());
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<Embedding> embeddings = new ArrayList<>();
        List<String> inputs = request.getInstructions();
        for (int i = 0; i < inputs.size(); i++) {
            embeddings.add(new Embedding(embed(inputs.get(i)), i));
        }
        return new EmbeddingResponse(embeddings);
    }

    @Override
    public int dimensions() {
        return dimensions;
    }
}
