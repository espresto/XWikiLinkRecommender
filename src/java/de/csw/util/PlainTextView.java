package de.csw.util;

import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
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

	private static final Pattern[] EXCLUDE_FROM_ENHANCEMENTS = { Pattern.compile("\\(%.*?%\\)", Pattern.DOTALL),
			Pattern.compile("\\{\\{(velocity|groovy|html|code|comment).*?\\}\\}.*?\\{\\{/\\1\\}\\}", Pattern.DOTALL),
			Pattern.compile("\\{\\{/?[a-zA-Z].*?/?\\}\\}"), Pattern.compile("\\[\\[.*?\\]\\]"), };

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
				Entry<Integer, Integer> currentOffset = offsets.floorEntry(startOffset + targetDeltaOffset);
				if (currentOffset != null) {
					targetOffset += currentOffset.getValue();
				}
				offsets.put(startOffset, targetOffset);

				// shift the larger entries appropriately, which we might have from earlier passes
				if (fullcopy) {
					TreeMap<Integer, Integer> oldOffsets = new TreeMap<Integer, Integer>(offsets);
					offsets.clear();
					for (Entry<Integer, Integer> entry : oldOffsets.entrySet()) {
						final int key = entry.getKey();
						if (key > startOffset + targetDeltaOffset) {
							offsets.put(key - targetDeltaOffset, entry.getValue() + targetDeltaOffset);
						} else if (key > startOffset) {
							// we have removed this offset in the replacement
						} else {
							offsets.put(key, entry.getValue());
						}
					}
				} else {
					SortedMap<Integer, Integer> upperMap = offsets.tailMap(startOffset + 1);
					if (!upperMap.isEmpty()) {
						// make a copy of the keys to avoid "concurrent modification exception"
						// need to copy them as sorted set to make sure we handle the entries in ascending order
						TreeSet<Integer> upperKeySet = new TreeSet<Integer>(upperMap.keySet());
						for (Integer key : upperKeySet) {
							Integer value = offsets.remove(key);
							if (key > startOffset + targetDeltaOffset) {
								offsets.put(key - targetDeltaOffset, value + targetDeltaOffset);
							}
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
	 * @param index
	 * @return the equivalent position in the original string
	 */
	public int getOriginalPosition(int index) {
		Entry<Integer, Integer> latestOffset = offsets.floorEntry(index);
		if (latestOffset != null) {
			index += latestOffset.getValue();
		}
		return index;
	}

	/**
	 * for a given position in the plain text string returns the index in the full text string such that
	 * originalText.substring(0, getOriginalEndPosition(position)) has plain text of plainText.substring(0, position) 
	 * @param index
	 * @return the equivalent position in the original string
	 */
	public int getOriginalEndPosition(int index) {
		return getOriginalPosition(index - 1) + 1;
	}

}
