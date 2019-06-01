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

package de.tudarmstadt.ukp.naacl2019.argannotation.crowdsourcing.goldestimator.mace.premise;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

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

import de.tudarmstadt.ukp.dkpro.argumentation.types.ArgumentRelation;
import de.tudarmstadt.ukp.dkpro.argumentation.types.ArgumentUnitUtils;
import de.tudarmstadt.ukp.dkpro.argumentation.types.Attack;
import de.tudarmstadt.ukp.dkpro.argumentation.types.Claim;
import de.tudarmstadt.ukp.dkpro.argumentation.types.Premise;
import de.tudarmstadt.ukp.dkpro.argumentation.types.Support;
import de.tudarmstadt.ukp.dkpro.core.api.metadata.type.DocumentMetaData;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token;
import de.tudarmstadt.ukp.dkpro.core.io.xmi.XmiReader;
import de.tudarmstadt.ukp.dkpro.core.io.xmi.XmiWriter;
import de.tudarmstadt.ukp.naacl2019.argannotation.crowdsourcing.goldestimator.mace.MaceResultAnnotator;
import de.tudarmstadt.ukp.naacl2019.argannotation.crowdsourcing.goldestimator.mace.claim.MaceClaimPremiseFileWriter;

public class MacePremiseResultAnnotator
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
        for(File file: files){
        	if(file.getName().contains("-") && file.getName().substring(0,file.getName().indexOf("-")).equals(metaData.getDocumentId())){
        		int claimNumber = Integer.parseInt(file.getName().substring(file.getName().indexOf("-")+1, file.getName().indexOf("-")+2));
                try {
                    String result = "";
                    BufferedReader br;
                    br = new BufferedReader(new FileReader(file));

                    String line;
                    while ((line = br.readLine()) != null) {
                        result += line + ",";
                    }
                    br.close();

                    // Postprocessing step
                    // Convert I tags to B tags
                    result = result.replaceAll("O,I-S", "O,B-S");
                    result = result.replaceAll("O,I-A", "O,B-A");
                    result = result.replaceAll("I-A,I-S", "I-A,B-S");
                    result = result.replaceAll("I-S,I-A", "I-S,B-A");
                    // Remove standalone B tags
                    result = result.replaceAll("B-S,I-A", "O,B-A");
                    result = result.replaceAll("B-A,I-S", "O,B-S");
                    // Remove single tag annotations
                    result = result.replaceAll("B-.,O", "O,O");

                    List<Token> tokens = getSortedTokenList(aJCas);
                    // Remove last comma from string and create an array to iterate for
                    List<String> annotation = Arrays.asList(result.substring(0, result.length() - 1)
                            .split(","));
                    if (tokens.size() != annotation.size()) {
                        throw new IllegalStateException(
                                "Size of the predicted file does not match the number of tokens; expected "
                                        + tokens.size() + " but was " + annotation.size());
                    }
                    boolean claimFlag = false;
                    String stance = "";
                    int index = 0;
                    List<List<String>> allAnnotations = new ArrayList<List<String>>();
                    List<String> singleAnnotation = new ArrayList<String>();
                    HashMap<String, List<Integer>> tokenIndex = new HashMap<String, List<Integer>>();
                    for (Token token : tokens) {
                        String tag = annotation.get(index);
                        List<Integer> indices = new ArrayList<Integer>();
                        indices.add(token.getBegin());
                        indices.add(token.getEnd());
                        tokenIndex.put(token.getId(), indices);
                        // If we have a starting claim annotation
                        if (tag.contains("B") && !claimFlag) {
                            singleAnnotation = new ArrayList<String>();
                            stance = getStance(tag);
                            claimFlag = true;
                            singleAnnotation.add(stance);
                            singleAnnotation.add(token.getId());
                        }
                        // Add token id when the annotation continues
                        if (tag.contains("I") && claimFlag) {
                            singleAnnotation.add(token.getId());
                        }
                        // If the annotation ends, add the list of claim tokenids to the overall list
                        if (tag.contains("O") && claimFlag) {
                            allAnnotations.add(singleAnnotation);
                            claimFlag = false;
                            singleAnnotation = new ArrayList<String>();
                        }
                        // In case we have 2 following separate claim annotations
                        if (tag.contains("B") && claimFlag && !getStance(tag).equals(stance)) {
                            allAnnotations.add(singleAnnotation);
                            singleAnnotation = new ArrayList<String>();
                            stance = getStance(tag);
                            singleAnnotation.add(stance);
                            singleAnnotation.add(token.getId());
                        }
                        index++;
                    }
                    // Now we can add the found claims to the jcas
                    for (List<String> premise : allAnnotations) {
                        int begin = -1;
                        int end = -1;
                        // First element contains the stance
                        stance = premise.get(0);
                        begin = tokenIndex.get(premise.get(1)).get(0);
                        end = tokenIndex.get(premise.get(premise.size() - 1)).get(1);
                        String coveredTokenIDs = "";
                        for (String tk : premise.subList(1, premise.size())) {
                            coveredTokenIDs += tk + ";";
                        }
                        Premise p = new Premise(aJCas, begin, end);
                        ArgumentRelation relation = null;
                        if (stance.equals("support")) {
                           relation = new Support(aJCas);
                        }
                        else if (stance.equals("attack")) {
                            relation = new Attack(aJCas);
                        }
                        else {
                            throw new IllegalArgumentException("Unknown stance type: " + stance);
                        }
                        relation.setSource(p);

                        Collection<Claim> claims = JCasUtil.select(aJCas, Claim.class);
                        SortedMap<Integer,Claim> claimMap = new TreeMap<Integer,Claim>();

                        for(Claim claim: claims){
                        	claimMap.put(claim.getBegin(), claim);
                        }

                        int i=0;
                        for(Integer key: claimMap.keySet()){
                        	if(i == claimNumber){
                        		relation.setTarget(claimMap.get(key));
                        	}
                        	i++;
                        }
                        ArgumentUnitUtils.setProperty(p, "Covered_Token", coveredTokenIDs);
                        p.addToIndexes(aJCas);
                        relation.addToIndexes(aJCas);
                    }
                }
                catch (IOException e) {
                    throw new AnalysisEngineProcessException(e);
                }
        	}
        }
    }

	public static void main(String[] args)
            throws Exception
    {
		new MacePremiseResultAnnotator().doMain(args);

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
                    MacePremiseResultAnnotator.class, MaceClaimPremiseFileWriter.PARAM_SOURCE_LOCATION,
                    resultFile), AnalysisEngineFactory.createEngineDescription(
                    XmiWriter.class, XmiWriter.PARAM_TARGET_LOCATION, outputDir,
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
