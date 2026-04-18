package com.takagen.launcher.bean;

import java.util.List;

public abstract class AppItem {

    public static final int TYPE_HEADER = 0;
    public static final int TYPE_SECTION = 1;

    public abstract int type();

    public static final class Header extends AppItem {
        public final String title;

        public Header(String title) {
            this.title = title;
        }

        @Override
        public int type() {
            return TYPE_HEADER;
        }
    }

    public static final class Section extends AppItem {
        public final List<AppInfo> apps;
        public final boolean isTv;

        public Section(List<AppInfo> apps, boolean isTv) {
            this.apps = apps;
            this.isTv = isTv;
        }

        @Override
        public int type() {
            return TYPE_SECTION;
        }
    }
}
