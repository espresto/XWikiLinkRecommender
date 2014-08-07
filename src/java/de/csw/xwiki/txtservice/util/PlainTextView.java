package de.csw.xwiki.txtservice.util;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * a helper class that extracts the plain text from wiki content.
 * This extraction does not only extract the text but also contains information
 * how to identify parts in the original text.
 * This information is only valid of the original content is not modified, of course.
 * The extraction only support xwiki/2.1 syntax.
 * in principle it could be extendable to other syntaxes.
 */
public class PlainTextView {

    protected final String originalText;
    protected String plainText;

    protected TreeMap<Integer, Integer> offsets = new TreeMap<>();

    
    public PlainTextView(String text) {
        this.originalText = text;
        extractPlainText();
    }
    

    // is copy & paste from XWikiTestServiceEnhancer
    private static final Pattern[] EXCLUDE_FROM_ENHANCEMENTS = {
        Pattern.compile("\\{\\{(velocity|groovy|html|code|comment).*?\\}\\}.*?\\{\\{/\\1\\}\\}", Pattern.DOTALL),
        Pattern.compile("\\{\\{(include).*?/\\}\\}"),
        Pattern.compile("\\[\\[.*?\\]\\]"),
    };

    // alternatively we could use plainText renderer instead, or try to operate on blocks
    // but in these cases we loose the "original position" information easily 
    protected void extractPlainText() {
        final boolean fullcopy = false;
        if (originalText == null)
            throw new NullPointerException("Parameter text must not be null");

        if (originalText.isEmpty()) {
            plainText = originalText;
            return;
        }
        
        String cleanedText = originalText;
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
        plainText = cleanedText;
    }


    public String getOriginalText() {
        return originalText;
    }

    public String getPlainText() {
        return plainText;
    }
    
    /**
     * for a given position in the plain text string give the position in the original string, such that
     * plainText.charAt(position) is the same as originalText.charAt( getOriginalPosition(position) ).
     * Behavior for out of range values is undefined
     * 
     * @param position
     * @return the equivalent position in the original string
     */
    public int getOriginalPosition(int index) {
        Entry<Integer, Integer> latestOffset = offsets.floorEntry(index);
        if (latestOffset != null) {
            index += latestOffset.getValue();
        }
        return index;
    }
    
}
