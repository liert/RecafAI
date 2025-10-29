package com.github.recafai.ui;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import com.github.recafai.InstanceManager;
import com.github.recafai.util.MethodParse;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import org.slf4j.Logger;
import software.coley.bentofx.dockable.Dockable;
import software.coley.bentofx.dockable.DockableIconFactory;
import software.coley.bentofx.layout.container.DockContainerLeaf;
import software.coley.recaf.analytics.logging.Logging;
import software.coley.recaf.info.ClassInfo;
import software.coley.recaf.info.JvmClassInfo;
import software.coley.recaf.info.member.MethodMember;
import software.coley.recaf.path.ClassPathNode;
import software.coley.recaf.path.PathNodes;
import software.coley.recaf.services.cell.context.ContextSource;
import software.coley.recaf.services.cell.context.MethodContextMenuAdapter;
import software.coley.recaf.services.cell.icon.IconProvider;
import software.coley.recaf.services.cell.icon.IconProviderService;
import software.coley.recaf.services.decompile.DecompileResult;
import software.coley.recaf.ui.docking.DockingManager;
import software.coley.recaf.util.FxThreadUtil;
import software.coley.recaf.workspace.model.Workspace;
import software.coley.recaf.workspace.model.bundle.ClassBundle;
import software.coley.recaf.workspace.model.resource.WorkspaceResource;

import java.util.Objects;

@Dependent
public class AIMethodMenuAdapter implements MethodContextMenuAdapter {
    private static final Logger logger = Logging.get(AIMethodMenuAdapter.class);
    private final IconProviderService iconService;
    private final DockingManager dockingManager;
    private final AIJvmClassPane jvmClassPane;

    @Inject
    public AIMethodMenuAdapter(DockingManager dockingManager, IconProviderService iconService) {
        this.dockingManager = dockingManager;
        this.iconService = iconService;
        this.jvmClassPane = InstanceManager.getBean(AIJvmClassPane.class);
    }

    @Override
    public void adaptMethodContextMenu(
            @Nonnull ContextMenu menu,
            @Nonnull ContextSource source,
            @Nonnull Workspace workspace,
            @Nonnull WorkspaceResource resource,
            @Nonnull ClassBundle<? extends ClassInfo> bundle,
            @Nonnull ClassInfo declaringClass,
            @Nonnull MethodMember method
    ) {
        MenuItem item = new MenuItem("AI分析");
        item.setOnAction(e -> analyzeMethod(workspace, resource, bundle, declaringClass, method));
        menu.getItems().add(item);
    }

    private void analyzeMethod(Workspace workspace, WorkspaceResource resource, ClassBundle<? extends ClassInfo> bundle, ClassInfo declaringClass, MethodMember method) {
        logger.info("开始分析方法: {}", MethodParse.methodMemberToJavaSignature(method));

        if (!(declaringClass instanceof JvmClassInfo jvmClass)) {
            logger.warn("只支持 JVM 类的分析");
            return;
        }

        ClassPathNode path = PathNodes.classPath(workspace, resource, bundle, declaringClass);
        IconProvider iconProvider = iconService.getClassMemberIconProvider(workspace, resource, bundle, jvmClass, method);

        FxThreadUtil.run(() -> {
            jvmClassPane.setType("Method");
            jvmClassPane.setMethod(method);
            jvmClassPane.onUpdatePath(path);
            DockableIconFactory graphicFactory = d -> Objects.requireNonNull(iconProvider.makeIcon(), "Missing graphic");
            Dockable dockable = createDockable(dockingManager.getPrimaryDockingContainer(), method.getName() + " - AI", graphicFactory, jvmClassPane);
        });
    }


    private void handleDecompileFailure(DecompileResult result, MethodMember method) {
        logger.error("方法 {} 反编译失败", method.getName());
        if (result.getException() != null) {
            logger.error("反编译失败原因", result.getException());
        }
    }

    /**
     * Shorthand for dockable-creation + graphic setting.
     *
     * @param container
     * 		Parent container to spawn in.
     * @param title
     * 		Dockable title.
     * @param graphicFactory
     * 		Dockable graphic factory.
     * @param node
     * 		Dockable content.
     *
     * @return Created dockable.
     */
    @Nonnull
    private Dockable createDockable(@Nullable DockContainerLeaf container,
                                    @Nonnull String title,
                                    @Nonnull DockableIconFactory graphicFactory,
                                    @Nonnull Node node) {
        Dockable dockable = dockingManager.newDockable(title, graphicFactory, node);
        if (container != null) {
            container.addDockable(dockable);
            container.selectDockable(dockable);
        }
        return dockable;
    }
}