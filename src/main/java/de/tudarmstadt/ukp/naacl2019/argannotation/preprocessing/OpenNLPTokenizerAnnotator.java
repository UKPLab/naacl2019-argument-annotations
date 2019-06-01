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

package de.tudarmstadt.ukp.naacl2019.argannotation.preprocessing;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;

import de.tudarmstadt.ukp.dkpro.core.api.metadata.type.DocumentMetaData;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token;
import opennlp.tools.tokenize.Tokenizer;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;

/**
 * A tokenizer using the tokenizer from Apache openNLP.
 * Please note that a path to the language model has to be specified
 * using PARAM_LANGUAGE_MODEL
 *
 */
public class OpenNLPTokenizerAnnotator
    extends JCasAnnotator_ImplBase
{
    public static final String ENGLISH_LANGUAGE_MODEL = "openNLPModels/en-token.bin";
    public static final String GERMAN_LANGUAGE_MODEL = "openNLPModels/de-token.bin";


    public static final String PARAM_LANGUAGE_MODEL = "language";
    @ConfigurationParameter(name = PARAM_LANGUAGE_MODEL,defaultValue = ENGLISH_LANGUAGE_MODEL)
    private String language;

    private TokenizerModel model;
    private Tokenizer tokenizer;
    private InputStream modelIn;

    @Override
    public void initialize(UimaContext ctx)
        throws ResourceInitializationException
    {
        super.initialize(ctx);
        language = ctx.getConfigParameterValue(PARAM_LANGUAGE_MODEL).toString();
        String workingPath = Paths.get("").toAbsolutePath().toString();
        // Initialize a tokenizer with the given languageModel e.g. "/path/to/file/en-token.bin"
        try {
            modelIn = new FileInputStream(workingPath+language);
            model = new TokenizerModel(modelIn);
        }
        catch (IOException e) {
            throw new ResourceInitializationException(e);
        }
        tokenizer = new TokenizerME(model);
    }

    @Override
    public void process(JCas aJCas)
        throws AnalysisEngineProcessException
    {
        String reviewText = aJCas.getDocumentText();
        DocumentMetaData metaData = DocumentMetaData.get(aJCas);
        tokenize(aJCas, reviewText, "token");

    }

    /**
     * Does the tokenizing using the opennlp tokenizer and some
     * regex patterns, which are explained below.
     *
     * @param aJCas
     *      the actual JCas
     * @param text
     *      the text we want to tokenize
     * @param tokentype
     *      the type of token we want our token id to have: tokentype_<startID>
     */
    private void tokenize(JCas aJCas, String text, String tokentype){
     // Generate tokens and tokenids
        String tokens[] = tokenizer.tokenize(text);
        int offset = 0;
        for (String reviewToken : tokens) {
            // We check, if we have basic punctuation inside the token. If so, we split
            // the token in smaller tokens and process them
            if (!checkValidTokenSplitting(reviewToken)) {
                // Split the string, but keep its delimiters!
                String[] tmpTokens = reviewToken.split("((?<=[,.:\\e{-}!?\\e{(}\\e{)}])|(?=[,.:\\e{-}!?\\e{(}\\e{)}]))");
                String[] checkTokens = reviewToken.split("(?!^)");  // In Java 8 we can just use String.split("").
                // Check if a token consists only of punctuations like in smilies. If so, we split them all
                // into single tokens.
                boolean onlyPunctuations = true;
                for(String checkTok : checkTokens){
                    if(checkTok.matches("\\p{Alnum}")) {
                        onlyPunctuations = false;
                    }
                }
                if(onlyPunctuations) {
                    tmpTokens = checkTokens;
                }
                for (String tmpReviewToken : tmpTokens) {
                    int start = text.indexOf(tmpReviewToken, offset);
                    int end = start + tmpReviewToken.length();
                    Token annotation = new Token(aJCas, start, end);
                    annotation.setId( tokentype + "_" + Integer.toString(start)); // Set token id to start index of the token
                    annotation.addToIndexes(aJCas);
                    offset = end;
                }
                continue;
            }
            int start = text.indexOf(reviewToken, offset);
            int end = start + reviewToken.length();
            Token annotation = new Token(aJCas, start, end);
            annotation.setId(tokentype+"_" + Integer.toString(start)); // Set token id to start index of the token
            annotation.addToIndexes(aJCas);
            offset = end;
        }
    }

    /**
     * Apache OpenNLP does not split punctuation, if it is
     * ahead of something else without a whitespace:
     * "hello ,world" -> "hello", ",world"
     * Therefore, we check the first char of our tokens if
     * any of them contain punctuation with having further characters
     *
     *  Patter explanations:
     *
     *  [,.!?]\\p{IsAlphabetic}+   - check for cases like .This ,..
     *  ..*[,.!?]..*       - check for cases like  before.That
     *  .[0-9]+[,.!?][0-9]+ - ignore version numbers like v0.1
     *  \\p{Punct}           - ignore single char punctuation
     *
     * @param token
     *      the token to check
     * @return
     *      true, if the splitting is valid
     *      false, if the splitting is invalid
     */
    private boolean checkValidTokenSplitting(String token){
        if((token.matches("[,.:\\e{-}!?\\e{(}\\e{)}]\\p{IsAlphabetic}+") || token.matches("..*[,.:\\e{-}!?\\e{(}\\e{)}]..*"))&& !(token.matches("\\p{Punct}") || token.matches(".[0-9]+[,.:\\e{-}][0-9]+"))){
            return false;
        }

        String[] checkTokens = token.split("(?!^)");  // In Java 8 we can just use String.split("").
        // Check if a token consists only of punctuations like in smilies. If so, we split them all
        // into single tokens.
        boolean onlyPunctuations = true;
        for(String checkTok : checkTokens){
            if(checkTok.matches("\\p{Alnum}")) {
                onlyPunctuations = false;
            }
        }
        if(onlyPunctuations) {
            return false;
        }
        return true;
    }
}
