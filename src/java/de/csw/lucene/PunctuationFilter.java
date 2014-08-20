package de.csw.lucene;

import java.io.IOException;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.KeywordAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.util.FilteringTokenFilter;

public class PunctuationFilter extends FilteringTokenFilter {

	private final CharTermAttribute termAttr = addAttribute(CharTermAttribute.class);
	private final OffsetAttribute offsetAttr = addAttribute(OffsetAttribute.class);
	private final PunctuationAttribute punctAttr = addAttribute(PunctuationAttribute.class);
	private final KeywordAttribute keywordAttr = addAttribute(KeywordAttribute.class);

	public PunctuationFilter(TokenStream input) {
		super(true, input);
	}

	@Override
	protected boolean accept() throws IOException {

		final char[] buffer = termAttr.buffer();
		final int origLen = termAttr.length();

		int len = origLen;
		int incStartOffset = 0;
		int decEndOffset = 0;
		boolean alnumSeen = false;
		for (int i = 0; i < len;) {
			char c = buffer[i];
			if (Character.isAlphabetic(c) || Character.isDigit(c)) {
				alnumSeen = true;
			}

			if (isPunctuation(c)) {
				len--;
				if (i < len) {
					for (int j = i; j < len; j++) {
						buffer[j] = buffer[j + 1];
					}
				}
				if (i == 0) {
					incStartOffset++;
				} else {
					decEndOffset++;
				}

			} else {
				i++;
				decEndOffset = 0;
			}
		}

		boolean accept = (len > 0) && alnumSeen;

		if (len < origLen) {
			termAttr.setLength(len);
			offsetAttr.setOffset(offsetAttr.startOffset() + incStartOffset, offsetAttr.endOffset() - decEndOffset);
			// assume it is an abbreviation if contains punctuation in the middle ... hmmm)
			if (accept && (origLen - len > decEndOffset + incStartOffset)) {
				keywordAttr.setKeyword(true);
			}
		}

		punctAttr.setPunctuation(decEndOffset > 0);

		return accept;
	}

	// TODO: better "punctuation" detection
	// we should use a positive determination (ie.. what is a punctuation) instead of a negative one
	private boolean isPunctuation(char c) {
		// TODO: maybe include more "hyphenation" and other chars
		// see list at: http://www.fileformat.info/info/unicode/category/Pd/list.htm
		// and maybe add these https://www.cs.tut.fi/~jkorpela/dashes.html#unidash
		return !Character.isAlphabetic(c) && !Character.isDigit(c) && !Character.isWhitespace(c) && (Character.getType(c) != Character.DASH_PUNCTUATION);
	}
}
