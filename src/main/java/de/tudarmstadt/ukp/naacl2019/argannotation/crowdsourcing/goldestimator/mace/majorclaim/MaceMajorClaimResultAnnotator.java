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

package de.tudarmstadt.ukp.naacl2019.argannotation.crowdsourcing.goldestimator.mace.majorclaim;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.uima.UIMAException;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.factory.CollectionReaderFactory;
import org.apache.uima.fit.pipeline.SimplePipeline;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;

import de.tudarmstadt.ukp.dkpro.argumentation.types.ArgumentUnitUtils;
import de.tudarmstadt.ukp.dkpro.argumentation.types.MajorClaim;
import de.tudarmstadt.ukp.dkpro.core.api.metadata.type.DocumentMetaData;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token;
import de.tudarmstadt.ukp.dkpro.core.io.xmi.XmiReader;
import de.tudarmstadt.ukp.dkpro.core.io.xmi.XmiWriter;
import de.tudarmstadt.ukp.naacl2019.argannotation.crowdsourcing.goldestimator.mace.MaceResultAnnotator;

public class MaceMajorClaimResultAnnotator
    extends MaceResultAnnotator
{

    @Override
    public void process(JCas aJCas)
        throws AnalysisEngineProcessException
    {
        ArrayList<File> files = new ArrayList<>(FileUtils.listFiles(new File(sourceLocation),
                new String[] { "csv" }, false));
        DocumentMetaData metaData = JCasUtil.selectSingle(aJCas, DocumentMetaData.class);
        // If we found the results file, read the file
        if (files.contains(new File(sourceLocation + metaData.getDocumentId() + ".csv"))) {
            System.out.println("Processing: " + metaData.getDocumentId());
            try {
                BufferedReader br;
                br = new BufferedReader(new FileReader(sourceLocation + metaData.getDocumentId()
                        + ".csv"));

                // Get the predictions
                List<String> annotation = new ArrayList<String>();
                String line = "";
                while ((line = br.readLine()) != null) {
                    // The first tab separated element contains the predicted tag and it's
                    // probability
                    annotation.add(line.split("\t")[0]);
                }
                br.close();

                List<Token> tokens = getSortedTokenList(aJCas);

                if (tokens.size() != annotation.size()) {
                    throw new IllegalStateException(
                            "Size of the predicted file does not match the number of tokens; expected "
                                    + tokens.size() + " but was " + annotation.size());
                }
                boolean claimFlag = false;
                int index = 0;
                List<List<String>> allAnnotations = new ArrayList<List<String>>();
                List<String> singleAnnotation = new ArrayList<String>();
                HashMap<String, List<Integer>> tokenIndex = new HashMap<String, List<Integer>>();
                double majorClaimProbability = 0.0;
                int length = 0;
                for (Token token : tokens) {
                    String tag = annotation.get(index);
                    List<Integer> indices = new ArrayList<Integer>();
                    indices.add(token.getBegin());
                    indices.add(token.getEnd());
                    tokenIndex.put(token.getId(), indices);
                    /*
                    // In case we have 2 following separate claim annotations
                    if (tag.contains("B") && claimFlag) {
                        double probability = majorClaimProbability / (double) length;
                        singleAnnotation.add(String.valueOf(probability));
                        allAnnotations.add(singleAnnotation);
                        singleAnnotation = new ArrayList<String>();
                        singleAnnotation.add(token.getId());
                        length = 0;
                        majorClaimProbability = 0.0;
                        majorClaimProbability += Double.valueOf(tag.split(" ")[1]);
                        length++;
                    }
                    */
                    // If we have a starting claim annotation
                    if (tag.contains("B") && !claimFlag) {
                        singleAnnotation = new ArrayList<String>();
                        claimFlag = true;
                        singleAnnotation.add(token.getId());
                        majorClaimProbability += Double.valueOf(tag.split(" ")[1]);
                        length++;
                    }
                    // Add token id when the annotation continues
                    if (tag.contains("I") && claimFlag) {
                        singleAnnotation.add(token.getId());
                        majorClaimProbability += Double.valueOf(tag.split(" ")[1]);
                        length++;
                    }
                    // If the annotation ends, add the list of claim tokenids to the overall list
                    if (claimFlag && (tag.contains("O") || index == tokens.size()-1)) {
                        double probability = majorClaimProbability / length;
                        singleAnnotation.add(String.valueOf(probability));
                        allAnnotations.add(singleAnnotation);
                        length = 0;
                        majorClaimProbability = 0.0;
                        claimFlag = false;
                        singleAnnotation = new ArrayList<String>();
                    }
                    index++;
                }
                // Now we can add the found claims to the jcas
                List<String> bestPair = new ArrayList<String>();
                double bestScore = 0.0;
                for (List<String> claim : allAnnotations) {
                    // TODO: Decide, if we discard all one token sized annotations
                    if(claim.size()<=2){
                        System.out.println("We have a one token annotation!");
                        System.out.println(claim.toString());
                        //continue;
                    }
                    List<String> majorClaim = new ArrayList<String>(claim.subList(0, claim.size()-1));
                    double score = Double.parseDouble(claim.get(claim.size()-1));
                    if (bestScore < score) {
                        bestScore = score;
                        bestPair = new ArrayList<String>(majorClaim);
                    }
                }
                if (bestPair.size() == 0){
                	System.err.println("We have no Major Claim annotation for this document.");
                	return;
                }

                int begin = -1;
                int end = -1;
                begin = tokenIndex.get(bestPair.get(0)).get(0);
                end = tokenIndex.get(bestPair.get(bestPair.size() - 1)).get(1);

                String coveredTokenIDs = "";
                for (String tk : bestPair) {
                    coveredTokenIDs += tk + ";";
                }
                MajorClaim cl = new MajorClaim(aJCas, begin, end);
                ArgumentUnitUtils.setProperty(cl, "Covered_Token", coveredTokenIDs);
                cl.addToIndexes(aJCas);

            }
            catch (IOException e) {
                throw new AnalysisEngineProcessException(e);
            }
        }
    }

	public static void main(String[] args)
            throws Exception
    {
		new MaceMajorClaimResultAnnotator().doMain(args);

    }

    private void doMain(String[] args) throws UIMAException, IOException
    {
    	CmdLineParser parser = new CmdLineParser(this);
		try {
            // parse the arguments.
            parser.parseArgument(args);
            SimplePipeline.runPipeline(CollectionReaderFactory.createReader(XmiReader.class,
                    XmiReader.PARAM_SOURCE_LOCATION, inputDir, XmiReader.PARAM_PATTERNS,
                    XmiReader.INCLUDE_PREFIX + "*.xmi"), AnalysisEngineFactory.createEngineDescription(
                    MaceMajorClaimResultAnnotator.class,
                    MaceMajorClaimFileWriter.PARAM_SOURCE_LOCATION, resultFile),
                    AnalysisEngineFactory.createEngineDescription(XmiWriter.class,
                            XmiWriter.PARAM_TARGET_LOCATION, outputDir,
                            XmiWriter.PARAM_OVERWRITE, true));
        } catch( CmdLineException e ) {
            System.err.println(e.getMessage());
            System.err.println("java "+this.getClass().getSimpleName()+" [options...] arguments...");
            parser.printUsage(System.err);
            System.err.println();
            return;
        }
    }

}
