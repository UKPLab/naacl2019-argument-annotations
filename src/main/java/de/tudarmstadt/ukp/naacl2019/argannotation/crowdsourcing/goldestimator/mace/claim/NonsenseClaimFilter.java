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

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.NoSuchElementException;

import org.apache.uima.UIMAException;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.cas.FeatureStructure;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.factory.CollectionReaderFactory;
import org.apache.uima.fit.pipeline.SimplePipeline;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;

import de.tudarmstadt.ukp.dkpro.argumentation.types.Attack;
import de.tudarmstadt.ukp.dkpro.argumentation.types.Claim;
import de.tudarmstadt.ukp.dkpro.argumentation.types.Support;
import de.tudarmstadt.ukp.dkpro.core.api.metadata.type.DocumentMetaData;
import de.tudarmstadt.ukp.dkpro.core.io.xmi.XmiReader;
import de.tudarmstadt.ukp.dkpro.core.io.xmi.XmiWriter;
import de.tudarmstadt.ukp.naacl2019.argannotation.crowdsourcing.goldestimator.mace.MaceFileWriter;

/**
 * Writes one review to the mace file with the associated tokens and annotators.
 *
 *
 */
public class NonsenseClaimFilter
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

    	/* ======================================================================
    	 * Removing nonsense claims from the cas for writing it later,
    	 * This assumes that the order of claims does not change during HIT creation and this step!
    	 * ======================================================================
    	 */
    	ArrayList<Claim> claimList = new ArrayList<Claim>();

        try {
            // Get the claims
            FSIterator<Claim> cIterator = aJCas.getAllIndexedFS(Claim.class);
            cIterator.moveToFirst();
            Claim checkclaim = cIterator.get();
            while(cIterator.hasNext()){
                Claim claim = cIterator.get();
                claimList.add(claim);
                cIterator.moveToNext();
            }
        }
        catch (NoSuchElementException e) {
            // Print error message, in case we have no claim at all.
            System.out.println("We have no Claim annotation in:  " + id);
        }
        // ======================================================================


        // If the current xmi file is contained in the amt results, start processing!
        if (mTurkMap.containsKey(id)) {
        	HashMap<Integer, List<HashMap<String, String>>> outerMap = mTurkMap.get(id);
            //System.out.println("FOUND ID " + id);
            int claimIndex = 0; 			// Claim ndex for removal
            for(Integer key: outerMap.keySet()){
            	List<HashMap<String, String>> mTurkResults = outerMap.get(key);

	            /*
	             * Claims are annotated in the following format:
	             * "[becauseid:1{token_0,undefined,token_3,undefined,token_7,},
	             * [becauseid:5{undefined,token_99,undefined,token_102,},
	             * [becauseid:2{undefined,token_565,undefined,token_568,},
	             * [butid:4{token_596,undefined,token_600,undefined,token_605,},],"
	             */
	            // Number of nonsense annotations for filtering nonsense claims
	            int numNonsense = 0;

	            for (HashMap<String, String> entry : mTurkResults) {

	                String nonsense = entry.get("Answer.intext");
	                if(nonsense.contains("nonsense") || nonsense.contains("false")){
	                	numNonsense+= 1;
	                }
	            }
	            // Post-processing step for single-token claims:
	            try{
					Claim claim = claimList.get(claimIndex);
					// Get list of tokenids in the claim
					String[] coveredTokens = claim.getProperties().split("Covered_Token=")[1].split("&#10")[0].split(",");
					if (coveredTokens.length < 2){
						// Print claim and mark this claim for skipping:
						System.out.println("Found single token claim: "+claim.getCoveredText());
						numNonsense=5;
					}
	            }catch(IndexOutOfBoundsException e){
	            	System.out.println(e);
	            }

	            /* ======================================================================
	             * Remove nonsense claim from the cas, if more than 3 annotators voted this
	             *
	             * ======================================================================
	             */
				try {
					if (numNonsense > 2) {
						Claim claim = claimList.get(claimIndex);
						ArrayList<FeatureStructure> toRemove = new ArrayList<FeatureStructure>();
						toRemove.add(claim);
						try {
							// Get all attacks
							FSIterator<Attack> sIterator = aJCas
									.getAllIndexedFS(Attack.class);
							sIterator.moveToFirst();
							Attack checkAstance = sIterator.get();
							while (sIterator.hasNext()) {
								Attack attack = sIterator.get();
								if (attack.getSource().equals(claim)) {
									toRemove.add(attack);
								}else if (attack.getTarget().equals(claim)){
									toRemove.add(attack);
									// Only premises can link as a source to claims, so remove them too!
									toRemove.add(attack.getSource());
								}
								sIterator.moveToNext();
							}
						} catch (NoSuchElementException e) {
							System.out
									.println("No attack relation found for nonsense claim");
						}

						try {
							// Get all supports
							FSIterator<Support> aIterator = aJCas
									.getAllIndexedFS(Support.class);
							aIterator.moveToFirst();
							Support checkSstance = aIterator.get();
							while (aIterator.hasNext()) {
								Support support = aIterator.get();
								if (support.getSource().equals(claim)) {
									toRemove.add(support);
								}else if (support.getTarget().equals(claim)){
									toRemove.add(support);
									toRemove.add(support.getSource());
								}
								aIterator.moveToNext();
							}

						} catch (NoSuchElementException e) {
							System.out
									.println("No support relation found for nonsense claim");
						}

						// Remove claim and all related stances
						for (FeatureStructure fs : toRemove) {
							aJCas.removeFsFromIndexes(fs);
						}
					}// IndexOutOfBoundsException
				} catch (IndexOutOfBoundsException e) {
					System.out
							.println("Error indexing nonsense claim! Maybe you used the wrong input file?");
				}
	            claimIndex += 1;
            }

        }
    }

	public static void main(String[] args)
            throws Exception
    {
		new NonsenseClaimFilter().doMain(args);

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
                    NonsenseClaimFilter.class, NonsenseClaimFilter.PARAM_SOURCE_LOCATION,
                    resultFile, NonsenseClaimFilter.PARAM_TARGET_LOCATION,
                    outputDir),AnalysisEngineFactory.createEngineDescription(
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
