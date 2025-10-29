package com.github.recafai.util;

import org.objectweb.asm.Opcodes;
import org.slf4j.Logger;
import software.coley.recaf.analytics.logging.Logging;
import software.coley.recaf.info.member.MethodMember;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MethodParse {
    private static final Logger logger = Logging.get(MethodParse.class);
    // 映射 JVM 基本类型描述符到 Java 类型名
    private static final Map<String, String> PRIMITIVE_TYPES = Map.of(
            "Z", "boolean",
            "B", "byte",
            "C", "char",
            "S", "short",
            "I", "int",
            "J", "long",
            "F", "float",
            "D", "double",
            "V", "void"
    );

    public static String methodMemberToJavaSignature(MethodMember method) {
        String methodName = method.getName();
        String descriptor = method.getDescriptor();
        int access = method.getAccess();
        String modifiers = accessToModifiers(access);
        return String.format("%s %s", modifiers, descriptorToJavaMethod(descriptor, methodName));
    }

    public static Pattern methodMemberToPattern(MethodMember method) {
        String methodName = method.getName();
        String descriptor = method.getDescriptor();
        int access = method.getAccess();
        String modifiers = accessToModifiers(access);
        String methodPattern = methodMemberToJavaSignature(method)
                .replace("(", "\\s*\\(\\s*")
                .replace(")", "\\s*\\)\\s*")
                .replace("[", "\\[")
                .replace("]", "\\]")
                .replace(",", "\\s*,\\s*")
                .replaceAll("\\s+", "\\\\s+")
                .replaceAll("\\s+\\.\\*\\?", "\\s+.*?");
        logger.info(methodPattern);
        return Pattern.compile(methodPattern);
    }

    /**
     * 将 JVM 方法描述符转换为 Java 方法签名
     * 示例输入: "(Leos/moe/dragoncore/bc;Leos/moe/dragoncore/cc;[C)Leos/moe/dragoncore/ml;"
     * 输出: "public static ml ALLATORIxDEMO(bc a, cc a2, char[] a3) throws IOException"
     */
    public static String descriptorToJavaMethod(String descriptor, String methodName) {
        Pattern pattern = Pattern.compile("^\\((.*)\\)(.+)$");
        Matcher matcher = pattern.matcher(descriptor.trim());

        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid method descriptor: " + descriptor);
        }

        String argsDescriptor = matcher.group(1);
        String returnTypeDescriptor = matcher.group(2);

        // 解析参数列表
        List<String> paramTypes = parseParameterDescriptors(argsDescriptor);
        List<String> paramDecls = new ArrayList<>();
        for (String type : paramTypes) {
            paramDecls.add(type + " .*?");
        }

        // 解析返回类型
        String returnType = descriptorToJavaType(returnTypeDescriptor);

        // 组装方法签名（按你的格式）
        String params = String.join(", ", paramDecls);
        return String.format("%s %s(%s)", returnType, methodName, params);
    }

    /**
     * 解析参数描述符字符串（如 "Leos/moe/dragoncore/bc;Leos/moe/dragoncore/cc;[C"）
     * 返回 Java 类型名称列表（如 ["bc", "cc", "char[]"]）
     */
    private static List<String> parseParameterDescriptors(String argsDesc) {
        List<String> types = new ArrayList<>();
        int i = 0;
        while (i < argsDesc.length()) {
            char c = argsDesc.charAt(i);
            if (c == '[') {
                // 数组类型：递归处理
                int arrayDim = 0;
                while (i < argsDesc.length() && argsDesc.charAt(i) == '[') {
                    arrayDim++;
                    i++;
                }
                String baseType = descriptorToJavaType(argsDesc.substring(i));
                // 找到下一个分号或结束（仅对对象类型需要）
                if (argsDesc.charAt(i) == 'L') {
                    int end = argsDesc.indexOf(';', i);
                    if (end != -1) {
                        i = end + 1;
                    }
                } else {
                    i++; // 基本类型只占一个字符
                }
                String arrayType = baseType + "[]".repeat(arrayDim);
                types.add(arrayType);
            } else if (c == 'L') {
                // 对象类型：Leos/moe/.../ClassName;
                int end = argsDesc.indexOf(';', i);
                if (end == -1) {
                    throw new IllegalArgumentException("Invalid object descriptor: " + argsDesc.substring(i));
                }
                String internalName = argsDesc.substring(i + 1, end); // 去掉 L 和 ;
                String simpleName = internalName.substring(internalName.lastIndexOf('/') + 1);
                types.add(simpleName);
                i = end + 1;
            } else {
                // 基本类型
                String type = PRIMITIVE_TYPES.get(String.valueOf(c));
                if (type == null) {
                    throw new IllegalArgumentException("Unknown primitive type: " + c);
                }
                types.add(type);
                i++;
            }
        }
        return types;
    }

    /**
     * 将单个类型描述符（如 "Leos/moe/.../T;" 或 "[C" 或 "I"）转为 Java 类型名
     */
    private static String descriptorToJavaType(String desc) {
        if (desc == null || desc.isEmpty()) {
            throw new IllegalArgumentException("Empty descriptor");
        }

        if (desc.startsWith("[")) {
            // 数组：递归处理
            int dims = 0;
            int i = 0;
            while (i < desc.length() && desc.charAt(i) == '[') {
                dims++;
                i++;
            }
            String base = descriptorToJavaType(desc.substring(i));
            return base + "[]".repeat(dims);
        } else if (desc.startsWith("L")) {
            if (!desc.endsWith(";")) {
                throw new IllegalArgumentException("Invalid object descriptor: " + desc);
            }
            String internalName = desc.substring(1, desc.length() - 1);
            return internalName.substring(internalName.lastIndexOf('/') + 1);
        } else {
            // 基本类型
            String type = PRIMITIVE_TYPES.get(desc);
            if (type == null) {
                throw new IllegalArgumentException("Unknown type descriptor: " + desc);
            }
            return type;
        }
    }

    /**
     * 将 JVM 方法访问标志（access flags）转换为 Java 修饰符字符串（如 "public static final"）
     *
     * @param access ASM/Opcodes 中的访问标志（如 method.getAccess()）
     * @return 修饰符字符串，多个修饰符用空格分隔，顺序符合 Java 习惯
     */
    public static String accessToModifiers(int access) {
        List<String> modifiers = new ArrayList<>();

        // 1. 访问级别（只能有一个）
        if ((access & Opcodes.ACC_PUBLIC) != 0) {
            modifiers.add("public");
        } else if ((access & Opcodes.ACC_PROTECTED) != 0) {
            modifiers.add("protected");
        } else if ((access & Opcodes.ACC_PRIVATE) != 0) {
            modifiers.add("private");
        }

        // 2. 其他修饰符（顺序按 Java 语言规范推荐）
        if ((access & Opcodes.ACC_STATIC) != 0) {
            modifiers.add("static");
        }
        if ((access & Opcodes.ACC_FINAL) != 0) {
            modifiers.add("final");
        }
        if ((access & Opcodes.ACC_ABSTRACT) != 0) {
            modifiers.add("abstract");
        }
        if ((access & Opcodes.ACC_SYNCHRONIZED) != 0) {
            modifiers.add("synchronized");
        }
        if ((access & Opcodes.ACC_NATIVE) != 0) {
            modifiers.add("native");
        }
        if ((access & Opcodes.ACC_STRICT) != 0) {
            modifiers.add("strictfp");
        }

        return String.join(" ", modifiers);
    }
}
