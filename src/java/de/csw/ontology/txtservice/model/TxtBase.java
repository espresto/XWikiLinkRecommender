package de.csw.ontology.txtservice.model;

import java.io.PrintWriter;
import java.io.StringWriter;

import com.fasterxml.jackson.databind.ObjectMapper;

public class TxtBase {

    public String toString() {
        StringWriter sw = new StringWriter();
        ObjectMapper om = new ObjectMapper();
        try {
            om.writeValue(sw, this);
        } catch (Exception e) {
            // this should not happen, but if ... 
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
        }
        return sw.toString();
    }

}