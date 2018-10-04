package com.ericrobertbrewer.podium.scrape;

import org.openqa.selenium.*;

import java.util.List;

public class DriverUtils {

    public static String getTextOrEmpty(WebDriver driver, By by) {
        try {
            final WebElement inner = driver.findElement(by);
            return inner.getText();
        } catch (NoSuchElementException e) {
            return "";
        }
    }

    public static String getTextOrEmpty(WebElement element, By by) {
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

    public static WebElement findElementWithAttributeOrNull(WebDriver driver, By by, String name, String value) {
        final List<WebElement> elements = driver.findElements(by);
        for (WebElement element : elements) {
            if (value.equals(element.getAttribute(name))) {
                return element;
            }
        }
        return null;
    }

    public static WebElement findElementWithAttributeOrNull(WebElement element, By by, String name, String value) {
        final List<WebElement> children = element.findElements(by);
        for (WebElement child : children) {
            if (value.equals(child.getAttribute(name))) {
                return child;
            }
        }
        return null;
    }

    /**
     * By 250 pixels.
     * @param driver The driver.
     * @param times Number of times to scroll down.
     * @param delayMillis Number of milliseconds to delay after each downward scroll.
     */
    public static void scrollDown(WebDriver driver, int times, long delayMillis) {
        for (int i = 0; i < times; i++) {
            ((JavascriptExecutor) driver).executeScript("window.scrollBy(0, 250);");
            try {
                Thread.sleep(delayMillis);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public static void scrollToBottom(WebDriver driver) {
        ((JavascriptExecutor) driver).executeScript("window.scrollTo(0, document.body.scrollHeight);");
    }

    public static String getLastComponent(String url) {
        if (url.endsWith("/")) {
            return getLastComponent(url.substring(0, url.length() - 1));
        }
        return url.substring(url.lastIndexOf('/') + 1);
    }

    private DriverUtils() {
    }
}
