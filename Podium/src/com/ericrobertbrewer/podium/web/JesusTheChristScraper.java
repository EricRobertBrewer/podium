package com.ericrobertbrewer.podium.web;

import org.openqa.selenium.WebDriver;

import java.io.File;

public class JesusTheChristScraper extends Scraper {

    public JesusTheChristScraper(WebDriver driver, File rootFolder) {
        super(driver, rootFolder);
    }

    public void scrapeAll(boolean force) {
        getDriver().navigate().to("https://www.lds.org/languages/eng/content/manual/jesus-the-christ");
        // TODO
    }
}
