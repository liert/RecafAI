package com.github.recafai.ui;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import javafx.util.Pair;
import com.github.recafai.model.Qwen3;
import com.github.recafai.util.MethodExtractor;
import org.slf4j.Logger;
import software.coley.observables.ObservableBoolean;
import software.coley.observables.ObservableInteger;
import software.coley.recaf.analytics.logging.Logging;
import software.coley.recaf.info.JvmClassInfo;
import software.coley.recaf.info.member.MethodMember;
import software.coley.recaf.services.compile.JavacCompilerConfig;
import software.coley.recaf.services.decompile.DecompileResult;
import software.coley.recaf.services.decompile.DecompilerManager;
import software.coley.recaf.services.decompile.JvmDecompiler;
import software.coley.recaf.services.info.association.FileTypeSyntaxAssociationService;
import software.coley.recaf.services.source.AstService;
import software.coley.recaf.services.tutorial.TutorialConfig;
import software.coley.recaf.ui.config.KeybindingConfig;
import software.coley.recaf.ui.control.ModalPaneComponent;
import software.coley.recaf.ui.control.richtext.search.SearchBar;
import software.coley.recaf.ui.control.richtext.source.JavaContextActionSupport;

import jakarta.annotation.Nonnull;
import software.coley.recaf.ui.pane.editing.AbstractDecompilePane;
import software.coley.recaf.ui.pane.editing.ToolsContainerComponent;
import software.coley.recaf.ui.pane.editing.jvm.DecompilerPaneConfig;
import software.coley.recaf.ui.pane.editing.jvm.JvmClassInfoProvider;
import software.coley.recaf.ui.pane.editing.jvm.JvmDecompilerPaneConfigurator;
import software.coley.recaf.util.FxThreadUtil;
import software.coley.recaf.util.StringUtil;
import software.coley.recaf.workspace.model.Workspace;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Dependent
public class AIJvmDecompilerPane extends AbstractDecompilePane {
    private static final Logger logger = Logging.get(AIJvmDecompilerPane.class);
    private final ObservableInteger javacTarget;
    private final ObservableInteger javacDownsampleTarget;
    private final ObservableBoolean javacDebug;
    private final ModalPaneComponent overlayModal = new ModalPaneComponent();
    private String type = "Class";
    private MethodMember method;

    @Inject
    public AIJvmDecompilerPane(
            @Nonnull DecompilerPaneConfig decompileConfig,
            @Nonnull TutorialConfig tutorialConfig,
            @Nonnull KeybindingConfig keys,
            @Nonnull SearchBar searchBar,
            @Nonnull ToolsContainerComponent toolsContainer,
            @Nonnull AstService astService,
            @Nonnull JavaContextActionSupport contextActionSupport,
            @Nonnull FileTypeSyntaxAssociationService languageAssociation,
            @Nonnull DecompilerManager decompilerManager,
            @Nonnull JavacCompilerConfig javacConfig) {
        super(decompileConfig, tutorialConfig, searchBar, astService, contextActionSupport, languageAssociation, decompilerManager);
        this.javacDebug = new ObservableBoolean(javacConfig.getDefaultEmitDebug().getValue());
        this.javacTarget = new ObservableInteger(javacConfig.getDefaultTargetVersion().getValue());
        this.javacDownsampleTarget = new ObservableInteger(javacConfig.getDefaultDownsampleTargetVersion().getValue());

        // Install tools container with configurator
        new JvmDecompilerPaneConfigurator(toolsContainer, decompileConfig, decompiler, javacTarget, javacDownsampleTarget, javacDebug, decompilerManager);
        new JvmClassInfoProvider(toolsContainer, this);
        installToolsContainer(toolsContainer);

        // Install overlay modal
        overlayModal.setPersistent(true);
        overlayModal.install(editor);
    }

    /**
     * Decompiles the class contained by the current {@link #path} and updates the {@link #editor}'s text
     * with the decompilation results.
     */
    public void decompile() {
        Workspace workspace = path.getValueOfType(Workspace.class);
        JvmClassInfo classInfo = path.getValue().asJvmClass();

        // Schedule decompilation task, update the editor's text asynchronously on the JavaFX UI thread when complete.
        decompileInProgress.setValue(true);
        editor.setMouseTransparent(true);
        decompilerManager.decompile(decompiler.getValue(), workspace, classInfo)
                .completeOnTimeout(timeoutResult(), decompileConfig.getTimeoutSeconds().getValue(), TimeUnit.SECONDS)
                .thenApplyAsync(result -> {
                    // 后台线程执行 AI 分析
                    String text = result.getText();
                    if (getType().equals("Method")) {
                        text = MethodExtractor.extractMethod(text, getMethod());
                    }
                    try {
                        text = Qwen3.chat(text); // 耗时操作在这里安全执行
                    } catch (Exception ex) {
                        logger.error(ex.getMessage(), ex);
                        text = "Error decompiling method: " + ex.getMessage();
                    }
                    return new Pair<>(result, text);
                })
                .whenCompleteAsync((pair, throwable) -> {
                    editor.setMouseTransparent(false);
                    decompileInProgress.setValue(false);

                    // Handle uncaught exceptions
                    if (throwable != null) {
                        editor.setMouseTransparent(false);
                        decompileInProgress.setValue(false);

                        String trace = StringUtil.traceToString(throwable);
                        editor.setText("/*\nUncaught exception when decompiling:\n" + trace + "\n*/");
                        return;
                    }
                    String text = pair.getValue();

                    if (Objects.equals(text, editor.getText()))
                        return; // Skip if existing text is the same

                    DecompileResult result = pair.getKey();
                    DecompileResult.ResultType resultType = result.getType();
                    decompileOutputErrored.setValue(resultType == DecompileResult.ResultType.FAILURE);
                    switch (resultType) {
                        case SUCCESS -> editor.setText(text);
                        case SKIPPED -> editor.setText(text == null ? "// Decompilation skipped" : text);
                        case FAILURE -> {
                            Throwable exception = result.getException();
                            if (exception != null) {
                                String trace = StringUtil.traceToString(exception);
                                editor.setText("/*\nDecompile failed:\n" + trace + "\n*/");
                            } else {
                                editor.setText("/*\nDecompile failed, but no trace was attached.\n*/");
                            }
                        }
                    }

                    // Schedule AST parsing for context action support.
                    contextActionSupport.scheduleAstParse();

                    // Prevent undo from reverting to empty state.
                    editor.getCodeArea().getUndoManager().forgetHistory();
                }, FxThreadUtil.executor());
    }

    /**
     * @return Result made for timed out decompilations.
     */
    @Nonnull
    private DecompileResult timeoutResult() {
        JvmClassInfo info = path.getValue().asJvmClass();
        JvmDecompiler jvmDecompiler = decompiler.getValue();
        return new DecompileResult("""
				// Decompilation timed out.
				//  - Class name: %s
				//  - Class size: %d bytes
				//  - Decompiler: %s - %s
				//  - Timeout: %d seconds
				//
				// Suggestions:
				//  - Increase timeout
				//  - Change decompilers in 'config' or bottom right (i)
				//  - Deobfuscate heavily obfuscated code and try again
				//
				// Reminder:
				//  - Class information is still available on the side panels ==>
				""".formatted(info.getName(),
                info.getBytecode().length,
                jvmDecompiler.getName(), jvmDecompiler.getVersion(),
                decompileConfig.getTimeoutSeconds().getValue()
        ));
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public MethodMember getMethod() {
        return method;
    }

    public void setMethod(MethodMember method) {
        this.method = method;
    }
}