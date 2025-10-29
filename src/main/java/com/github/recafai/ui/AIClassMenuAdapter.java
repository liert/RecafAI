package com.github.recafai.ui;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import org.slf4j.Logger;
import software.coley.bentofx.dockable.Dockable;
import software.coley.bentofx.dockable.DockableIconFactory;
import software.coley.bentofx.layout.container.DockContainerLeaf;
import software.coley.recaf.analytics.logging.Logging;
import software.coley.recaf.info.JvmClassInfo;
import software.coley.recaf.path.ClassPathNode;
import software.coley.recaf.path.PathNodes;
import com.github.recafai.InstanceManager;
import software.coley.recaf.services.cell.context.ClassContextMenuAdapter;
import software.coley.recaf.services.cell.context.ContextSource;
import software.coley.recaf.services.cell.icon.IconProvider;
import software.coley.recaf.services.cell.icon.IconProviderService;
import software.coley.recaf.ui.docking.DockingManager;
import software.coley.recaf.util.FxThreadUtil;
import software.coley.recaf.workspace.model.Workspace;
import software.coley.recaf.workspace.model.bundle.JvmClassBundle;
import software.coley.recaf.workspace.model.resource.WorkspaceResource;

import java.util.Objects;

@Dependent
public class AIClassMenuAdapter implements ClassContextMenuAdapter {
    private static final Logger logger = Logging.get(AIClassMenuAdapter.class);
    private final DockingManager dockingManager;
    private final IconProviderService iconService;
    private final AIJvmClassPane jvmClassPane;

    @Inject
    public AIClassMenuAdapter(DockingManager dockingManager, IconProviderService iconService) {
        this.dockingManager = dockingManager;
        this.iconService = iconService;
        this.jvmClassPane = InstanceManager.getBean(AIJvmClassPane.class);
    }

    @Override
    public void adaptJvmClassMenu(@Nonnull ContextMenu menu,
                                  @Nonnull ContextSource source,
                                  @Nonnull Workspace workspace,
                                  @Nonnull WorkspaceResource resource,
                                  @Nonnull JvmClassBundle bundle,
                                  @Nonnull JvmClassInfo info) {
        MenuItem item = new MenuItem("AI分析");
        item.setOnAction(e -> analyzeClass(workspace, resource, bundle, info));
        menu.getItems().add(item);
    }

    private void analyzeClass(Workspace workspace, WorkspaceResource resource, JvmClassBundle bundle, JvmClassInfo info) {
        logger.info("开始分析类: {}", info.getName());

        ClassPathNode path = PathNodes.classPath(workspace, resource, bundle, info);
        IconProvider iconProvider = iconService.getJvmClassInfoIconProvider(workspace, resource, bundle, info);

        FxThreadUtil.run(() -> {
            jvmClassPane.onUpdatePath(path);
            DockableIconFactory graphicFactory = d -> Objects.requireNonNull(iconProvider.makeIcon(), "Missing graphic");
            int index = info.getName().lastIndexOf('/');
            String simpleName = info.getName().substring(index + 1);
            Dockable dockable = createDockable(dockingManager.getPrimaryDockingContainer(), simpleName + " - AI", graphicFactory, jvmClassPane);
        });
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
