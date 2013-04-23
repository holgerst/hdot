package org.ifomis.ontologyaggregator.integration;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.ifomis.ontologyaggregator.exception.HdotExtensionException;
import org.ifomis.ontologyaggregator.recommendation.Recommendation;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.AddAxiom;
import org.semanticweb.owlapi.model.AddImport;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLImportsDeclaration;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;
import org.semanticweb.owlapi.vocab.OWLRDFVocabulary;

import uk.ac.ebi.ontocat.OntologyService;
import uk.ac.ebi.ontocat.OntologyServiceException;
import uk.ac.ebi.ontocat.OntologyTerm;

/**
 * Extends HDOT with the class that is recommended and confirmed by the user.
 * 
 * @author Nikolina
 * 
 */
public class HDOTExtender {

	/**
	 * The URI manager that decides if the original URI should be kept or new
	 * should be generated.
	 */
	private HDOTURIManager uriManager;

	/**
	 * the HDOTVerifier that check the consistency of the extended ontology.
	 */
	private HDOTVerifier hdotVerifier;

	/**
	 * The recommendation that was accepted by the user.
	 */
	private Recommendation acceptedRecommendation;

	private static final Logger log = Logger.getLogger(HDOTExtender.class);
	/**
	 * the data factory for the hdot ontology
	 */
	private OWLDataFactory dataFactory;

	/**
	 * The ontology manager
	 */
	private OWLOntologyManager ontology_manager;

	/**
	 * The path to the modules.
	 */
	private String pathToModules = "";

	/**
	 * The current class that should be integrated under HDOT.
	 */
	private OWLClass hitForIntegration;

	/**
	 * The accepted class. Needed to be stored here in order to know the parent
	 * of the subclasses if the user wants to include them, too.
	 */
	private OWLClass theAcceptedHit;

	private OWLOntology newModule;

	private String nameOfNewModule;

	public HDOTExtender(Recommendation accceptedRecommendation,
			boolean includeSubclasses, OWLOntologyManager ontology_manager,
			OWLOntology hdot_ontology, OntologyService ontologyService,
			String userID) throws OntologyServiceException, IOException,
			OWLOntologyStorageException, URISyntaxException,
			HdotExtensionException, OWLOntologyCreationException {

		// initialize fields
		this.ontology_manager = OWLManager.createOWLOntologyManager();
		this.dataFactory = this.ontology_manager.getOWLDataFactory();
		this.acceptedRecommendation = accceptedRecommendation;
		this.nameOfNewModule = "hdot_module_user" + userID + ".owl";
		this.uriManager = new HDOTURIManager(accceptedRecommendation,
				includeSubclasses);
		this.hdotVerifier = new HDOTVerifier();

		log.debug("extracted documentIRI:"
				+ ontology_manager.getOntologyDocumentIRI(hdot_ontology)
						.toString());

		// extract the path to the physical documents from the ontology iri
		String[] partsOfPath = ontology_manager
				.getOntologyDocumentIRI(hdot_ontology).toString().split("/");

		for (int i = 0; i < partsOfPath.length - 1; i++) {
			this.pathToModules += partsOfPath[i] + "/";

		}

		initNewModule(accceptedRecommendation);

		// extends HDOT with the actual hit
		extendHDOT(
				acceptedRecommendation.getHit(),
				dataFactory.getOWLClass(IRI.create(acceptedRecommendation
						.getMatchedClass().getURI().toString())),
				acceptedRecommendation.getHitDefinition(), true);

		// in case the user want extends HDOT with the subclasses of the actual
		// hit
		if (includeSubclasses) {

			List<OntologyTerm> subClasses = accceptedRecommendation
					.getHitChildren();

			for (OntologyTerm subClass : subClasses) {
				//TODO ensure that the classes are not already contained in hdot
//				ensureNotAlreadyContainedInHDOT(subClass);
				
				
				List<String> definitionsOfSubClass = ontologyService
						.getDefinitions(subClass);
				extendHDOT(subClass, theAcceptedHit, definitionsOfSubClass,
						false);
			}
		}
		createNewModuleAndUpdateHdotAll();
	}

//	private void ensureNotAlreadyContainedInHDOT(OntologyTerm subClass) throws OWLOntologyCreationException {
//		OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
//		manager.loadOntologyFromOntologyDocument(new File("data/hdot/hdot_all.owl"));
//		Set<OWLOntology> hdotModules = ontology_manager.getOntologies();
//		for (OWLOntology owlOntology : hdotModules) {
//			if(owlOntology.getClassesInSignature())
//		}
//	}

	private void initNewModule(Recommendation accceptedRecommendation)
			throws OWLOntologyCreationException, URISyntaxException {
		boolean found = false;
		log.debug("path to hdot ontology " + this.pathToModules);

		File directory = new File(new URI(pathToModules));

		for (File f : directory.listFiles()) {
			if (f.getName().equals(this.nameOfNewModule)) {
				found = true;
				break;
			}
		}
		if (found) {
			log.debug("MODULE EXISTS");

			this.newModule = this.ontology_manager
					.loadOntologyFromOntologyDocument(new File(new URI(
							this.pathToModules + this.nameOfNewModule)));
		} else {
			log.debug("MODULE DOES NOT EXIST");

			IRI ontologyIRI = IRI.create("http://www.ifomis.org/hdot/"
					+ this.nameOfNewModule);

			// Now create the ontology - we use the ontology IRI (not the
			// physical
			// IRI)
			this.newModule = this.ontology_manager.createOntology(ontologyIRI);
		}
		OWLImportsDeclaration importDeclaraton = this.dataFactory
				.getOWLImportsDeclaration(accceptedRecommendation
						.getHdotModule().getOntologyID().getOntologyIRI());

		this.ontology_manager.applyChange(new AddImport(newModule,
				importDeclaraton));

	}

	/**
	 * Extends HDOT with the given new class and embeds it under the given
	 * parent.
	 * 
	 * @param newClass
	 *            the class to be integrated in the ontology
	 * @param parent
	 *            the parent class of the new class
	 * @param definitions
	 *            the definitions of the new class
	 * @param isTheActualHit
	 *            true if the new class is the hit and false if the new class is
	 *            one of the subclasses of the hit
	 * @throws OWLOntologyStorageException
	 * @throws URISyntaxException
	 * @throws HdotExtensionException
	 * @throws IOException
	 * @throws OWLOntologyCreationException 
	 */
	public void extendHDOT(OntologyTerm newClass, OWLClass parent,
			List<String> definitions, boolean isTheActualHit)
			throws OWLOntologyStorageException, URISyntaxException,
			HdotExtensionException, IOException, OWLOntologyCreationException {

		String newHdotURI = "";
		if (!uriManager.keepOriginalURI()) {

			newHdotURI = uriManager.generateNextHdotUri();
			log.debug("new uri" + newHdotURI);

		}
		// add new class wrt accepted recommendation

		integrateClass(newClass, parent, newHdotURI, isTheActualHit);
		log.debug("class is integrated");

		integrateLabelOfNewClass(newClass);
		log.debug("label is for the class is added");

		integrateDefinitionOfNewClass(definitions);
		log.debug("definition(s) of the class is integrated");

		integrateOriginalId(newClass);
		log.debug("original id is integrated");

		if (hdotVerifier.verifyOntology(newModule)) {
			log.debug("extended ontology is verified");

		} else {
			throw new HdotExtensionException(
					"HDOT cannot be extended due to inconsistency");
		}
	}

	/**
	 * Integrates the new class with the given uri.
	 * 
	 * @param newClass
	 * @param parent
	 * @param newHdotURI
	 * @param isTheActualHit
	 */
	private void integrateClass(OntologyTerm newClass, OWLClass parent,
			String newHdotURI, boolean isTheActualHit) {

		log.debug("class for integration: " + newClass);
		log.debug("parent of class for integration: " + parent);
		
		
		if (newHdotURI.isEmpty()) {
			hitForIntegration = dataFactory.getOWLClass(IRI.create(newClass
					.getURI().toString()));

			if (isTheActualHit) {
				theAcceptedHit = dataFactory.getOWLClass(IRI.create(newClass
						.getURI().toString()));
			}
		} else {
			hitForIntegration = dataFactory.getOWLClass(IRI.create(newHdotURI));

			if (isTheActualHit) {
				theAcceptedHit = dataFactory
						.getOWLClass(IRI.create(newHdotURI));
			}
		}

		OWLAxiom axiom = dataFactory.getOWLSubClassOfAxiom(hitForIntegration,
				parent);

		AddAxiom addAxiom = new AddAxiom(newModule, axiom);

		ontology_manager.applyChange(addAxiom);
//		log.debug("axioms after class insertion: "
//				+ newModule.getAxioms().toString());

	}

	/**
	 * Adds a label to the given class.
	 * 
	 * @param newClass
	 */
	private void integrateLabelOfNewClass(OntologyTerm newClass) {
		OWLAnnotation commentAnno = dataFactory.getOWLAnnotation(
				dataFactory
						.getOWLAnnotationProperty(OWLRDFVocabulary.RDFS_LABEL
								.getIRI()), dataFactory.getOWLLiteral(newClass
						.getLabel().toLowerCase(), "en"));

		OWLAxiom ax = dataFactory.getOWLAnnotationAssertionAxiom(
				hitForIntegration.getIRI(), commentAnno);
		ontology_manager.applyChange(new AddAxiom(newModule, ax));
//		log.debug("axioms after label insertion : "
//				+ newModule.getAxioms().toString());

	}

	/**
	 * Adds definitions to the integrated class.
	 * 
	 * @param definitions
	 */
	private void integrateDefinitionOfNewClass(List<String> definitions) {
		for (String definition : definitions) {
			OWLAnnotation defAnno = dataFactory
					.getOWLAnnotation(
							dataFactory
									.getOWLAnnotationProperty(IRI
											.create("http://purl.obolibrary.org/obo/IAO_0000115")),
							dataFactory.getOWLLiteral(definition, "en"));

			OWLAxiom ax = dataFactory.getOWLAnnotationAssertionAxiom(
					hitForIntegration.getIRI(), defAnno);
			ontology_manager.applyChange(new AddAxiom(newModule, ax));
		}

//		log.debug("axioms after integrate definitions: "
//				+ newModule.getAxioms().toString());

	}

	/**
	 * Integrates the original URI as definition source.
	 * 
	 * @param newClass
	 */
	private void integrateOriginalId(OntologyTerm newClass) {
		OWLAnnotation sourceAnno = dataFactory.getOWLAnnotation(dataFactory
				.getOWLAnnotationProperty(IRI
						.create("http://purl.obolibrary.org/obo/IAO_0000119")),
				dataFactory.getOWLLiteral(newClass.getURI().toString()));

		OWLAxiom ax = dataFactory.getOWLAnnotationAssertionAxiom(
				hitForIntegration.getIRI(), sourceAnno);
		ontology_manager.applyChange(new AddAxiom(newModule, ax));

//		log.debug("axioms after integrate original ids: "
//				+ newModule.getAxioms().toString());

	}

	/**
	 * Saves the ontology administrated by the ontology manager.
	 * 
	 * @param ontoOut
	 *            the name of the file where the ontology is stored
	 * @throws OWLOntologyStorageException
	 *             occurs if the ontology can not be stored
	 * @throws URISyntaxException
	 * @throws OWLOntologyCreationException 
	 * @throws IOException 
	 */
	private void createNewModuleAndUpdateHdotAll() throws OWLOntologyStorageException,
			URISyntaxException, OWLOntologyCreationException, IOException {

		// finally the ontology can be stored
		ontology_manager.saveOntology(newModule,
				IRI.create(this.pathToModules + this.nameOfNewModule));
		
		ontology_manager.removeOntology(newModule);
		
		 OWLOntology hdot_all = this.ontology_manager.loadOntologyFromOntologyDocument(new File("data/hdot/hdot_all.owl"));
		//import the new module in hdot_all.owl and save it
		
		OWLImportsDeclaration importDeclaraton = this.dataFactory
				.getOWLImportsDeclaration(newModule.getOntologyID().getOntologyIRI());

		this.ontology_manager.applyChange(new AddImport(hdot_all,
				importDeclaraton));
		
		ontology_manager.saveOntology(hdot_all);
//		ontology_manager.saveOntology(hdot_all, IRI.create("https://code.google.com/p/hdot/source/browse/trunk/hdot_module_user2.owl"));
		//add the new module to the list of sorted ids of the hdot modules
		List<String> orderOfModules = FileUtils.readLines(new File("data/sortedHdotModuleIds"));
		orderOfModules.add(0, newModule.getOntologyID().getOntologyIRI().toString());
		FileUtils.writeLines(new File("data/sortedHdotModuleIds"), orderOfModules);
		
		log.info("The extended HDOT module is saved in: " + this.pathToModules
				+ this.nameOfNewModule);
	}
}