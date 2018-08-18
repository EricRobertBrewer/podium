package com.ericrobertbrewer.podium;

import java.io.File;

public class Folders {
    
    private static final String SLASH = File.separator;

    /**
     * Root of downloaded (scraped) content.
     */
    private static final String CONTENT_ROOT = ".." + SLASH + "content" + SLASH;

    /**
     * Folder for speeches and devotional messages.
     */
    private static final String SPEECHES_ROOT = CONTENT_ROOT + "speeches" + SLASH;
    public static final String SPEECHES_BYU = SPEECHES_ROOT + "byu" + SLASH;
    public static final String SPEECHES_BYUH = SPEECHES_ROOT + "byuh" + SLASH;
    public static final String SPEECHES_BYUI = SPEECHES_ROOT + "byui" + SLASH;
    public static final String SPEECHES_CHRISTMAS_DEVOTIONALS = SPEECHES_ROOT + "christmas-devotionals" + SLASH;
    public static final String SPEECHES_GENERAL_CONFERENCE = SPEECHES_ROOT + "general-conference" + SLASH;
    
    private static final String BOOKS_ROOT = CONTENT_ROOT + "books" + SLASH;
    public static final String BOOKS_JESUS_THE_CHRIST = BOOKS_ROOT + "jesus-the-christ" + SLASH;

    private static final String NODE_MODULES_ROOT = ".." + SLASH + "node_modules" + SLASH;
    public static final String SCRIPTURES_ROOT = NODE_MODULES_ROOT + "@bencrowder" + SLASH + "scriptures-json" + SLASH;

    private Folders() {
    }
}
