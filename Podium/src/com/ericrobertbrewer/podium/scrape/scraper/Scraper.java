package com.ericrobertbrewer.podium.scrape.scraper;

import org.apache.commons.io.FileUtils;
import org.openqa.selenium.WebDriver;

import java.io.*;

public abstract class Scraper {

    private final WebDriver driver;

    public Scraper(WebDriver driver) {
        this.driver = driver;
    }

    public WebDriver getDriver() {
        return driver;
    }

    // TODO: Separate `scrapeText` from `scrapeAudio`? Audio is slow to download...
    public abstract void scrapeAll(File rootFolder, boolean force);

    public void close() {
        driver.close();
    }

    public void quit() {
        driver.quit();
    }

    protected void writeSource(File folder, String sourceFileName) throws IOException {
        writeSource(folder, sourceFileName, false);
    }

    protected void writeSource(File folder, String sourceFileName, boolean force) throws IOException {
        final File sourceFile = new File(folder, sourceFileName);
        if (sourceFile.exists()) {
            if (force) {
                FileUtils.forceDelete(sourceFile);
            } else {
                return;
            }
        }
        if (!sourceFile.createNewFile()) {
            throw new RuntimeException("Unable to create source file: `" + sourceFile.getPath() + "`.");
        }
        if (!sourceFile.canWrite() && !sourceFile.setWritable(true)) {
            throw new RuntimeException("Unable to write to source file: `" + sourceFile.getPath() + "`.");
        }
        final OutputStream outputStream = new FileOutputStream(sourceFile);
        final PrintStream out = new PrintStream(outputStream);
        final String source = getDriver().getPageSource();
        out.println(source);
        out.close();
        outputStream.close();
    }
}
