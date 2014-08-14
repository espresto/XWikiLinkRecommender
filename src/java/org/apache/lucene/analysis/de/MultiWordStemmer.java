package org.apache.lucene.analysis.de;

import org.apache.commons.lang.StringUtils;

import de.csw.ontology.OntologyIndex;

public class MultiWordStemmer implements Stemmer {

    private final Stemmer stemmer;

    public MultiWordStemmer(Stemmer singleWordStemmer) {
        this.stemmer = singleWordStemmer;
    }

    @Override
    public String stem(String term) {
        // TODO we should use a global splitter for terms being used in OntologyIndex and here
        String[] frags = StringUtils.split(term);

        for (int i = 0; i < frags.length; i++) {
            frags[i] = stemSingleTerm(frags[i]);
        }
        return StringUtils.join(frags, OntologyIndex.PREFIX_SEPARATOR);
    }
    
    private String stemSingleTerm(String term) {
        return stemmer.stem(term);
    }

}
