package com.ericrobertbrewer.podium;

import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class GeneralConference {

    private static final String GENERAL_CONFERENCE_HOME_URL = "https://www.lds.org/languages/eng/lib/general-conference";

    public static void main(String[] args) throws IOException {
        System.setProperty("webdriver.chrome.driver", "/Users/brewer/Code/Eric/drivers/chromedriver");
        final WebDriver driver = new ChromeDriver();
        final File rootFolder = new File("gc" + File.separator);
        if (!rootFolder.exists() && !rootFolder.mkdir()) {
            throw new RuntimeException("Unable to create root directory: `" + rootFolder.getPath() + "`.");
        }
	    get(driver, rootFolder);
    }

    private static void get(WebDriver driver, File rootFolder) throws IOException {
        driver.navigate().to(GENERAL_CONFERENCE_HOME_URL);
        // Collect conference URLs before navigating away from this page.
        final List<String> conferenceUrls = new ArrayList<String>();
        final List<WebElement> tileAs = driver.findElements(By.className("tile-3KqhL"));
        for (WebElement tileA : tileAs) {
            final String conferenceUrl = tileA.getAttribute("href");
            conferenceUrls.add(conferenceUrl);
        }
        for (String conferenceUrl : conferenceUrls) {
            // Navigate to this general conference page.
            driver.navigate().to(conferenceUrl);
            // Extract conference title as `YYYY-MM`.
//            final WebElement titleHeader = driver.findElement(By.className("panelHeader-2k7Jd bookTitle-39aO5"));
            final WebElement titleHeader = driver.findElement(By.className("bookTitle-39aO5"));
            final String conferenceTitle = titleHeader.getText();
            final String conferenceFileName = toConferenceFileName(conferenceTitle);
            // Create the directory into which the talks will be written.
            final File conferenceFolder = new File(rootFolder, conferenceFileName);
            if (!conferenceFolder.exists() && !conferenceFolder.mkdirs()) {
                throw new RuntimeException("Unable to create conference directory: `" + conferenceFolder.getPath() + "`.");
            }
            // Create the program file. Write the column headers.
            final File programFile = new File(conferenceFolder, "program.tsv");
            if (!programFile.createNewFile()) {
                throw new RuntimeException("Unable to create program file in `" + conferenceFolder.getName() + "`.");
            }
            final OutputStream programOutputStream = new FileOutputStream(programFile);
            final PrintStream programPrintStream = new PrintStream(programOutputStream);
            programPrintStream.println("title\tspeaker\trole\tfile");
            // Collect talk URLs before navigating.
            final List<String> talkUrls = new ArrayList<String>();
            final WebElement talksListDiv = driver.findElement(By.className("items-21msL"));
            final List<WebElement> talkAnchors = talksListDiv.findElements(By.tagName("a"));
            for (WebElement talkAnchor : talkAnchors) {
                final String talkUrl = talkAnchor.getAttribute("href");
                talkUrls.add(talkUrl);
            }
            for (String talkUrl : talkUrls) {
                // Navigate to this talk.
                driver.navigate().to(talkUrl);
                // Extract talk title.
                final WebElement titleH1 = driver.findElement(By.id("title1"));
                final String title = titleH1.getText();
                // Extract speaker.
                // Leave any leading "By" or "Presented by" as it retains more information.
                final String speaker = getElementText(driver, By.className("author-name"));
//                final String speaker = getElementText(driver, By.id("p1"));
                // Extract speaker's role.
                final String role = getElementText(driver, By.className("author-role"));
//                final String role = getElementText(driver, By.id("p2"));
                // Create the file to which this talk will be written.
                final String fileName;
                final WebElement bodyBlockDiv = findElementOrNull(driver, By.className("body-block"));
                if (bodyBlockDiv != null) {
                    // This is a talk.
                    fileName = talkUrl.substring(talkUrl.lastIndexOf('/') + 1) + ".txt";
                    final File file = new File(conferenceFolder, fileName);
                    if (!file.createNewFile()) {
                        throw new RuntimeException("Unable to create file: `" + file.getPath() + "`.");
                    }
                    if (!file.canWrite() && !file.setWritable(true)) {
                        throw new RuntimeException("Unable to write to file: `" + file.getPath() + "`.");
                    }
                    // Write each paragraph to the file.
                    final OutputStream outputStream = new FileOutputStream(file);
                    final PrintStream printStream = new PrintStream(outputStream);
                    final List<WebElement> ps = bodyBlockDiv.findElements(By.tagName("p"));
                    for (WebElement p : ps) {
                        printStream.println(p.getText());
                    }
                    printStream.close();
                    outputStream.close();
                } else {
                    // This is a session [video], i.e., not actually a talk.
                    fileName = "";
                }
                // Add to the program.
                programPrintStream.println(title + "\t" + speaker + "\t" + role + "\t" + fileName);
            }
            programPrintStream.close();
            programOutputStream.close();
        }
    }

    private static final String MONTH_MM_APRIL = "04";
    private static final String MONTH_MM_OCTOBER = "10";
    private static final String MONTH_NAME_APRIL = "April";
    private static final String MONTH_NAME_OCTOBER = "October";

    private static String toConferenceFileName(String title) {
        final String[] parts = title.split(" ");
        final String YYYY = parts[1];
        final String MM;
        if (MONTH_NAME_APRIL.equals(parts[0])) {
            MM = MONTH_MM_APRIL;
        } else if (MONTH_NAME_OCTOBER.equals(parts[0])) {
            MM = MONTH_MM_OCTOBER;
        } else {
            throw new RuntimeException("Unknown month name in conference title: `" + title + "`.");
        }
        return YYYY + "-" + MM;
    }

    private static String toConferenceTitle(String fileName) {
        final String[] parts = fileName.split("-");
        final String YYYY = parts[0];
        final String month;
        if (MONTH_MM_APRIL.equals(parts[1])) {
            month = MONTH_NAME_APRIL;
        } else if (MONTH_MM_OCTOBER.equals(parts[1])) {
            month = MONTH_NAME_OCTOBER;
        } else {
            throw new RuntimeException("Unknown month (`MM`) in conference file name: `" + fileName + "`.");
        }
        return month + " " + YYYY;
    }

    private static String getElementText(WebDriver driver, By by) {
        try {
            final WebElement element = driver.findElement(by);
            return element.getText();
        } catch (NoSuchElementException e) {
            return "";
        }
    }

    private static final String SPEAKER_START_BY = "By ";
    private static final String SPEAKER_START_PRESENTED_BY = "Presented by ";

    private static String trimSpeakerText(String roleText) {
        if (roleText.startsWith(SPEAKER_START_BY)) {
            return roleText.substring(SPEAKER_START_BY.length());
        }
        if (roleText.startsWith(SPEAKER_START_PRESENTED_BY)) {
            return roleText.substring(SPEAKER_START_PRESENTED_BY.length());
        }
        return roleText;
    }

    private static WebElement findElementOrNull(WebDriver driver, By by) {
        try {
            return driver.findElement(by);
        } catch (NoSuchElementException e) {
            return null;
        }
    }
}
