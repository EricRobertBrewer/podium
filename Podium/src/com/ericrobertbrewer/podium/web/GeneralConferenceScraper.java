package com.ericrobertbrewer.podium.web;

import org.apache.commons.io.FileUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class GeneralConferenceScraper extends Scraper {

    public GeneralConferenceScraper(WebDriver driver) {
        super(driver);
    }

    public void scrapeAll(File rootFolder, boolean force) {
        getDriver().navigate().to("https://www.lds.org/languages/eng/lib/general-conference");
        // Collect conference URLs before navigating away from this page.
        final List<String> urls = new ArrayList<String>();
        final List<String> titles = new ArrayList<String>();
        final List<WebElement> tileAs = getDriver().findElements(By.className("tile-3KqhL"));
        for (WebElement tileA : tileAs) {
            // Extract link.
            final String url = tileA.getAttribute("href");
            urls.add(url);
            // Extract title.
            final WebElement titleSpan = tileA.findElement(By.className("tileTitle-1aoed"));
            final String title = titleSpan.getText().trim();
            titles.add(title);
        }
        for (int i = 0; i < urls.size(); i++) {
            final String url = urls.get(i);
            final String title = titles.get(i);
            try {
                scrapeConference(rootFolder, url, title, force);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void scrapeConference(File rootFolder, String url, String title, boolean force) throws IOException {
        // Convert conference title to `YYYY-MM`.
        final String fileName = toConferenceFileName(title);
        // Create the directory into which the talks will be written.
        final File folder = new File(rootFolder, fileName);
        if (folder.exists()) {
            if (force) {
                // Since `force == true`, delete the folder then re-create it.
                FileUtils.deleteDirectory(folder);
                createConferenceFolder(folder);
            } else {
                // The folder exists and `force == false`. Skip this conference.
                return;
            }
        } else {
            createConferenceFolder(folder);
        }
        // Navigate to this general conference page, if needed.
        if (!getDriver().getCurrentUrl().equals(url)) {
            getDriver().navigate().to(url);
        }
        // Create the program file. Write the column headers.
        final File programFile = new File(folder, "program.tsv");
        if (!programFile.createNewFile()) {
            throw new RuntimeException("Unable to create program file in `" + folder.getName() + "`.");
        }
        if (!programFile.canWrite() && !programFile.setWritable(true)) {
            throw new RuntimeException("Unable to write to program file: `" + programFile.getPath() + "`.");
        }
        final OutputStream programOutputStream = new FileOutputStream(programFile);
        final PrintStream programOut = new PrintStream(programOutputStream);
        programOut.println("title\tspeaker\trole\tkicker\tfile\tref_file\tref_count");
        // Collect talk URLs before navigating.
        final List<String> talkUrls = new ArrayList<String>();
        final List<String> talkTitles = new ArrayList<String>();
        final List<String> speakers = new ArrayList<String>();
        final WebElement talksListDiv = getDriver().findElement(By.className("items-21msL"));
        final List<WebElement> talkAs = talksListDiv.findElements(By.tagName("a"));
        for (WebElement talkA : talkAs) {
            // Extract talk link.
            final String talkUrl = talkA.getAttribute("href");
            talkUrls.add(talkUrl);
            // Extract talk link.
            final String talkTitle = talkA.getAttribute("data-title").trim();
            talkTitles.add(talkTitle);
            // Extract speaker.
            // This may be overridden by `scrapeTalk`, since the page itself may have additional, informative leading words.
            final String speaker = DriverUtils.getElementTextOrEmpty(talkA, By.className("subtitle-GfBVZ")).trim();
            speakers.add(speaker);
        }
        for (int i = 0; i < talkUrls.size(); i++) {
            final String talkUrl = talkUrls.get(i);
            final String talkTitle = talkTitles.get(i);
            final String speaker = speakers.get(i);
            scrapeTalk(folder, talkUrl, talkTitle, speaker, programOut);
        }
        programOut.close();
        programOutputStream.close();
    }

    private void scrapeTalk(File conferenceFolder, String url, String title, String speaker, PrintStream programOut) throws IOException {
        // Navigate to this talk, if needed.
        if (!getDriver().getCurrentUrl().equals(url)) {
            getDriver().navigate().to(url);
        }
        // Close the left navigation panel if it's open.
        // It would otherwise prevent us from opening the "Related Content" section, reading content, etc.
        final WebElement navigationDiv = DriverUtils.findElementOrNull(getDriver(), By.className("leftPanelOpen-3UyrD"));
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
        final WebElement panelHeader = DriverUtils.findElementOrNull(getDriver(), By.className("contentHead-3F0ox"));
        if (panelHeader != null) {
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
        }
        // Extract attributes from this page to be written to the program.
        final String role;
        final String kicker;
        final String fileName;
        final String referencesFileName;
        final int referenceCount;
        // Work within the content section.
        final WebElement contentSection = DriverUtils.findElementOrNull(getDriver(), By.id("content"));
        if (contentSection != null) {
            // Extract speaker and role. (Role may not be present on page.)
            final WebElement byDiv = DriverUtils.findElementOrNull(contentSection, By.className("byline"));
            if (byDiv != null) {
                final List<WebElement> byPs = byDiv.findElements(By.tagName("p"));
                // Extract speaker.
                // Leave any leading "By" or "Presented by" as it retains more information.
                if (byPs.size() > 0) {
                    speaker = byPs.get(0).getText().trim();
                }
                // Extract speaker's role, if it is shown.
                if (byPs.size() > 1) {
                    role = byPs.get(1).getText().trim();
                } else {
                    // Role isn't shown on the page. May be prophet/President.
                    // For example, `https://www.lds.org/languages/eng/content/general-conference/2018/04/revelation-for-the-church-revelation-for-our-lives`.
                    // In the above example, the `speaker` starts with `By President `.
                    // These are usually not missing in pages for talks given before April 2012.
                    role = "";
                }
            } else {
                role = "";
            }
            // Extract kicker.
            kicker = DriverUtils.getElementTextOrEmpty(contentSection, By.id("kicker1")).trim();
            // Extract the file name from the URL.
            final String fileNameBase = url.substring(url.lastIndexOf('/') + 1);
            // Collect any references in the "Related Content" section.
            final WebElement referencesAside = getDriver().findElement(By.className("rightPanel-2LIL7"));
            final WebElement referencesSection = DriverUtils.findElementOrNull(referencesAside, By.className("panelGridLayout-3J74n"));
            if (referencesSection != null) {
                // Create and write to references file.
                referencesFileName = fileNameBase + "_ref.tsv";
                referenceCount = writeReferences(referencesSection, conferenceFolder, referencesFileName);
            } else {
                // No references exist.
                referencesFileName = "";
                referenceCount = 0;
            }
            // If this is a talk, create the file to which it will be written.
            final WebElement bodyBlockDiv = DriverUtils.findElementOrNull(contentSection, By.className("body-block"));
            if (bodyBlockDiv != null) {
                // This is a talk.
                fileName = fileNameBase + ".txt";
                writeTalk(bodyBlockDiv, conferenceFolder, fileName, referenceCount);
            } else {
                // This is a session [video], i.e., not actually a talk by a speaker.
                fileName = "";
            }
        } else {
            // The link is broken.
            // For example, `https://www.lds.org/languages/eng/content/general-conference/2010/10/and-of-some-have-compassion-making-a-difference`.
            // In this case, at least the title and the speaker will be written in the program.
            role = "";
            kicker = "";
            fileName = "";
            referencesFileName = "";
            referenceCount = 0;
        }
        // Add to the program.
        programOut.print(title + "\t" + speaker + "\t" + role + "\t" + kicker);
        programOut.print("\t" + fileName + "\t" + referencesFileName + "\t" + referenceCount);
        programOut.println();
    }

    /**
     * Write the references to the given file.
     * @param referencesSection The `section` which contains the references.
     * @param conferenceFolder The conference folder.
     * @param referencesFileName The name of the file.
     * @return The number of references. This will be used to escape reference numbers in the talk's contents.
     * @throws IOException When I/O error occurs.
     */
    private int writeReferences(WebElement referencesSection, File conferenceFolder, String referencesFileName) throws IOException {
        final File referencesFile = new File(conferenceFolder, referencesFileName);
        if (!referencesFile.createNewFile()) {
            throw new RuntimeException("Unable to create references file: `" + referencesFile.getPath() + "`.");
        }
        if (!referencesFile.canWrite() && !referencesFile.setWritable(true)) {
            throw new RuntimeException("Unable to write to references file: `" + referencesFile.getPath() + "`.");
        }
        final OutputStream outputStream = new FileOutputStream(referencesFile);
        final PrintStream out = new PrintStream(outputStream);
        // Print header.
        out.println("id\treference");
        final List<WebElement> referenceSpans = referencesSection.findElements(By.tagName("span"));
        final List<WebElement> referenceDivs = referencesSection.findElements(By.tagName("div"));
        final int referenceCount = referenceSpans.size();
        for (int i = 0; i < referenceCount; i++) {
            final WebElement span = referenceSpans.get(i);
            final String spanText = span.getText().trim();
            if (spanText.endsWith(".")) {
                out.print(spanText.substring(0, spanText.lastIndexOf('.')));
            } else {
                out.print(spanText);
            }
            out.print("\t");
            // Single references may contain multiple paragraphs. Separate paragraphs with two bar (`||`) characters.
            final WebElement div = referenceDivs.get(i);
            final List<WebElement> ps = div.findElements(By.tagName("p"));
            boolean hasPrinted = false;
            for (WebElement p : ps) {
                final String pText = p.getText().trim();
                // Skip blank paragraphs. Sometimes they are added for extra spacing (?).
                // For example, `https://www.lds.org/languages/eng/content/general-conference/2018/04/the-prophet-of-god`.
                if (pText.length() == 0) {
                    continue;
                }
                if (hasPrinted) {
                    out.print("||");
                }
                out.print(pText);
                hasPrinted = true;
            }
            out.println();
        }
        out.close();
        outputStream.close();
        return referenceCount;
    }

    /**
     * Write this talk to the given file.
     * Also, escape reference numbers with less-than (<) and greater-than (>) symbols.
     * @param bodyBlockDiv The `div` with `class="body-block"`.
     * @param conferenceFolder The conference folder.
     * @param fileName The name of the file.
     * @param referenceCount The number of references. This may be used to verify the reference numbers in the text.
     * @throws IOException When I/O error occurs.
     */
    private void writeTalk(WebElement bodyBlockDiv, File conferenceFolder, String fileName, int referenceCount) throws IOException {
        final File file = new File(conferenceFolder, fileName);
        if (!file.createNewFile()) {
            throw new RuntimeException("Unable to create talk file: `" + file.getPath() + "`.");
        }
        if (!file.canWrite() && !file.setWritable(true)) {
            throw new RuntimeException("Unable to write to talk file: `" + file.getPath() + "`.");
        }
        final OutputStream outputStream = new FileOutputStream(file);
        final PrintStream out = new PrintStream(outputStream);
        // Get every top-level child of the `body-block` `div`.
        // We do this because `section`s and `p`s can be interspersed within a talk.
        final List<WebElement> children = bodyBlockDiv.findElements(By.xpath("./*"));
        for (WebElement child : children) {
            if ("section".equals(child.getTagName())) {
                // This child is a `section` with a header.
                // For example, `https://www.lds.org/languages/eng/content/general-conference/2018/04/small-and-simple-things`.
                final WebElement header = DriverUtils.findElementOrNull(child, By.tagName("header"));
                if (header != null) {
                    // Write the header text.
                    // We assume that headers don't contain references.
                    final String headerText = header.getText().trim();
                    out.println("{{" + headerText + "}}");
                }
                // Write every paragraph's decoded text.
                final List<WebElement> ps = child.findElements(By.tagName("p"));
                for (WebElement p : ps) {
                    writeText(p, out);
                }
            } else if ("p".equals(child.getTagName())){
                // This child is not a `section`.
                // We assume that it doesn't contain any nested paragraphs.
                writeText(child, out);
            } else {
                // Probably just an `img`, but it may contain more text.
                final List<WebElement> ps = child.findElements(By.tagName("p"));
                for (WebElement p : ps) {
                    writeText(p, out);
                }
            }
        }
        out.close();
        outputStream.close();
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

    private static void createConferenceFolder(File folder) {
        if (!folder.mkdirs()) {
            throw new RuntimeException("Unable to create conference folder: `" + folder.getPath() + "`.");
        }
    }

    private static void writeText(WebElement p, PrintStream out) {
        final String pHtml = p.getAttribute("innerHTML").trim();
        final String pText = htmlToText(pHtml);
        out.println(pText);
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
     * These are found on talk pages starting from April 2012.
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
