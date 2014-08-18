package de.csw.lucene;

import org.apache.lucene.util.AttributeImpl;

// true if the current token is the first word after a punctuation
public class AfterPunctuationAttributeImpl extends AttributeImpl implements AfterPunctuationAttribute {

	private boolean punct;

	@Override
	public void setAfterPunct(boolean value) {
		punct = value;
	}

	@Override
	public boolean isAfterPunct() {
		return punct;
	}

	@Override
	public void clear() {
		punct = false;
	}

	@Override
	public void copyTo(AttributeImpl target) {
		AfterPunctuationAttributeImpl attrTarget = (AfterPunctuationAttributeImpl)target;
		attrTarget.setAfterPunct(punct);
	}

}
