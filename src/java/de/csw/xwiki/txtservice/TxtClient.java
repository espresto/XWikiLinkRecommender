package de.csw.xwiki.txtservice;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.log4j.Logger;

import de.csw.xwiki.txtservice.model.Category;
import de.csw.xwiki.txtservice.model.DateRange;
import de.csw.xwiki.txtservice.model.Entity;
import de.csw.xwiki.txtservice.model.Tag;
import de.csw.xwiki.txtservice.model.TxtResponse;

public class TxtClient {

    private static final Logger log = Logger.getLogger(TxtClient.class);

    private MultiThreadedHttpConnectionManager connectionManager = new MultiThreadedHttpConnectionManager();
    private HttpClient client = new HttpClient(connectionManager);

    private String txtServiceUrl;
    private String txtApiToken;

    public TxtResponse analyze(String text) {

        TxtResponse result = null;

        if (!isConfigured()) {
            return result;
        }

        if (text == null || text.trim().isEmpty()) {
            return result;
        }

        PostMethod cmd = new PostMethod(getTxtServiceUrl());
        cmd.setRequestHeader("X-Api-Key", getTxtApiToken());
        cmd.getParams().setContentCharset("UTF-8");
        cmd.setParameter("text", text);
        cmd.setParameter("services", "entities,dates,tags,categories");
        // TODO: maybe not really, if we have many concurrent request
        cmd.setRequestHeader("Connection", "close");

        try {
            try {

                int status = client.executeMethod(cmd);

                // hard wire the encoding ... should be ok here, too
                String response = new String(cmd.getResponseBody(), "UTF-8");
                if (log.isDebugEnabled()) {
                    log.debug("enhancing content: got status " + status + " and response : " + response);
                }

                if (status == HttpStatus.SC_OK) {
                    ObjectMapper mapper = new ObjectMapper();
                    result = mapper.readValue(response, TxtResponse.class);
                } else {
                    log.warn("got status code " + status + " when querying service");
                }
            } catch (Exception e) {
                log.warn("error while calling txt service", e);
            }
        } finally {
            cmd.releaseConnection();
        }

        return result;
    }

    public static void main(String[] args) {
        try {
            TxtClient txt = new TxtClient();
            txt.setTxtServiceUrl(args[0]);
            txt.setTxtApiToken(args[1]);

            TxtResponse response = txt.analyze("Test München 07/22/1999 codecs");
            if (response == null) {
                log.info("error while analyzing: no reponse");
            } else {
                log.debug("parsed result to " + response);
                if (!response.getDates().isEmpty()) {
                    log.info("we have dates");
                    for (DateRange dates : response.getDates()) {
                        log.info("we have date " + dates.getSurface());
                    }
                }
                if (!response.getEntities().isEmpty()) {
                    log.info("we have entities");

                    for (Entity entity : response.getEntities()) {
                        log.info("we have entity " + entity.getSurface());
                    }
                }
                if (!response.getTags().isEmpty()) {
                    log.info("we have tags");
                    for (Tag tag : response.getTags()) {
                        log.info("we have tag " + tag.getTerm() + " with confidence " + tag.getConfidence());
                    }
                }
                if (!response.getCategories().isEmpty()) {
                    log.info("we have categories");
                    for (Category category : response.getCategories()) {
                        log.info("we have category " + category.getLabel() + " with confidence " + category.getConfidence());
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String getTxtServiceUrl() {
        return txtServiceUrl;
    }

    public void setTxtServiceUrl(String txtServiceUrl) {
        this.txtServiceUrl = txtServiceUrl;
    }

    public String getTxtApiToken() {
        return txtApiToken;
    }

    public void setTxtApiToken(String txtApiToken) {
        this.txtApiToken = txtApiToken;
    }

    public boolean isConfigured() {
        return txtApiToken != null && !"".equals(txtApiToken);
    }

}