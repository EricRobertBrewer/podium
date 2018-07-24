package com.ericrobertbrewer.podium;

import com.ericrobertbrewer.podium.web.*;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public final class Scrape {

    public static void main(String[] args) {
        if (args.length < 3 || args.length > 4) {
            printContentOptions();
            printDriverOptions();
            throw new IllegalArgumentException("Usage: <content> <driver-name> <driver-path> [<force>]");
        }
        // Get the content option.
        final String content = args[0];
        if (!CONTENT_OPTION_MAP.containsKey(content)) {
            printContentOptions();
            throw new IllegalArgumentException("Unknown content option: `" + content + "`.");
        }
        final ContentOption contentOption = CONTENT_OPTION_MAP.get(content);
        // Get the web driver option.
        final String driverName = args[1];
        if (!DRIVER_OPTION_MAP.containsKey(driverName)) {
            printDriverOptions();
            throw new IllegalArgumentException("Unknown driver name: `" + driverName + "`.");
        }
        final DriverOption driverOption = DRIVER_OPTION_MAP.get(driverName);
        final String path = args[2];
        System.setProperty(driverOption.systemKey, path);
        final WebDriver driver = driverOption.newInstance();
        // Get the `force` argument.
        final boolean force;
        if (args.length > 3) {
            force = Boolean.parseBoolean(args[3]);
        } else {
            force = false;
        }
        // Create the root folder.
        final File rootFolder = new File(contentOption.rootFolderName);
        if (!rootFolder.exists() && !rootFolder.mkdirs()) {
            throw new RuntimeException("Unable to create root directory: `" + rootFolder.getPath() + "`.");
        }
        // Scrape the web content.
        final Scraper scraper = contentOption.newInstance(driver);
        scraper.scrapeAll(rootFolder, force);
        scraper.quit();
    }

    private static abstract class ContentOption {
        final String name;
        final String description;
        final String rootFolderName;

        ContentOption(String arg, String description, String rootFolderName) {
            this.name = arg;
            this.description = description;
            this.rootFolderName = rootFolderName;
        }

        abstract Scraper newInstance(WebDriver driver);
    }

    private static final ContentOption[] CONTENT_OPTIONS = {
            new ContentOption("byu", "BYU Provo Speeches", Folders.CONTENT_BYU_SPEECHES) {
                @Override
                Scraper newInstance(WebDriver driver) {
                    return new ByuSpeechesScraper(driver);
                }
            },
            new ContentOption("byuh", "BYU Hawai'i Speeches", Folders.CONTENT_BYUH_SPEECHES) {
                @Override
                Scraper newInstance(WebDriver driver) {
                    return new ByuhSpeechesScraper(driver);
                }
            },
            new ContentOption("byui", "BYU Idaho Speeches", Folders.CONTENT_BYUI_SPEECHES) {
                @Override
                Scraper newInstance(WebDriver driver) {
                    return new ByuiSpeechesScraper(driver);
                }
            },
            new ContentOption("xmas", "Christmas Devotionals", Folders.CONTENT_CHRISTMAS_DEVOTIONALS) {
                @Override
                Scraper newInstance(WebDriver driver) {
                    return new ChristmasDevotionalsScraper(driver);
                }
            },
            new ContentOption("gc", "General Conference Talks", Folders.CONTENT_GENERAL_CONFERENCE) {
                @Override
                Scraper newInstance(WebDriver driver) {
                    return new GeneralConferenceScraper(driver);
                }
            },
            new ContentOption("jtc", "Jesus the Christ Chapters", Folders.CONTENT_JESUS_THE_CHRIST) {
                @Override
                Scraper newInstance(WebDriver driver) {
                    return new JesusTheChristScraper(driver);
                }
            }
    };

    private static final Map<String, ContentOption> CONTENT_OPTION_MAP = new HashMap<String, ContentOption>();
    static {
        for (ContentOption contentOption : CONTENT_OPTIONS) {
            CONTENT_OPTION_MAP.put(contentOption.name, contentOption);
        }
    }

    private static void printContentOptions() {
        System.out.println("Content options (for the <content> argument):");
        for (ContentOption contentOption : CONTENT_OPTIONS) {
            System.out.println(contentOption.name + " -> " + contentOption.description);
        }
    }

    private static abstract class DriverOption {
        final String name;
        final String description;
        final String systemKey;

        DriverOption(String name, String description, String systemKey) {
            this.name = name;
            this.description = description;
            this.systemKey = systemKey;
        }

        abstract WebDriver newInstance();
    }

    private static final DriverOption[] DRIVER_OPTIONS = {
            new DriverOption("chrome", "Google Chrome", "webdriver.chrome.driver") {
                @Override
                WebDriver newInstance() {
                    return new ChromeDriver();
                }
            }
    };

    private static final Map<String, DriverOption> DRIVER_OPTION_MAP = new HashMap<String, DriverOption>();
    static {
        for (DriverOption driverOption : DRIVER_OPTIONS) {
            DRIVER_OPTION_MAP.put(driverOption.name, driverOption);
        }
    }

    private static void printDriverOptions() {
        System.out.println("Driver options (for the <driver-name> argument):");
        for (DriverOption driverOption : DRIVER_OPTIONS) {
            System.out.println(driverOption.name + " -> " + driverOption.description);
        }
    }
}
