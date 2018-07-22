package com.ericrobertbrewer.podium;

import com.ericrobertbrewer.podium.web.DriverUtil;
import com.ericrobertbrewer.podium.web.TextExtractor;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class GeneralConference {

    private static final String GENERAL_CONFERENCE_HOME_URL = "https://www.lds.org/languages/eng/lib/general-conference";

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: <chrome-driver-path>");
        }
        System.setProperty("webdriver.chrome.driver", args[0]);
        final WebDriver driver = new ChromeDriver();
        final File rootFolder = new File(Folders.CONTENT_GENERAL_CONFERENCE);
        if (!rootFolder.exists() && !rootFolder.mkdir()) {
            throw new RuntimeException("Unable to create root directory: `" + rootFolder.getPath() + "`.");
        }
        writeConferences(driver, rootFolder);
    }

    private static void writeConferences(WebDriver driver, File rootFolder) throws IOException {
        driver.navigate().to(GENERAL_CONFERENCE_HOME_URL);
        // Collect conference URLs before navigating away from this page.
        final List<String> conferenceUrls = new ArrayList<String>();
        final List<WebElement> tileAs = driver.findElements(By.className("tile-3KqhL"));
        for (WebElement tileA : tileAs) {
            final String conferenceUrl = tileA.getAttribute("href");
            conferenceUrls.add(conferenceUrl);
        }
        for (String conferenceUrl : conferenceUrls) {
            writeConference(driver, rootFolder, conferenceUrl);
        }
    }

    private static void writeConference(WebDriver driver, File rootFolder, String conferenceUrl) throws IOException {
        // Navigate to this general conference page, if needed.
        if (!driver.getCurrentUrl().equals(conferenceUrl)) {
            driver.navigate().to(conferenceUrl);
        }
        // Extract conference title as `YYYY-MM`.
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
        programPrintStream.println("title\tspeaker\trole\tkicker\tfile\tref_file");
        // Collect talk URLs before navigating.
        final List<String> talkUrls = new ArrayList<String>();
        final WebElement talksListDiv = driver.findElement(By.className("items-21msL"));
        final List<WebElement> talkAnchors = talksListDiv.findElements(By.tagName("a"));
        for (WebElement talkAnchor : talkAnchors) {
            final String talkUrl = talkAnchor.getAttribute("href");
            talkUrls.add(talkUrl);
        }
        for (String talkUrl : talkUrls) {
            writeTalk(driver, conferenceFolder, talkUrl, programPrintStream);
        }
        programPrintStream.close();
        programOutputStream.close();
    }

    private static void writeTalk(WebDriver driver, File conferenceFolder, String talkUrl, PrintStream programPrintStream) throws IOException {
        // Navigate to this talk.
        driver.navigate().to(talkUrl);
        // Close the left navigation panel if it's open.
        // It would otherwise prevent us from opening the "Related Content" section, reading content, etc.
        final WebElement navigationDiv = DriverUtil.findElementOrNull(driver, By.className("leftPanelOpen-3UyrD"));
        if (navigationDiv != null && navigationDiv.isDisplayed()) {
            final WebElement backHeader = navigationDiv.findElement(By.className("backToAll-1PgB6"));
            final WebElement closeButton = backHeader.findElement(By.tagName("button"));
            closeButton.click();
            // Allow the navigation section to complete its close animation.
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        // Open the "Related Content" section.
        // This seems to prevent references from being read as blank.
        final WebElement panelHeader = driver.findElement(By.className("contentHead-3F0ox"));
        final List<WebElement> buttons = panelHeader.findElements(By.className("iconButton--V5Iv"));
        for (WebElement button : buttons) {
            if ("Related Content".equals(button.getAttribute("title"))) {
                button.click();
                // Allow the "Related Content" section to complete its open animation.
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                break;
            }
        }
        // Work within the content section.
        final WebElement contentSection = driver.findElement(By.id("content"));
        // Extract talk (or video/session) title.
        final String title = DriverUtil.getElementTextOrEmpty(contentSection, By.id("title1")).trim();
        // Extract speaker and role. (Role may not be present on page.)
        final WebElement byDiv = contentSection.findElement(By.className("byline"));
        final List<WebElement> byPs = byDiv.findElements(By.tagName("p"));
        // Extract speaker.
        // Leave any leading "By" or "Presented by" as it retains more information.
        final String speaker = byPs.get(0).getText().trim();
        // Extract speaker's role, if it is shown.
        final String role;
        if (byPs.size() > 1) {
            role = byPs.get(1).getText().trim();
        } else {
            // Role isn't shown on the page. May be prophet/President.
            // For example, `https://www.lds.org/languages/eng/content/general-conference/2018/04/revelation-for-the-church-revelation-for-our-lives`.
            // In the above case, the `speaker` starts with `By President `.
            role = "";
        }
        // Extract kicker.
        final String kicker = DriverUtil.getElementTextOrEmpty(contentSection, By.id("kicker1")).trim();
        // If this is a talk, create the file to which it will be written.
        final String fileNameBase = talkUrl.substring(talkUrl.lastIndexOf('/') + 1);
        final String fileName;
        final WebElement bodyBlockDiv = DriverUtil.findElementOrNull(contentSection, By.className("body-block"));
        if (bodyBlockDiv != null) {
            // This is a talk.
            final List<WebElement> ps = bodyBlockDiv.findElements(By.tagName("p"));
            fileName = fileNameBase + ".txt";
            final File file = new File(conferenceFolder, fileName);
            DriverUtil.writeElements(ps, file, new TextExtractor() {
                @Override
                public String getText(WebElement p) {
                    return p.getText().trim();
                }
            });
        } else {
            // This is a session [video], i.e., not actually a talk by a speaker.
            fileName = "";
        }
        // Collect any references in the "Related Content" section.
        final String referencesFileName;
        final WebElement referencesAside = driver.findElement(By.className("rightPanel-2LIL7"));
        final WebElement referencesSection = DriverUtil.findElementOrNull(referencesAside, By.className("panelGridLayout-3J74n"));
        if (referencesSection != null) {
            // Create and write to references file.
            referencesFileName = fileNameBase + "_ref.tsv";
            final File referencesFile = new File(conferenceFolder, referencesFileName);
            final OutputStream outputStream = new FileOutputStream(referencesFile);
            final PrintStream printStream = new PrintStream(outputStream);
            // Print header.
            printStream.println("id\treference");
            final List<WebElement> referenceSpans = referencesSection.findElements(By.tagName("span"));
            final List<WebElement> referenceDivs = referencesSection.findElements(By.tagName("div"));
            for (int i = 0; i < referenceSpans.size(); i++) {
                final WebElement span = referenceSpans.get(i);
                final String spanText = span.getText().trim();
                if (spanText.endsWith(".")) {
                    printStream.print(spanText.substring(0, spanText.lastIndexOf('.')));
                } else {
                    printStream.print(spanText);
                }
                printStream.print("\t");
                // Single references may contain multiple paragraphs. Separate paragraphs with two bar (`||`) characters.
                final WebElement div = referenceDivs.get(i);
                final List<WebElement> ps = div.findElements(By.tagName("p"));
                printStream.print(ps.get(0).getText().trim());
                for (int j = 1; j < ps.size(); j++) {
                    final String pText = ps.get(j).getText().trim();
                    // Skip blank paragraphs. Sometimes they are added for extra spacing (?).
                    // For example, `https://www.lds.org/languages/eng/content/general-conference/2018/04/the-prophet-of-god`.
                    if (pText.length() == 0) {
                        continue;
                    }
                    printStream.print("||");
                    printStream.print(pText);
                }
                printStream.println();
            }
            printStream.close();
            outputStream.close();
        } else {
            // No references exist.
            referencesFileName = "";
        }
        // Add to the program.
        programPrintStream.println(title + "\t" + speaker + "\t" + role + "\t" + kicker + "\t" + fileName + "\t" + referencesFileName);
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
            throw new RuntimeException("Unknown month (MM) in conference file name: `" + fileName + "`.");
        }
        return month + " " + YYYY;
    }

    private static final String SPEAKER_START_BY = "By ";
    private static final String SPEAKER_START_PRESENTED_BY = "Presented by ";
    private static final String[] SPEAKER_STARTS = {SPEAKER_START_BY, SPEAKER_START_PRESENTED_BY};

    /**
     * Get only the speaker's name from the `speaker` attribute without any leading words, e.g. `By `.
     * @param speakerText The `speaker` attribute text. May include leading words.
     * @return The speaker's name.
     */
    private static String trimSpeakerText(String speakerText) {
        for (String start : SPEAKER_STARTS) {
            if (speakerText.startsWith(start)) {
                return speakerText.substring(start.length());
            }
        }
        return speakerText;
    }
}
