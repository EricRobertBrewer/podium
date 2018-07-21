package com.ericrobertbrewer.podium.web;

import org.openqa.selenium.WebElement;

public interface TextExtractor {
    String getText(WebElement element);
}
