package com.ericrobertbrewer.podium.web.scraper;

import org.openqa.selenium.WebDriver;

import java.io.File;

public class ByuiSpeechesScraper extends Scraper {

    public ByuiSpeechesScraper(WebDriver driver) {
        super(driver);
    }

    public void scrapeAll(File rootFolder, boolean force) {
        getDriver().navigate().to("https://web.byui.edu/devotionalsandspeeches/");
        // TODO
    }
}
