package com.ericrobertbrewer.podium.web;

import org.openqa.selenium.WebDriver;

import java.io.File;

public class ByuhSpeechesScraper extends Scraper {

    public ByuhSpeechesScraper(WebDriver driver, File rootFolder) {
        super(driver, rootFolder);
    }

    public void scrapeAll(boolean force) {
        getDriver().navigate().to("https://devotional.byuh.edu/archive");
        // TODO
    }
}
