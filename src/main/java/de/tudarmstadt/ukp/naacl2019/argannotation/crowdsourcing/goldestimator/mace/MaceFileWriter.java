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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
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

/**
 * Writes one review to the mace file with the associated tokens and annotators.
 *
 *
 */
public abstract class MaceFileWriter
    extends JCasAnnotator_ImplBase
{

    /**
     * Location of the AMT results file
     */
    public static final String PARAM_SOURCE_LOCATION = ComponentParameters.PARAM_SOURCE_LOCATION;
    @ConfigurationParameter(name = PARAM_SOURCE_LOCATION, mandatory = true)
    protected String sourceLocation;

    /**
     * Location to write the result csv files
     */
    public static final String PARAM_TARGET_LOCATION = ComponentParameters.PARAM_TARGET_LOCATION;
    @ConfigurationParameter(name = PARAM_TARGET_LOCATION, mandatory = true)
	protected String outputLocation;

    // HashMap containing the AMT results data
    protected HashMap<String, HashMap<Integer, List<HashMap<String, String>>>> mTurkMap = new HashMap<String, HashMap<Integer, List<HashMap<String, String>>>>();
    // List containing the AMT result columns
    protected List<String> parameters = new ArrayList<String>();

    // List of all worker ids in the results file
    protected List<String> allWorkers = new ArrayList<String>();

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
        sourceLocation = ctx.getConfigParameterValue(PARAM_SOURCE_LOCATION).toString();
        outputLocation = ctx.getConfigParameterValue(PARAM_TARGET_LOCATION).toString();
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

    private void loadMTurkResults()
        throws IOException
    {
        BufferedReader br = new BufferedReader(new FileReader(sourceLocation));
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

    @Override
    public abstract void process(JCas aJCas)
        throws AnalysisEngineProcessException;

    private String removeQuotation(String element)
    {
        if (element.contains("\"")) {
            element = element.substring(1, element.length() - 1);
        }
        return element;
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
