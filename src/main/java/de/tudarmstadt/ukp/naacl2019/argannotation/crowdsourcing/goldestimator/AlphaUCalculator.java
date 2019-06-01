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

package de.tudarmstadt.ukp.naacl2019.argannotation.crowdsourcing.goldestimator;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.uima.UIMAException;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.collection.CollectionReader;
import org.apache.uima.fit.factory.CollectionReaderFactory;
import org.apache.uima.fit.factory.JCasFactory;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.dkpro.statistics.agreement.unitizing.KrippendorffAlphaUnitizingAgreement;
import org.dkpro.statistics.agreement.unitizing.UnitizingAnnotationStudy;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import de.tudarmstadt.ukp.dkpro.core.api.metadata.type.DocumentMetaData;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token;
import de.tudarmstadt.ukp.dkpro.core.io.xmi.XmiReader;

/**
 * Calculates inter annotator agreement (Krippendorff's alpha u), and writes
 * them into the .report file
 *
 *
 */
public class AlphaUCalculator{

    // HashMap containing the AMT results data
    protected HashMap<String, HashMap<Integer, List<HashMap<String, String>>>> mTurkMap = new HashMap<String, HashMap<Integer, List<HashMap<String, String>>>>();
    // List containing the AMT result columns
    protected List<String> parameters = new ArrayList<String>();

    // List of all worker ids in the results file
    protected List<String> allWorkers = new ArrayList<String>();

	@Option(name="-o",aliases = { "--output" },metaVar="dir",usage="output folder", required=true)
	private File outputDir;

	@Option(name="-i",aliases = { "--input" },metaVar="dir",usage="input folder", required=true)
	private File inputDir;

	@Option(name = "-l", aliases = { "--level" }, metaVar = "str", usage = "annotation level", required = true)
	private String annotationLevel;


	@Option(name="-r",aliases = { "--resultFile" },metaVar="file",usage="AMT .result file", required=true)
	protected File resultFile;

	private String annotationType;


	// TODO: Add annotations as mc, claims, premises to the cas!

	private void initialize() throws ResourceInitializationException{
        parameters.add("hitid");
        parameters.add("hittypeid");
        parameters.add("workerid");
        parameters.add("Answer.intext");
        parameters.add("Answer.tokens");
        parameters.add("annotation");
        parameters.add("Answer.textinput");
        parameters.add("assignmentstatus");
        parameters.add("reject");

        try {
            loadMTurkResults();
        }
        catch (IOException e) {
            throw new ResourceInitializationException(e);
        }

	}

	public void getStatistics(File inputDir, File outputDir, String annotLevel)
			throws UIMAException, IOException {
		annotationType = annotLevel;

		initialize();

		List<File> xmiFiles = new ArrayList<>(FileUtils.listFiles(inputDir,
				new String[] { "xmi" }, true));
		List<File> faultyFiles = new ArrayList<>();
		System.out.println("We have " + xmiFiles.size() + " files");
		if (annotationType.equals("m")){
			System.out.println("Computing major claim statistics ...");
		}
		if (annotationType.equals("c")){
			System.out.println("Computing claim statistics ...");
		}
		if (annotationType.equals("p")){
			System.out.println("Computing premise statistics ...");
		}
		for (File file : xmiFiles) {
			try {
				JCas jcas = JCasFactory.createJCas();

				CollectionReader reader = CollectionReaderFactory.createReader(
						XmiReader.class, XmiReader.PARAM_SOURCE_LOCATION, file);
				// "fill" the jCas container
				reader.getNext(jcas.getCas());
				if (annotationType.equals("m")) {
					majorClaimStatistics(jcas);

				}
				if (annotationType.equals("c")) {
					claimStatistics(jcas);
				}
				if (annotationType.equals("p")) {
					premiseStatistics(jcas);
				}
			} catch (IOException e) {
				System.out.println(e);
				faultyFiles.add(file);
			}

		}
		System.out.println("Please check this files again:");
		for (File file : faultyFiles) {
			System.out.println(file);
		}
	}

	/**
	 * Calculates several statistics of the annotated review and adds them to
	 * the cas.
	 */
	private void majorClaimStatistics(JCas aJCas) {
		Double alphaUAgreement = this
				.calculateKrippendorffAlphaUTokensMC(aJCas);
		Double binaryAgreement = this.calculateBinaryAgreementMC(aJCas);

		// Write scores into .report file:
		// Get Metadata:
		String documentID = "";
		for (DocumentMetaData metaData : JCasUtil.select(aJCas,
				DocumentMetaData.class)) {
			documentID = metaData.getDocumentId();
		}
		try (Writer writer = new BufferedWriter(new FileWriter(outputDir + "/"
				+ documentID + ".report"))) {
			writer.write("Alpha_U: " + alphaUAgreement.toString());
			writer.write(System.getProperty("line.separator"));
			writer.write("Binary : " + binaryAgreement.toString());

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/**
	 * Calculates statistics for the claim annotations. Note, that one worker
	 * can assign several claims in the review text.
	 *
	 * @param aJCas
	 */
	private void claimStatistics(JCas aJCas) {

		List<Double> alphaUAgreement = this
				.calculateAlphaUAgreementTokenClaims(aJCas);
		List<Double> binaryAgreement = this.calculateBinaryAgreementClaims(aJCas);

		// Write scores into .report file:
		// Get Metadata:
		String documentID = "";
		for (DocumentMetaData metaData : JCasUtil.select(aJCas,
				DocumentMetaData.class)) {
			documentID = metaData.getDocumentId();
		}
		if (!alphaUAgreement.isEmpty()) {
			for (int i = 0; i < alphaUAgreement.size(); i++) {
				if(Double.isNaN(alphaUAgreement.get(i))){
					continue;
				}
				try (Writer writer = new BufferedWriter(new FileWriter(
						outputDir + "/" + documentID + "-"
								+ Integer.toString(i) + ".report"))) {
					writer.write("Alpha_U: "
							+ alphaUAgreement.get(i).toString());
					writer.write(System.getProperty("line.separator"));
					writer.write("Binary : "
							+ binaryAgreement.get(i).toString());

				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
	}

	private void premiseStatistics(JCas aJCas) {

		List<Double> alphaUAgreement = this
				.calculateAlphaUAgreementTokenClaims(aJCas);
		List<Double> binaryAgreement = this.calculateBinaryAgreementClaims(aJCas);
		System.out.println(alphaUAgreement.toString());

		// Write scores into .report file:
		// Get Metadata:
		String documentID = "";
		for (DocumentMetaData metaData : JCasUtil.select(aJCas,
				DocumentMetaData.class)) {
			documentID = metaData.getDocumentId();
		}
		if (!alphaUAgreement.isEmpty()) {
			for (int i = 0; i < alphaUAgreement.size(); i++) {
				if(Double.isNaN(alphaUAgreement.get(i))){
					continue;
				}
				try (Writer writer = new BufferedWriter(new FileWriter(
						outputDir + "/" + documentID + "-"
								+ Integer.toString(i) + ".report"))) {
					writer.write("Alpha_U: "
							+ alphaUAgreement.get(i).toString());
					writer.write(System.getProperty("line.separator"));
					writer.write("Binary : "
							+ binaryAgreement.get(i).toString());

				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
	}


	/**
	 * Calculates the krippendorff's alpha u for inter annotator agreement and
	 * adds it to the current jcas. The smallest possible unit here is a token,
	 * since the annotators can only mark one token.
	 */
	private Double calculateKrippendorffAlphaUTokensMC(JCas aJCas) {
		String id = "";
		for (DocumentMetaData metaData : JCasUtil.select(aJCas,
				DocumentMetaData.class)) {
			id = metaData.getDocumentId();
		}

		// If the current xmi file is contained in the amt results, start
		// processing!
		if (mTurkMap.containsKey(id)) {
			// List containing all workers for this review
			List<String> workers = new ArrayList<String>();

			// Hashmap mapping workerids to their annotations:
			HashMap<String, List<String>> workerAnnotations = new HashMap<String, List<String>>();

			System.out.println("Found ID " + id);
			for (Integer key : mTurkMap.get(id).keySet()) {
				List<HashMap<String, String>> mTurkResults = mTurkMap.get(id)
						.get(key);

				// HashMap for storing BIO tagging for all annotators
				for (HashMap<String, String> entry : mTurkResults) {
					// Processing single line of the AMT results file

					// Add worker id, if not already in the list of workers
					String workerID = entry.get("workerid");
					if (!workers.contains(workerID)) {
						workers.add(workerID);
					}
					// List containing the major claim annotation:
					List<String> majorClaimTokens = new ArrayList<String>();
					if (entry.get("Answer.intext").equals("true")
							&& entry.get("Answer.tokens") != null) {
						for (String majorClaimToken : entry
								.get("Answer.tokens").split(",")) {
							majorClaimTokens.add(majorClaimToken);
						}
					}
					// Add this to the annotation map:
					workerAnnotations.put(workerID, majorClaimTokens);
				}

				int numberOfAnnotators = workers.size();
				// Since we can annotate the review title as well, the document
				// length is the review text
				// length + review title length
				List<Token> sortedTokenList = this.getSortedTokenList(aJCas);

				UnitizingAnnotationStudy study = new UnitizingAnnotationStudy(
						numberOfAnnotators, sortedTokenList.size());

				// Iterate over the workers, get every mc annotation, add it to
				// the study with the according worker index
				for (String wID : workers) {
					List<String> thisAnots = workerAnnotations.get(wID);
					try{
					study.addUnit(sortedTokenList.indexOf(this.getBeginToken(
							aJCas, thisAnots.get(0))), thisAnots.size(),
							workers.indexOf(wID), "X");
					}catch(IndexOutOfBoundsException e){
						System.out.println("No annotation from worker: " + wID);
					}
				}

				KrippendorffAlphaUnitizingAgreement alpha = new KrippendorffAlphaUnitizingAgreement(
						study);
				return alpha.calculateAgreement();
			}
		}
		return 0.0;
	}

	/**
	 * Calculate the percentage agreement on the task: Is there an annotation in
	 * the given text?
	 *
	 * @return the percentage who many annotators were able to find a major
	 *         claim in the text
	 */
	private Double calculateBinaryAgreementMC(JCas aJCas) {

		String id = "";
		for (DocumentMetaData metaData : JCasUtil.select(aJCas,
				DocumentMetaData.class)) {
			id = metaData.getDocumentId();
		}
		// If the current xmi file is contained in the amt results, start
		// processing!
		if (mTurkMap.containsKey(id)) {
			// List containing all workers for this review
			List<String> workers = new ArrayList<String>();

			// Hashmap mapping workerids to their annotations:
			HashMap<String, List<String>> workerAnnotations = new HashMap<String, List<String>>();

			for (Integer key : mTurkMap.get(id).keySet()) {
				List<HashMap<String, String>> mTurkResults = mTurkMap.get(id)
						.get(key);
				int numberOfFoundMC = 0;

				// HashMap for storing BIO tagging for all annotators
				for (HashMap<String, String> entry : mTurkResults) {
					// Processing single line of the AMT results file

					// Add worker id, if not already in the list of workers
					String workerID = entry.get("workerid");
					if (!workers.contains(workerID)) {
						workers.add(workerID);
					}
					// List containing the major claim annotation:
					if (entry.get("Answer.intext").equals("true")
							&& entry.get("Answer.tokens") != null) {
						numberOfFoundMC++;
					}
					// Add this to the annotation map:
				}

				int numberOfAnnotators = workers.size();
				Double rate = (double) (numberOfFoundMC)
						/ (double) (numberOfAnnotators);
				return rate;
			}
		}
		return 0.0;
	}

	/**
	 * Calculates how many annotators think there should be some kind of
	 * annotation at this point in the review and returns the ratio.
	 *
	 * @param aJCas
	 *            the current jCas
	 * @param annotations
	 *            the annotations with overlap we want to evaluate
	 * @param numberOfWorkers
	 *            the overall number of annotators for this review
	 * @return
	 */
	private List<Double> calculateBinaryAgreementClaims(JCas aJCas) {
		List<Double> agreements = new ArrayList<Double>();
		String id = "";
		for (DocumentMetaData metaData : JCasUtil.select(aJCas,
				DocumentMetaData.class)) {
			id = metaData.getDocumentId();
		}
		// If the current xmi file is contained in the amt results, start
		// processing!
		if (mTurkMap.containsKey(id)) {
			// List containing all workers for this review
			List<String> workers = new ArrayList<String>();

			// Hashmap mapping workerids to their annotations:
			HashMap<String, List<String>> workerAnnotations = new HashMap<String, List<String>>();

			for (Integer key : mTurkMap.get(id).keySet()) {
				List<HashMap<String, String>> mTurkResults = mTurkMap.get(id)
						.get(key);
				int numberOfFoundMC = 0;
				// HashMap for storing BIO tagging for all annotators
				for (HashMap<String, String> entry : mTurkResults) {
					// Processing single line of the AMT results file
					// Add worker id, if not already in the list of workers
					String workerID = entry.get("workerid");
					if (!workers.contains(workerID)) {
						workers.add(workerID);
					}
					// List containing the major claim annotation:
					if (entry.get("Answer.intext").equals("true")
							&& entry.get("Answer.tokens") != null) {
						numberOfFoundMC++;
					}
					// Add this to the annotation map:
				}

				int numberOfAnnotators = workers.size();
				Double rate = (double) (numberOfFoundMC)
						/ (double) (numberOfAnnotators);
				agreements.add(rate);
			}
		}
		return agreements;

	}

	/**
	 * Calculates the krippendorff's alpha u for inter annotator agreement and
	 * adds it to the current jcas. The smallest possible unit here is a token,
	 * since the annotators can only mark one token.
	 */
	private List<Double> calculateAlphaUAgreementTokenClaims(JCas aJCas) {

		List<Double> agreements = new ArrayList<Double>();
		String id = "";
		for (DocumentMetaData metaData : JCasUtil.select(aJCas,
				DocumentMetaData.class)) {
			id = metaData.getDocumentId();
		}

		// If the current xmi file is contained in the amt results, start
		// processing!
		if (mTurkMap.containsKey(id)) {
			// List containing all workers for this review
			List<String> workers = new ArrayList<String>();

			// Hashmap mapping workerids to their annotations:
			HashMap<String, List<List<String>>> workerAnnotations = new HashMap<String, List<List<String>>>();

			System.out.println("Found ID " + id);
			for (Integer key : mTurkMap.get(id).keySet()) {
				List<HashMap<String, String>> mTurkResults = mTurkMap.get(id)
						.get(key);

				for (HashMap<String, String> entry : mTurkResults) {
					// Processing single line of the AMT results file

					// Add worker id, if not already in the list of workers
					String workerID = entry.get("workerid");
					if (!workers.contains(workerID)) {
						workers.add(workerID);
					}

					List<List<String>> claims = new ArrayList<List<String>>();
					if (entry.get("Answer.intext").equals("true")
							&& entry.get("Answer.tokens") != null) {
						for (String claim : entry.get("Answer.tokens").split(
								"}")) {
							// Remove all the undefined strings (whitespaces) in
							// the annotation
							String tmpClaim = claim.replace("undefined,", "");
							if (tmpClaim.endsWith(",")) {
								tmpClaim = tmpClaim.substring(0,
										tmpClaim.length() - 1);
							}
							// Add single claim to the claim list
							List<String> claimTokens = new ArrayList<String>();
							for (String claimToken : tmpClaim.split(",")) {
								claimTokens.add(claimToken);
							}
							claims.add(claimTokens);
						}

					}
					// Add this to the annotation map:
					workerAnnotations.put(workerID, claims);
				}

				int numberOfAnnotators = workers.size();
				// Since we can annotate the review title as well, the document
				// length is the review text
				// length + review title length
				List<Token> sortedTokenList = this.getSortedTokenList(aJCas);

				UnitizingAnnotationStudy study = new UnitizingAnnotationStudy(
						numberOfAnnotators, sortedTokenList.size());

				// Iterate over the workers, get every mc annotation, add it to
				// the study with the according worker index
				for (String wID : workers) {
					for (List<String> thisAnots : workerAnnotations.get(wID)) {
						try {
							study.addUnit(sortedTokenList.indexOf(this
									.getBeginToken(aJCas, thisAnots.get(0))),
									thisAnots.size(), workers.indexOf(wID), "X");
						} catch (IndexOutOfBoundsException e) {
							System.out.println("No annotation from worker: "
									+ wID);
						}
					}
				}

				KrippendorffAlphaUnitizingAgreement alpha = new KrippendorffAlphaUnitizingAgreement(
						study);
				agreements.add(alpha.calculateAgreement());
			}
		}
		return agreements;
	}

    /*
     * ==================================================================================
     * 								Helper functions start
     * ==================================================================================
     */



	/**
	 * Simple function to check for equality of lists independent of the order.
	 *
	 * @param l1
	 * @param l2
	 * @return true if lists contain the same elements; else false
	 */
	public static <T> boolean listEqualsNoOrder(List<T> l1, List<T> l2) {
		final Set<T> s1 = new HashSet<>(l1);
		final Set<T> s2 = new HashSet<>(l2);

		return s1.equals(s2);
	}

	/**
	 * Returns a list of the covered token in the major claim annotation
	 *
	 * @return a list of the covered token. Returns an empty list, if there was
	 *         no annotation at all
	 */
	// private List<Token> getCoveredTokens(JCas aJCas, int start, int end,
	// String type)
	// {
	// String tokenType = type;
	// List<Token> sortedTokenList = new ArrayList<Token>();
	// // Return an empty token list, if we have annotation at all!
	// if (!(tokenType.equals("token"))) {
	// return sortedTokenList;
	// }
	// sortedTokenList = this.getSortedTokenList(aJCas);
	// Token startToken = this.getBeginToken(aJCas, start);
	// Token endToken = this.getEndToken(aJCas, end);
	// // In case we have an annotation with only 1 token, return it instantly
	// if(start>=end){
	// sortedTokenList.add(startToken);
	// return sortedTokenList;
	// }
	// sortedTokenList.subList(sortedTokenList.indexOf(startToken),
	// sortedTokenList.indexOf(endToken));
	// return sortedTokenList;
	// }

	/**
	 * Returns the end token of a major claim annotation
	 */
	private Token getEndToken(JCas aJCas, int end) {
		int endIndex = end;
		FSIterator<Token> tokenIterator = aJCas.getAllIndexedFS(Token.class);
		tokenIterator.moveToFirst();
		while (tokenIterator.hasNext()) {
			Token token = tokenIterator.get();
			if (token.getId().contains("titleToken")
					&& token.getEnd() == endIndex) {
				return token;
			}
			tokenIterator.moveToNext();
		}
		return null;
	}

	/**
	 * Returns the start token of a major claim annotation
	 */
	private Token getBeginToken(JCas aJCas, String tokenID) {
		FSIterator<Token> tokenIterator = aJCas.getAllIndexedFS(Token.class);
		tokenIterator.moveToFirst();
		while (tokenIterator.hasNext()) {
			Token token = tokenIterator.get();
			if (token.getId() == tokenID) {
				return token;
			}
			tokenIterator.moveToNext();
		}
		return null;
	}

	/**
	 * Returns the id of the startToken of an annotation If the annotation was a
	 * comment, the returned index will be -1.
	 */
	private String getStartTokenID(JCas aJCas, int begin, String type) {
		String tokenType = type;
		int startIndex = begin;
		FSIterator<Token> tokenIterator = aJCas.getAllIndexedFS(Token.class);
		tokenIterator.moveToFirst();
		while (tokenIterator.hasNext()) {
			Token token = tokenIterator.get();
			if (token.getId().contains(tokenType)
					&& token.getBegin() == startIndex) {
				return token.getId();
			}
			tokenIterator.moveToNext();
		}
		String errorString = "-1";
		return errorString;
	}

	/**
	 * Returns the id of the endToken of an annotation If the annotation was a
	 * comment, the returned index will be -1.
	 */
	private String getEndTokenID(JCas aJCas, int end, String type) {
		String tokenType = type;
		int endIndex = end;
		FSIterator<Token> tokenIterator = aJCas.getAllIndexedFS(Token.class);
		tokenIterator.moveToFirst();
		while (tokenIterator.hasNext()) {
			Token token = tokenIterator.get();
			if (token.getId().contains(tokenType) && token.getEnd() == endIndex) {
				return token.getId();
			}
			if (tokenType.equals("titleToken_reviewToken")) {
				if ((token.getId().contains("titleToken") || token.getId()
						.contains("reviewToken")) && token.getEnd() == endIndex) {
					return token.getId();
				}
			}
			tokenIterator.moveToNext();
		}
		String errorString = "-1";
		return errorString;
	}

    /**
     * Returns an array list of tokens which are sorted in the correct order.
     */
    protected List<Token> getSortedTokenList(JCas aJCas)
    {
        FSIterator<Token> tokenIterator = aJCas.getAllIndexedFS(Token.class);
        tokenIterator.moveToFirst();
        List<Token> sortedList = new ArrayList<Token>();
        // First add the title tokens:
        tokenIterator.moveToFirst();
        while (tokenIterator.hasNext()) {
            Token token = tokenIterator.get();
            sortedList.add(token);
            tokenIterator.moveToNext();
        }
        // Then add the review token
        return sortedList;
    }

    private String removeQuotation(String element)
    {
        if (element.contains("\"")) {
            element = element.substring(1, element.length() - 1);
        }
        return element;
    }

    private void loadMTurkResults()
            throws IOException
        {
            BufferedReader br = new BufferedReader(new FileReader(resultFile));
            String[] headers = br.readLine().split("\t");
            String line;
            while ((line = br.readLine()) != null) {
                HashMap<String, String> innerMap = new HashMap<String, String>();
                String[] lineElements = line.split("\t");
                for (int i = 0; i < lineElements.length; i++) {
                    if (parameters.contains(removeQuotation(headers[i]))) {
                        innerMap.put(removeQuotation(headers[i]), removeQuotation(lineElements[i]));
                    }
                }
                if (innerMap.get("assignmentstatus").equals("Rejected")
                        || !innerMap.get("reject").isEmpty()) {
                    continue;
                }
                String documentname = innerMap.get("annotation");
                String workerID = innerMap.get("workerid");
                // Add worker to the overall workerlist
                if(!allWorkers.contains(workerID)){
                    allWorkers.add(workerID);
                }
                Integer number = 0;
                if(documentname.contains("hit-premise")){
                	number = Integer.parseInt(documentname.substring(
                    		documentname.indexOf("hit-premise-")+"hit-premise-".length(),
                    		documentname.indexOf("-review-")));
                }
                documentname = documentname.split("-")[documentname.split("-").length - 1].replace(
                        ".html", "");
                List<HashMap<String, String>> tmpList;
                HashMap<Integer, List<HashMap<String, String>>> tmpMap;
                if (mTurkMap.containsKey(documentname)) {
                    tmpMap = mTurkMap.get(documentname);
                    if(mTurkMap.get(documentname).containsKey(number)){
                        tmpList = mTurkMap.get(documentname).get(number);
                    }
                    else{
                    	tmpList = new ArrayList<HashMap<String, String>>();
                    }
                }
                else {
                    tmpList = new ArrayList<HashMap<String, String>>();
                    tmpMap = new HashMap<Integer, List<HashMap<String, String>>>();
                }
                tmpList.add(innerMap);
                tmpMap.put(number, tmpList);
                mTurkMap.put(documentname, tmpMap);
            }
            br.close();
        }


    /*
     * ==================================================================================
     * 								Helper functions end
     * ==================================================================================
     */

	public static void main(String[] args) throws IOException, UIMAException {
		new AlphaUCalculator().doMain(args);
	}

	private void doMain(String[] args) throws IOException, UIMAException {
		CmdLineParser parser = new CmdLineParser(this);
		try {
			// parse the arguments.
			parser.parseArgument(args);
			this.getStatistics(inputDir, outputDir, annotationLevel);
		} catch (CmdLineException e) {
			System.err.println(e.getMessage());
			System.err.println("java " + this.getClass().getSimpleName()
					+ " [options...] arguments...");
			parser.printUsage(System.err);
			System.err.println();
			return;
		}
	}




}
