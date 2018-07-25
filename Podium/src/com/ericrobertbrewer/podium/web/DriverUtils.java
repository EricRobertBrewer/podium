package com.ericrobertbrewer.podium.web;

import org.openqa.selenium.*;

public class DriverUtils {

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

    private DriverUtils() {
    }
}
