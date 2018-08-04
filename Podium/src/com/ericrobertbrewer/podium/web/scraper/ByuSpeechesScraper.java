package com.ericrobertbrewer.podium.web.scraper;

import com.ericrobertbrewer.podium.web.DriverUtils;
import com.ericrobertbrewer.podium.web.Encoding;
import org.apache.commons.io.FileUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class ByuSpeechesScraper extends Scraper {

    public ByuSpeechesScraper(WebDriver driver) {
        super(driver);
    }

    public void scrapeAll(File rootFolder, boolean force) {
        getDriver().navigate().to("https://speeches.byu.edu/talks/");
        // Write the page source.
        final String sourceFileName = "root.html";
        try {
            writeSource(rootFolder, sourceFileName, force);
        } catch (IOException e) {
            e.printStackTrace();
        }
        // Write each year.
        final List<String> years = new ArrayList<>();
        final WebElement yearSelect = getDriver().findElement(By.id("speech-date-archive__year-selection"));
        final List<WebElement> yearOptions = yearSelect.findElements(By.tagName("option"));
        for (WebElement yearOption : yearOptions) {
            years.add(yearOption.getAttribute("value").trim());
        }
        for (String year : years) {
            try {
                scrapeYear(rootFolder, year, force);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void scrapeYear(File rootFolder, String year, boolean force) throws IOException {
        // Create the year folder.
        final File folder = new File(rootFolder, year);
        if (folder.exists()) {
            if (force) {
                FileUtils.deleteDirectory(folder);
            } else {
                return;
            }
        }
        if (!folder.mkdirs()) {
            throw new RuntimeException("Unable to create year folder: `" + folder.getPath() + "`.");
        }
        // Navigate to the URL, if necessary.
        final String url = "https://speeches.byu.edu/talks/" + year + "/";
        if (!url.equals(getDriver().getCurrentUrl())) {
            getDriver().navigate().to(url);
        }
        System.out.println("Starting year `" + year + "`.");
        // Scroll down a few times, each time waiting for a tenth of a second, then wait again.
        // This tends to help load all of the talks.
        DriverUtils.scrollDown(getDriver(), 25, 100L);
        try {
            Thread.sleep(3000L);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        // Write the page source for this year.
        final String sourceFileName = "year.html";
        writeSource(folder, sourceFileName, force);
        // Create the summary file. Write the column headers.
        final File summaryFile = new File(folder, "summary.tsv");
        if (!summaryFile.createNewFile()) {
            throw new RuntimeException("Unable to create summary file in `" + folder.getName() + "`.");
        }
        if (!summaryFile.canWrite() && !summaryFile.setWritable(true)) {
            throw new RuntimeException("Unable to write to summary file: `" + summaryFile.getPath() + "`.");
        }
        final OutputStream summaryOutputStream = new FileOutputStream(summaryFile);
        final PrintStream summaryOut = new PrintStream(summaryOutputStream);
        summaryOut.println("title\tspeaker\tposition\tdate\ttype\ttopics\ttext\tnotes\turl\tsource");
        // Extract talk urls, titles, speakers, dates if available.
        final List<String> talkUrls = new ArrayList<>();
        final List<String> titles = new ArrayList<>();
        final List<String> speakers = new ArrayList<>();
        final List<String> dates = new ArrayList<>();
        final WebElement listDiv = getDriver().findElement(By.id("speech-date-archive-listing__talks-list"));
        final List<WebElement> listItems = listDiv.findElements(By.tagName("li"));
        for (WebElement listItem : listItems) {
            final WebElement rightDiv = listItem.findElement(By.className("image-excerpt-media-listing-block__right"));
            // Extract URL and title.
            final WebElement titleH2 = rightDiv.findElement(By.className("image-excerpt-media-listing-block__title"));
            final WebElement talkA = titleH2.findElement(By.tagName("a"));
            final String talkUrl = talkA.getAttribute("href");
            talkUrls.add(talkUrl);
            final String title = talkA.getText().trim();
            titles.add(title);
            // Extract speaker and date.
            final WebElement bylineSpan = rightDiv.findElement(By.className("image-excerpt-media-listing-block__byline"));
            final String speaker = DriverUtils.getTextOrEmpty(bylineSpan, By.className("image-excerpt-media-listing-block__speaker")).trim();
            speakers.add(speaker);
            final String date = DriverUtils.getTextOrEmpty(bylineSpan, By.className("image-excerpt-media-listing-block__date")).trim();
            dates.add(date);
        }
        for (int i = 0; i < talkUrls.size(); i++) {
            final String talkUrl = talkUrls.get(i);
            final String title = titles.get(i);
            final String speaker = speakers.get(i);
            final String date = dates.get(i);
            scrapeSpeech(folder, talkUrl, title, speaker, date, summaryOut);
        }
        summaryOut.close();
        summaryOutputStream.close();
    }

    private void scrapeSpeech(File folder, String url, String title, String speaker, String date, PrintStream summaryOut) {
        // Navigate to this talk, if needed.
        if (!getDriver().getCurrentUrl().equals(url)) {
            getDriver().navigate().to(url);
        }
        System.out.println("Starting speech `" + title + "`.");
        final String fileNameBase = getFileNameBase(url);
        final String sourceFileName = fileNameBase + ".html";
        try {
            writeSource(folder, sourceFileName);
        } catch (IOException e) {
            e.printStackTrace();
        }
        final String position;
        final String type;
        final String topics;
        final String fileName;
        final String notesFileName;
        final WebElement main = DriverUtils.findElementOrNull(getDriver(), By.tagName("main"));
        if (main != null) {
            // Extract position and type.
            final WebElement metaSection = main.findElement(By.className("section__speech-meta"));
            position = DriverUtils.getTextOrEmpty(metaSection, By.className("speech__speaker-position")).trim();
            final String dateAndType = DriverUtils.getTextOrEmpty(metaSection, By.className("speech__date"));
            final String[] dateAndTypeParts = dateAndType.split("•");
            if (dateAndTypeParts.length > 1) {
                type = dateAndTypeParts[1].trim();
            } else {
                type = "";
            }
            // Extract talk.
            final WebElement bodyDiv = DriverUtils.findElementOrNull(getDriver(), By.className("body-copy__standard"));
            if (bodyDiv != null) {
                fileName = fileNameBase + ".txt";
                notesFileName = fileNameBase + "_notes.tsv";
                topics = getTopicsAndWriteSpeechAndNotes(bodyDiv, folder, fileName, notesFileName);
            } else {
                // The page doesn't have a body content section.
                topics = "";
                fileName = "";
                notesFileName = "";
            }
        } else {
            // The link is most likely broken.
            // See `https://speeches.byu.edu/talks/%first_names%-%last_name%_challenges-mission-brigham-young-university/`.
            // On the page `https://speeches.byu.edu/talks/2017/`.
            position = "";
            type = "";
            topics = "";
            fileName = "";
            notesFileName = "";
        }
        // Write to summary file.
        summaryOut.print(title + "\t" + speaker + "\t" + position + "\t" + date + "\t" + type + "\t" + topics);
        summaryOut.print("\t" + fileName + "\t" + notesFileName);
        summaryOut.print("\t" + url + "\t" + sourceFileName);
        summaryOut.println();
    }

    private String getTopicsAndWriteSpeechAndNotes(WebElement bodyDiv, File folder, String fileName, String notesFileName) {
        try {
            return writeSpeechAndNotes(bodyDiv, folder, fileName, notesFileName);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "";
    }

    /**
     * Write this speech and its notes.
     * @param bodyDiv The container `div` which is the direct parent of all of the relevant `p`s and `h2`s.
     * @param folder Of the year.
     * @param fileName Of the speech.
     * @param notesFileName Of the notes of the speech.
     * @return The list of topics assigned to this speech.
     * @throws IOException When I/O error occurs.
     */
    @SuppressWarnings("StringConcatenationInLoop")
    private String writeSpeechAndNotes(WebElement bodyDiv, File folder, String fileName, String notesFileName) throws IOException {
        // Create the speech file.
        final File file = new File(folder, fileName);
        if (!file.createNewFile()) {
            throw new RuntimeException("Unable to create speech file: `" + file.getPath() + "`.");
        }
        if (!file.canWrite() && !file.setWritable(true)) {
            throw new RuntimeException("Unable to write to speech file: `" + file.getPath() + "`.");
        }
        // Create the notes file.
        final File notesFile = new File(folder, notesFileName);
        if (!notesFile.createNewFile()) {
            throw new RuntimeException("Unable to create notes file: `" + notesFile.getPath() + "`.");
        }
        if (!notesFile.canWrite() && !notesFile.setWritable(true)) {
            throw new RuntimeException("Unable to write to notes file: `" + notesFile.getPath() + "`.");
        }
        final OutputStream outputStream = new FileOutputStream(file);
        final PrintStream out = new PrintStream(outputStream);
        final OutputStream notesOutputStream = new FileOutputStream(notesFile);
        final PrintStream notesOut = new PrintStream(notesOutputStream);
        notesOut.println("id\tnote");
        // Start reading the speech and notes on the page.
        String topics = "";
        int notesCount = 0;
        boolean hasReachedNotes = false;
        // Get every top-level child of the body `div`.
        final List<WebElement> children = bodyDiv.findElements(By.xpath("./*"));
        for (WebElement child : children) {
            final String tagName = child.getTagName();
            final String text = child.getText().trim();
            final String className = child.getAttribute("class");
            if ("p".equals(tagName)) {
                // Check whether we have reached the notes section.
                if (!hasReachedNotes && "NOTES:".equals(text)) {
                    hasReachedNotes = true;
                    continue;
                }
                final String pHtml = child.getAttribute("innerHTML");
                if (!hasReachedNotes) {
                    final String pText = encode(pHtml);
                    out.println(pText);
                } else {
                    // Determine whether or not this is a new note.
                    final int dotSpaceIndex = pHtml.indexOf(". ");
                    if (dotSpaceIndex != -1) {
                        try {
                            final int noteId = Integer.parseInt(pHtml.substring(0, dotSpaceIndex));
                            // At this point, we know that are reading a new note.
                            // Close the previously-written note [line] in the file, if one exists.
                            if (noteId > 1) {
                                notesOut.println();
                            }
                            // Start writing the current note [line] in the file.
                            notesOut.print("" + noteId + "\t");
                            // Update the notes count.
                            notesCount = noteId;
                        } catch (NumberFormatException e) {
                            // The same note is broken up into multiple `p`s.
                            notesOut.print("||" + pHtml);
                        }
                    } else {
                        // A `. ` pattern did not appear at all.
                        // The same note is broken up into multiple `p`s.
                        notesOut.print("||" + pHtml);
                    }
                }
            } else if ("h2".equals(tagName) || "h3".equals(tagName)) {
                assert !hasReachedNotes;
                final String hHtml = child.getAttribute("innerHTML");
                final String hText = encode(hHtml);
                out.println(Encoding.HEADER_START + hText + Encoding.HEADER_END);
            } else if ("div".equals(tagName)) {
                // A `div` is usually a related topics/talks sections.
                if ("related_topics_and_talks".equals(className)) {
                    final WebElement relatedTopicsDiv = DriverUtils.findElementOrNull(child, By.className("related_topics_wrapper"));
                    if (relatedTopicsDiv != null) {
                        final List<WebElement> as = relatedTopicsDiv.findElements(By.tagName("a"));
                        for (WebElement a : as) {
                            final String aText = a.getText().trim();
                            if (topics.length() > 0) {
                                topics += ",";
                            }
                            topics += aText;
                        }
                    }
                }
            } else if ("ul".equals(tagName) || "ol".equals(tagName)) {
                final List<WebElement> lis = child.findElements(By.tagName("li"));
                for (WebElement li : lis) {
                    final String liHtml = li.getAttribute("innerHTML");
                    final String liText = encode(liHtml);
                    out.println(liText);
                }
            } else if ("i".equals(tagName)) {
                // For example, `https://speeches.byu.edu/talks/dilworth-b-parkinson_received-need/`.
                final String iHtml = child.getAttribute("innerHTML");
                final String iText = encode(iHtml);
                if (iText.length() > 0) {
                    out.println(iText);
                }
            } else {
                System.out.println("Unknown tag `" + tagName + "` in page for `" + file.getPath() + "`.");
            }
        }
        // Close a previously-written note [line], if one exists.
        if (notesCount > 0) {
            notesOut.println();
        }
        // Close files.
        notesOut.close();
        notesOutputStream.close();
        out.close();
        outputStream.close();
        return topics;
    }

    private static String getFileNameBase(String url) {
        if (url.endsWith("/")) {
            return getFileNameBase(url.substring(0, url.length() - 1));
        }
        return url.substring(url.lastIndexOf('/') + 1);
    }

    private static String encode(String html) {
        // Encode note reference super-scripts as `<<#>>`.
        return html.replaceAll("<sup>([0-9]+)</sup>",
                Encoding.REFERENCE_NUMBER_START + "$1" + Encoding.REFERENCE_NUMBER_END)
                // Ignore all other HTML formatting tags (links, italics, etc.).
                .replaceAll("<([-a-zA-Z0-9]+).*?>(.*?)</\\1>", "$2")
                // Ignore self-closing tags.
                .replaceAll("<[^>]+?/>", "");
    }
}
