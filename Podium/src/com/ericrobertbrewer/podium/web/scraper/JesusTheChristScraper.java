package com.ericrobertbrewer.podium.web.scraper;

import org.openqa.selenium.WebDriver;

import java.io.File;

public class JesusTheChristScraper extends Scraper {

    public JesusTheChristScraper(WebDriver driver) {
        super(driver);
    }

    public void scrapeAll(File rootFolder, boolean force) {
        getDriver().navigate().to("https://www.lds.org/languages/eng/content/manual/jesus-the-christ");
        // TODO
    }
}
