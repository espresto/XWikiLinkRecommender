package de.csw.lucene;

import org.apache.lucene.util.AttributeImpl;

// true if the current token is the first word after a punctuation
public class PunctuationAttributeImpl extends AttributeImpl implements PunctuationAttribute {

	private boolean punct;

	@Override
	public void setPunctuation(boolean value) {
		punct = value;
	}

	@Override
	public boolean isPunctuation() {
		return punct;
	}

	@Override
	public void clear() {
		punct = false;
	}

	@Override
	public void copyTo(AttributeImpl target) {
		PunctuationAttributeImpl attrTarget = (PunctuationAttributeImpl)target;
		attrTarget.setPunctuation(punct);
	}

}
