package de.csw.lucene;

import org.apache.lucene.util.Attribute;

/**
 * boolean attribute flagging if the current term contains a punctuation.
 * his might be end-of sentence mark or an intermediate (comma, etc.).
 * Used for the purpose of stopping phrase detection at the occurrence of a punctuation
 */
public interface PunctuationAttribute extends Attribute {

	/*
	 *  setter: current token contains a punctuation mark
	 */
	/**
	 * set true if current token contains a punctuation mark
	 * @param value
	 */
	public void setPunctuation(boolean value);

	/**
	 * @return true if current token contains a punctuation mark
	 */
	public boolean isPunctuation();

}