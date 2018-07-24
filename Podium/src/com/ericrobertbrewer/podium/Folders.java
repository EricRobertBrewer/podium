package com.ericrobertbrewer.podium;

import java.io.File;

public class Folders {

    public static final String CONTENT_ROOT = ".." + File.separator + "content" + File.separator;
    public static final String CONTENT_BYU_SPEECHES = CONTENT_ROOT + "byu-speeches" + File.separator;
    public static final String CONTENT_BYUH_SPEECHES = CONTENT_ROOT + "byuh-speeches" + File.separator;
    public static final String CONTENT_BYUI_SPEECHES = CONTENT_ROOT + "byui-speeches" + File.separator;
    public static final String CONTENT_CHRISTMAS_DEVOTIONALS = CONTENT_ROOT + "christmas-devotionals" + File.separator;
    public static final String CONTENT_GENERAL_CONFERENCE = CONTENT_ROOT + "general-conference" + File.separator;
    public static final String CONTENT_JESUS_THE_CHRIST = CONTENT_ROOT + "jesus-the-christ" + File.separator;

    public static final String NODE_MODULES_ROOT = ".." + File.separator + "node_modules" + File.separator;
    public static final String SCRIPTURES_ROOT = NODE_MODULES_ROOT + "@bencrowder" + File.separator + "scriptures-json" + File.separator;

    private Folders() {
    }
}
