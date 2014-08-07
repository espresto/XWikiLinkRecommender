package de.csw.xwiki.txtservice;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.phase.Initializable;

import de.csw.ontology.TextEnhancer;
import de.csw.util.Config;
import de.csw.xwiki.txtservice.model.Entity;
import de.csw.xwiki.txtservice.model.TxtResponse;

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
        
        // factor out to plain text extractor
        TreeMap<Integer, Integer> offsets = new TreeMap<>();
        String filteredText = extractPlainText(text, offsets);

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
            log.warn("text got modified by service call"); // todo: here diff ?
        }

        int offSet = 0;
        Set<String> alreadyFound = new HashSet<>();
        for (Entity e : response.getEntities()) {

            if (alreadyFound.contains(e.getLabel()) || alreadyFound.contains(e.getSurface())) {
                continue;
            }

            int start = getRealIndex(offsets, e.getStart()) + offSet;
            int end = getRealIndex(offsets, e.getEnd() - 1) + 1 + offSet;

            String e_text = text.substring(start, end);
            log.info("found " + e.getSurface() + " as " + e.getLabel() + " under " + e_text + ((e.getUri() != null) ? (" see also " + e.getUri()) : ""));

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

    // is copy & paste from XWikiTestServiceEnhancer
    private static final Pattern[] EXCLUDE_FROM_ENHANCEMENTS = {
        Pattern.compile("\\[\\[.*?\\]\\]"),
        Pattern.compile("\\{\\{(velocity|groovy|html|code).*?\\}\\}.*?\\{\\{/\\1\\}\\}", Pattern.DOTALL),
        Pattern.compile("\\{\\{(include).*?/\\}\\}"),
    };

    /**
     * Extract from text all phrases that are enclosed by '[' and ']' denoting
     * an xWiki link.
     * 
     * @param text
     *            text to parse
     */
    // TODO: here call plaintext renderer instead? operate on blocks?
    public String extractPlainText(String text, TreeMap<Integer, Integer> offsets) {
        final boolean fullcopy = false;
        if (text == null)
            throw new NullPointerException("Parameter text must not be null");

        if (text.isEmpty())
            return text;

        String cleanedText = text;
        for (Pattern pattern : EXCLUDE_FROM_ENHANCEMENTS) {
            StringBuffer plainText = new StringBuffer();
            Matcher matcher = pattern.matcher(cleanedText);
            while (matcher.find()) {
                matcher.appendReplacement(plainText, "");
                final int startOffset = plainText.length();
                int targetDeltaOffset = matcher.end() - matcher.start();

                int targetOffset = targetDeltaOffset;
                Entry<Integer, Integer> currentOffset = offsets.floorEntry(startOffset);
                if (currentOffset != null) {
                    targetOffset += currentOffset.getValue();
                }
                offsets.put(startOffset, targetOffset);

                // shift the larger entries appropriately, which we might have from earlier passes
                if (fullcopy) {
                    TreeMap<Integer, Integer> oldOffsets = new TreeMap<>(offsets);
                    offsets.clear();
                    for (Entry<Integer, Integer> entry : oldOffsets.entrySet()) {
                        if (entry.getKey() > startOffset) {
                            offsets.put(entry.getKey() - targetDeltaOffset, entry.getValue() + targetDeltaOffset);
                        } else {
                            offsets.put(entry.getKey(), entry.getValue());
                        }
                    }
                } else {
                    Map<Integer, Integer> upperMap = offsets.tailMap(startOffset + 1);
                    if (!upperMap.isEmpty()) {
                        // we need to remove the old positions first, in case some old and new indexes overlap
                        upperMap = new HashMap<>(upperMap);
                        offsets.entrySet().removeAll(upperMap.entrySet());

                        for (Entry<Integer, Integer> entry : upperMap.entrySet()) {
                            offsets.put(entry.getKey() - targetDeltaOffset, entry.getValue() + targetDeltaOffset);
                        }
                    }
                }
            }

            matcher.appendTail(plainText);
            cleanedText = plainText.toString();
        }
        return cleanedText;
    }

    public int getRealIndex(TreeMap<Integer, Integer> offsets, int index) {
        Entry<Integer, Integer> latestOffset = offsets.floorEntry(index);
        if (latestOffset != null) {
            index += latestOffset.getValue();
        }
        return index;
    }

}
