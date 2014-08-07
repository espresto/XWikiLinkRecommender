package de.csw.xwiki.txtservice;

import java.util.HashSet;
import java.util.Set;

import org.apache.log4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.phase.Initializable;

import de.csw.ontology.TextEnhancer;
import de.csw.util.Config;
import de.csw.xwiki.txtservice.model.Entity;
import de.csw.xwiki.txtservice.model.TxtResponse;
import de.csw.xwiki.txtservice.util.PlainTextView;

@Component("txt-service")
public class XWikiTextServiceEnhancer implements TextEnhancer, Initializable {

    private static final Logger log = Logger.getLogger(XWikiTextServiceEnhancer.class);

    private TxtClient client;

    private boolean initialized = false; 

    @Override
    public void initialize() {
        client = new TxtClient();        
        client.setTxtServiceUrl(Config.getAppProperty("neofonie.services.txt.serviceurl"));
        client.setTxtApiToken(Config.getAppProperty("neofonie.services.txt.apitoken"));
        initialized = client.isConfigured();
        if (!initialized) {
            log.warn("not initialized property; will do no enhancements");
        }
    }

    @Override
    public String enhance(String text) {

        if (!initialized) {
            if (log.isDebugEnabled()) {
                log.debug("not initialized; no enhancements");
            }
            return text;
        }
        
        PlainTextView plainTextView = new PlainTextView(text);
        String filteredText = plainTextView.getPlainText();

        TxtResponse response = client.analyze(filteredText);
        if (response == null) {
            log.info("no result from text service");
            return text;
        }

        if (log.isDebugEnabled()) {
            if (response.getLanguage() != null) {
                log.info("seems we have language " + response.getLanguage());
            }
        }

        if (!filteredText.equals(response.getText())) {
            log.warn("text got modified by service call");
        }

        int offSet = 0;
        Set<String> alreadyFound = new HashSet<>();
        for (Entity e : response.getEntities()) {

            if (alreadyFound.contains(e.getLabel()) || alreadyFound.contains(e.getSurface())) {
                continue;
            }

            int start = plainTextView.getOriginalPosition(e.getStart()) + offSet;
            int end = plainTextView.getOriginalPosition(e.getEnd() - 1) + 1 + offSet;

            String e_text = text.substring(start, end);
            if (log.isDebugEnabled()) {
                log.debug("found " + e.getSurface() + " as " + e.getLabel() + " under " + e_text + ((e.getUri() != null) ? (" see also " + e.getUri()) : ""));
            }

            // FIXME: we should html-attribute-escape instead of trusting the service
            String prefix = "(% class=\"txt\" title=\"" + e.getType() + "\" %)";
            String suffix = "(%%)";

            text = text.substring(0, start) + prefix + text.substring(start, end) + suffix + text.substring(end);

            offSet += prefix.length() + suffix.length();

            if (e.getLabel() != null) {
                alreadyFound.add(e.getLabel());
            } else {
                alreadyFound.add(e.getSurface());
            }
        }

        return text;
    }

}
