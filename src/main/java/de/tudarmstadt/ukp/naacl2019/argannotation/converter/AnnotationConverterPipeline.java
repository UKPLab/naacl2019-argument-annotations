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

package de.tudarmstadt.ukp.naacl2019.argannotation.converter;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.Properties;

import org.apache.uima.UIMAException;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.fit.factory.JCasFactory;
import org.apache.uima.fit.factory.TypeSystemDescriptionFactory;
import org.apache.uima.fit.util.CasIOUtil;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.metadata.TypeSystemDescription;
import de.tudarmstadt.ukp.naacl2019.argannotation.types.ReviewBody;

import de.tudarmstadt.ukp.dkpro.argumentation.types.ArgumentUnitUtils;
import de.tudarmstadt.ukp.dkpro.argumentation.types.Claim;
import de.tudarmstadt.ukp.dkpro.argumentation.types.MajorClaim;
import de.tudarmstadt.ukp.dkpro.argumentation.types.Premise;
import de.tudarmstadt.ukp.dkpro.core.api.metadata.type.DocumentMetaData;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token;

public class AnnotationConverterPipeline extends JCasAnnotator_ImplBase{

	public static final String PARAM_ANNOTATION_PATH = "annotationPath";
    @ConfigurationParameter(name = PARAM_ANNOTATION_PATH,defaultValue = PARAM_ANNOTATION_PATH)
    private String annotationPath;

    @Override
    public void initialize(UimaContext ctx)
        throws ResourceInitializationException
    {
        super.initialize(ctx);
        annotationPath = ctx.getConfigParameterValue(PARAM_ANNOTATION_PATH).toString();
    }

	@Override
	public void process(JCas aJCas) throws AnalysisEngineProcessException {
		String fileName = JCasUtil.selectSingle(aJCas, DocumentMetaData.class).getDocumentId()+".xmi";
		int bodyBegin = JCasUtil.selectSingle(aJCas, ReviewBody.class).getBegin()+1;
		TypeSystemDescription typeSystem = TypeSystemDescriptionFactory.createTypeSystemDescriptionFromPath(annotationPath+"/TypeSystem.xml");
		try {
			JCas oldJCas = JCasFactory.createJCas(typeSystem);
			File xmiFile = new File(annotationPath+"/"+fileName);
			CasIOUtil.readJCas(oldJCas, xmiFile);

			//MajorClaim code
			for(MajorClaim component: JCasUtil.select(oldJCas, MajorClaim.class)){
				Properties properties = new Properties();
				properties.load(new StringReader(component.getProperties()));
				String tokens = convertTokenPositions(properties.getProperty("Covered_Token"),bodyBegin);

				int begin = getTokenPosition(tokens.split(",")[0]);
				int end = getTokenPosition(tokens.split(",")[tokens.split(",").length-1]);

				for(Token token: JCasUtil.select(aJCas,Token.class)){
					if(token.getBegin() == begin){
						begin = token.getBegin();
					}
					if(token.getBegin() == end){
						end = token.getEnd();
					}
				}
				MajorClaim newAnnotation = new MajorClaim(aJCas, begin, end);
                ArgumentUnitUtils.setProperty(newAnnotation, "Covered_Token", tokens);

                newAnnotation.addToIndexes();
			}

			//Claim code
			for(Claim component: JCasUtil.select(oldJCas, Claim.class)){
				Properties properties = new Properties();
				properties.load(new StringReader(component.getProperties()));
				String tokens = convertTokenPositions(properties.getProperty("Covered_Token"),bodyBegin);

				int begin = getTokenPosition(tokens.split(",")[0]);
				int end = getTokenPosition(tokens.split(",")[tokens.split(",").length-1]);

				for(Token token: JCasUtil.select(aJCas,Token.class)){
					if(token.getBegin() == begin){
						begin = token.getBegin();
					}
					if(token.getBegin() == end){
						end = token.getEnd();
					}
				}
				Claim newAnnotation = new Claim(aJCas, begin, end);
                ArgumentUnitUtils.setProperty(newAnnotation, "Covered_Token", tokens);
                newAnnotation.setStance(component.getStance());

                newAnnotation.addToIndexes();
			}

			//Premise Code
			for(Premise component: JCasUtil.select(oldJCas, Premise.class)){
				Properties properties = new Properties();
				properties.load(new StringReader(component.getProperties()));
				String tokens = convertTokenPositions(properties.getProperty("Covered_Token"),bodyBegin);

				int begin = getTokenPosition(tokens.split(",")[0]);
				int end = getTokenPosition(tokens.split(",")[tokens.split(",").length-1]);

				for(Token token: JCasUtil.select(aJCas,Token.class)){
					if(token.getBegin() == begin){
						begin = token.getBegin();
					}
					if(token.getBegin() == end){
						end = token.getEnd();
					}
				}
				Premise newAnnotation = new Premise(aJCas, begin, end);
                ArgumentUnitUtils.setProperty(newAnnotation, "Covered_Token", tokens);

                newAnnotation.addToIndexes();
			}
		} catch (UIMAException | IOException e) {
			throw new AnalysisEngineProcessException(e);
		}
	}

	/**
	 * Converts all tokenPositions into the new formats without title- and reviewTokens
	 *
	 * @param tokensString The String containing only(!) the tokens separated by commas
	 * @param bodyBegin The begin of the body part of the review
	 * @return
	 */
	private String convertTokenPositions(String tokensString, int bodyBegin) {
		String [] tokens =  tokensString.split(";");
		for(int i=0; i < tokens.length; i++){
			tokens[i] = convertTokenPosition(tokens[i], bodyBegin);
		}
		String result = String.join(",", tokens);
		return result;
	}

	/**
	 * Converts one token into the new token type. Also calculates the position in the new document.
	 *
	 * @param token
	 * @param bodyBegin
	 * @return
	 */
	private String convertTokenPosition(String token, int bodyBegin) {
		if(token.contains("_")){
			String convertedToken;

			Integer pos = getTokenPosition(token);
			if(token.startsWith("titleToken") || token.startsWith("token")){
				convertedToken = "token_"+pos;
			}
			else{
				convertedToken = "token_"+(pos + bodyBegin);
			}
			return convertedToken;
		}
        else {
            return token;
        }
	}

	/**
	 * Gets the position of a token
	 *
	 * @param token
	 * @return
	 */
	private int getTokenPosition(String token){
		Integer pos = Integer.parseInt(token.split("_")[1]);
		return pos;
	}

}
