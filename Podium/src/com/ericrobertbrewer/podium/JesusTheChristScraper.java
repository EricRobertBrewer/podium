package com.ericrobertbrewer.podium;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;

import java.io.File;
import java.io.IOException;

public class JesusTheChristScraper {

    public static void main(String[] args) throws IOException {
        if (args.length < 2 || args.length > 3) {
            throw new IllegalArgumentException("Usage: <driver-name> <path> [<force>]");
        }
        final String driverName = args[0];
        final String path = args[1];
        final WebDriver driver;
        if ("chrome".equalsIgnoreCase(driverName)) {
            System.setProperty("webdriver.chrome.driver", path);
            driver = new ChromeDriver();
        } else {
            throw new IllegalArgumentException("Unknown driver name: `" + driverName + "`.");
        }
        final boolean force;
        if (args.length > 2) {
            force = Boolean.parseBoolean(args[2]);
        } else {
            force = false;
        }
        final File rootFolder = new File(Folders.CONTENT_JESUS_THE_CHRIST);
        if (!rootFolder.exists() && !rootFolder.mkdir()) {
            throw new RuntimeException("Unable to create root directory: `" + rootFolder.getPath() + "`.");
        }
        final JesusTheChristScraper scraper = new JesusTheChristScraper(driver, rootFolder);
        scraper.getAllChapters(force);
    }

    private final WebDriver driver;
    private final File rootFolder;

    private JesusTheChristScraper(WebDriver driver, File rootFolder) {
        this.driver = driver;
        this.rootFolder = rootFolder;
    }

    private void getAllChapters(boolean force) throws IOException {
        driver.navigate().to("https://www.lds.org/languages/eng/content/manual/jesus-the-christ");
        // TODO
    }
}
