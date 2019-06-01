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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.uima.UIMAException;
import org.apache.uima.collection.CollectionReader;
import org.apache.uima.fit.factory.CollectionReaderFactory;
import org.apache.uima.fit.factory.JCasFactory;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;

import de.tudarmstadt.ukp.dkpro.argumentation.types.Claim;
import de.tudarmstadt.ukp.dkpro.argumentation.types.MajorClaim;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token;
import de.tudarmstadt.ukp.dkpro.core.io.xmi.XmiReader;

/**
 * Parent class for creating HITs for crowd-sourcing
 *
 */
public abstract class AbstractHITProducer
{
    protected static final String TEMPLATE_DIRECTORY = "templates";
    protected static final String MTURK_SANDBOX_URL = "https://workersandbox.mturk.com/mturk/externalSubmit";
    protected static final String MTURK_ACTUAL_URL = "https://www.mturk.com/mturk/externalSubmit";

    /**
     * Use sandbox or real MTurk?
     */
    protected boolean useSandbox;
    protected String language;
    protected Mustache mustache;

    /**
     * Initializes the template system
     *
     * @throws IOException exception
     */
    public void initialize(boolean useSandbox, String lang)
            throws IOException
    {
        this.language = lang;
        this.useSandbox = useSandbox;

        InputStream stream = this.getClass().getClassLoader()
                .getResourceAsStream(getMustacheTemplateFileName());
        if (stream == null) {
            throw new FileNotFoundException("Resource not found: " + getMustacheTemplateFileName());
        }

        // compile template
        MustacheFactory mf = new DefaultMustacheFactory();
        Reader reader = new InputStreamReader(stream, "utf-8");
        mustache = mf.compile(reader, "template");
    }

    public void process(File inputDir, File outputDir)
            throws IOException, UIMAException
    {
        List<File> xmiFiles = new ArrayList<>(
                FileUtils.listFiles(inputDir, new String[] { "xmi" }, true));

        // read files one-by-one
        for (File file : xmiFiles) {
        	//Windows support for relative paths in xmis
            if(System.getProperty("os.name").contains("Windows")){
            	String path = FileUtils.readFileToString(file);
            	if(path.startsWith("..")){
            		file = new File(inputDir.getPath() + "/" + path);
            	}
            }
            JCas jcas = JCasFactory.createJCas();
            CollectionReader reader = CollectionReaderFactory.createReader(XmiReader.class,
                    XmiReader.PARAM_SOURCE_LOCATION, file);

            // "fill" the jCas container
            reader.getNext(jcas.getCas());

            createHITHTMLFromJCas(jcas, outputDir);
        }
    }

    /**
     * Takes a single jCas and creates HIT (or multiple HITs) in HTML format in the output folder
     *
     * @param jcas      jCas with annotations from the previous step
     * @param outputDir output directory
     * @throws IOException I/O exception
     */
    protected abstract void createHITHTMLFromJCas(JCas jcas, File outputDir)
            throws IOException;

    /**
     * Returns the name of Mustache template located under src/main/resources
     *
     * @return name
     */
    public abstract String getMustacheTemplateFileName();

    protected List<String> createRawList(Annotation annotation){
    	return createRawList(annotation, null, null, null);
    }

    protected List<String> createRawList(Annotation annotation, MajorClaim majorClaim){
    	return createRawList(annotation, majorClaim, null, null);
    }

    protected List<String> createRawList(Annotation annotation, MajorClaim majorClaim, Claim primaryClaim, List<Claim> claims) {
    	List<String> rawList = new ArrayList<String>();
        int previousTokenEndOffset = 0;
        boolean inMajorClaim = false;
        boolean inPrimaryClaim = false;
        boolean[] inClaim = null;
        List<Token> majorClaimTokens = new ArrayList<Token>();
        List<Token> primaryClaimTokens = new ArrayList<Token>();
        List<List<Token>> claimTokens = new ArrayList<List<Token>>();
        //Claim and MajorClaim handling
        if(majorClaim != null){
        	majorClaimTokens = JCasUtil.selectCovered(Token.class, majorClaim);
        }
        if(primaryClaim != null){
        	primaryClaimTokens = JCasUtil.selectCovered(Token.class, primaryClaim);
        }
        if(claims != null){
        	claimTokens = new ArrayList<>();
        	for(Claim claim: claims){
            	claimTokens.add(JCasUtil.selectCovered(Token.class, claim));
            }
        	inClaim = new boolean[claimTokens.size()];
        }

		for(Token token: JCasUtil.selectCovered(Token.class, annotation)){
			if (token.getBegin() > previousTokenEndOffset) {
				rawList.add("<span> </span>");
            }
            previousTokenEndOffset = token.getEnd();
            String tokenText = token.getCoveredText();
            String tokenClass = Pattern.matches("\\p{Punct}", tokenText) ? "punct" : "token";

            // First close any (major) claim spans that are open
            if (inMajorClaim == true && !majorClaimTokens.contains(token)) {
                rawList.add("</span>");
                inMajorClaim = false;
            }

            if (inPrimaryClaim == true && !primaryClaimTokens.contains(token)) {
                rawList.add("</span>");
                inPrimaryClaim = false;
            }

            for(int i=0; i < claimTokens.size(); i++){
                if (inClaim[i] == true && !claimTokens.get(i).contains(token)) {
                    rawList.add("</span>");
                    inClaim[i] = false;
                }
            }

            // Now open any new (major) claim spans
            if (inMajorClaim == false && majorClaimTokens.contains(token)) {
            	rawList.add("<span class=\"major noselect\">");
                inMajorClaim = true;
            }

            if (inPrimaryClaim == false && primaryClaimTokens.contains(token)) {
            	rawList.add("<span class=\""+primaryClaim.getStance()+" noselect\">");
            	inPrimaryClaim = true;
            }

            for(int i=0; i < claimTokens.size(); i++){
                if (inClaim[i] == false && claimTokens.get(i).contains(token)) {
                    rawList.add("<span class=\"major noselect\">");
                    inClaim[i] = true;
                }
            }

            rawList.add("<span class=" + tokenClass + " id=" + token.getId() + ">"
                    + tokenText + "</span>");
		}

		if (inMajorClaim) {
			rawList.add("</span>");
        }
		for(int i=0; i < claimTokens.size(); i++){
        	if (inClaim[i]) {
        		rawList.add("</span>");
            }
        }

		return rawList;
	}
}
