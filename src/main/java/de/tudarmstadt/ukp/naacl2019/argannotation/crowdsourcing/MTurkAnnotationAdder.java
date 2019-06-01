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

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;

import de.tudarmstadt.ukp.dkpro.argumentation.types.Claim;
import de.tudarmstadt.ukp.dkpro.argumentation.types.MajorClaim;
import de.tudarmstadt.ukp.dkpro.core.api.metadata.type.DocumentMetaData;
import de.tudarmstadt.ukp.dkpro.core.api.parameter.ComponentParameters;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token;

/**
 * Annotator to add Major claims based on the Mechanical Turk annotations specified in PARAM_SOURCE_LOCATION
 *
 */
public class MTurkAnnotationAdder extends JCasAnnotator_ImplBase{

	/**
     * Location from which the input is read.
     */
    public static final String PARAM_SOURCE_LOCATION = ComponentParameters.PARAM_SOURCE_LOCATION;
    @ConfigurationParameter(name = PARAM_SOURCE_LOCATION, mandatory = true)
    private String sourceLocation;

    public static final String PARAM_ANNOTATION_TYPE = "m";
    @ConfigurationParameter(name = PARAM_ANNOTATION_TYPE, mandatory = false)
    private String annotationType;

	HashMap<String,ArrayList<HashMap<String,String>>> mTurkMap = new HashMap<String,ArrayList<HashMap<String,String>>>();
	ArrayList<String> parameters = new ArrayList<String>();
	@Override
    public void initialize(UimaContext ctx)
        throws ResourceInitializationException
    {
		super.initialize(ctx);
		sourceLocation = ctx.getConfigParameterValue(PARAM_SOURCE_LOCATION).toString();
		annotationType= ctx.getConfigParameterValue(PARAM_ANNOTATION_TYPE).toString();
		parameters.add("hitid");
		parameters.add("hittypeid");
		parameters.add("workerid");
		parameters.add("Answer.tokens");
		parameters.add("annotation");
		parameters.add("Answer.textinput");
		try {
            loadMTurkResults();
        }
        catch (IOException e) {
            throw new ResourceInitializationException(e);
        }
    }

	private void loadMTurkResults() throws IOException {
			BufferedReader br = new BufferedReader(new FileReader(sourceLocation));
			String[] headers = br.readLine().split("\t");
			String line;
			while((line = br.readLine())!= null){
				HashMap<String,String> innerMap = new HashMap<String,String>();
				String[] lineElements = line.split("\t");
				for(int i=0; i < lineElements.length; i++){
					if(parameters.contains(removeQuotation(headers[i]))){
						innerMap.put(removeQuotation(headers[i]), removeQuotation(lineElements[i]));
					}
				}
				String documentname = innerMap.get("annotation");
				documentname = documentname.split("-")[documentname.split("-").length-1].replace(".html", "");
				ArrayList<HashMap<String, String>> tmpList;
				if(mTurkMap.containsKey(documentname)){
					tmpList = mTurkMap.get(documentname);
				}
				else{
					tmpList = new ArrayList<HashMap<String, String>>();
				}
				tmpList.add(innerMap);
				mTurkMap.put(documentname, tmpList);
			}
			br.close();
	}

    @Override
    public void process(JCas aJCas)
        throws AnalysisEngineProcessException
    {
        String id = "";
        for (DocumentMetaData meta : JCasUtil.select(aJCas, DocumentMetaData.class)) {
            id = meta.getDocumentId();
        }
        if (mTurkMap.containsKey(id)) {
            System.out.println("FOUND ID " + id);
            ArrayList<HashMap<String, String>> mTurkResults = mTurkMap.get(id);
            // Do major claim annotations
            if (annotationType.equals("m")) {
                for (HashMap<String, String> entry : mTurkResults) {
                    int begin = -1;
                    int end = -1;
                    String typeValue = "";
                    ArrayList<String> tokens = new ArrayList<String>();
                    for (String token : entry.get("Answer.tokens").split(",")) {
                        tokens.add(token);
                    }

                    for (Token token : JCasUtil.select(aJCas, Token.class)) {
                        if (tokens.contains(token.getId())) {
                            if (token.getBegin() < begin || begin == -1) {
                                begin = token.getBegin();
                            }
                            if (token.getEnd() > end) {
                                end = token.getEnd();
                            }
                            if (typeValue == "") {
                                typeValue = token.getId().split("_")[0];
                            }
                            else if (!typeValue.equals(token.getId().split("_")[0])
                                    && !typeValue.contains("_")) {
                                typeValue += "_" + token.getId().split("_")[0];
                                end = token.getEnd();
                            }
                        }
                    }
                    if (begin != -1 && end != -1) {
                        MajorClaim claim = new MajorClaim(aJCas, begin, end);
                        claim.setProperties(entry.get("workerid"));
                        claim.setTypeValue(typeValue);
                        claim.addToIndexes();
                    }
                    else if (!entry.get("Answer.textinput").trim().isEmpty()) {
                        MajorClaim claim = new MajorClaim(aJCas, 0, 0);
                        claim.setProperties(entry.get("workerid"));
                        claim.setTypeValue(entry.get("Answer.textinput"));
                        claim.addToIndexes();
                    }
                }
            }
            // Do claim annotations
            if(annotationType.equals("c")){
                /*
                 * Claims are annotated in the following format:
                 * "[becauseid:1{token_0,undefined,token_3,undefined,token_7,},
                 * [becauseid:5{undefined,token_99,undefined,token_102,},
                 * [becauseid:2{undefined,token_565,undefined,token_568,},
                 * [butid:4{token_596,undefined,token_600,undefined,token_605,},],"
                 */
                for (HashMap<String, String> entry : mTurkResults) {
                    // First split for all claims and add them to a list
                    ArrayList<String> claims = new ArrayList<String>();
                    for(String claim : entry.get("Answer.tokens").split(",}")){
                        // Remove all the undefined strings (whitespaces) in the annotation
                        claims.add(claim.replace("undefined,", ""));
                    }
                    for(String claimString : claims){

                        int begin = -1;
                        int end = -1;
                        String typeValue = "";
                        String claimType = "";
                        // Set if we have a supporting or an attacking claim
                        if(claimString.contains("because")){
                            claimType = "support";
                        }else if(claimString.contains("but")){
                            claimType = "attack";
                        }
                        // Handle the closing bracket getting split too...
                        if(claimType.isEmpty()) {
                            continue;
                        }
                        // Handle comment annotation
                        if(!claimString.contains("]")){
                            ArrayList<String> tokens = new ArrayList<String>();
                            // The first part of the claim string contains its type (attacking or supporting)
                            for (String token : claimString.split("\\{")[1].split(",")) {
                                tokens.add(token);
                            }
                            for (Token token : JCasUtil.select(aJCas, Token.class)) {
                                if (tokens.contains(token.getId())) {
                                    if (token.getBegin() < begin || begin == -1) {
                                        begin = token.getBegin();
                                    }
                                    if (token.getEnd() > end) {
                                        end = token.getEnd();
                                    }
                                    if (typeValue == "") {
                                        typeValue = token.getId().split("_")[0];
                                    }
                                    else if (!typeValue.equals(token.getId().split("_")[0])
                                            && !typeValue.contains("_")) {
                                        typeValue += "_" + token.getId().split("_")[0];
                                        end = token.getEnd();
                                    }
                                }
                            }
                        }
                        if (begin != -1 && end != -1) {
                            Claim claim = new Claim(aJCas, begin, end);
                            claim.setProperties(entry.get("workerid"));
                            claim.setTypeValue(typeValue);
                            claim.setStance(claimType);
                            claim.addToIndexes();
                        }
                        else if (!entry.get("Answer.textinput").trim().isEmpty()) {
                            Claim claim = new Claim(aJCas, 0, 0);
                            claim.setProperties(entry.get("workerid"));
                            claim.setTypeValue(entry.get("Answer.textinput"));
                            claim.setStance(claimType);
                            claim.addToIndexes();
                        }
                    }
                }
            }
        }
    }

	private String removeQuotation(String element){
		if(element.contains("\"")){
			element = element.substring(1, element.length()-1);
		}
		return element;
	}

}
