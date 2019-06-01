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

package de.tudarmstadt.ukp.naacl2019.argannotation.crowdsourcing.goldestimator.mace;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.kohsuke.args4j.Option;

import de.tudarmstadt.ukp.dkpro.core.api.parameter.ComponentParameters;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token;

public abstract class MaceResultAnnotator
    extends JCasAnnotator_ImplBase
{

    /**
     * Location of the MACE result files
     */
    public static final String PARAM_SOURCE_LOCATION = ComponentParameters.PARAM_SOURCE_LOCATION;
    @ConfigurationParameter(name = PARAM_SOURCE_LOCATION, mandatory = true)
    protected String sourceLocation;

    @Option(name="-o",aliases = { "--output" },metaVar="dir",usage="output folder", required=true)
	protected File outputDir;

	@Option(name="-i",aliases = { "--input" },metaVar="dir",usage="input folder", required=true)
	protected File inputDir;

	@Option(name="-r",aliases = { "--resultFile" },metaVar="file",usage="AMT .result file", required=true)
	protected File resultFile;

    @Override
    public void initialize(UimaContext ctx)
        throws ResourceInitializationException
    {
        super.initialize(ctx);
        sourceLocation = ctx.getConfigParameterValue(PARAM_SOURCE_LOCATION).toString() + "/";
    }

    @Override
    public abstract void process(JCas aJCas)
        throws AnalysisEngineProcessException;

    protected String getStance(String tag)
    {
        if (tag.contains("A")) {
            return "attack";
        }
        else if (tag.contains("S")) {
            return "support";
        }
        else {
            return "none";
        }
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

}
