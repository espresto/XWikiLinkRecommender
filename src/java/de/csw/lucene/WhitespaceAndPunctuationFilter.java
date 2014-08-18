package de.csw.lucene;

import java.io.IOException;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.util.FilteringTokenFilter;

public class WhitespaceAndPunctuationFilter extends FilteringTokenFilter {

	private boolean punctSeen = false;
	
	private final CharTermAttribute termAttr = addAttribute(CharTermAttribute.class);	
	private final AfterPunctuationAttribute punctAttr = addAttribute(AfterPunctuationAttribute.class);

	public WhitespaceAndPunctuationFilter(TokenStream input) {
		super(true, input);
	}

	@Override
	protected boolean accept() throws IOException {
		
		char[] buffer = termAttr.buffer();
		for (int i = 0, n = termAttr.length(); i<n; i++) {
			char c = buffer[i];
			if (Character.isAlphabetic(c)) {
				punctAttr.setAfterPunct(punctSeen);
				punctSeen = false;
				return true;
			}
			if (!Character.isWhitespace(c)) {
				punctSeen = true;
			}
		}
		
		return false;
	}
	
	@Override
	public void reset() throws IOException {
		super.reset();
		punctSeen = false;
	}

}
