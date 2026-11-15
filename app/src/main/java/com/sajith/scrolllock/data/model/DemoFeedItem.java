package com.sajith.scrolllock.data.model;

/**
 * Card item displayed inside the Scroll-Lock Sandbox Feed demonstration.
 */
public class DemoFeedItem {
    private final String tag;
    private final String title;
    private final String body;

    public DemoFeedItem(String tag, String title, String body) {
        this.tag = tag;
        this.title = title;
        this.body = body;
    }

    public String getTag() {
        return tag;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }
}
