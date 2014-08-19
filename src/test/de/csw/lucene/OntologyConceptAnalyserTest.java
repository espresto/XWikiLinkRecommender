package de.csw.lucene;

import org.junit.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import de.nbi.ontology.test.TestBase;

public class OntologyConceptAnalyserTest extends TestBase {

	@BeforeClass
	public void beforeClass() {
		loadDomainOntology();
	}
	
	// here maybe "expectation format": term:from:to:y/n
	@Test
	public void tokenize() {
		String text = "Ein bischen Kerbel, z.b. mit Mehrzweck-Ingwer.";
		OntologyConceptAnalyzer analyzer = new OntologyConceptAnalyzer(text);
		// we love our stemmer ...
		// String[] tokens = {"bisch", "kerbel", "x","b", "ingw" };
		String[] tokens = {"bisch", "kerbel", "zb", "mehrzweck-ingwer" }; // todo stemming in the last token as not only letters :(
		int counter = 0;
		while (analyzer.hasNextMatch()) {
			Assert.assertEquals( tokens[counter++], analyzer.token());
		}
		analyzer.close();
	}

	@Test
	public void testChars() {
		Assert.assertTrue(Character.isAlphabetic('a'));
		Assert.assertTrue(!Character.isAlphabetic(','));
		Assert.assertTrue(!Character.isWhitespace(','));
	}
	
}
