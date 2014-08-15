package de.csw.lucene;

import org.apache.lucene.util.Attribute;

public interface AfterPunctuationAttribute extends Attribute {

	// setter: if we have seen a punctuation
	public void setAfterPunct(boolean value);

	// getter: we are at the first token after a punctuation
	public boolean isAfterPunct();

}