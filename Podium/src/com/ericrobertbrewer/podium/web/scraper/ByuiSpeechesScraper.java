package com.ericrobertbrewer.podium.web.scraper;

import com.ericrobertbrewer.podium.web.DriverUtils;
import com.ericrobertbrewer.podium.web.Encoding;
import org.apache.commons.io.FileUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.Select;

import java.io.*;
import java.net.URL;
import java.util.*;

/**
 * Through the persistence that we will some day do something with the encoded note reference numbers
 * and the magic of regular expressions, we are able to scrape the wild world of BYU-Idaho speeches.
 *
 * The year-by-year pages are beautifully formatted and rich with content.
 * The transcripts, however, are far from standardized.
 */
public class ByuiSpeechesScraper extends Scraper {

    private static final String ROOT_URL = "https://web.byui.edu/devotionalsandspeeches/";

    /**
     * A collection of speeches whose formatting will be more easily manually added than automatically parsed
     * because of [really] funky formatting.
     * Files will be created for these speeches, but they will be blank.
     */
    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    private static final Set<String> BLACKLIST_TRANSCRIPT_URLS = new HashSet<>();
    static {
    }

    public ByuiSpeechesScraper(WebDriver driver) {
        super(driver);
    }

    public void scrapeAll(File rootFolder, boolean force) {
        getDriver().navigate().to(ROOT_URL);
        // Allow years to load into the `<select>`.
        try {
            Thread.sleep(2000L);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        // Save root page source.
        final String sourceFileName = "root.html";
        try {
            writeSource(rootFolder, sourceFileName, force);
        } catch (IOException e) {
            e.printStackTrace();
        }
        // Collect years.
        final Select yearSelect = getYearSelect();
        final List<WebElement> options = yearSelect.getOptions();
        final List<String> years = new ArrayList<>();
        for (WebElement option : options) {
            final String text = option.getText();
            if (!"All Years".equalsIgnoreCase(text)) {
                years.add(text);
            }
        }
        for (String year : years) {
            try {
                scrapeYear(rootFolder, year, force);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private Select getYearSelect() {
        WebElement yearElement = DriverUtils.findElementWithAttributeOrNull(
                getDriver(), By.tagName("select"), "data-ng-model", "vm.year");
        if (yearElement != null) {
            return new Select(yearElement);
        }
        throw new RuntimeException("Unable to find year select element in root page.");
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
            throw new RuntimeException("Unable to create year folder `" + folder.getName() + "`.");
        }
        // Scrape this year.
        System.out.println("Starting year `" + year + "`.");
        if (!ROOT_URL.equalsIgnoreCase(getDriver().getCurrentUrl())) {
            getDriver().navigate().to(ROOT_URL);
        }
        // Allow years to load into the `<select>`.
        try {
            Thread.sleep(2000L);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        // Select the correct year in the `select` element. Allow the speeches list to populate.
        final Select yearSelect = getYearSelect();
        yearSelect.selectByVisibleText(year);
        try {
            Thread.sleep(2000L);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        // Write the page source for this year.
        final String sourceFileName = "year.html";
        writeSource(folder, sourceFileName, force);
        // Create the summary file. Write the column headers.
        final File summaryFile = com.ericrobertbrewer.podium.web.FileUtils.newFile(folder, "summary.tsv");
        final OutputStream summaryOutputStream = new FileOutputStream(summaryFile);
        final PrintStream summaryOut = new PrintStream(summaryOutputStream);
        summaryOut.println("title\tspeaker\tposition\tdate\ttype\ttranscript\tnotes\taudio\turl\tsource\taudio_url");
        // Extract title, speaker, position, date, type.
        final List<String> titles = new ArrayList<>();
        final List<String> speakers = new ArrayList<>();
        final List<String> positions = new ArrayList<>();
        final List<String> dates = new ArrayList<>();
        final List<String> types = new ArrayList<>();
        final List<String> transcriptUrls = new ArrayList<>();
        final List<String> audioUrls = new ArrayList<>();
        final WebElement speechesDiv = getDriver().findElement(By.className("speeches"));
        final WebElement speechesUl = speechesDiv.findElement(By.tagName("ul"));
        final List<WebElement> speechItems = speechesUl.findElements(By.tagName("li"));
        for (WebElement speechItem : speechItems) {
            final WebElement speechInfoDiv = speechItem.findElement(By.className("speechInfo"));
            final String title = DriverUtils.getTextOrEmpty(speechInfoDiv, By.className("speechTitle"));
            titles.add(title);
            final String speaker = DriverUtils.getTextOrEmpty(speechInfoDiv, By.className("speaker"));
            speakers.add(speaker);
            final String position = DriverUtils.getTextOrEmpty(speechInfoDiv, By.className("speakerPosition"));
            positions.add(position);
            // Extract date and type.
            final String date;
            final String type;
            final String dateAndType = DriverUtils.getTextOrEmpty(speechInfoDiv, By.className("speechDate"));
            final String[] dateAndTypeParts = dateAndType.split(" - ");
            if (dateAndTypeParts.length == 2) {
                date = standardizeDate(dateAndTypeParts[0]);
                type = dateAndTypeParts[1];
            } else {
                System.out.println("Unrecognized date and type: `" + dateAndType + "`.");
                date = "";
                type = "";
            }
            dates.add(date);
            types.add(type);
            // Extract the transcript and audio URLs.
            // Ignore the video for now.
            final String transcriptUrl;
            final String audioUrl;
            final WebElement speechLinksDiv = DriverUtils.findElementOrNull(speechItem, By.className("speechLinks"));
            if (speechLinksDiv != null) {
                final List<WebElement> as = speechLinksDiv.findElements(By.tagName("a"));
                transcriptUrl = getTranscriptUrl(as);
                audioUrl = getAudioUrl(as);
            } else {
                transcriptUrl = "";
                audioUrl = "";
            }
            transcriptUrls.add(transcriptUrl);
            audioUrls.add(audioUrl);
        }
        for (int i = 0; i < titles.size(); i++) {
            final String title = titles.get(i);
            final String speaker = speakers.get(i);
            final String position = positions.get(i);
            final String date = dates.get(i);
            final String type = types.get(i);
            final String transcriptUrl = transcriptUrls.get(i);
            final String audioUrl = audioUrls.get(i);
            scrapeSpeech(folder, transcriptUrl, audioUrl, title, speaker, position, date, type, summaryOut);
        }
        // Close summary file.
        summaryOut.close();
        summaryOutputStream.close();
    }

    private void scrapeSpeech(File yearFolder,
                              String transcriptUrl, String audioUrl,
                              String title, String speaker, String position, String date, String type,
                              PrintStream summaryOut) {
        final String fileName;
        final String notesFileName;
        final String audioFileName;
        final String sourceFileName;
        // Extract the file name base as the date, speaker, and title.
        final String fileNameBase = (date + "_" + speaker + "_" + title).toLowerCase()
                .replaceAll(" +", "_")
                .replaceAll("[:.,\"'()\\[\\]?]+", "");
        // Extract the text (transcript).
        if (BLACKLIST_TRANSCRIPT_URLS.contains(transcriptUrl)) {
            System.out.println("Skipping blacklisted speech `" + title + "`.");
            // Still navigate to the page, just to download the source.
            getDriver().navigate().to(transcriptUrl);
            // Save the page source.
            sourceFileName = fileNameBase + ".html";
            try {
                writeSource(yearFolder, sourceFileName);
            } catch (IOException e) {
                e.printStackTrace();
            }
            // Create new blank files for this speech.
            fileName = fileNameBase + ".txt";
            try {
                com.ericrobertbrewer.podium.web.FileUtils.newFile(yearFolder, fileName);
            } catch (IOException e) {
                e.printStackTrace();
            }
            notesFileName = fileNameBase + "_notes.tsv";
            try {
                com.ericrobertbrewer.podium.web.FileUtils.newFile(yearFolder, notesFileName);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else if (transcriptUrl.startsWith("http://www.byui.edu/speeches/") ||
                transcriptUrl.startsWith("http://www.byui.edu/devotionals/")) {
            // This is a more modern speech or devotional (post 2013).
            // There does not appear to be any structural difference between a speech page and a devotional page.
            // Navigate to the page.
            getDriver().navigate().to(transcriptUrl);
            // Save the page source.
            sourceFileName = fileNameBase + ".html";
            try {
                writeSource(yearFolder, sourceFileName);
            } catch (IOException e) {
                e.printStackTrace();
            }
            // Write the speech and the notes.
            final WebElement transcriptTextDiv = DriverUtils.findElementOrNull(getDriver(), By.id("transcript-text"));
            if (transcriptTextDiv != null) {
                fileName = fileNameBase + ".txt";
                notesFileName = fileNameBase + "_notes.tsv";
                try {
                    writeTranscriptAndNotes2(transcriptTextDiv, yearFolder, fileName, notesFileName);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                fileName = "";
                notesFileName = "";
            }
        } else if (transcriptUrl.startsWith("http://www.byui.edu/Presentations/Transcripts")) {
            // This is a transcript from roughly before 2013.
            System.out.println("Scraping speech `" + title + "`.");
            getDriver().navigate().to(transcriptUrl);
            // Save the page source.
            sourceFileName = fileNameBase + ".html";
            try {
                writeSource(yearFolder, sourceFileName);
            } catch (IOException e) {
                e.printStackTrace();
            }
            fileName = fileNameBase + ".txt";
            notesFileName = fileNameBase + "_notes.tsv";
            try {
                writeTranscriptAndNotes(yearFolder, fileName, notesFileName);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            // This speech has no transcript. It may be an older audio recording.
            // Transcripts appear to begin being published around 1997 - the Bednar era.
            System.out.println("Skipping speech without transcript `" + title + "`.");
            fileName = "";
            notesFileName = "";
            sourceFileName = "";
        }
        // Download the audio.
        if (audioUrl.length() > 0) {
            audioFileName = fileNameBase + ".mp3";
            try {
                System.out.println("Downloading audio file: `" + audioFileName + "`.");
                final File audioFile = com.ericrobertbrewer.podium.web.FileUtils.newFile(yearFolder, audioFileName);
                FileUtils.copyURLToFile(new URL(audioUrl), audioFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            audioFileName = "";
        }
        // Add this speech's information to the summary.
        summaryOut.print(title + "\t" + speaker + "\t" + position + "\t" + date + "\t" + type);
        summaryOut.print("\t" + fileName + "\t" + notesFileName + "\t" + audioFileName);
        summaryOut.print("\t" + transcriptUrl + "\t" + sourceFileName + "\t" + audioUrl);
        summaryOut.println();
    }

    /**
     * Attempt to scrape and write the text (transcript) and notes of this relatively modern speech to the given files.
     * @param yearFolder For the year.
     * @param fileName Of the speech.
     * @param notesFileName Of the notes of the speech.
     * @throws IOException When I/O error occurs.
     */
    private void writeTranscriptAndNotes2(WebElement transcriptTextDiv,
                                          File yearFolder, String fileName, String notesFileName) throws IOException {
        // Create the text file.
        final File file = com.ericrobertbrewer.podium.web.FileUtils.newFile(yearFolder, fileName);
        final OutputStream outputStream = new FileOutputStream(file);
        final PrintStream out = new PrintStream(outputStream);
        // Create the notes file.
        final File notesFile = com.ericrobertbrewer.podium.web.FileUtils.newFile(yearFolder, notesFileName);
        final OutputStream notesOutputStream = new FileOutputStream(notesFile);
        final PrintStream notesOut = new PrintStream(notesOutputStream);
        // Write the notes header.
        notesOut.println("id\tnote");
        final WebElement firstChild = transcriptTextDiv.findElement(By.xpath("./*"));
        // Skip MSO converted documents. They're just too difficult to parse.
        // See `http://www.byui.edu/devotionals/forest-gahn`.
        if ("MsoNormal".equals(firstChild.getAttribute("class"))) {
            // This speech seems to have been automatically converted to HTML from an MSO document. It's messy...
            System.out.println("Skipping MSO formatted speech `" + fileName + "`.");
        } else {
            // Other speeches seem to have fairly consistent formatting.
            System.out.println("Scraping modern speech `" + fileName + "`.");
            final ModernSpeechParser parser = new ModernSpeechParser();
            parser.writeChildElementsOrSelf(transcriptTextDiv, out, notesOut);
        }
        // Close files.
        notesOut.close();
        notesOutputStream.close();
        out.close();
        outputStream.close();
    }

    private static class ModernSpeechParser {

        boolean hasReachedNotes = false;
        /**
         * This will be set the first time the notes format is detected.
         * It is assumed that the notes format will never change within a speech.
         */
        NotesFormat notesFormat = null;
        /**
         * Used only when the notes format is a plain digit.
         */
        int currentNote = 1;

        ModernSpeechParser() {
        }

        enum NotesFormat {
            BRACKETED_DIGIT_LINK,
            BRACKETED_ROMAN_LOWERCASE_LINK,
            DIGIT_LINK,
            BRACKETED_DIGIT,
            BRACKETED_ROMAN_LOWERCASE,
            DIGIT
        }

        void writeChildElementsOrSelf(WebElement element, PrintStream out, PrintStream notesOut) {
            // Write the transcript, then write the notes.
            final List<WebElement> children = element.findElements(By.xpath("./*"));
            if (children.size() > 0 && !areAllFormatting(children)) {
                // Write every inner element.
                for (WebElement child : children) {
                    writeChildElement(child, out, notesOut);
                }
            } else {
                // Write this element, which has no relevant inner elements.
                final String html = element.getAttribute("innerHTML").trim();
                // Skip blank paragraphs.
                if (html.length() == 0) {
                    return;
                }
                if (!hasReachedNotes) {
                    // Split paragraphs by one or more line breaks.
                    // Line breaks (`<br>`s) are sometimes wrapped in formatting tags.
                    // See `http://www.byui.edu/speeches/bishop-w-christopher-waddell`.
                    final String[] htmlParts = html
                            .split("(?:<br */?>|<sup><br */?></sup>|<strong><br */?></strong>|<em><br */?></em>)+");
                    for (String htmlPart : htmlParts) {
                        htmlPart = htmlPart.trim();
                        // Check whether or not this paragraph part is a header.
                        // We assume that it's a header if it's entirely bold or italicized.
                        if (isInSingleTag(htmlPart, "strong") ||
                                isInSingleTag(htmlPart, "em") ||
                                isInSingleTag(htmlPart, "b")) {
                            writeTextIfNotEmpty(htmlPart, out, Encoding.HEADER_START, Encoding.HEADER_END);
                        } else {
                            writeTextIfNotEmpty(htmlPart, out);
                        }
                    }
                } else {
                    // We are reading the notes section.
                    final String decodedHtml = html
                            .replaceAll("</?(?:sup|strong|em)>", "")
                            .replaceAll("&nbsp;", " ")
                            .trim();
                    if (decodedHtml.length() == 0) {
                        return;
                    }
                    // Check for a note header.
                    // See `http://www.byui.edu/devotionals/elder-anthony-d-perkins`.
                    final String text = element.getText().trim();
                    if ("End Notes".equalsIgnoreCase(text)) {
                        return;
                    }
                    // Notes can be clumped into one giant paragraph `<p>`.
                    // This placeholder is used to replace expressions and split separate notes in the same paragraph.
                    final String noteSplitPlaceholder = "__NOTE_START__";
                    // Determine the note formatting.
                    if (notesFormat == NotesFormat.BRACKETED_DIGIT_LINK ||
                            notesFormat == NotesFormat.DIGIT_LINK ||
                            html.startsWith("<a ")) {
                        // The notes are hyperlinks.
                        // Check the formatting of the reference numbers.
                        if (notesFormat == NotesFormat.BRACKETED_DIGIT_LINK ||
                                text.startsWith("[1]")) {
                            notesFormat = NotesFormat.BRACKETED_DIGIT_LINK;
                            // The reference numbers are hyperlinked digits within square brackets.
                            // See `http://www.byui.edu/devotionals/rich-llewellyn`.
                            final String[] htmlNotes = html
                                    .replaceAll("(?: |&nbsp;)*<a .*?>(?:<span.*?>)?\\[([0-9]+)](?:</span>)?</a>(?: |&nbsp;)*", noteSplitPlaceholder + "$1|")
                                    .split(noteSplitPlaceholder);
                            for (String htmlNote : htmlNotes) {
                                if (htmlNote.length() == 0) {
                                    continue;
                                }
                                final String[] parts = htmlNote.trim().split("\\|");
                                if (parts.length != 2) {
                                    throw new RuntimeException("Unrecognized bracketed digit link note format: `" + htmlNote + "`.");
                                }
                                writeNote(notesOut, parts);
                            }
                        } else if (notesFormat == NotesFormat.DIGIT_LINK ||
                                text.startsWith("1.")) {
                            notesFormat = NotesFormat.DIGIT_LINK;
                            // The reference numbers are hyperlinked digits followed by a period.
                            // See `http://www.byui.edu/devotionals/president-kim-b-clark-winter-2014`.
                            final String[] htmlNotes = html
                                    .replaceAll("(?: |&nbsp;)*<a .*?>([0-9]+)</a>\\.?(?: |&nbsp;)*", noteSplitPlaceholder + "$1|")
                                    .split(noteSplitPlaceholder);
                            for (String htmlNote : htmlNotes) {
                                if (htmlNote.length() == 0) {
                                    continue;
                                }
                                final String[] parts = htmlNote.trim().split("\\|");
                                if (parts.length != 2) {
                                    throw new RuntimeException("Unrecognized digit link note format: `" + htmlNote + "`.");
                                }
                                writeNote(notesOut, parts);
                            }
                        } else if (notesFormat == NotesFormat.BRACKETED_ROMAN_LOWERCASE_LINK ||
                                text.startsWith("[i]")) {
                            notesFormat = NotesFormat.BRACKETED_ROMAN_LOWERCASE_LINK;
                            // Lowercase Roman numerals, bracketed links.
                            // See `http://www.byui.edu/devotionals/tim-rarick#_edn1`.
                            // Assume that there is always a link, but that a `<span>` is optional.
                            final String[] htmlNotes = html
                                    .replaceAll("(?: |&nbsp;)*<a .*?>(?:<span.*?>)?\\[([ivx]+)](?:</span>)?</a>(?: |&nbsp;)*", noteSplitPlaceholder + "$1|")
                                    .split(noteSplitPlaceholder);
                            for (String htmlNote : htmlNotes) {
                                if (htmlNote.length() == 0) {
                                    continue;
                                }
                                final String[] parts = htmlNote.trim().split("\\|");
                                if (parts.length != 2) {
                                    throw new RuntimeException("Unrecognized bracketed lowercase Roman numeral note format: `" + htmlNote + "`.");
                                }
                                writeNote(notesOut, parts);
                            }
                        } else {
                            throw new RuntimeException("Unrecognized link notes format: `" + html + "`.");
                        }
                    } else if (notesFormat == NotesFormat.BRACKETED_DIGIT ||
                            text.startsWith("[1]")) {
                        notesFormat = NotesFormat.BRACKETED_DIGIT;
                        // The notes are not hyperlinks, in square brackets.
                        // Optionally allow a `<span>` element to surround the brackets.
                        // See `http://www.byui.edu/devotionals/kyoung-dabell`.
                        final String[] htmlNotes = html
                                .replaceAll("(?: |&nbsp;)*(?:<span.*?>)?\\[([0-9]+)](?:</span>)?(?: |&nbsp;)*", noteSplitPlaceholder + "$1|")
                                .split(noteSplitPlaceholder);
                        for (String htmlNote : htmlNotes) {
                            if (htmlNote.length() == 0) {
                                continue;
                            }
                            final String[] parts = htmlNote.trim().split("\\|");
                            if (parts.length != 2) {
                                throw new RuntimeException("Unrecognized bracketed digit note format: `" + htmlNote + "`.");
                            }
                            writeNote(notesOut, parts);
                        }
                    } else if (notesFormat == NotesFormat.BRACKETED_ROMAN_LOWERCASE ||
                            text.startsWith("[i]")) {
                        notesFormat = NotesFormat.BRACKETED_ROMAN_LOWERCASE;
                        // Lowercase Roman numerals in brackets.
                        // See `http://www.byui.edu/devotionals/president-eyring-winter-2018`.
                        // The speech may intersperse hyperlinks, as in the above example ([ix]).
                        final String[] htmlNotes = html
                                .replaceAll("(?: |&nbsp;)*(?:<a .*?>)?(?:<span.*?>)?\\[([ivx]+)](?:</span>)?(?:</a>)?(?: |&nbsp;)*", noteSplitPlaceholder + "$1|")
                                .split(noteSplitPlaceholder);
                        for (String htmlNote : htmlNotes) {
                            if (htmlNote.length() == 0) {
                                continue;
                            }
                            final String[] parts = htmlNote.trim().split("\\|");
                            if (parts.length != 2) {
                                throw new RuntimeException("Unrecognized bracketed lowercase Roman numeral note format: `" + htmlNote + "`.");
                            }
                            writeNote(notesOut, parts);
                        }
                    } else if (notesFormat == NotesFormat.DIGIT ||
                            // Using `html` instead of `text` here because we assume that there is no surrounding `<span>`.
                            html.startsWith("1.")) {
                        notesFormat = NotesFormat.DIGIT;
                        // The reference numbers are not hyperlinks, nor within square brackets.
                        // See `http://www.byui.edu/devotionals/president-clark-g-and-sister-christine-c-gilbert`.
                        // Search the text iteratively for note reference numbers in ascending order.
                        int currentIndex = 0;
                        final String dotSpace = ". ";
                        while (true) {
                            final String sub = html.substring(currentIndex);
                            final String[] parts = new String[2];
                            // Extract the id number.
                            final int dotSpaceIndex = sub.indexOf(dotSpace);
                            parts[0] = sub.substring(0, dotSpaceIndex);
                            // Extract the note up to the start of the next id, or the end of the paragraph.
                            final int nextIndex = sub.indexOf("" + (currentNote + 1) + dotSpace);
                            if (nextIndex == -1) {
                                parts[1] = sub.substring(dotSpaceIndex + dotSpace.length());
                                writeNote(notesOut, parts);
                                break;
                            }
                            parts[1] = sub.substring(dotSpaceIndex + dotSpace.length(), nextIndex);
                            writeNote(notesOut, parts);
                            // Index is additive because substring is taken from `pHtml` (`pHtml` is not mutated).
                            currentNote++;
                            currentIndex += nextIndex;
                        }
                    } else {
                        System.err.println("Unrecognized notes format: `" + html + "`.");
                    }
                }
            }
        }

        void writeChildElement(WebElement child, PrintStream out, PrintStream notesOut) {
            final String tag = child.getTagName();
            if ("p".equals(tag)) {
                writeChildElementsOrSelf(child, out, notesOut);
            } else if ("hr".equals(tag)) {
                assert !hasReachedNotes;
                hasReachedNotes = true;
            } else if ("ul".equals(tag) ||
                    "ol".equals(tag)) {
                writeChildElementsOrSelf(child, out, notesOut);
            } else if ("li".equals(tag)) {
                writeChildElementsOrSelf(child, out, notesOut);
            } else if ("div".equals(tag)) {
                writeChildElementsOrSelf(child, out, notesOut);
            } else {
                System.err.println("Unrecognized HTML tag: `" + tag + "`.");
            }
        }

        static final Set<String> FORMATTING_TAGS = new HashSet<>();
        static {
            FORMATTING_TAGS.add("b");
            FORMATTING_TAGS.add("i");
            FORMATTING_TAGS.add("strong");
            FORMATTING_TAGS.add("em");
            FORMATTING_TAGS.add("a");
            FORMATTING_TAGS.add("sup");
            FORMATTING_TAGS.add("font");
            FORMATTING_TAGS.add("br");
            // Not text formatting, per se, but it is to be ignored for the purposes of the method below.
            FORMATTING_TAGS.add("img");
            FORMATTING_TAGS.add("s");
            FORMATTING_TAGS.add("u");
            FORMATTING_TAGS.add("g");
            // These seem to always accompany `<sup>` tags.
            FORMATTING_TAGS.add("span");
            FORMATTING_TAGS.add("sub");
        }

        boolean areAllFormatting(List<WebElement> elements) {
            for (WebElement element : elements) {
                final String tagName = element.getTagName();
                if (!FORMATTING_TAGS.contains(tagName)) {
                    return false;
                }
            }
            return true;
        }
    }

    private void writeTranscriptAndNotes(File yearFolder, String fileName, String notesFileName) throws IOException {
        // Create the text file.
        final File file = com.ericrobertbrewer.podium.web.FileUtils.newFile(yearFolder, fileName);
        final OutputStream outputStream = new FileOutputStream(file);
        final PrintStream out = new PrintStream(outputStream);
        // Create the notes file.
        final File notesFile = com.ericrobertbrewer.podium.web.FileUtils.newFile(yearFolder, notesFileName);
        final OutputStream notesOutputStream = new FileOutputStream(notesFile);
        final PrintStream notesOut = new PrintStream(notesOutputStream);
        // Write the notes header.
        notesOut.println("id\tnote");
        // Write the speech and notes.
        // The page format can very greatly.
        final WebElement contentSectionDiv = DriverUtils.findElementOrNull(getDriver(), By.id("content_section"));
        if (contentSectionDiv != null) {
            // Thin page w/ navigation, sans serif font.
            // See `http://www2.byui.edu/Presentations/Transcripts/Devotionals/2012_01_10_KimClark.htm`.
            final WebElement leftAreaDiv = DriverUtils.findElementOrNull(contentSectionDiv, By.className("leftAREA"));
            if (leftAreaDiv != null) {
                final ThinSpeechParser parser = new ThinSpeechParser();
                parser.writeChildElementsOrSelf(leftAreaDiv, out, notesOut);
            } else {
                System.err.println("Unrecognized speech format.");
            }
        } else {
            // Wide page, no navigation, serif font.
            // See `http://www2.byui.edu/Presentations/Transcripts/Devotionals/1999_01_05_Bednar.htm`.
            // Or possibly a wide page w/ navigation, serif font.
            // See `http://www2.byui.edu/Presentations/Transcripts/Devotionals/2002_01_08_Bednar.htm`.
            final WebElement body = getDriver().findElement(By.tagName("body"));
            final WideSpeechParser parser = new WideSpeechParser();
            parser.writeChildElementsOrSelf(body, out, notesOut);
        }
        // Close files.
        notesOut.close();
        notesOutputStream.close();
        out.close();
        outputStream.close();
    }

    private static class ThinSpeechParser {

        boolean hasReachedSpeech = false;
        boolean hasReachedNotes = false;

        ThinSpeechParser() {
        }

        void writeChildElementsOrSelf(WebElement element, PrintStream out, PrintStream notesOut) {
            writeChildElementsOrSelf(element, out, notesOut, "", "");
        }

        void writeChildElementsOrSelf(WebElement element, PrintStream out, PrintStream notesOut, String start, String end) {
            final List<WebElement> children = element.findElements(By.xpath("./*"));
            if (children.size() > 0 && !areAllFormatting(children)) {
                for (WebElement child : children) {
                    writeChildElement(child, out, notesOut, start, end);
                }
            } else {
                writeElementText(element, out, start, end);
            }
        }

        @SuppressWarnings("StatementWithEmptyBody")
        void writeChildElement(WebElement child, PrintStream out, PrintStream notesOut, String start, String end) {
            final String tag = child.getTagName();
            if ("hr".equals(tag)) {
                // A `<hr>` is assumed to separate the heading, speech content, and notes (if any).
                if (!hasReachedSpeech) {
                    hasReachedSpeech = true;
                } else if (!hasReachedNotes) {
                    hasReachedNotes = true;
                } else {
                    System.err.println("Extra <hr> element found.");
                }
            } else if ("p".equals(tag)) {
                if (!hasReachedSpeech) {
                    // Ignore text in the heading.
                } else if (!hasReachedNotes) {
                    // Write the text.
                    final String pHtml = child.getAttribute("innerHTML");
                    if (isInSingleTag(pHtml, "strong") ||
                            isInSingleTag(pHtml, "em")) {
                        writeTextIfNotEmpty(pHtml, out, Encoding.HEADER_START, Encoding.HEADER_END);
                    } else {
                        writeTextIfNotEmpty(pHtml, out);
                    }
                } else {
                    // Write the note.
                    final String pHtml = child.getAttribute("innerHTML")
                            .replaceAll("<sup>", "");
                    final String[] parts = pHtml.split("</sup> *?");
                    if (parts.length != 2) {
                        throw new RuntimeException("Unrecognized note format: `" + pHtml + "`.");
                    }
                    writeNote(notesOut, parts);
                }
            } else if ("blockquote".equals(tag)) {
                if (!hasReachedSpeech) {
                } else if (!hasReachedNotes) {
                    writeChildElementsOrSelf(child, out, notesOut, start, end);
                } else {
                    writeChildElementsOrSelf(child, out, notesOut, start, end);
                }
            } else if ("ul".equals(tag) ||
                    "ol".equals(tag)) {
                if (!hasReachedSpeech) {
                } else if (!hasReachedNotes) {
                    writeChildElementsOrSelf(child, out, notesOut, start, end);
                } else {
                    writeChildElementsOrSelf(child, out, notesOut, start, end);
                }
            } else if ("li".equals(tag)) {
                if (!hasReachedSpeech) {
                } else if (!hasReachedNotes) {
                    writeChildElementsOrSelf(child, out, notesOut, start, end);
                } else {
                    writeChildElementsOrSelf(child, out, notesOut, start, end);
                }
            } else {
                System.err.println("Unrecognized tag: `" + tag + "`.");
            }
        }

        static final Set<String> FORMATTING_TAGS = new HashSet<>();
        static {
            FORMATTING_TAGS.add("b");
            FORMATTING_TAGS.add("i");
            FORMATTING_TAGS.add("strong");
            FORMATTING_TAGS.add("em");
            FORMATTING_TAGS.add("a");
            FORMATTING_TAGS.add("sup");
            FORMATTING_TAGS.add("font");
        }

        static boolean areAllFormatting(List<WebElement> elements) {
            for (WebElement element : elements) {
                final String tagName = element.getTagName();
                if (!FORMATTING_TAGS.contains(tagName)) {
                    return false;
                }
            }
            return true;
        }
    }

    private static class WideSpeechParser {

        boolean hasReachedSpeech = false;
        boolean hasReachedNotes = false;

        WideSpeechParser() {
        }

        void writeChildElementsOrSelf(WebElement element, PrintStream out, PrintStream notesOut) {
            writeChildElementsOrSelf(element, out, notesOut, "", "");
        }

        void writeChildElementsOrSelf(WebElement element, PrintStream out, PrintStream notesOut, String start, String end) {
            final List<WebElement> children = element.findElements(By.xpath("./*"));
            if (children.size() > 0 && !areAllFormatting(children)) {
                for (WebElement child : children) {
                    writeChildElement(child, out, notesOut, start, end);
                }
            } else if (!hasReachedNotes) {
                final String[] parts = getSuperScriptNotesParts(element);
                writeNote(notesOut, parts);
            } else {
                writeElementText(element, out, start, end);
            }
        }

        @SuppressWarnings("StatementWithEmptyBody")
        void writeChildElement(WebElement child, PrintStream out, PrintStream notesOut, String start, String end) {
            final String tag = child.getTagName();
            if ("hr".equals(tag)) {
                if (!hasReachedSpeech) {
                    // The <hr> should only separate the speech from the notes.
                } else if (!hasReachedNotes) {
                    hasReachedNotes = true;
                } else {
                    System.err.println("Extra <hr> element found.");
                }
            } else if ("p".equals(tag)) {
                if (!hasReachedSpeech) {
                    if (isDate(child.getText())) {
                        hasReachedSpeech = true;
                    }
                } else if (!hasReachedNotes) {
                    // Write the text.
                    final String pHtml = child.getAttribute("innerHTML");
                    if (isInSingleTag(pHtml, "strong") ||
                            isInSingleTag(pHtml, "em")) {
                        writeTextIfNotEmpty(pHtml, out, Encoding.HEADER_START, Encoding.HEADER_END);
                    } else {
                        writeTextIfNotEmpty(pHtml, out);
                    }
                } else {
                    // Write the note.
                    // See `http://www2.byui.edu/Presentations/Transcripts/Devotionals/2008_01_15_Clark.htm`.
                    final String[] parts = getSuperScriptNotesParts(child);
                    writeNote(notesOut, parts);
                }
            } else if ("blockquote".equals(tag)) {
                if (!hasReachedSpeech) {
                    // Do nothing.
                } else if (!hasReachedNotes) {
                    writeChildElementsOrSelf(child, out, notesOut, start, end);
                } else {
                    writeChildElementsOrSelf(child, out, notesOut, start, end);
                }
            } else if ("ul".equals(tag) ||
                    "ol".equals(tag)) {
                if (!hasReachedSpeech) {
                    // Do nothing.
                } else if (!hasReachedNotes) {
                    writeChildElementsOrSelf(child, out, notesOut, start, end);
                } else {
                    writeChildElementsOrSelf(child, out, notesOut, start, end);
                }
            } else if ("li".equals(tag)) {
                if (!hasReachedSpeech) {
                    // Do nothing.
                } else if (!hasReachedNotes) {
                    writeChildElementsOrSelf(child, out, notesOut, start, end);
                } else {
                    writeChildElementsOrSelf(child, out, notesOut, start, end);
                }
            } else {
                System.err.println("Unrecognized tag: `" + tag + "`.");
            }
        }

        static final Set<String> FORMATTING_TAGS = new HashSet<>();
        static {
            FORMATTING_TAGS.add("b");
            FORMATTING_TAGS.add("i");
            FORMATTING_TAGS.add("strong");
            FORMATTING_TAGS.add("em");
            FORMATTING_TAGS.add("a");
            FORMATTING_TAGS.add("sup");
            FORMATTING_TAGS.add("font");
        }

        static boolean areAllFormatting(List<WebElement> elements) {
            for (WebElement element : elements) {
                final String tagName = element.getTagName();
                if (!FORMATTING_TAGS.contains(tagName)) {
                    return false;
                }
            }
            return true;
        }

        static boolean isDate(String text) {
            return text.startsWith("January") ||
                    text.startsWith("February") ||
                    text.startsWith("March") ||
                    text.startsWith("April") ||
                    text.startsWith("May") ||
                    text.startsWith("June") ||
                    text.startsWith("July") ||
                    text.startsWith("August") ||
                    text.startsWith("September") ||
                    text.startsWith("October") ||
                    text.startsWith("November") ||
                    text.startsWith("December");
        }
    }

    private static final Map<String, Integer> MONTH_TO_INDEX = new HashMap<>();
    static {
        MONTH_TO_INDEX.put("Jan", 0);
        MONTH_TO_INDEX.put("Feb", 1);
        MONTH_TO_INDEX.put("Mar", 2);
        MONTH_TO_INDEX.put("Apr", 3);
        MONTH_TO_INDEX.put("May", 4);
        MONTH_TO_INDEX.put("Jun", 5);
        MONTH_TO_INDEX.put("Jul", 6);
        MONTH_TO_INDEX.put("Aug", 7);
        MONTH_TO_INDEX.put("Sep", 8);
        MONTH_TO_INDEX.put("Oct", 9);
        MONTH_TO_INDEX.put("Nov", 10);
        MONTH_TO_INDEX.put("Dec", 11);
    }

    /**
     * Return a textual date as `YYYY-MM-dd`.
     * @param date A textual date, as shown on the speeches website. Usually as `dd MMM YYYY`.
     * @return The formatted date.
     */
    private static String standardizeDate(String date) {
        final String[] parts = date.split(" ");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Unrecognized date format: `" + date + "`.");
        }
        if (!MONTH_TO_INDEX.containsKey(parts[1])) {
            throw new IllegalArgumentException("Unrecognized month: `" + parts[1] + "`.");
        }
        final int dd = Integer.parseInt(parts[0]);
        final int mm = MONTH_TO_INDEX.get(parts[1]) + 1;
        final int yyyy = Integer.parseInt(parts[2]);
        return "" + yyyy + "-" +
                (mm < 10 ? "0" : "") + mm + "-" +
                (dd < 10 ? "0" : "") + dd;
    }

    private static String getTranscriptUrl(List<WebElement> as) {
        String url = "";
        for (WebElement a : as) {
            final String href = a.getAttribute("href");
            final String title = a.getAttribute("title");
            if (href != null && title != null && title.startsWith("Click to read the transcript")) {
                return href;
            }
        }
        return url;
    }

    private static String getAudioUrl(List<WebElement> as) {
        String url = "";
        for (WebElement a : as) {
            final String href = a.getAttribute("href");
            if (href != null && href.endsWith(".mp3")) {
                return href;
            }
        }
        return url;
    }

    private static boolean isInSingleTag(String html, String tag) {
        final String start = "<" + tag + ">";
        final String end = "</" + tag + ">";
        return html.startsWith(start) &&
                html.endsWith(end) &&
                html.indexOf(end) == html.length() - end.length();
    }

    private static void writeElementText(WebElement element, PrintStream out) {
        writeElementText(element, out, "", "");
    }

    private static void writeElementText(WebElement element, PrintStream out, String start, String end) {
        final String html = element.getAttribute("innerHTML");
        writeTextIfNotEmpty(html, out, start, end);
    }

    private static void writeTextIfNotEmpty(String html, PrintStream out) {
        writeTextIfNotEmpty(html, out, "", "");
    }

    private static void writeTextIfNotEmpty(String html, PrintStream out, String start, String end) {
        final String text = encode(html);
        if (text.length() == 0 ||
                // The text does not contain any alphabetic characters.
                // Some speeches include lines of dashes to separate paragraphs.
                // See `http://www.byui.edu/devotionals/kelly-burgener`.
                !text.matches(".*?[a-zA-Z].*?")) {
            return;
        }
        out.println(start + text + end);
    }

    /**
     * Encode reference numbers, then strip away any unwanted HTML tags.
     * @param html Which may contain encoded reference numbers (as super/subscript numbers), or other HTML tags.
     * @return The encoded text.
     */
    private static String encode(String html) {
        final String encoded = html
                // Encode note reference numbers that are hyperlinks.
                // See `http://www.byui.edu/devotionals/president-kim-b-clark-winter-2014`.
                .replaceAll("<a .*?><sup.*?> *\\[?([0-9]+|[ivx]+)]? *</sup></a>",
                        Encoding.REFERENCE_NUMBER_START + "$1" + Encoding.REFERENCE_NUMBER_END)
                // Encode note reference numbers in square brackets.
                // See `http://www.byui.edu/speeches/bishop-w-christopher-waddell`.
                // Allow lowercase Roman numerals.
                // See `http://www.byui.edu/devotionals/president-eyring-winter-2018`.
                // Sometimes, reference numbers in text are in subscript instead of superscript.
                // Reference numbers may be enclosed in `<span>` elements.
                // See `http://www.byui.edu/devotionals/elder-k-brett-nattress`.
                .replaceAll("<(sup|sub).*?> *(?:<span.*?>)? *\\[?([0-9]+|[ivx]+)]? *(?:</span>)? *</\\1>",
                        Encoding.REFERENCE_NUMBER_START + "$2" + Encoding.REFERENCE_NUMBER_END)
                // Allow bracketed reference numbers to exist outside of a superscript tag.
                // See `http://www.byui.edu/devotionals/president-eyring-winter-2018` (reference [ix]).
                .replaceAll("(?:<a.*?>)? *\\[([0-9]+|[ivx]+)] *(?:</a.*?>)?",
                        Encoding.REFERENCE_NUMBER_START + "$1" + Encoding.REFERENCE_NUMBER_END);
        return decode(encoded);
    }

    /**
     * Decode a string which may include HTML tags.
     * Note: Be sure to never clobber encodings added from the method above!!
     * @param html Possibly containing HTML tags.
     * @return The decoded text.
     */
    private static String decode(String html) {
        return html
                // Replace non-breakable spaces.
                .replaceAll("&nbsp;", " ")
                // Replace ampersands.
                .replaceAll("&amp;", "&")
                // Ignore all other known HTML formatting tags (links, italics, etc.).
//                .replaceAll("<([-a-zA-Z0-9]+).*?>(.*?)</\\1>", "$2")
                .replaceAll("(?<!<)</?(?:sup|strong|em|b|i|font|style|s|a|u|sub|g).*?>(?!>)", "")
                // Ignore self-closing HTML tags.
                .replaceAll("<[^>]+?/>", "")
                // Ignore images (for now!!).
                // Since an `<img>` is a Void Element, it doesn't need a closing tag or closing slash in HTML5.
                // Reference: `https://stackoverflow.com/questions/7366344/do-we-still-need-end-slashes-in-html5`.
                .replaceAll("<img .*?>", "")
                .trim();
    }

    private static String[] getSuperScriptNotesParts(WebElement element) {
        final String html = element.getAttribute("innerHTML")
                .replaceAll("<sup>", "");
        final String[] parts = html.split("</sup> *?");
        if (parts.length != 2) {
            throw new RuntimeException("Unrecognized note format: `" + html + "`.");
        }
        return parts;
    }

    private static void writeNote(PrintStream notesOut, String[] parts) {
        // ID may be a [lowercase] Roman numeral.
        final String id = parts[0];
        notesOut.print("" + id);
        final String note = decode(parts[1]);
        notesOut.print("\t" + note);
        notesOut.println();
    }
}
