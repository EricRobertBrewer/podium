package com.ericrobertbrewer.podium;

import com.ericrobertbrewer.podium.web.DriverUtil;
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
        getAllConferences(driver, rootFolder);
    }

    private static void getAllConferences(WebDriver driver, File rootFolder) throws IOException {
        driver.navigate().to(GENERAL_CONFERENCE_HOME_URL);
        // Collect conference URLs before navigating away from this page.
        final List<String> conferenceUrls = new ArrayList<String>();
        final List<WebElement> tileAs = driver.findElements(By.className("tile-3KqhL"));
        for (WebElement tileA : tileAs) {
            final String conferenceUrl = tileA.getAttribute("href");
            conferenceUrls.add(conferenceUrl);
        }
        for (String conferenceUrl : conferenceUrls) {
            getConference(driver, rootFolder, conferenceUrl);
        }
    }

    private static void getConference(WebDriver driver, File rootFolder, String conferenceUrl) throws IOException {
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
        programPrintStream.println("title\tspeaker\trole\tkicker\tfile\tref_file\tref_count");
        // Collect talk URLs before navigating.
        final List<String> talkUrls = new ArrayList<String>();
        final WebElement talksListDiv = driver.findElement(By.className("items-21msL"));
        final List<WebElement> talkAnchors = talksListDiv.findElements(By.tagName("a"));
        for (WebElement talkAnchor : talkAnchors) {
            final String talkUrl = talkAnchor.getAttribute("href");
            talkUrls.add(talkUrl);
        }
        for (String talkUrl : talkUrls) {
            getTalk(driver, conferenceFolder, talkUrl, programPrintStream);
        }
        programPrintStream.close();
        programOutputStream.close();
    }

    private static void getTalk(WebDriver driver, File conferenceFolder, String talkUrl, PrintStream programPrintStream) throws IOException {
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
        final String speaker;
        final String role;
        final WebElement byDiv = DriverUtil.findElementOrNull(contentSection, By.className("byline"));
        if (byDiv != null) {
            final List<WebElement> byPs = byDiv.findElements(By.tagName("p"));
            // Extract speaker.
            // Leave any leading "By" or "Presented by" as it retains more information.
            speaker = byPs.get(0).getText().trim();
            // Extract speaker's role, if it is shown.
            if (byPs.size() > 1) {
                role = byPs.get(1).getText().trim();
            } else {
                // Role isn't shown on the page. May be prophet/President.
                // For example, `https://www.lds.org/languages/eng/content/general-conference/2018/04/revelation-for-the-church-revelation-for-our-lives`.
                // In the above case, the `speaker` starts with `By President `.
                role = "";
            }
        } else {
            speaker = "";
            role = "";
        }
        // Extract kicker.
        final String kicker = DriverUtil.getElementTextOrEmpty(contentSection, By.id("kicker1")).trim();
        // Extract the file name from the URL.
        final String fileNameBase = talkUrl.substring(talkUrl.lastIndexOf('/') + 1);
        // Collect any references in the "Related Content" section.
        final String referencesFileName;
        final int referenceCount;
        final WebElement referencesAside = driver.findElement(By.className("rightPanel-2LIL7"));
        final WebElement referencesSection = DriverUtil.findElementOrNull(referencesAside, By.className("panelGridLayout-3J74n"));
        if (referencesSection != null) {
            // Create and write to references file.
            referencesFileName = fileNameBase + "_ref.tsv";
            referenceCount = writeReferences(driver, referencesSection, conferenceFolder, referencesFileName);
        } else {
            // No references exist.
            referencesFileName = "";
            referenceCount = 0;
        }
        // If this is a talk, create the file to which it will be written.
        final String fileName;
        final WebElement bodyBlockDiv = DriverUtil.findElementOrNull(contentSection, By.className("body-block"));
        if (bodyBlockDiv != null) {
            // This is a talk.
            fileName = fileNameBase + ".txt";
            writeTalk(driver, bodyBlockDiv, conferenceFolder, fileName, referenceCount);
        } else {
            // This is a session [video], i.e., not actually a talk by a speaker.
            fileName = "";
        }
        // Add to the program.
        programPrintStream.print(title + "\t" + speaker + "\t" + role + "\t" + kicker);
        programPrintStream.print("\t" + fileName + "\t" + referencesFileName + "\t" + referenceCount);
        programPrintStream.println();
    }

    /**
     * Write the references to the given file.
     * @param driver The driver.
     * @param referencesSection The `section` which contains the references.
     * @param conferenceFolder The conference folder.
     * @param referencesFileName The name of the file.
     * @return The number of references. This will be used to escape reference numbers in the talk's contents.
     * @throws IOException When I/O error occurs.
     */
    private static int writeReferences(WebDriver driver, WebElement referencesSection, File conferenceFolder, String referencesFileName) throws IOException {
        final File referencesFile = new File(conferenceFolder, referencesFileName);
        if (!referencesFile.createNewFile()) {
            throw new RuntimeException("Unable to create references file: `" + referencesFile.getPath() + "`.");
        }
        if (!referencesFile.canWrite() && !referencesFile.setWritable(true)) {
            throw new RuntimeException("Unable to write to references file: `" + referencesFile.getPath() + "`.");
        }
        final OutputStream outputStream = new FileOutputStream(referencesFile);
        final PrintStream printStream = new PrintStream(outputStream);
        // Print header.
        printStream.println("id\treference");
        final List<WebElement> referenceSpans = referencesSection.findElements(By.tagName("span"));
        final List<WebElement> referenceDivs = referencesSection.findElements(By.tagName("div"));
        final int referenceCount = referenceSpans.size();
        for (int i = 0; i < referenceCount; i++) {
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
        return referenceCount;
    }

    /**
     * Write this talk to the given file.
     * Also, escape reference numbers with less-than (<) and greater-than (>) symbols.
     * @param driver The driver.
     * @param bodyBlockDiv The `div` with `class="body-block"`.
     * @param conferenceFolder The conference folder.
     * @param fileName The name of the file.
     * @param referenceCount The number of references. This may be used to verify the reference numbers in the text.
     * @throws IOException When I/O error occurs.
     */
    private static void writeTalk(WebDriver driver, WebElement bodyBlockDiv, File conferenceFolder, String fileName, int referenceCount) throws IOException {
        final File file = new File(conferenceFolder, fileName);
        if (!file.createNewFile()) {
            throw new RuntimeException("Unable to create talk file: `" + file.getPath() + "`.");
        }
        if (!file.canWrite() && !file.setWritable(true)) {
            throw new RuntimeException("Unable to write to talk file: `" + file.getPath() + "`.");
        }
        final OutputStream outputStream = new FileOutputStream(file);
        final PrintStream printStream = new PrintStream(outputStream);
        // Get every top-level child of the `body-block` `div`.
        // We do this because `section`s and `p`s can be interspersed within a talk.
        final List<WebElement> children = bodyBlockDiv.findElements(By.xpath("./*"));
        for (WebElement child : children) {
            if ("section".equals(child.getTagName())) {
                // This child is a `section` with a header.
                // For example, `https://www.lds.org/languages/eng/content/general-conference/2018/04/small-and-simple-things`.
                final WebElement header = DriverUtil.findElementOrNull(child, By.tagName("header"));
                if (header != null) {
                    // Write the header text.
                    // We assume that headers don't contain references.
                    final String headerText = header.getText().trim();
                    printStream.println("{{" + headerText + "}}");
                }
                // Write every paragraph's decoded text.
                final List<WebElement> ps = child.findElements(By.tagName("p"));
                for (WebElement p : ps) {
                    writeText(p, printStream);
                }
            } else if ("p".equals(child.getTagName())){
                // This child is not a `section`.
                // We assume that it doesn't contain any nested paragraphs.
                writeText(child, printStream);
            } else {
                // Probably just an `img`, but it may contain more text.
                final List<WebElement> ps = child.findElements(By.tagName("p"));
                for (WebElement p : ps) {
                    writeText(p, printStream);
                }
            }
        }
        printStream.close();
        outputStream.close();
    }

    private static void writeText(WebElement p, PrintStream printStream) {
        final String pHtml = p.getAttribute("innerHTML").trim();
        final String pText = htmlToText(pHtml);
        printStream.println(pText);
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

    /**
     * Convert the inner HTML from a paragraph of a talk to a more readable text representation.
     * @param html Inner HTML from a paragraph of a talk.
     * @return A more readable text representation of a `p` element's inner HTML.
     */
    private static String htmlToText(String html) {
        // Replace the superscript reference notation with double arrow brackets.
        return html.replaceAll("<a class=\"note-ref\" href=\"#note([0-9]+)\"><sup class=\"marker\">\\1</sup></a>", "<<$1>>")
                // Ignore any other HTML tags, e.g. links, italics.
                // Scripture references in parentheses can be parsed at another time.
                .replaceAll("<(.+?).*?>(.*?)</\\1>", "$2");
    }

    /**
     * Normal. Usually as in a member of the [Emeritus] Seventy, a female speaker, etc.
     */
    private static final String SPEAKER_START_BY = "By ";
    /**
     * As in an administrative report, sustaining, etc.
     */
    private static final String SPEAKER_START_PRESENTED_BY = "Presented by ";
    /**
     * Usually as in the President of the Church.
     */
    private static final String SPEAKER_START_BY_PRESIDENT = "By President ";
    /**
     * As in a member of the Quorum of the Twelve Apostles.
     */
    private static final String SPEAKER_START_BY_ELDER = "By Elder ";
    /**
     * As in the Presiding Bishop.
     */
    private static final String SPEAKER_START_BY_BISHOP = "By Bishop ";
    /**
     * All of the above `speaker` starting words.
     */
    private static final String[] SPEAKER_STARTS = {
            SPEAKER_START_BY, SPEAKER_START_PRESENTED_BY,
            SPEAKER_START_BY_PRESIDENT, SPEAKER_START_BY_ELDER,
            SPEAKER_START_BY_BISHOP
    };

    /**
     * Get only the speaker's name from the `speaker` attribute within a program without any leading words, e.g. `By `.
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
