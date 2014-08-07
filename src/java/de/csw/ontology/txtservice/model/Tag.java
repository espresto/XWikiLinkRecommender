package de.csw.ontology.txtservice.model;

public class Tag extends TxtBase {

    private String term;
    private double confidence;

    public String getTerm() {
        return term;
    }

    public void setTerm(String term) {
        this.term = term;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

}