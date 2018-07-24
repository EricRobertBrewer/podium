package com.ericrobertbrewer.podium;

import com.ericrobertbrewer.podium.web.DriverUtils;
import org.apache.commons.io.FileUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class ByuSpeechesScraper {

    public static void main(String[] args) throws IOException {
        if (args.length < 2 || args.length > 3) {
            throw new IllegalArgumentException("Usage: <driver-name> <path> [<force>]");
        }
        final String driverName = args[0];
        final String path = args[1];
        final WebDriver driver;
        if ("chrome".equalsIgnoreCase(driverName)) {
            System.setProperty("webdriver.chrome.driver", path);
            driver = new ChromeDriver();
        } else {
            throw new IllegalArgumentException("Unknown driver name: `" + driverName + "`.");
        }
        final boolean force;
        if (args.length > 2) {
            force = Boolean.parseBoolean(args[2]);
        } else {
            force = false;
        }
        final File rootFolder = new File(Folders.CONTENT_BYU_SPEECHES);
        if (!rootFolder.exists() && !rootFolder.mkdir()) {
            throw new RuntimeException("Unable to create root directory: `" + rootFolder.getPath() + "`.");
        }
        final ByuSpeechesScraper scraper = new ByuSpeechesScraper(driver, rootFolder);
        scraper.getAllSpeeches(force);
    }

    private final WebDriver driver;
    private final File rootFolder;

    private ByuSpeechesScraper(WebDriver driver, File rootFolder) {
        this.driver = driver;
        this.rootFolder = rootFolder;
    }

    private void getAllSpeeches(boolean force) throws IOException {
        driver.navigate().to("https://speeches.byu.edu/talks/");
        final List<String> years = new ArrayList<String>();
        final WebElement yearSelect = driver.findElement(By.id("speech-date-archive__year-selection"));
        final List<WebElement> yearOptions = yearSelect.findElements(By.tagName("option"));
        for (WebElement yearOption : yearOptions) {
            years.add(yearOption.getAttribute("value").trim());
        }
        for (String year : years) {
            getYear(year, force);
        }
    }

    private void getYear(String year, boolean force) throws IOException {
        // Create the year folder.
        final File folder = new File(rootFolder, year);
        if (folder.exists()) {
            if (force) {
                FileUtils.deleteDirectory(folder);
                createYearFolder(folder);
            } else {
                return;
            }
        } else {
            createYearFolder(folder);
        }
        // Navigate to the URL, if necessary.
        final String url = "https://speeches.byu.edu/talks/" + year + "/";
        if (!url.equals(driver.getCurrentUrl())) {
            driver.navigate().to(url);
        }
        // Scroll down a few times, each time waiting for a tenth of a second, then wait again.
        // This tends to help load all of the talks.
        scrollDown(driver, 25);
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
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
        summaryOut.println("title\tspeaker\tposition\tdate\ttype\ttopics\tfile\tnotes_file\turl");
        // Extract talk urls, titles, speakers, dates if available.
        final List<String> talkUrls = new ArrayList<String>();
        final List<String> titles = new ArrayList<String>();
        final List<String> speakers = new ArrayList<String>();
        final List<String> dates = new ArrayList<String>();
        final WebElement listDiv = driver.findElement(By.id("speech-date-archive-listing__talks-list"));
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
            final String speaker = DriverUtils.getElementTextOrEmpty(bylineSpan, By.className("image-excerpt-media-listing-block__speaker")).trim();
            speakers.add(speaker);
            final String date = DriverUtils.getElementTextOrEmpty(bylineSpan, By.className("image-excerpt-media-listing-block__date")).trim();
            dates.add(date);
        }
        for (int i = 0; i < talkUrls.size(); i++) {
            final String talkUrl = talkUrls.get(i);
            final String title = titles.get(i);
            final String speaker = speakers.get(i);
            final String date = dates.get(i);
            getSpeech(folder, talkUrl, title, speaker, date, summaryOut);
        }
        summaryOut.close();
        summaryOutputStream.close();
    }

    private void getSpeech(File folder, String url, String title, String speaker, String date, PrintStream summaryOut) throws IOException {
        // Navigate to this talk, if needed.
        if (!driver.getCurrentUrl().equals(url)) {
            driver.navigate().to(url);
        }
        final String position;
        final String type;
        final String topics;
        final String fileName;
        final String notesFileName;
        final WebElement main = DriverUtils.findElementOrNull(driver, By.tagName("main"));
        if (main != null) {
            // Extract position and type.
            final WebElement metaSection = main.findElement(By.className("section__speech-meta"));
            position = DriverUtils.getElementTextOrEmpty(metaSection, By.className("speech__speaker-position")).trim();
            final String dateAndType = DriverUtils.getElementTextOrEmpty(metaSection, By.className("speech__date"));
            final String[] dateAndTypeParts = dateAndType.split("•");
            if (dateAndTypeParts.length > 1) {
                type = dateAndTypeParts[1].trim();
            } else {
                type = "";
            }
            // Extract talk.
            final WebElement bodyDiv = DriverUtils.findElementOrNull(driver, By.className("body-copy__standard"));
            if (bodyDiv != null) {
                final String fileNameBase = getFileNameBase(url);
                // Create the speech file.
                fileName = fileNameBase + ".txt";
                final File file = new File(folder, fileName);
                if (!file.createNewFile()) {
                    throw new RuntimeException("Unable to create speech file: `" + file.getPath() + "`.");
                }
                if (!file.canWrite() && !file.setWritable(true)) {
                    throw new RuntimeException("Unable to write to speech file: `" + file.getPath() + "`.");
                }
                // Create the notes file.
                notesFileName = fileNameBase + "_notes.tsv";
                final File notesFile = new File(folder, notesFileName);
                if (!notesFile.createNewFile()) {
                    throw new RuntimeException("Unable to create notes file: `" + notesFile.getPath() + "`.");
                }
                if (!notesFile.canWrite() && !notesFile.setWritable(true)) {
                    throw new RuntimeException("Unable to write to notes file: `" + notesFile.getPath() + "`.");
                }
                topics = writeSpeechAndNotes(bodyDiv, file, notesFile);
            } else {
                // The page doesn't have a body content section.
                topics = "";
                fileName = "";
                notesFileName = "";
            }
        } else {
            // The link is most likely broken.
            // For example, `https://speeches.byu.edu/talks/%first_names%-%last_name%_challenges-mission-brigham-young-university/`.
            position = "";
            type = "";
            topics = "";
            fileName = "";
            notesFileName = "";
        }
        // Write to summary file.
        summaryOut.print(title + "\t" + speaker + "\t" + position + "\t" + date + "\t" + type + "\t" + topics);
        summaryOut.print("\t" + fileName + "\t" + notesFileName);
        summaryOut.print("\t" + url);
        summaryOut.println();
    }

    /**
     * Write this speech and its notes.
     * @param bodyDiv The container `div` which is the direct parent of all of the relevant `p`s and `h2`s.
     * @param file Of the speech.
     * @param notesFile Of the notes of the speech.
     * @return The list of topics assigned to this speech.
     * @throws IOException When I/O error occurs.
     */
    @SuppressWarnings("StringConcatenationInLoop")
    private String writeSpeechAndNotes(WebElement bodyDiv, File file, File notesFile) throws IOException {
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
                    final String pText = htmlToText(pHtml);
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
            } else if ("h2".equals(tagName)) {
                assert !hasReachedNotes;
                final String h2Html = child.getAttribute("innerHTML");
                final String h2Text = htmlToText(h2Html);
                out.println("{{" + h2Text + "}}");
            } else if ("h3".equals(tagName)) {
                assert !hasReachedNotes;
                final String h3Html = child.getAttribute("innerHTML");
                final String h3Text = htmlToText(h3Html);
                out.println("{{{" + h3Text + "}}}");
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
            } else if ("ul".equals(tagName)) {
                final List<WebElement> lis = child.findElements(By.tagName("li"));
                for (WebElement li : lis) {
                    final String liHtml = li.getAttribute("innerHTML");
                    final String liText = htmlToText(liHtml);
                    out.println("__" + liText);
                }
            } else if ("ol".equals(tagName)) {
                final List<WebElement> lis = child.findElements(By.tagName("li"));
                for (WebElement li : lis) {
                    final String liHtml = li.getAttribute("innerHTML");
                    final String liText = htmlToText(liHtml);
                    out.println("##" + liText);
                }
            } else {
                System.out.println("Unknown tag `" + tagName + "`.");
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

    private static void createYearFolder(File folder) {
        if (!folder.mkdirs()) {
            throw new RuntimeException("Unable to create year folder: `" + folder.getPath() + "`.");
        }
    }

    private static void scrollDown(WebDriver driver, int times) {
        for (int i = 0; i < times; i++) {
            ((JavascriptExecutor) driver).executeScript("window.scrollBy(0, 250)");
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private static String getFileNameBase(String url) {
        if (url.endsWith("/")) {
            return getFileNameBase(url.substring(0, url.length() - 1));
        }
        return url.substring(url.lastIndexOf('/') + 1);
    }

    private static String htmlToText(String html) {
        // Encode note reference super-scripts as `<<#>>`.
        // For now, leave all other sub-tags.
        return html.replaceAll("<sup>([0-9]+)</sup>", "<<$1>>");
    }
}
