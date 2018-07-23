package com.ericrobertbrewer.podium.web;

import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

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
}
