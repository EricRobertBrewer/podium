package com.ericrobertbrewer.podium.web;

import com.ericrobertbrewer.podium.Folders;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;

import java.io.File;

public class ByuiSpeechesScraper extends Scraper {

    public static void main(String[] args) {
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
        final File rootFolder = new File(Folders.CONTENT_BYUI_SPEECHES);
        if (!rootFolder.exists() && !rootFolder.mkdir()) {
            throw new RuntimeException("Unable to create root directory: `" + rootFolder.getPath() + "`.");
        }
        final ByuiSpeechesScraper scraper = new ByuiSpeechesScraper(driver, rootFolder);
        scraper.scrapeAll(force);
    }

    private ByuiSpeechesScraper(WebDriver driver, File rootFolder) {
        super(driver, rootFolder);
    }

    public void scrapeAll(boolean force) {
        getDriver().navigate().to("https://web.byui.edu/devotionalsandspeeches/");
        // TODO
    }
}
