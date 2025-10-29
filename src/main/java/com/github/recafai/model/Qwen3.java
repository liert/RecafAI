package com.github.recafai.model;

import java.util.Arrays;

import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.common.Role;
import com.alibaba.dashscope.exception.ApiException;
import com.alibaba.dashscope.exception.InputRequiredException;
import com.alibaba.dashscope.exception.NoApiKeyException;

public class Qwen3 {
    public static GenerationResult callWithMessage(String message) throws ApiException, NoApiKeyException, InputRequiredException {
        Generation gen = new Generation();
        Message systemMsg = Message.builder()
                .role(Role.SYSTEM.getValue())
                .content("""
                        请你一名资深的开发与逆向工程专家。
                        我将提供一段被混淆的代码。
                        
                        你的任务是：
                        保持逻辑和结构不变；
                        将变量、函数、类名重命名为可读、有意义的名称；
                        为关键逻辑、函数和复杂语句添加简洁准确的中文注释；
                        不要输出任何额外说明、解释、或分析文字，只输出最终的完整代码结果。
                        
                        要求输出的代码：
                        不要添加 Markdown 代码块标记；
                        格式整齐、语法正确；
                        命名风格一致（驼峰命名法）；
                        注释使用 // 或 /* */；
                        不改变代码逻辑或结构；
                        不省略任何代码。
                        
                        输出中不要包含任何“解释”、“步骤”、“总结”或非代码内容。
                        只输出最终的反混淆后代码。""")
                .build();
        Message userMsg = Message.builder()
                .role(Role.USER.getValue())
                .content(message)
                .build();
        GenerationParam param = GenerationParam.builder()
                .apiKey("sk-be5d81b657154bca91aabbc075cccdfc")
                // 此处以qwen-plus为例，可按需更换模型名称。模型列表：https://help.aliyun.com/zh/model-studio/getting-started/models
                .model("qwen3-coder-plus")
                .messages(Arrays.asList(systemMsg, userMsg))
                .resultFormat(GenerationParam.ResultFormat.MESSAGE)
                .build();
        return gen.call(param);
    }

    public static String chat(String message) throws ApiException, NoApiKeyException, InputRequiredException {
        GenerationResult result = callWithMessage(message);
        return result.getOutput().getChoices().getFirst().getMessage().getContent();
    }
}
