package com.ericrobertbrewer.podium.web;

import org.openqa.selenium.WebDriver;

import java.io.File;

public class ByuhSpeechesScraper extends Scraper {

    public ByuhSpeechesScraper(WebDriver driver) {
        super(driver);
    }

    public void scrapeAll(File rootFolder, boolean force) {
        getDriver().navigate().to("https://devotional.byuh.edu/archive");
        // TODO
    }
}
