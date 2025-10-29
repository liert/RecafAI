package com.github.recafai.ui;

import jakarta.annotation.Nonnull;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import software.coley.recaf.analytics.logging.Logging;
import software.coley.recaf.info.member.MethodMember;
import com.github.recafai.InstanceManager;
import software.coley.recaf.ui.pane.editing.ClassPane;
import software.coley.recaf.ui.pane.editing.SideTabsInjector;

@Dependent
public class AIJvmClassPane extends ClassPane {
    private final Logger logger = Logging.get(AIJvmClassPane.class.getName());

    private final AIJvmDecompilerPane decompilerProvider;
    private String type = "Class";
    private MethodMember method;

    @Inject
    public AIJvmClassPane(@Nonnull SideTabsInjector sideTabsInjector) {
        this.decompilerProvider = InstanceManager.getBean(AIJvmDecompilerPane.class);
        sideTabsInjector.injectLater(this);
    }

    public void refresh() {
        refreshDisplay();
    }

    @Override
    protected void generateDisplay() {
        if (hasDisplay()) {
            return;
        }
        setDisplay(decompilerProvider);
    }

    public void setType(String type) {
        decompilerProvider.setType(type);
        this.type = type;
    }

    public void setMethod(MethodMember method) {
        decompilerProvider.setMethod(method);
        this.method = method;
    }
}
