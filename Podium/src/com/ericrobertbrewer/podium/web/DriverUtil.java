package com.ericrobertbrewer.podium.web;

import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.io.*;
import java.util.List;

public class DriverUtil {

    public static String getElementTextOrEmpty(WebDriver driver, By by) {
        try {
            final WebElement inner = driver.findElement(by);
            return inner.getText();
        } catch (NoSuchElementException e) {
            return "";
        }
    }

    public static String getElementTextOrEmpty(WebElement element, By by) {
        try {
            final WebElement inner = element.findElement(by);
            return inner.getText();
        } catch (NoSuchElementException e) {
            return "";
        }
    }

    public static WebElement findElementOrNull(WebDriver driver, By by) {
        try {
            return driver.findElement(by);
        } catch (NoSuchElementException e) {
            return null;
        }
    }

    public static WebElement findElementOrNull(WebElement element, By by) {
        try {
            return element.findElement(by);
        } catch (NoSuchElementException e) {
            return null;
        }
    }

    public static void writeElements(List<WebElement> elements, File file, TextExtractor extractor) throws IOException {
        if (!file.createNewFile()) {
            throw new RuntimeException("Unable to create file: `" + file.getPath() + "`.");
        }
        if (!file.canWrite() && !file.setWritable(true)) {
            throw new RuntimeException("Unable to write to file: `" + file.getPath() + "`.");
        }
        // Write each paragraph to the file.
        final OutputStream outputStream = new FileOutputStream(file);
        final PrintStream printStream = new PrintStream(outputStream);
        for (WebElement element : elements) {
            final String text = extractor.getText(element);
            printStream.println(text);
        }
        printStream.close();
        outputStream.close();
    }
}
