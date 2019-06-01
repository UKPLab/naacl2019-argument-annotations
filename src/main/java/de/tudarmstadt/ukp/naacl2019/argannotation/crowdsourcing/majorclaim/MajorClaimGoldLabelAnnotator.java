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

package de.tudarmstadt.ukp.naacl2019.argannotation.crowdsourcing.majorclaim;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.uima.UIMAException;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.fit.component.JCasAnnotator_ImplBase;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.factory.CollectionReaderFactory;
import org.apache.uima.fit.pipeline.SimplePipeline;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import de.tudarmstadt.ukp.naacl2019.argannotation.types.GoldEstimation;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import de.tudarmstadt.ukp.dkpro.argumentation.types.ArgumentUnitUtils;
import de.tudarmstadt.ukp.dkpro.argumentation.types.MajorClaim;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token;
import de.tudarmstadt.ukp.dkpro.core.io.xmi.XmiReader;
import de.tudarmstadt.ukp.dkpro.core.io.xmi.XmiWriter;

public class MajorClaimGoldLabelAnnotator
    extends JCasAnnotator_ImplBase
{

    /**
     * Returns an array list of tokens which are sorted in the correct order, which means we first
     * get a list of all the tokens
     */
    private List<Token> getSortedTokenList(JCas aJCas)
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

    private boolean hasGoldStandard(JCas aJCas)
    {
        List<GoldEstimation> goldEstimations = new ArrayList<>(JCasUtil.select(aJCas,
                GoldEstimation.class));
        for (GoldEstimation ge : goldEstimations) {
            if (ge.getEstimatedGoldAnnotation()) {
                return true;
            }
        }
        return false;
    }

    protected void annotateJCasWithGoldData(JCas aJCas)
    {

        List<GoldEstimation> goldEstimations = new ArrayList<>(JCasUtil.select(aJCas,
                GoldEstimation.class));
        GoldEstimation goldStandard = new GoldEstimation(aJCas);
        for (GoldEstimation ge : goldEstimations) {
            if (ge.getEstimatedGoldAnnotation()) {
                goldStandard = ge;
            }

        }
        if (this.hasGoldStandard(aJCas)) {
            String startTokenID = goldStandard.getStartTokenID();
            String endTokenID = goldStandard.getEndTokenID();
            List<Token> tokens = this.getSortedTokenList(aJCas);
            Token startToken = new Token(aJCas);
            Token endToken = new Token(aJCas);
            // Set the appropriate start and end token
            for (Token tk : tokens) {
                if (tk.getId().equals(startTokenID)) {
                    startToken = tk;
                }
                if (tk.getId().equals(endTokenID)) {
                    endToken = tk;
                }
            }
            List<Token> coveredToken = tokens.subList(tokens.indexOf(startToken),
                    tokens.indexOf(endToken)+1);
            String coveredTokenIDs = "";
            for (Token tk : coveredToken) {
                coveredTokenIDs += tk.getId() + ";";
            }

            // For the major claims, we set the begin, the end, and the list of tokenIDs for
            // properties
            MajorClaim majorClaim = new MajorClaim(aJCas);
            majorClaim.setBegin(startToken.getBegin());
            majorClaim.setEnd(startToken.getEnd());
            ArgumentUnitUtils.setProperty(majorClaim, "Covered_Token", coveredTokenIDs);
            majorClaim.addToIndexes();
        }
        // remove gold annotations from the xmi and only keep the major claim
        for (GoldEstimation ge : goldEstimations) {
            ge.removeFromIndexes(aJCas);
        }
    }

    @Override
    public void process(JCas aJCas)
        throws AnalysisEngineProcessException
    {
        annotateJCasWithGoldData(aJCas);
    }


    @Option(name="-o",aliases = { "--output" },metaVar="dir",usage="output folder", required=true)
	private File outputDir;

	@Option(name="-i",aliases = { "--input" },metaVar="dir",usage="input folder", required=true)
	private File inputDir;

    public static void main(String[] args)
        throws IOException, UIMAException
    {
       new MajorClaimGoldLabelAnnotator().doMain(args);
    }

    /**
     * @param args
     * @throws IOException
     * @throws IllegalArgumentException
     * @throws UIMAException
     * @throws ResourceInitializationException
     */
    private void doMain(String[] args)
        throws ResourceInitializationException, UIMAException, IllegalArgumentException,
        IOException
    {
    	CmdLineParser parser = new CmdLineParser(this);
		try {
            // parse the arguments.
            parser.parseArgument(args);
            SimplePipeline.runPipeline(CollectionReaderFactory.createReader(XmiReader.class,
                    XmiReader.PARAM_SOURCE_LOCATION, inputDir, XmiReader.PARAM_PATTERNS,
                    XmiReader.INCLUDE_PREFIX + "*.xmi"), AnalysisEngineFactory
                    .createEngineDescription(MajorClaimGoldLabelAnnotator.class), AnalysisEngineFactory
                    .createEngineDescription(XmiWriter.class, XmiWriter.PARAM_OVERWRITE, true,
                            XmiWriter.PARAM_TARGET_LOCATION, outputDir));
        } catch( CmdLineException e ) {
            System.err.println(e.getMessage());
            System.err.println("java "+this.getClass().getSimpleName()+" [options...] arguments...");
            parser.printUsage(System.err);
            System.err.println();
            return;
        }

    }

}
