package com.ericrobertbrewer.podium.web;

import org.openqa.selenium.WebDriver;

import java.io.File;

public class ByuiSpeechesScraper extends Scraper {

    public ByuiSpeechesScraper(WebDriver driver, File rootFolder) {
        super(driver, rootFolder);
    }

    public void scrapeAll(boolean force) {
        getDriver().navigate().to("https://web.byui.edu/devotionalsandspeeches/");
        // TODO
    }
}
