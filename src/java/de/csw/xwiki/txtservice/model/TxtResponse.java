package de.csw.xwiki.txtservice.model;

import java.util.ArrayList;
import java.util.List;

public class TxtResponse extends TxtBase {

    private long timestamp;
    private String language;
    private String text;

    private List<Entity> entities = new ArrayList<>();
    private List<Tag> tags = new ArrayList<>();
    private List<Category> categories = new ArrayList<>();
    private List<DateRange> dates = new ArrayList<>();

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public List<Entity> getEntities() {
        return entities;
    }

    public void setEntities(List<Entity> entities) {
        this.entities = entities;
    }

    public List<Tag> getTags() {
        return tags;
    }

    public void setTags(List<Tag> tags) {
        this.tags = tags;
    }

    public List<Category> getCategories() {
        return categories;
    }

    public void setCategories(List<Category> categories) {
        this.categories = categories;
    }

    public List<DateRange> getDates() {
        return dates;
    }

    public void setDates(List<DateRange> dates) {
        this.dates = dates;
    }

}