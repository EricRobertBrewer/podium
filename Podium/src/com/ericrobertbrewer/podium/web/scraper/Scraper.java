package com.ericrobertbrewer.podium.web.scraper;

import org.openqa.selenium.WebDriver;

import java.io.File;

public abstract class Scraper {

    private final WebDriver driver;

    public Scraper(WebDriver driver) {
        this.driver = driver;
    }

    public WebDriver getDriver() {
        return driver;
    }

    public abstract void scrapeAll(File rootFolder, boolean force);

    public void close() {
        driver.close();
    }

    public void quit() {
        driver.quit();
    }
}
