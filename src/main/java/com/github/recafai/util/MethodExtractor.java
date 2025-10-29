package com.github.recafai.util;

import org.slf4j.Logger;
import software.coley.recaf.analytics.logging.Logging;
import software.coley.recaf.info.member.MethodMember;

import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MethodExtractor {
    private static Logger logger = Logging.get(MethodExtractor.class.getName());
    /**
     * 从源码字符串中提取指定方法的完整代码。
     *
     * @param source     Java 类源码
     * @param method 方法
     * @return 方法完整源码，找不到返回 null
     */
    public static String extractMethod(String source, MethodMember method) {
        String methodPattern = MethodParse.methodMemberToJavaSignature(method);
        // String methodPattern = "(public|protected|private|static|\\s)+[\\w<>\\[\\]]+\\s+"
        //         + methodName + "\\s*\\([^)]*\\)\\s*\\{";
        // Pattern pattern = Pattern.compile(methodPattern);
        Pattern pattern = MethodParse.methodMemberToPattern(method);
        Matcher matcher = pattern.matcher(source);

        if (!matcher.find()){
            logger.warn("Method pattern not found: {}", pattern);
            return null;
        }

        int startIndex = matcher.start();
        int i = source.indexOf('{', matcher.end() - 1);
        if (i < 0) {
            logger.warn("Opening brace not found for method: {}", pattern);
            return null;
        }

        Stack<Character> stack = new Stack<>();
        boolean inString = false, inChar = false, escape = false;

        for (; i < source.length(); i++) {
            char c = source.charAt(i);

            if (escape) { escape = false; continue; }
            if (c == '\\') { escape = true; continue; }

            if (c == '"' && !inChar) inString = !inString;
            if (c == '\'' && !inString) inChar = !inChar;
            if (inString || inChar) continue;

            if (c == '{') stack.push(c);
            if (c == '}') stack.pop();

            if (stack.isEmpty()) {
                String result = source.substring(startIndex, i + 1).strip();
                String[] lines = result.split("\n");
                for (int j = 0; j < lines.length; j++) {
                    lines[j] = lines[j].replaceFirst(" {4}", "");
                }
                return String.join("\n", lines);
            }
        }

        return null;
    }
}
