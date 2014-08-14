package org.apache.lucene.analysis.de;

public class GermanLightStemmerWrapperFactory {

    private static final class GermanLightStemmerWrapper implements Stemmer {

        private final GermanLightStemmer inner;
        private GermanLightStemmerWrapper(GermanLightStemmer gls) {
            inner = gls;
        }
        @Override
        public String stem(String term) {
            // we first need to convert to lower case to match the transformations of the analyzer
            term = term.toLowerCase();
            char[] chars = term.toCharArray();
            int newLength = inner.stem(chars, chars.length);
            return new String(chars, 0, newLength);
        }
    }
    
    public static Stemmer forGermanLightStemmer() {
        GermanLightStemmer gls = new GermanLightStemmer();
        return new MultiWordStemmer( new GermanLightStemmerWrapper(gls));
    }
    
}
