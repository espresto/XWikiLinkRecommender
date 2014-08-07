package de.csw.ontology.txtservice.model;

public class Date extends TxtBase {

    private boolean bc;
    private int day, month, year;

    public boolean isBc() {
        return bc;
    }

    public void setBc(boolean bc) {
        this.bc = bc;
    }

    public int getDay() {
        return day;
    }

    public void setDay(int day) {
        this.day = day;
    }

    public int getMonth() {
        return month;
    }

    public void setMonth(int month) {
        this.month = month;
    }

    public int getYear() {
        return year;
    }

    public void setYear(int year) {
        this.year = year;
    }

}