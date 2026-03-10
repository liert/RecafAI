package com.github.recafai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.recafai.util.QueryUtils;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.json.schema.jackson.DefaultJsonSchemaValidator;
import jakarta.annotation.Nonnull;
import com.github.recafai.mcp.RecafMcpServer;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import software.coley.recaf.analytics.logging.Logging;
import software.coley.recaf.plugin.Plugin;
import software.coley.recaf.plugin.PluginInformation;
import software.coley.recaf.services.cell.context.ContextMenuProviderService;
import software.coley.recaf.services.plugin.CdiClassAllocator;
import software.coley.recaf.services.search.match.StringPredicateProvider;
import software.coley.recaf.services.workspace.WorkspaceManager;
import software.coley.recaf.util.FxThreadUtil;

import java.lang.reflect.Field;

@Dependent
@PluginInformation(id = "##ID##", version = "##VERSION##", name = "##NAME##", description = "##DESC##")
public class AIPlugin implements Plugin {
    private static final Logger logger = Logging.get(AIPlugin.class);

    private final WorkspaceManager workspaceManager;
    private final ContextMenuProviderService contextMenuService;


    @Inject
    public AIPlugin(
            WorkspaceManager workspaceManager,
            ContextMenuProviderService contextMenuService,
            @Nonnull CdiClassAllocator classAllocator,
            @Nonnull StringPredicateProvider stringPredicateProvider
    ) {
        this.workspaceManager = workspaceManager;
        this.contextMenuService = contextMenuService;
        InstanceManager.cdiClassAllocator = classAllocator;
        QueryUtils.stringPredicateProvider = stringPredicateProvider;
    }

    @Override
    public void onEnable() {
        patchDefaultJson();
        workspaceManager.addWorkspaceOpenListener(workspace -> {
            logger.info("Workspace opened: {}", workspace);
        });

        FxThreadUtil.run(() -> {
            InstanceManager.getBean(RecafMcpServer.class).register();
        });

        logger.info("Recaf AI Plugin enabled.");
    }

    @Override
    public void onDisable() {
        // Remove any hooks and stop timers.
    }

    private void patchDefaultJson() {
        try {
            Class<?> McpJsonInternal = Class.forName("io.modelcontextprotocol.json.McpJsonInternal");
            Field defaultJsonMapper = McpJsonInternal.getDeclaredField("defaultJsonMapper");
            defaultJsonMapper.setAccessible(true);
            defaultJsonMapper.set(null, new JacksonMcpJsonMapper(new ObjectMapper()));
        } catch (Exception e) {
            logger.error("Error while patching default json mapper.", e);
        }
        try {
            Class<?> JsonSchemaInternal = Class.forName("io.modelcontextprotocol.json.schema.JsonSchemaInternal");
            Field defaultValidator = JsonSchemaInternal.getDeclaredField("defaultValidator");
            defaultValidator.setAccessible(true);
            defaultValidator.set(null, new DefaultJsonSchemaValidator());
        } catch (Exception e) {
            logger.error("Error while patching default json schema validator.", e);
        }
    }
}