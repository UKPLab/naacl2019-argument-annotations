/*
* Copyright 2019
* Ubiquitous Knowledge Processing (UKP) Lab
* Technische Universität Darmstadt
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*      http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package de.tudarmstadt.ukp.naacl2019.argannotation.crowdsourcing;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.NoSuchElementException;

import org.apache.commons.io.FileUtils;
import org.apache.uima.UIMAException;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.collection.CollectionReader;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.fit.factory.CollectionReaderFactory;
import org.apache.uima.fit.factory.JCasFactory;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import de.tudarmstadt.ukp.naacl2019.argannotation.types.ReviewBody;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import de.tudarmstadt.ukp.dkpro.argumentation.types.ArgumentUnit;
import de.tudarmstadt.ukp.dkpro.argumentation.types.Attack;
import de.tudarmstadt.ukp.dkpro.argumentation.types.Claim;
import de.tudarmstadt.ukp.dkpro.argumentation.types.MajorClaim;
import de.tudarmstadt.ukp.dkpro.argumentation.types.Premise;
import de.tudarmstadt.ukp.dkpro.argumentation.types.Support;
import de.tudarmstadt.ukp.dkpro.core.api.metadata.type.DocumentMetaData;
import de.tudarmstadt.ukp.dkpro.core.api.parameter.ComponentParameters;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token;
import de.tudarmstadt.ukp.dkpro.core.io.xmi.XmiReader;

/**
 * Gets the xmi input and writes the conll data
 *
 *
 */
public class XmiToConllConverter{
	@Option(name="-o",aliases = { "--output" },metaVar="dir",usage="output folder", required=true)
	private File outputDir;

	@Option(name="-i",aliases = { "--input" },metaVar="dir",usage="input folder", required=true)
	private File inputDir;

	@Option(name="-l",aliases = { "--level" },metaVar="str",usage="annotation level", required=true)
	private String annotationLevel;

    public static final String PARAM_SOURCE_LOCATION = ComponentParameters.PARAM_SOURCE_LOCATION;
    @ConfigurationParameter(name = PARAM_SOURCE_LOCATION, mandatory = true)
    private String sourceLocation;

    public static final String PARAM_ANNOTATION_TYPE = "l";
    @ConfigurationParameter(name = PARAM_ANNOTATION_TYPE, mandatory = true)
    private String annotationType;


    public void process(File inputDir, File outputDir, String annotLevel) throws UIMAException, IOException{
    	annotationType = annotLevel;
        List<File> xmiFiles = new ArrayList<>(
                FileUtils.listFiles(inputDir, new String[] { "xmi" }, true));
        List<File> faultyFiles = new ArrayList<>();
        System.out.println("We have " + xmiFiles.size() + " files");
        for (File file : xmiFiles) {
        	try{
            JCas jcas = JCasFactory.createJCas();

            CollectionReader reader = CollectionReaderFactory.createReader(XmiReader.class,
                    XmiReader.PARAM_SOURCE_LOCATION, file);
			// "fill" the jCas container
            reader.getNext(jcas.getCas());
            createConll(jcas, outputDir);
        	}catch(IOException e){
        		faultyFiles.add(file);
        		System.out.println("Error in file: " + file);
        	}

        }
        System.out.println("Please check this files again:");
        for (File file : faultyFiles){
        	System.out.println(file);
        }
    }

    public void createConll(JCas aJCas, File outputDir)    {
        // Get Metadata:
        String documentID = "";
        // DocumentMetaData metaData =(DocumentMetaData) JCasUtil.select(aJCas,
        // DocumentMetaData.class);
        for (DocumentMetaData metaData : JCasUtil.select(aJCas, DocumentMetaData.class)) {
            documentID = metaData.getDocumentId();
        }
    	System.out.println("Processing... "+documentID);

        // Skip indicator for the writing process
        boolean skipthis = false;

        // HashMap containing TokenID - Annotation
        HashMap<String, ArrayList<String>> tokenAnnotations = new HashMap<String, ArrayList<String>>();
        // First fill the hash map with O annotations
        for (Token token : getSortedTokenList(aJCas)) {
        	ArrayList<String> oList = new ArrayList<String>();
        	oList.add("NONE");
        	oList.add("O");
            tokenAnnotations.put(token.getId(), oList);
        }
        // Now change the annotated tokens to the corresponding annotation:
        try {
            // Get the major claim (We assume, we have exact one major claim per xmi!)
            FSIterator<MajorClaim> mcIterator = aJCas.getAllIndexedFS(MajorClaim.class);
            mcIterator.moveToFirst();
            MajorClaim majorClaim = mcIterator.get();
            int mcIndex = majorClaim.getAddress();
            // Extract the token ids from the property field and add the annotations to the hashmap
            // First token gets the B-Tag:
            boolean isfirst = true;
            List<Token> mcTokens = getPropsCoveredTokens(aJCas, majorClaim.getProperties());
            for (Token token : mcTokens){
        		ArrayList<String> mcList = new ArrayList<String>();
        		mcList.add(Integer.toString(mcIndex));
            	if (isfirst){
            		mcList.add("B-MajorClaim");
                    isfirst = false;
            	}else{
            		mcList.add("I-MajorClaim");
            	}
            	tokenAnnotations.put(token.getId(), mcList);
            }
            // If the last element of the claim is a punctuation, set it to O:
            Token lastMajorClaimToken = getCoveredTokens(aJCas, majorClaim.getBegin(), majorClaim.getEnd()).get(getCoveredTokens(aJCas, majorClaim.getBegin(), majorClaim.getEnd()).size()-1);
            if(lastMajorClaimToken.getCoveredText().matches("\\p{Punct}")){
            	ArrayList<String> mcList = new ArrayList<String>();
            	mcList.add(Integer.toString(mcIndex));
            	mcList.add("O");
            	tokenAnnotations.put(lastMajorClaimToken.getId(), mcList);
            }
        }
        catch (NoSuchElementException e) {
            // Print error message, in case we have no major claim at all.
            skipthis = true;
            System.out.println("We have no MajorClaim annotation in:  " + documentID);
        }
        // Checker for overlapping argument components
        boolean overlapping = false;
        // Set annotation level to extract:
    	if(annotationType.equals("Claim") || annotationType.equals("Premise")){
	        try {
	            // Get the claims
	            FSIterator<Claim> cIterator = aJCas.getAllIndexedFS(Claim.class);
	            cIterator.moveToFirst();
	            Claim checkclaim = cIterator.get();
	            while(cIterator.hasNext()){
	                Claim claim = cIterator.get();
	                int cIndex = claim.getAddress();
	                // First token gets the B-Tag:
	                boolean isfirst = true;
	                boolean skip_claim = false;
	                if(getPropsCoveredTokens(aJCas, claim.getProperties()).size()<2){
	                	System.out.println("Size 1 claim annotation! Skipping "+documentID);
	                	skip_claim = true;
	                }
	                if(!skip_claim){
	                    for (Token token : getCoveredTokens(aJCas, claim.getBegin(), claim.getEnd())){
	                		ArrayList<String> cList = new ArrayList<String>();
	                		cList.add(Integer.toString(cIndex));
	                    	if (isfirst){
	                    		cList.add("B-Claim");
	                            isfirst = false;
	                    	}else{
	                    		cList.add("I-Claim");
	                    	}
	                    	if(!tokenAnnotations.get(token.getId()).get(1).contains("O")){
	                    		overlapping = true;
	                    		break;
	                    	}
	                    	tokenAnnotations.put(token.getId(), cList);
	                    }
	                    // If the last element of the claim is a punctuation, set it to O:
	                    Token lastClaimToken = getCoveredTokens(aJCas, claim.getBegin(), claim.getEnd()).get(getCoveredTokens(aJCas, claim.getBegin(), claim.getEnd()).size()-1);
	                    if(lastClaimToken.getCoveredText().matches("\\p{Punct}")){
	                    	ArrayList<String> cList = new ArrayList<String>();
	                    	cList.add(Integer.toString(cIndex));
	                    	cList.add("O");
	                    	tokenAnnotations.put(lastClaimToken.getId(), cList);
	                    }
	                }
	                cIterator.moveToNext();
	            }
	        }
	        catch (NoSuchElementException e) {
	                // Print error message, in case we have no claim at all.
	        		skipthis = true;
	        		System.out.println("We have no Claim annotation in:  " + documentID);
	        }
    	}

    	// Set annotation level to extract
		if (annotationType.equals("Premise")) {

			// Get premises
			try {
				// Get the premises
				FSIterator<Premise> pIterator = aJCas
						.getAllIndexedFS(Premise.class);
				pIterator.moveToFirst();
				Premise checkpremise = pIterator.get();
				while (pIterator.hasNext()) {
					Premise premise = pIterator.get();
					int pIndex = premise.getAddress();
					// Check single token premises and ignore them:
					if(getCoveredTokens(aJCas,
							premise.getBegin(), premise.getEnd()).size()<2){
						System.out.println("We have a single sized premise: " + getCoveredTokens(aJCas,
							premise.getBegin(), premise.getEnd()).get(0).getCoveredText() );
						pIterator.moveToNext();
						continue;
					}
					// First token gets the B-Tag:
					boolean isfirst = true;
					for (Token token : getCoveredTokens(aJCas,
							premise.getBegin(), premise.getEnd())) {
						ArrayList<String> pList = new ArrayList<String>();
						pList.add(Integer.toString(pIndex));
						if (isfirst) {
							pList.add("B-Premise");
							isfirst = false;
						} else {
							pList.add("I-Premise");
						}
						if (!tokenAnnotations.get(token.getId()).get(1)
								.contains("O")) {
							overlapping = true;
							break;
						}
						tokenAnnotations.put(token.getId(), pList);
					}
                    // If the last element of the premise is a punctuation, set it to O:
                    Token lastPremiseToken = getCoveredTokens(aJCas, premise.getBegin(), premise.getEnd()).get(getCoveredTokens(aJCas, premise.getBegin(), premise.getEnd()).size()-1);
                    if(lastPremiseToken.getCoveredText().matches("\\p{Punct}")){
                    	ArrayList<String> pList = new ArrayList<String>();
                    	pList.add(Integer.toString(pIndex));
                    	pList.add("O");
                    	tokenAnnotations.put(lastPremiseToken.getId(), pList);
                    }
					pIterator.moveToNext();
				}
			} catch (NoSuchElementException e) {
				// Print error message, in case we have no premise at all.
				skipthis = true;
				System.out.println("We have no Premise annotation in:  "
						+ documentID);
			}
		}

		if(overlapping){
			System.out.println("We have an overlapping annotation in " + documentID);
		}
		if (!skipthis && !overlapping) {
	        List<Token> sortedList = getSortedTokenList(aJCas);

	        // Adding argument relations for claims and premises
	        // First sort all argument component indicies according to their appearing order in the text:
	        ArrayList<String> sortedAnnotations = new ArrayList<String>();
	        for (Token token : sortedList){
	        	String thisIndex = tokenAnnotations.get(token.getId()).get(0);
	        	if(sortedAnnotations.contains(thisIndex) || thisIndex.equals("NONE")){
	        		continue;
	        	}else{
	        		sortedAnnotations.add(thisIndex);
	        	}

	        }

	        //System.out.println(sortedAnnotations);
	        HashMap<String, ArrayList<String>> argumentRelations = new HashMap<String, ArrayList<String>>();
	        // Add supports
	        FSIterator<Support> sIterator = aJCas.getAllIndexedFS(Support.class);
	        sIterator.moveToFirst();
	        while(sIterator.hasNext()){
	        	Support sup = sIterator.get();
	        	// Get source index and target index depending on their position in the sorted list
	        	ArgumentUnit targetUnit = sup.getTarget();
	        	int targetAddress = sortedAnnotations.indexOf(Integer.toString(targetUnit.getAddress()));
	        	ArgumentUnit sourceUnit = sup.getSource();
	        	int sourceAddress = sortedAnnotations.indexOf(Integer.toString(sourceUnit.getAddress()));
	        	int relation = targetAddress - sourceAddress;
	        	ArrayList<String> relationArray = new ArrayList<String>();
	        	relationArray.add(Integer.toString(relation));
		        relationArray.add("Support");
	        	argumentRelations.put(Integer.toString(sourceUnit.getAddress()),relationArray);
	        	sIterator.moveToNext();
	        }
	        // Add attacks
	        FSIterator<Attack> aIterator = aJCas.getAllIndexedFS(Attack.class);
	        aIterator.moveToFirst();
	        while(aIterator.hasNext()){
	        	Attack att = aIterator.get();
	        	// Get source index and target index depending on their position in the sorted list
	        	ArgumentUnit targetUnit = att.getTarget();
	        	int targetAddress = sortedAnnotations.indexOf(Integer.toString(targetUnit.getAddress()));
	        	ArgumentUnit sourceUnit = att.getSource();
	        	int sourceAddress = sortedAnnotations.indexOf(Integer.toString(sourceUnit.getAddress()));
	        	int relation = targetAddress - sourceAddress;
	        	ArrayList<String> relationArray = new ArrayList<String>();
	        	relationArray.add(Integer.toString(relation));
		        relationArray.add("Attack");
	        	argumentRelations.put(Integer.toString(sourceUnit.getAddress()),relationArray);
	        	aIterator.moveToNext();
	        }

			try (Writer writer = new BufferedWriter(new FileWriter(
					outputDir + "/" + documentID + ".conll"))) {
				// Get the review body:
				ReviewBody reviewBody = aJCas.getAllIndexedFS(ReviewBody.class).get();
				// ID of the first token in the review body
				String reviewBodyBegin = getCoveredTokens(aJCas, reviewBody.getBegin(), reviewBody.getEnd()).get(0).getId();
				String lastAnnotation = "O";
				for (Token token : sortedList) {
					String text = token.getCoveredText();
					String annotation = tokenAnnotations.get(token.getId()).get(1);
					//System.out.println(token.getId() + "   " + annotation);
					if(!annotation.contains("MajorClaim") && !annotation.equals("O")){
						String annotationID = tokenAnnotations.get(token.getId()).get(0);
						// Only add relational link when we have a premise
						if(annotation.contains("Premise")){
							String relation = argumentRelations.get(annotationID).get(0);
							String stance = argumentRelations.get(annotationID).get(1);
							annotation = annotation + ":" + relation + ":"+stance;
						}else{
							String stance = argumentRelations.get(annotationID).get(1);
							annotation = annotation + ":" +stance;
						}
					}
					// Add split token #EOT-Token# for the review title
					if (token.getId().equals(reviewBodyBegin)){
						String splitAnnotation = lastAnnotation;
						if (lastAnnotation.contains("B-")){
							splitAnnotation.replace("B-","I-");
						}
						if (annotation.equals("O")){
							splitAnnotation = annotation;
						}
						// If the argument component does not span over the title, we set the EOT-label to O
						if(!lastAnnotation.replace("B-", "I-").equals(annotation)){
							splitAnnotation = "O";
						}
						writer.write("#EOT-Token#" + "\t" + splitAnnotation);
						writer.write(System.getProperty("line.separator"));
					}
					writer.write(text + "\t" + annotation);
					writer.write(System.getProperty("line.separator"));
					lastAnnotation = annotation;
				}
				writer.write(System.getProperty("line.separator"));

			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
    }


    /**
     * Returns an array list of tokens which are sorted in the correct order. E.g. : titleToken_0 ,
     * ... , reviewToken_0, ...
     */
    private List<Token> getSortedTokenList(JCas aJCas)
    {
        FSIterator<Token> tokenIterator = aJCas.getAllIndexedFS(Token.class);
        tokenIterator.moveToFirst();
        List<Token> sortedList = new ArrayList<Token>();
        // First add the title tokens:
        while (tokenIterator.hasNext()) {
            Token token = tokenIterator.get();
            sortedList.add(token);
            tokenIterator.moveToNext();
        }
        // Then add the review token
        return sortedList;
    }

    /**
     * Get a list of covered tokens
     * @param args
     * @throws IOException
     * @throws UIMAException
     */
    private List<Token> getCoveredTokens(JCas aJCas,int beginIndex,int endIndex){
    	List<Token> coveredTokens = new ArrayList<Token>();
    	List<Token>sorted = getSortedTokenList(aJCas);

    	for (Token token : sorted){
    		if(token.getBegin()>= beginIndex && token.getEnd() <= endIndex){
    			coveredTokens.add(token);
    		}
    	}

    	return coveredTokens;
    }

    /**
     * Get a list of covered tokens
     * @param args
     * @throws IOException
     * @throws UIMAException
     */
    private List<Token> getPropsCoveredTokens(JCas aJCas,String props){

    	List<Token>sorted = getSortedTokenList(aJCas);
    	String tokenIDs = props.split("Covered_Token=")[1].split("&#10")[0];
    	// FIXME: Cleanwrite this!!!!
    	String splitString = ";";
    	if (!tokenIDs.contains(";")){
    		splitString=",";
    	}
    	String[] tokenIDList = tokenIDs.split(splitString);
    	int beginIndex = 0;
    	int endIndex = 0;
    	for (Token token : sorted){
    		if (token.getId().equals(tokenIDList[0].trim())){
    			beginIndex = token.getBegin();
    		}
    		if (token.getId().equals(tokenIDList[tokenIDList.length-1].trim())){
    			endIndex = token.getEnd();
    		}
    		// Special case for premises
    		if (splitString.equals(";")){
    			System.out.println(tokenIDs);
        		if (token.getId().equals(tokenIDList[tokenIDList.length-2].trim())){
        			endIndex = token.getEnd();
        		}
    		}
    	}
    	return getCoveredTokens(aJCas,beginIndex,endIndex);
    }

    // ======================= HELPER FUNCTIONS END =======================



    public static void main(String[] args)
            throws IOException, UIMAException
        {
        	new XmiToConllConverter().doMain(args);
        }

        private void doMain(String[] args) throws IOException, UIMAException {
    		CmdLineParser parser = new CmdLineParser(this);
    		try {
                // parse the arguments.
                parser.parseArgument(args);
                this.process(inputDir, outputDir, annotationLevel);
            } catch( CmdLineException e ) {
                System.err.println(e.getMessage());
                System.err.println("java "+this.getClass().getSimpleName()+" [options...] arguments...");
                parser.printUsage(System.err);
                System.err.println();
                return;
            }
    	}
}
