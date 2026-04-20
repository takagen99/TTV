package com.takagen.launcher.bean;

/** Wrapper used by the Show/Hide Apps screen. Carries visibility state alongside AppInfo. */
public class AppHideItem {

    public final AppInfo app;
    public boolean hidden;

    public AppHideItem(AppInfo app, boolean hidden) {
        this.app = app;
        this.hidden = hidden;
    }
}
