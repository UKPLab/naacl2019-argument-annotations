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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.apache.commons.compress.utils.IOUtils;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.CASException;
import org.apache.uima.collection.CollectionException;
import org.apache.uima.jcas.JCas;
import de.tudarmstadt.ukp.naacl2019.argannotation.types.ProductReviewMetaData;
import de.tudarmstadt.ukp.naacl2019.argannotation.types.ReviewBody;
import de.tudarmstadt.ukp.naacl2019.argannotation.types.ReviewTitle;
import org.unbescape.html.HtmlEscape;

import com.google.gson.Gson;

import de.tudarmstadt.ukp.dkpro.core.api.io.ResourceCollectionReaderBase;
import de.tudarmstadt.ukp.dkpro.core.api.metadata.type.DocumentMetaData;
import de.tudarmstadt.ukp.dkpro.core.api.resources.CompressionUtils;

/**
 * UIMA reader for Amazon reviews in JSON format
 *
 */
public class AmazonReviewJSONReader
        extends ResourceCollectionReaderBase
{
    @Override
    public void getNext(CAS aCAS)
            throws IOException, CollectionException
    {
    	JCas jcas;
        try {
          jcas = aCAS.getJCas();
        } catch (CASException e) {
          throw new CollectionException(e);
        }
        Resource res = nextFile();

        InputStream is = CompressionUtils.getInputStream(res.getLocation(), res.getInputStream());
        BufferedReader br = new BufferedReader(new InputStreamReader(is));
        // load JSON to JCas text
        // NOTE: Assume we read files using the json format of: http://jmcauley.ucsd.edu/data/amazon/
        Gson gson = new Gson();
        String line = br.readLine();
    	// close
        IOUtils.closeQuietly(is);

        Review review = gson.fromJson(line, Review.class);
        String reviewTitle = HtmlEscape.unescapeHtml(review.getSummary());
        jcas.setDocumentText(reviewTitle + "\n\n" +  HtmlEscape.unescapeHtml(review.getReviewText()));

        // set all metadata
        try {
            DocumentMetaData metaData = DocumentMetaData.create(jcas);
            // an unique review id is not given in the used data. So we use asin_reviewerID_unixReviewTime
            String id = review.getProduct().getAsin()+"_"+review.getReviewerID()+"_"+review.getUnixReviewTime();
            metaData.setDocumentId(id);
            metaData.setDocumentTitle(reviewTitle);
            ProductReviewMetaData reviewMetaData = new ProductReviewMetaData(aCAS.getJCas());
            reviewMetaData.addToIndexes();
            reviewMetaData.setProductID(review.getProduct().getAsin());
            reviewMetaData.setReviewerID(review.getReviewerID());
            //reviewMetaData.setReviewTitle(reviewTitle);
            reviewMetaData.setProductName(HtmlEscape.unescapeHtml(review.getProduct().getTitle()));
            reviewMetaData.setStarRating(review.getOverall());
            reviewMetaData.setHelpfulness("["+review.getHelpful()[0].toString()+ " "
                                            + review.getHelpful()[1].toString()+"]");
            reviewMetaData.setTimestamp(review.getReviewTime());

            ReviewTitle reviewTitleAnnotation = new ReviewTitle(aCAS.getJCas());
            reviewTitleAnnotation.setBegin(0);
            reviewTitleAnnotation.setEnd(reviewTitle.length());
            reviewTitleAnnotation.addToIndexes();

            ReviewBody reviewBodyAnnotation = new ReviewBody(aCAS.getJCas());
            reviewBodyAnnotation.setBegin(reviewTitle.length()+1);
            reviewBodyAnnotation.setEnd(jcas.getDocumentText().length());
            reviewBodyAnnotation.addToIndexes();

        } catch (CASException e) {
            throw new CollectionException(e);
        }
    }

}
