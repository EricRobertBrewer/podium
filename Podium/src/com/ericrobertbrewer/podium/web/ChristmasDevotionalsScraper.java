package com.ericrobertbrewer.podium.web;

import org.openqa.selenium.WebDriver;

import java.io.File;

public class ChristmasDevotionalsScraper extends Scraper {

    public ChristmasDevotionalsScraper(WebDriver driver, File rootFolder) {
        super(driver, rootFolder);
    }

    public void scrapeAll(boolean force) {
        getDriver().navigate().to("https://www.lds.org/languages/eng/lib/jesus-christ/christmas-devotionals");
        // TODO
    }
}
