package com.github.recafai.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.recafai.util.QueryUtils;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpAsyncServer;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.transport.HttpServletSseServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import org.apache.commons.text.StringEscapeUtils;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.slf4j.Logger;
import reactor.core.publisher.Mono;
import software.coley.recaf.analytics.logging.Logging;
import software.coley.recaf.info.ClassInfo;
import software.coley.recaf.info.JvmClassInfo;
import software.coley.recaf.path.ClassPathNode;
import com.github.recafai.util.RecafInvoke;
import software.coley.recaf.services.decompile.DecompilerManager;
import software.coley.recaf.services.search.SearchService;
import software.coley.recaf.services.search.match.StringPredicateProvider;
import software.coley.recaf.services.search.query.Query;
import software.coley.recaf.services.search.result.Result;
import software.coley.recaf.services.workspace.WorkspaceManager;
import software.coley.recaf.workspace.model.Workspace;
import software.coley.recaf.workspace.model.bundle.JvmClassBundle;
import software.coley.recaf.workspace.model.resource.BasicWorkspaceDirectoryResource;
import software.coley.recaf.workspace.model.resource.BasicWorkspaceFileResource;
import software.coley.recaf.workspace.model.resource.WorkspaceFileResource;
import software.coley.recaf.workspace.model.resource.WorkspaceResource;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.*;

@Dependent
public class RecafMcpServer {
    private static final Logger logger = Logging.get(RecafMcpServer.class);
    private final WorkspaceManager workspaceManager;
    private final DecompilerManager decompilerManager;
    private final SearchService searchService;
    private final RecafInvoke recafInvoke;
    private final String baseUrl = "http://127.0.0.1:11223";
    private final String sseEndpoint = "/sse";
    private final String messageEndpoint = "/message";
    private final JacksonMcpJsonMapper jsonMapper;

    @Inject
    public RecafMcpServer(WorkspaceManager workspaceManager,
                          DecompilerManager decompilerManager,
                          SearchService searchService) {
        this.workspaceManager = workspaceManager;
        this.decompilerManager = decompilerManager;
        this.searchService = searchService;
        this.recafInvoke = new RecafInvoke();
        this.jsonMapper = new JacksonMcpJsonMapper(new ObjectMapper());
    }

    public void register() {
        HttpServletSseServerTransportProvider transportProvider = HttpServletSseServerTransportProvider.builder()
                .baseUrl(baseUrl)
                .jsonMapper(jsonMapper)
                .messageEndpoint(messageEndpoint)
                .sseEndpoint(sseEndpoint)
                .keepAliveInterval(Duration.ofSeconds(30))
                .build();

        Server server = new Server(11223);
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");
        server.setHandler(context);

        // 注册你的 Servlet
        context.addServlet(new ServletHolder(transportProvider), sseEndpoint);
        context.addServlet(new ServletHolder(transportProvider), messageEndpoint);

        try {
            server.start();
            logger.info("Jetty server started on port 11223");
        } catch (Exception e) {
            logger.error("Failed to start Jetty server", e);
            return;
        }

        McpAsyncServer asyncServer = McpServer.async(transportProvider)
                .serverInfo("recaf-mcp-server", "1.0.0")
                .capabilities(McpSchema.ServerCapabilities.builder()
                        .resources(false, true)     // 启用资源支持
                        .tools(true)                // 启用工具支持
                        .prompts(true)              // 启用提示支持
                        .logging()                  // 启用日志记录支持
                        .completions()              // 启用补全支持
                        .build())
                .build();

        asyncServer.addTool(getCurrentWorkspaceTool())
                .doOnSuccess(v -> logger.info("getCurrentWorkspaceTool registered"))
                .subscribe();
        asyncServer.addTool(getAllClassNamesTool())
                .doOnSuccess(v -> logger.info("getAllClassNamesTool registered"))
                .subscribe();
        asyncServer.addTool(getClassTool())
                .doOnSuccess(v -> logger.info("getClassTool registered"))
                .subscribe();
        asyncServer.addTool(invokeTool())
                .doOnSuccess(v -> logger.info("invokeTool registered"))
                .subscribe();
        asyncServer.addTool(invokesTool())
                .doOnSuccess(v -> logger.info("invokesTool registered"))
                .subscribe();
        asyncServer.addTool(searchTool())
                .doOnSuccess(v -> logger.info("searchTool registered"))
                .subscribe();
    }

    private McpServerFeatures.AsyncToolSpecification getCurrentWorkspaceTool() {
        McpSchema.JsonSchema jsonSchema = new McpSchema.JsonSchema(
                "object", null, null, false, null, null
        );
        return new McpServerFeatures.AsyncToolSpecification(
                McpSchema.Tool.builder()
                        .name("getCurrentWorkspace")
                        .description("获取当前打开的工作区信息")
                        .inputSchema(jsonSchema)
                        .build(), null,
                (exchange, arguments) -> {
                    Workspace workspace = workspaceManager.getCurrent();
                    Map<String, Object> outputSchema = new HashMap<>();
                    WorkspaceResource resource = workspace.getPrimaryResource();
                    if (resource instanceof BasicWorkspaceFileResource fileResource) {
                        outputSchema.put("type", "BasicWorkspaceFileResource");
                        outputSchema.put("name", fileResource.getFileInfo().getName());
                    } else if (resource instanceof BasicWorkspaceDirectoryResource directoryResource) {
                        outputSchema.put("type", "BasicWorkspaceDirectoryResource");
                        outputSchema.put("name", directoryResource.getDirectoryPath().getFileName());
                        Map<String, WorkspaceFileResource> embeddedResources = directoryResource.getEmbeddedResources();
                        outputSchema.put("embeddedResource", embeddedResources.keySet());
                    }
                    McpSchema.Content content = new McpSchema.TextContent(toJson(outputSchema));
                    return Mono.just(McpSchema.CallToolResult.builder().addContent(content).build());
                }
        );
    }

    private McpServerFeatures.AsyncToolSpecification getAllClassNamesTool() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("name", Map.of(
                "type", "string",
                "description", "Jar包名，当前工作区为目录资源时需要指定"
        ));
        McpSchema.JsonSchema jsonSchema = new McpSchema.JsonSchema(
                "object", properties, null, false, null, null
        );

        return new McpServerFeatures.AsyncToolSpecification(
                McpSchema.Tool.builder()
                        .name("getAllClassNames")
                        .description("获取工作区的所有类名")
                        .inputSchema(jsonSchema)
                        .build(), null,
                (exchange, arguments) -> {
                    Workspace workspace = workspaceManager.getCurrent();
                    WorkspaceResource resource = workspace.getPrimaryResource();
                    WorkspaceResource targetResource = resource;
                    if (resource instanceof BasicWorkspaceFileResource fileResource) {
                        // JvmClassBundle bundle = resource.getJvmClassBundle();
                        // List<String> classNames = bundle.stream().map(JvmClassInfo::getName).toList();
                        // McpSchema.Content content = new McpSchema.TextContent(classNames.toString());
                        // return Mono.just(McpSchema.CallToolResult.builder().addContent(content).build());
                    } else if (resource instanceof BasicWorkspaceDirectoryResource directoryResource) {
                        Map<String, WorkspaceFileResource> embeddedResources = directoryResource.getEmbeddedResources();
                        targetResource = embeddedResources.get(arguments.arguments().get("name"));
                        // JvmClassBundle bundle = fileResource.getJvmClassBundle();
                        // List<String> classNames = bundle.stream().map(JvmClassInfo::getName).toList();
                        // McpSchema.Content content = new McpSchema.TextContent(classNames.toString());
                        // return Mono.just(McpSchema.CallToolResult.builder().addContent(content).build());
                    }
                    // List<WorkspaceResource> workspaceResources = workspace.getAllResources(false);
                    // logger.info(workspaceResources.toString());
                    // WorkspaceResource resource = workspaceResources.getFirst();
                    JvmClassBundle bundle = targetResource.getJvmClassBundle();
                    List<String> classNames = bundle.stream().map(JvmClassInfo::getName).toList();
                    McpSchema.Content content = new McpSchema.TextContent(classNames.toString());
                    return Mono.just(McpSchema.CallToolResult.builder().addContent(content).build());
                }
        );
    }

    private McpServerFeatures.AsyncToolSpecification getClassTool() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("class", Map.of(
                "type", "string",
                "description", "需要查找的类名,使用/格式，例如：xx/xx/xx"
        ));
        properties.put("decompiler", Map.of(
                "type", "string",
                "description", "反编译器名称，可选：CFR、Vineflower，默认使用CFR"
        ));
        McpSchema.JsonSchema jsonSchema = new McpSchema.JsonSchema(
                "object", properties, null, false, null, null
        );
        return new McpServerFeatures.AsyncToolSpecification(
                McpSchema.Tool.builder()
                        .name("getClass")
                        .description("获取某个类的代码")
                        .inputSchema(jsonSchema)
                        .build(), null,
                (exchange, arguments) -> {
                    logger.debug("getClass called with arguments: {}", arguments.arguments());
                    Workspace workspace = workspaceManager.getCurrent();
                    ClassPathNode classPathNode = workspace.findJvmClass((String) arguments.arguments().get("class"));
                    if (classPathNode == null) {
                        logger.error("Class not found: {}", arguments.arguments().get("class"));
                        return Mono.just(McpSchema.CallToolResult.builder().build());
                    }
                    JvmClassInfo classInfo = classPathNode.getValueOfType(JvmClassInfo.class);
                    if (classInfo == null) {
                        logger.error("Class not found: {}", arguments.arguments().get("class"));
                        return Mono.just(McpSchema.CallToolResult.builder().build());
                    }
                    String decompilerName = "CFR";
                    if (arguments.arguments().containsKey("decompiler")) {
                        decompilerName = (String) arguments.arguments().get("decompiler");
                    }
                    String targetDecompilerName;
                    if (decompilerName.equalsIgnoreCase("vineflower")) {
                        targetDecompilerName = "Vineflower";
                    } else {
                        targetDecompilerName = "CFR";
                    }
                    return Mono.fromFuture(() -> decompilerManager.decompile(Objects.requireNonNull(decompilerManager.getJvmDecompiler(targetDecompilerName)), workspace, classInfo))
                            .map(result -> {
                                String codeText = result.getText();
                                return McpSchema.CallToolResult.builder()
                                        .addContent(new McpSchema.TextContent(codeText))
                                        .build();
                            })
                            .doOnError(throwable ->
                                    logger.error("Failed to decompile class: {}", classInfo.getName(), throwable)
                            );
                }
        );
    }

    private McpServerFeatures.AsyncToolSpecification invokeTool() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("class", Map.of(
                "type", "string",
                "description", "需要调用的类名"
        ));
        properties.put("method", Map.of(
                "type", "string",
                "description", "需要调用的解密函数"
        ));
        properties.put("args", Map.of(
                "type", "string",
                "description", "需要解密的字符串"
        ));
        properties.put("libraries", Map.of(
                "type", "array",
                "items", Map.of("type", "string"),
                "description", "需要加载的外部库列表, 绝对路径"
        ));
        McpSchema.JsonSchema jsonSchema = new McpSchema.JsonSchema("object", properties, List.of("class", "method", "libraries"), false, null, null);
        return new McpServerFeatures.AsyncToolSpecification(
                McpSchema.Tool.builder()
                        .name("invoke")
                        .description("调用解密函数")
                        .inputSchema(jsonSchema)
                        .build(), null,
                (exchange, arguments) -> {
                    String className = (String) arguments.arguments().get("class");
                    String methodName = (String) arguments.arguments().get("method");
                    String argString = (String) arguments.arguments().get("args");
                    List<String> libraries = (List<String>) arguments.arguments().get("libraries");
                    if (libraries == null) {
                        libraries = new ArrayList<>();
                    }
                    logger.info("Invoking: {}.{}({})", className, methodName, argString);
                    recafInvoke.addLibraries(libraries);
                    String result = recafInvoke.invoke(className, methodName, argString);
                    McpSchema.Content content = new McpSchema.TextContent(result);
                    return Mono.just(McpSchema.CallToolResult.builder().addContent(content).build());
                }
        );
    }

    private McpServerFeatures.AsyncToolSpecification invokesTool() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("data", Map.of(
                "type", "array",
                "description", "数据数组",
                "items", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "class", Map.of(
                                        "type", "string",
                                        "description", "要调用的类全限定名"
                                ),
                                "method", Map.of(
                                        "type", "string",
                                        "description", "要调用的方法名"
                                ),
                                "args", Map.of(
                                        "type", "string",
                                        "description", "需要解密的字符串"
                                )
                        ),
                        "required", List.of("class", "method")
                )
        ));
        properties.put("libraries", Map.of(
                "type", "array",
                "items", Map.of("type", "string"),
                "description", "需要加载的外部库列表, 绝对路径"
        ));
        McpSchema.JsonSchema jsonSchema = new McpSchema.JsonSchema("object", properties, List.of("data", "libraries"), false, null, null);
        return new McpServerFeatures.AsyncToolSpecification(
                McpSchema.Tool.builder()
                        .name("invokes")
                        .description("批量调用解密函数")
                        .inputSchema(jsonSchema)
                        .build(), null,
                (exchange, arguments) -> {
                    Object dataObj = arguments.arguments().get("data");
                    if (!(dataObj instanceof List<?> dataList)) {
                        return Mono.just(McpSchema.CallToolResult.builder()
                                .addContent(new McpSchema.TextContent("参数错误：data应该是数组"))
                                .build());
                    }
                    List<String> libraries = (List<String>) arguments.arguments().get("libraries");
                    if (libraries == null) {
                        libraries = new ArrayList<>();
                    }
                    recafInvoke.addLibraries(libraries);
                    List<Map<String, Object>> result = new ArrayList<>();
                    for (Object item : dataList) {
                        if (!(item instanceof Map<?, ?> entry)) continue;
                        String className = (String) entry.get("class");
                        String methodName = (String) entry.get("method");
                        // String args;
                        // try {
                        //     args = new ObjectMapper().readValue("\"" + entry.get("args") + "\"", String.class);
                        // } catch (JsonProcessingException e) {
                        //     logger.error("参数错误：args应该是字符串", e);
                        //     continue;
                        // }
                        String args = StringEscapeUtils.unescapeJava((String) entry.get("args"));
                        logger.info("Invoking: {}.{}({})", className, methodName, args);
                        String plainText = recafInvoke.invoke(className, methodName, args);
                        Map<String, Object> map = new HashMap<>();
                        map.put("class", className);
                        map.put("method", methodName);
                        map.put("args", args);
                        map.put("result", plainText);
                        result.add(map);
                    }
                    McpSchema.Content content = new McpSchema.TextContent(toJson(result));
                    return Mono.just(McpSchema.CallToolResult.builder().addContent(content).build());
                }
        );
    }

    private McpServerFeatures.AsyncToolSpecification searchTool() {
        String inputSchema = """
                {
                    "type": "object",
                    "properties": {
                        "type": {
                            "type": "string",
                            "description": "搜索类型: 字符串（String）、类引用（ClassReference）、成员引用（MemberReference），使用括号里面的标志"
                        },
                        "match": {
                            "type": "string",
                            "description": "字符串匹配方式: KEY_CONTAINS、KEY_CONTAINS_IGNORE_CASE"
                        },
                        "query": {
                            "type": "object",
                            "description": "搜索查询数据",
                            "properties": {
                                "search": {
                                    "type": "string",
                                    "description": "需要搜索的内容"
                                },
                                "owner": {
                                    "type": "string",
                                    "description": "类名（只在使用MemberReference需要填充，非必须），以/区分包"
                                },
                                "owner_match": {
                                    "type": "string",
                                    "description": "类名匹配方式（只在使用MemberReference需要填充，非必须）"
                                }
                            },
                            "required": ["search"]
                        }
                    },
                    "required": ["type", "match", "query"]
                }
                """;
        return new McpServerFeatures.AsyncToolSpecification(
                McpSchema.Tool.builder()
                        .name("search")
                        .description("在当前工作区中搜索字符串、类引用或成员引用")
                        .inputSchema(jsonMapper, inputSchema)
                        .build(), null,
                (exchange, arguments) -> {
                    Map<String, Object> args = arguments.arguments();
                    logger.debug("search called with arguments: {}", args);
                    String type = (String) args.get("type");
                    String match = (String) args.get("match");
                    Map<String, Object> queryData = (Map<String, Object>) args.get("query");
                    String search = (String) queryData.get("search");
                    Query query;
                    if (type.equalsIgnoreCase("String")) {
                        query = QueryUtils.stringSearch(getMatchKey(match), search);
                    } else if (type.equalsIgnoreCase("ClassReference")) {
                        query = QueryUtils.classReferenceSearch(getMatchKey(match), search);
                    } else if (type.equalsIgnoreCase("MemberReference")) {
                        String owner = "";
                        if (queryData.containsKey("owner")) {
                            owner = (String) queryData.get("owner");
                        }
                        String ownerMatch = "KEY_CONTAINS";
                        if (queryData.containsKey("owner_match")) {
                            ownerMatch = (String) queryData.get("owner_match");
                        }
                        query = QueryUtils.memberReferenceSearch(getMatchKey(ownerMatch), owner, getMatchKey(match), search);
                    } else {
                        return Mono.just(McpSchema.CallToolResult.builder()
                                .addContent(new McpSchema.TextContent("参数错误：未知的搜索类型"))
                                .build());
                    }

                    if (query == null) {
                        return Mono.just(McpSchema.CallToolResult.builder()
                                .addContent(new McpSchema.TextContent("参数错误：无法构建查询"))
                                .build());
                    }

                    Set<String> resultStrings = new HashSet<>();
                    Set<Result<?>> results = searchService.search(workspaceManager.getCurrent(), query);
                    for (Result<?> result : results) {
                        logger.debug("Found result: {}", result.getPath());
                        ClassInfo classInfo = result.getPath().getValueOfType(ClassInfo.class);
                        if (classInfo != null) {
                            resultStrings.add(classInfo.getName());
                        }
                    }
                    return Mono.just(McpSchema.CallToolResult.builder()
                            .addContent(new McpSchema.TextContent(toJson(resultStrings)))
                            .build());
                });
    }

    private String toJson(Object obj) {
        try {
            return jsonMapper.writeValueAsString(obj);
        } catch (Exception e) {
            logger.error("Failed to serialize object to JSON", e);
            return "{}";
        }
    }

    public String getMatchKey(String match) {
        try {
            Field field = StringPredicateProvider.class.getDeclaredField(match);
            field.setAccessible(true);
            return (String) field.get(null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
