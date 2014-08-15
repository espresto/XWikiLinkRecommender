package de.csw.lucene;

import java.util.List;

import com.hp.hpl.jena.ontology.OntResource;

import org.apache.lucene.util.Attribute;

public interface ConceptAttribute extends Attribute {

	public List<OntResource> getConcepts();

	public void setConcepts(List<OntResource> concepts);

	public boolean isConcept();

}