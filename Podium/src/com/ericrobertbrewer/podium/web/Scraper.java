package com.ericrobertbrewer.podium.web;

import com.sun.istack.internal.NotNull;
import org.openqa.selenium.WebDriver;

import java.io.File;

public abstract class Scraper {

    private final WebDriver driver;
    private final File rootFolder;

    public Scraper(@NotNull WebDriver driver, @NotNull File rootFolder) {
        this.driver = driver;
        this.rootFolder = rootFolder;
    }

    public WebDriver getDriver() {
        return driver;
    }

    public File getRootFolder() {
        return rootFolder;
    }

    public abstract void scrapeAll(boolean force);

    public void close() {
        driver.close();
    }

    public void quit() {
        driver.quit();
    }
}
