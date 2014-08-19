package de.csw.lucene;

import java.io.IOException;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.util.FilteringTokenFilter;

public class WhitespaceAndPunctuationFilter extends FilteringTokenFilter {

	private boolean punctSeen = false;
	
	private final CharTermAttribute termAttr = addAttribute(CharTermAttribute.class);	
	private final OffsetAttribute offsetAttr = addAttribute(OffsetAttribute.class);	
	private final AfterPunctuationAttribute punctAttr = addAttribute(AfterPunctuationAttribute.class);
	
	public WhitespaceAndPunctuationFilter(TokenStream input) {
		super(true, input);
	}

	@Override
	protected boolean accept() throws IOException {
		
		punctAttr.setAfterPunct(punctSeen);
		char[] buffer = termAttr.buffer();
		for (int i = 0, n = termAttr.length(); i<n; i++) {
			char c = buffer[i];
			if (Character.isAlphabetic(c)) {
				punctSeen = false;
				break;
			}
		}
		
		int len = termAttr.length();
		
		int incStartOffset=0;
		int decEndOffset=0;
		for (int i = 0; i<len; ) {
			char c = buffer[i];
			if (isPunctuation(c)) { 
				punctSeen = true;

				len--;
				if (i<len) {
					for (int j=i;j<len;j++) {
						buffer[j] = buffer[j+1];
					}
				}
				if (i==0) {
					incStartOffset++;
				} else {
					decEndOffset++;
				}

			} else {
				i++;
				decEndOffset=0;
			}
		}
		offsetAttr.setOffset(offsetAttr.startOffset()+incStartOffset, offsetAttr.endOffset() - (decEndOffset));
		termAttr.setLength(len);
		
		return len>0;
	}

	// TODO: better "punctuation" detection
	private boolean isPunctuation(char c) {
		// TODO: or at least  find more "hyphenation" chars to except
		return !Character.isAlphabetic(c) && !Character.isWhitespace(c) && c != '-';
	}
	
	@Override
	public void reset() throws IOException {
		super.reset();
		punctSeen = false;
	}

}
