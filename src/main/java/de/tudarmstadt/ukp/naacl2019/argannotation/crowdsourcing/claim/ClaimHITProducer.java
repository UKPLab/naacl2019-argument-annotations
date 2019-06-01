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

package de.tudarmstadt.ukp.naacl2019.argannotation.crowdsourcing.claim;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.apache.uima.UIMAException;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import de.tudarmstadt.ukp.naacl2019.argannotation.types.ProductReviewMetaData;
import de.tudarmstadt.ukp.naacl2019.argannotation.types.ReviewBody;
import de.tudarmstadt.ukp.naacl2019.argannotation.types.ReviewTitle;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import de.tudarmstadt.ukp.dkpro.argumentation.types.MajorClaim;
import de.tudarmstadt.ukp.dkpro.core.api.metadata.type.DocumentMetaData;
import de.tudarmstadt.ukp.naacl2019.argannotation.crowdsourcing.AbstractHITProducer;

/**
 * Creates HITs for Claim annotations
 */
public class ClaimHITProducer
    extends AbstractHITProducer
{
    @Override
    protected void createHITHTMLFromJCas(JCas aJCas, File outputDir)
        throws IOException
    {
        MajorClaim majorClaim;
        try {
            majorClaim = JCasUtil.selectSingle(aJCas, MajorClaim.class);
        }
        catch (IllegalArgumentException e) {
            // If there is no major claim in the XMI then do nothing
            return;
        }

        // Wrap the tokens in appropriately classed <span> tags
        ProductReviewMetaData reviewMetaData = JCasUtil.selectSingle(aJCas,
                ProductReviewMetaData.class);
        List<String> rawTextList = createRawList(JCasUtil.selectSingle(aJCas, ReviewBody.class), majorClaim);
        List<String> rawTitleList = createRawList(JCasUtil.selectSingle(aJCas, ReviewTitle.class), majorClaim);

        // Prepare the HIT
        ClaimHITContainer hitContainer = new ClaimHITContainer();
        if (this.useSandbox) {
            hitContainer.mturkURL = MTURK_SANDBOX_URL;
        }
        else {
            hitContainer.mturkURL = MTURK_ACTUAL_URL;
        }
        String reviewID = DocumentMetaData.get(aJCas).getDocumentId();
        hitContainer.reviewID = reviewID;
        hitContainer.reviewTime = reviewMetaData.getTimestamp();
        hitContainer.reviewerID = reviewMetaData.getReviewerID();
        hitContainer.reviewStarRating = reviewMetaData.getStarRating();
        hitContainer.productName = reviewMetaData.getProductName();
        hitContainer.productASIN = reviewMetaData.getProductID();
        hitContainer.language = language;
        hitContainer.rawHTMLTokens = rawTextList;
        hitContainer.reviewTitle = rawTitleList;
        // The following doesn't work when the major claim is partly or entirely in the title,
        // but we don't care as it's not (currently) used at the moment.
        hitContainer.majorClaim = majorClaim.getCoveredText();

        // Render the template
        File outputHITFile = new File(outputDir, "hit-claim-review-" + reviewID + ".html");
        System.out.println("Rendering " + outputHITFile);
        PrintWriter pw = new PrintWriter(outputHITFile);
        this.mustache.execute(pw, hitContainer);
        IOUtils.closeQuietly(pw);
    }

	@Override
    public String getMustacheTemplateFileName()
    {
        return TEMPLATE_DIRECTORY + "/mturk-template-claim-" + language + ".html";
    }


    @Option(name="-o",aliases = { "--output" },metaVar="dir",usage="output folder", required=true)
	private File outputDir;

	@Option(name="-i",aliases = { "--input" },metaVar="dir",usage="input folder", required=true)
	private File inputDir;

	@Option(name="-l",aliases = { "--language" },metaVar="string",usage="language of the processed review files", required=true)
	private String language;

	@Option(name="-s",aliases = { "--sandbox" },usage="use MTurk sandbox")
    private boolean sandbox = false;

    public static void main(String[] args)
        throws IOException, UIMAException
    {
    	new ClaimHITProducer().doMain(args);
    }

    private void doMain(String[] args) throws IOException, UIMAException {
		CmdLineParser parser = new CmdLineParser(this);
		try {
            // parse the arguments.
            parser.parseArgument(args);
            this.initialize(sandbox, language);
            this.process(inputDir, outputDir);
        } catch( CmdLineException e ) {
            System.err.println(e.getMessage());
            System.err.println("java "+this.getClass().getSimpleName()+" [options...] arguments...");
            parser.printUsage(System.err);
            System.err.println();
            return;
        }
	}
}
