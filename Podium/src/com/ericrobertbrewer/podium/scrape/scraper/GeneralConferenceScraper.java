package com.ericrobertbrewer.podium.scrape.scraper;

import com.ericrobertbrewer.podium.scrape.DriverUtils;
import com.ericrobertbrewer.podium.Encoding;
import org.apache.commons.io.FileUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.io.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GeneralConferenceScraper extends Scraper {

    public GeneralConferenceScraper(WebDriver driver) {
        super(driver);
    }

    public void scrapeAll(File rootFolder, boolean force) {
        getDriver().navigate().to("https://www.lds.org/languages/eng/lib/general-conference");
        final String sourceFileName = "root.html";
        try {
            writeSource(rootFolder, sourceFileName, force);
        } catch (IOException e) {
            e.printStackTrace();
        }
        // TODO: Download giant images for conferences - `tileRow-3ETk4`.
        // Collect conference URLs before navigating away from this page.
        final List<String> urls = new ArrayList<>();
        final List<String> titles = new ArrayList<>();
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
                // Since `force == true`, delete the folder.
                FileUtils.deleteDirectory(folder);
            } else {
                // The folder exists and `force == false`. Skip this conference.
                return;
            }
        }
        if (!folder.mkdirs()) {
            throw new RuntimeException("Unable to create conference folder: `" + folder.getPath() + "`.");
        }
        // Navigate to this general conference page, if needed.
        if (!getDriver().getCurrentUrl().equals(url)) {
            getDriver().navigate().to(url);
        }
        System.out.println("Starting conference `" + title + "`.");
        // Each individual talk has a complete navigation pane (there is no page which only contains the list of talks).
        // For this reason, we do not save the page source for each conference root.
        // Create the program file. Write the column headers.
        final File programFile = com.ericrobertbrewer.podium.scrape.FileUtils.newFile(folder, "program.tsv");
        final OutputStream programOutputStream = new FileOutputStream(programFile);
        final PrintStream programOut = new PrintStream(programOutputStream);
        programOut.println("title\tspeaker\trole\tkicker\ttext\treferences\turl\tsource");
        // Collect talk URLs before navigating.
        final List<String> talkUrls = new ArrayList<>();
        final List<String> talkTitles = new ArrayList<>();
        final List<String> speakers = new ArrayList<>();
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
            final String speaker = DriverUtils.getTextOrEmpty(talkA, By.className("subtitle-GfBVZ")).trim();
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

    private void scrapeTalk(File conferenceFolder, String url, String title, String speaker, PrintStream programOut) {
        // Navigate to this talk, if needed.
        if (!getDriver().getCurrentUrl().equals(url)) {
            getDriver().navigate().to(url);
        }
        System.out.println("Starting talk `" + title + "`.");
        // Close the left navigation panel if it's open.
        // It would otherwise prevent us from opening the "Related Content" section, reading content, etc.
        final WebElement navigationDiv = DriverUtils.findElementOrNull(getDriver(), By.className("leftPanelOpen-3UyrD"));
        if (navigationDiv != null && navigationDiv.isDisplayed()) {
            final WebElement backHeader = navigationDiv.findElement(By.className("backToAll-1PgB6"));
            final WebElement closeButton = backHeader.findElement(By.tagName("button"));
            closeButton.click();
            // Allow the navigation section to complete its close animation.
            try {
                Thread.sleep(500L);
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
                        Thread.sleep(500L);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    break;
                }
            }
        }
        // Extract the file name from the URL.
        final String fileNameBase = url.substring(url.lastIndexOf('/') + 1);
        // Save the page source.
        final String sourceFileName = fileNameBase + ".html";
        try {
            writeSource(conferenceFolder, sourceFileName);
        } catch (IOException e) {
            e.printStackTrace();
        }
        // Extract attributes from this page to be written to the program.
        final String role;
        final String kicker;
        final String fileName;
        final String referencesFileName;
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
            kicker = DriverUtils.getTextOrEmpty(contentSection, By.id("kicker1")).trim();
            // Collect any references in the "Related Content" section.
            final WebElement referencesAside = getDriver().findElement(By.className("rightPanel-2LIL7"));
            final WebElement referencesSection = DriverUtils.findElementOrNull(referencesAside, By.className("panelGridLayout-3J74n"));
            if (referencesSection != null) {
                // Create and write to references file.
                referencesFileName = fileNameBase + "_ref.tsv";
                try {
                    writeReferences(referencesSection, conferenceFolder, referencesFileName);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                // No references exist.
                referencesFileName = "";
            }
            // If this is a talk, create the file to which it will be written.
            final WebElement bodyBlockDiv = DriverUtils.findElementOrNull(contentSection, By.className("body-block"));
            if (bodyBlockDiv != null) {
                // This is a talk.
                fileName = fileNameBase + ".txt";
                try {
                    writeText(bodyBlockDiv, conferenceFolder, fileName);
                } catch (IOException e) {
                    e.printStackTrace();
                }
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
        }
        // Add to the program.
        programOut.print(title + "\t" + speaker + "\t" + role + "\t" + kicker);
        programOut.print("\t" + fileName + "\t" + referencesFileName);
        programOut.print("\t" + url + "\t" + sourceFileName);
        programOut.println();
    }

    /**
     * Write the references to the given file.
     * @param referencesSection The `section` which contains the references.
     * @param conferenceFolder The conference folder.
     * @param referencesFileName The name of the file.
     * @throws IOException When I/O error occurs.
     */
    private void writeReferences(WebElement referencesSection, File conferenceFolder, String referencesFileName) throws IOException {
        final File referencesFile = com.ericrobertbrewer.podium.scrape.FileUtils.newFile(conferenceFolder, referencesFileName);
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
                final String pHtml = p.getAttribute("innerHTML").trim();
                // Skip blank paragraphs. Sometimes they are added for extra spacing (?).
                // See `https://www.lds.org/languages/eng/content/general-conference/2018/04/the-prophet-of-god`.
                if (pHtml.length() == 0) {
                    continue;
                }
                if (hasPrinted) {
                    out.print(Encoding.PARAGRAPH_SEPARATOR);
                }
                out.print(pHtml);
                hasPrinted = true;
            }
            out.println();
        }
        out.close();
        outputStream.close();
    }

    /**
     * Write this text to the given file.
     * Escape reference numbers with two less-than (<<) and two greater-than (>>) symbols.
     * Escape other unspoken text (section headers, etc.) inside double curly braces ({{,}}).
     * @param bodyBlockDiv The `div` with `class="body-block"`.
     * @param conferenceFolder The conference folder.
     * @param fileName The name of the file.
     * @throws IOException When I/O error occurs.
     */
    private void writeText(WebElement bodyBlockDiv, File conferenceFolder, String fileName) throws IOException {
        final File file = com.ericrobertbrewer.podium.scrape.FileUtils.newFile(conferenceFolder, fileName);
        final OutputStream outputStream = new FileOutputStream(file);
        final PrintStream out = new PrintStream(outputStream);
        // Get every top-level child of the `body-block` `div`.
        // We do this because `section`s, `p`s, etc. can be interspersed within a talk.
        writeChildElementsOrSelf(bodyBlockDiv, out);
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

    private static void writeChildElementsOrSelf(WebElement element, PrintStream out) {
        writeChildElementsOrSelf(element, out, "", "");
    }

    private static void writeChildElementsOrSelf(WebElement element, PrintStream out, String start, String end) {
        final List<WebElement> children = element.findElements(By.xpath("./*"));
        if (children.size() > 0 && !areAllFormatting(children)) {
            for (WebElement child : children) {
                writeChildElement(child, out, start, end);
            }
        } else {
            writeEncoded(element, out, start, end);
        }
    }

    private static final Set<String> FORMATTING_TAGS = new HashSet<>();
    static {
        FORMATTING_TAGS.add("b");
        FORMATTING_TAGS.add("i");
        FORMATTING_TAGS.add("strong");
        FORMATTING_TAGS.add("em");
        FORMATTING_TAGS.add("a");
        // Superscripts should always be within an `a` element as a reference.
//        FORMATTING_TAGS.add("sup");
    }

    private static boolean areAllFormatting(List<WebElement> elements) {
        for (WebElement element : elements) {
            final String tagName = element.getTagName();
            if (!FORMATTING_TAGS.contains(tagName)) {
                return false;
            }
        }
        return true;
    }

    @SuppressWarnings("StatementWithEmptyBody")
    private static void writeChildElement(WebElement child, PrintStream out, String start, String end) {
        // TODO: Check if any text is being lost due to text outside of a non-formatting child element:
        // <div>Some text outside of a child paragraph element.
        //   <p>Some text inside of a paragraph element.</p>
        // </div>
        // Determine what to write based on this element's tag.
        final String tagName = child.getTagName();
        if ("p".equals(tagName)) {
            // We assume that a `p` doesn't contain any non-formatting child elements.
            writeEncoded(child, out, start, end);
        } else if ("h2".equals(tagName) || "h3".equals(tagName)) {
            // See `https://www.lds.org/languages/eng/content/general-conference/2017/10/exceeding-great-and-precious-promises`.
            // We assume that an `h#` doesn't contain any non-formatting child elements.
            writeEncoded(child, out, Encoding.HEADER_START, Encoding.HEADER_END);
        } else if ("header".equals(tagName)) {
            // Write the encoded header text.
            // A `header` may or may not have child elements.
            writeChildElementsOrSelf(child, out, Encoding.HEADER_START, Encoding.HEADER_END);
        } else if ("section".equals(tagName)) {
            // This child is a `section`, most likely containing a header.
            // See `https://www.lds.org/languages/eng/content/general-conference/2018/04/teaching-in-the-home-a-joyful-and-sacred-responsibility`.
            // See `https://www.lds.org/languages/eng/content/general-conference/2018/04/small-and-simple-things`.
            writeChildElementsOrSelf(child, out, start, end);
        } else if ("ul".equals(tagName)) {
            writeChildElementsOrSelf(child, out, start, end);
        } else if ("ol".equals(tagName)) {
            // See `https://www.lds.org/languages/eng/content/general-conference/1998/04/in-harms-way`.
            // In the case above, a `li` contains a poetry `div`.
            writeChildElementsOrSelf(child, out, start, end);
        } else if ("li".equals(tagName)) {
            // A `li` element may or may not have child elements.
            // If it does, they are usually `p`s.
            writeChildElementsOrSelf(child, out, start, end);
        } else if ("table".equals(tagName) ||
                "tbody".equals(tagName) ||
                "td".equals(tagName)) {
            // Usually statistical reports.
            // Don't bother with any special formatting.
            // See `https://www.lds.org/languages/eng/content/general-conference/2017/04/statistical-report-2016`.
            writeChildElementsOrSelf(child, out, start, end);
        } else if ("tr".equals(tagName)) {
            // Each `td` of a `tr` could be written on the same line, but for now we don't.
            writeChildElementsOrSelf(child, out, start, end);
        } else if ("div".equals(tagName)) {
            // This could be a `poetry` div.
            // See `https://www.lds.org/languages/eng/content/general-conference/2018/04/small-and-simple-things`.
            // For now, ignore any special `div` formatting.
            writeChildElementsOrSelf(child, out, start, end);
            // It could also be an `img` placeholder, in which case its only child element should be a `noscript`.
        } else if ("img".equals(tagName) ||
                "video".equals(tagName)) {
            // Ignore.
        } else if ("noscript".equals(tagName)) {
            // Usually a placeholder for an `img`.
            // If we scroll down, the `img` would load, but ChromeDriver has issues scrolling these pages.
            // The inner content of the `noscript` seems to be the same as the loaded `img`, anyway.
            // Ignore.
        } else if ("figure".equals(tagName)) {
            // Usually contains an `img` and a `p` as the caption.
            // See `https://www.lds.org/languages/eng/content/general-conference/2014/10/joseph-smith`.
            writeChildElementsOrSelf(child, out, start, end);
        } else {
            System.out.println("Unrecognized HTML tag `" + tagName + "`.");
        }
    }

    private static void writeEncoded(WebElement element, PrintStream out, String start, String end) {
        final String html = element.getAttribute("innerHTML").trim();
        final String text = encode(html);
        out.print(start);
        out.print(text);
        out.print(end);
        out.println();
    }

    /**
     * Encode reference numbers.
     * @param html Inner HTML from a paragraph of a talk.
     * @return A more readable text representation of a `p`, `header`, `h2`, or `li` element's inner HTML.
     */
    private static String encode(String html) {
        // Replace the superscript reference notation with double arrow brackets.
        return html.replaceAll("<a class=\"note-ref\" href=\"#note([0-9]+)\"><sup class=\"marker\">\\1</sup></a>",
                Encoding.REFERENCE_NUMBER_START + "$1" + Encoding.REFERENCE_NUMBER_END)
                // Ignore other HTML formatting tags, e.g. links, italics.
                .replaceAll("<([-a-zA-Z0-9]+).*?>(.*?)</\\1>", "$2")
                // Ignore self-closing tags.
                .replaceAll("<[^>]+?/>", "");
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
