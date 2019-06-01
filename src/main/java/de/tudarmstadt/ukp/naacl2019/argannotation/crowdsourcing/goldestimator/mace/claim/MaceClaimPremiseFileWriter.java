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

package de.tudarmstadt.ukp.naacl2019.argannotation.crowdsourcing.goldestimator.mace.claim;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.uima.UIMAException;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.factory.CollectionReaderFactory;
import org.apache.uima.fit.pipeline.SimplePipeline;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;

import de.tudarmstadt.ukp.dkpro.core.api.metadata.type.DocumentMetaData;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token;
import de.tudarmstadt.ukp.dkpro.core.io.xmi.XmiReader;
import de.tudarmstadt.ukp.naacl2019.argannotation.crowdsourcing.goldestimator.mace.MaceFileWriter;

/**
 * Writes one review to the mace file with the associated tokens and annotators.
 *
 *
 */
public class MaceClaimPremiseFileWriter
    extends MaceFileWriter
{
    @Override
    public void process(JCas aJCas)
        throws AnalysisEngineProcessException
    {


        String id = "";
        for (DocumentMetaData meta : JCasUtil.select(aJCas, DocumentMetaData.class)) {
            id = meta.getDocumentId();
        }


        // If the current xmi file is contained in the amt results, start processing!
        if (mTurkMap.containsKey(id)) {
        	HashMap<Integer, List<HashMap<String, String>>> outerMap = mTurkMap.get(id);
            System.out.println("FOUND ID " + id);
            for(Integer key: outerMap.keySet()){
            	List<HashMap<String, String>> mTurkResults = outerMap.get(key);
	            // List of sorted tokens of the review, since we have title and review token, we have to use this list!
	            List<Token> sortedTokens = new ArrayList<Token>();
	            sortedTokens = getSortedTokenList(aJCas);

	            // HashMap for storing BIO tagging for all annotators
	            HashMap<String, List<String>> annotations = new HashMap<String, List<String>>();

	            /*
	             * Claims are annotated in the following format:
	             * "[becauseid:1{token_0,undefined,token_3,undefined,token_7,},
	             * [becauseid:5{undefined,token_99,undefined,token_102,},
	             * [becauseid:2{undefined,token_565,undefined,token_568,},
	             * [butid:4{token_596,undefined,token_600,undefined,token_605,},],"
	             */


	            for (HashMap<String, String> entry : mTurkResults) {
	                // Processing single line of the AMT results file
	                                // HashMap containing the mapping tokenID, BIO tag for the annotations
	                HashMap<String, String> bioTagging = new HashMap<String,String>();
	                String workerID = entry.get("workerid");

	                // List containing all the claims
	                List<String> claims = new ArrayList<String>();
	                for (String claim : entry.get("Answer.tokens").split("}")) {
	                    // Remove all the undefined strings (whitespaces) in the annotation
	                	String tmpClaim = claim.replace("undefined,", "");
	                	if(tmpClaim.endsWith(",")){
	                		tmpClaim = tmpClaim.substring(0, tmpClaim.length()-1);
	                	}
	                    claims.add(claim.replace("undefined,", ""));
	                }
	                for (String claimString : claims) {
	                    String stance = "";
	                    // Set if we have a supporting or an attacking claim
	                    if (claimString.contains("because")) {
	                        stance = "support";
	                    }
	                    else if (claimString.contains("but")) {
	                        stance = "attack";
	                    }
	                    // Handle the closing bracket ] case getting split too...
	                    if (stance.isEmpty()) {
	                        continue;
	                    }
	                    // Ignore elements which contain the single ']' element due to the splitting beforehand
	                    if (claimString.length()>1) {
	                        List<String> tokens = new ArrayList<String>();
	                        // The first part of the claim string contains its type ("but" or "because")
	                        for (String token : claimString.split("\\{")[1].split(",")) {
	                            tokens.add(token);
	                            //System.out.println(token);
	                        }
	                        // For all claim annotations set the appropriate annotation tag
	                        if(stance.equals("attack")){
	                            // Set the B tag
	                            bioTagging.put(tokens.get(0), "B-A");
	                            for (String token : tokens.subList(1, tokens.size())){
	                                // Set the I tag
	                                bioTagging.put(token, "I-A");
	                            }
	                        }else if(stance.equals("support")){
	                            bioTagging.put(tokens.get(0), "B-S");
	                            for (String token : tokens.subList(1, tokens.size())){
	                                bioTagging.put(token, "I-S");
	                            }
	                        }
	                    }
	                }
	                // List containing the final BIO annotation for one worker
	                List<String> bioAnnotation = new ArrayList<String>();
	                for(Token token : sortedTokens){
	                    if(bioTagging.containsKey(token.getId())){
	                    	//System.out.println(bioTagging.get(token.getId()));
	                        bioAnnotation.add(bioTagging.get(token.getId()));
	                    }else{
	                        bioAnnotation.add("O");
	                    }
	                }
	                // Add this annotation to the overall annotation list
	                annotations.put(workerID, bioAnnotation);
	            }
	            String annotation = outerMap.get(key).get(0).get("annotation");
	            String path = id;
	            if(annotation.contains("hit-premise")){
	            	path +="-"+key;
	            }

		            System.out.println("Writing mace csv to: " + outputLocation + "/" + path + ".csv");
		            try (Writer writer = new BufferedWriter(new FileWriter(outputLocation + "/" + path + ".csv"))) {
		                // Iterate over the tokens to get the index of the list
		                for(int i=0;i<sortedTokens.size();i++){
		                    String singleLine = "";
		                    for(String worker : allWorkers){
		                        if(i>=sortedTokens.size()){
		                            System.out.println("Bad indexing! " + i);
		                            continue;
		                        }
		                        // If we have an annotation for this worker id, we add the tag, else
		                        if(annotations.containsKey(worker)){
		                            if(i>= annotations.get(worker).size()){
		                                System.out.println("Bad worker indexing! " + i + " Worker Size: " + annotations.get(worker).size() + " Tokens: " + sortedTokens.size() );
		                            }
		                            singleLine += annotations.get(worker).get(i);
		                        }
		                        singleLine += ",";
		                    }
		                    // Remove last comma in the line
		                    singleLine = singleLine.substring(0,singleLine.length()-1);
		                    writer.write(singleLine);
		                    writer.write(System.getProperty("line.separator"));
		                }
		                writer.close();
		                // Write the list of annotators into a file, that we can get the order later:
		            }
		            catch (IOException e) {
		                throw new AnalysisEngineProcessException(e);
		            }
	            try (Writer writer = new BufferedWriter(new FileWriter(outputLocation + "/" +  "workerIDs.txt"))) {
	                for(String worker : allWorkers){
	                    writer.write(worker);
	                    writer.write(System.getProperty("line.separator"));
	                }
	                writer.close();
	                // Write the list of annotators into a file, that we can get the order later:
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
		new MaceClaimPremiseFileWriter().doMain(args);

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
                    MaceClaimPremiseFileWriter.class, MaceClaimPremiseFileWriter.PARAM_SOURCE_LOCATION,
                    resultFile, MaceClaimPremiseFileWriter.PARAM_TARGET_LOCATION,
                    outputDir));
//            ,AnalysisEngineFactory.createEngineDescription(
//                    XmiWriter.class, XmiWriter.PARAM_TARGET_LOCATION, outputDir,
//                    XmiWriter.PARAM_OVERWRITE, true)
        } catch( CmdLineException e ) {
            System.err.println(e.getMessage());
            System.err.println("java "+this.getClass().getSimpleName()+" [options...] arguments...");
            parser.printUsage(System.err);
            System.err.println();
            return;
        }
    }
}
