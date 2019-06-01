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

package de.tudarmstadt.ukp.naacl2019.argannotation.crowdsourcing.goldestimator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.fit.component.JCasAnnotator_ImplBase;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.StringArray;
import org.apache.uima.resource.ResourceInitializationException;
import org.dkpro.statistics.agreement.unitizing.KrippendorffAlphaUnitizingAgreement;
import org.dkpro.statistics.agreement.unitizing.UnitizingAnnotationStudy;
import de.tudarmstadt.ukp.naacl2019.argannotation.types.GoldEstimation;

import de.tudarmstadt.ukp.dkpro.argumentation.types.Claim;
import de.tudarmstadt.ukp.dkpro.argumentation.types.MajorClaim;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token;

/**
 * Calculates inter annotator agreement (Krippendorff's alpha u), and merges the same annotations
 * into one gold estimation annotation containing all the worker ids.
 *
 *
 */
public class GoldStandardCalculator
    extends JCasAnnotator_ImplBase
{

    // Threshold params
    public static final String PARAM_THRESHOLD_BINARY = "0.5";
    @ConfigurationParameter(name = PARAM_THRESHOLD_BINARY, mandatory = false)
    private String thresholdBinary;

    public static final String PARAM_THRESHOLD_ALPHA = "0.6";
    @ConfigurationParameter(name = PARAM_THRESHOLD_ALPHA, mandatory = false)
    private String thresholdAlpha;

    public static final String PARAM_ANNOTATION_TYPE = "m";
    @ConfigurationParameter(name = PARAM_ANNOTATION_TYPE, mandatory = true)
    private String annotationType;

    /**
     * Check, if we are supposed to do annotations for major claims, claims, or premises and set the
     * appropriate flag to call the correct method during processing.
     */
    @Override
    public void initialize(UimaContext ctx)
        throws ResourceInitializationException
    {
        super.initialize(ctx);
        thresholdAlpha = ctx.getConfigParameterValue(PARAM_THRESHOLD_ALPHA).toString();
        thresholdBinary = ctx.getConfigParameterValue(PARAM_THRESHOLD_BINARY).toString();
        annotationType = ctx.getConfigParameterValue(PARAM_ANNOTATION_TYPE).toString();

    }

    @Override
    public void process(JCas aJCas)
        throws AnalysisEngineProcessException
    {
        // TODO: Check if we are annotation a major claim, a claim, or a premise
        /*
         * We do annotation in three steps: Major Claim Annotation Claim Annotation Premise
         * Annotation So we check reversed, which is the latest annotation contained in the JCas and
         * use that for selecting the right annotation method.
         */

        if(annotationType.equals("m")){
            if (!JCasUtil.select(aJCas, MajorClaim.class).isEmpty()) {
                majorClaimStatistics(aJCas);
            }
        }
        if(annotationType.equals("c")){
            if (!JCasUtil.select(aJCas, Claim.class).isEmpty()) {
                claimStatistics(aJCas);
            }
        }

    }

    /**
     * Calculates several statistics of the annotated review and adds them to the cas.
     */
    private void majorClaimStatistics(JCas aJCas)
    {
        Double alphaUAgreement = this.calculateKrippendorffAlphaUTokensMC(aJCas);
        Double binaryAgreement = this.calculateBinaryAgreementMC(aJCas);
        Collection<MajorClaim> majorClaims = JCasUtil.select(aJCas, MajorClaim.class);
        // Our annotation pair consists of:
        // < majorClaimText : < startTokenID, endTokenID, workerID_1, ..., workerID_n>>
        List<AnnotationPair<String, List<String>>> annotations = this.calcuateExactMatches(aJCas);
        // Look what the annotation with the most comments is:
        AnnotationPair<String, List<String>> bestPair = new AnnotationPair<String, List<String>>(
                "dummy", new ArrayList<String>());
        boolean drawIndicator = false;
        for (AnnotationPair<String, List<String>> annotation : annotations) {
            if (bestPair.getValue().size() == annotation.getValue().size()) {
                drawIndicator = true;
            }
            if (bestPair.getValue().size() < annotation.getValue().size()) {
                bestPair = annotation;
                drawIndicator = false;
            }
        }
        for (AnnotationPair<String, List<String>> annotation : annotations) {
            GoldEstimation ge = new GoldEstimation(aJCas);
            ge.setAnnotationText(annotation.getKey());
            // The workerID string array contains 2 elements (startTokenID, endTokenID) less,
            // but we have to keep them in mind when indexing from the value array.
            StringArray workerIDs = new StringArray(aJCas, annotation.getValue().size() - 2);
            for (int i = 0; i < annotation.getValue().size() - 2; i++) {
                workerIDs.set(i, annotation.getValue().get(i + 2));
            }
            ge.setStartTokenID(annotation.getValue().get(0));
            ge.setEndTokenID(annotation.getValue().get(1));
            ge.setAnnotatorID(workerIDs);
            ge.setAlphaUAgreement(alphaUAgreement);
            ge.setAnnotationType("major_claim");
            ge.setBinaryAgreement(binaryAgreement);
            ge.setEstimatedGoldAnnotation(false);
            // Only set a major claim to the gold standard, if we have no draw vote
            // and the thresholds are fulfilled!
            if (annotation.equals(bestPair) && !drawIndicator
                    && binaryAgreement >= Double.parseDouble(thresholdBinary)
                    && (alphaUAgreement >= Double.parseDouble(thresholdAlpha) || bestPair.getValue().size() >= 3)) {
                ge.setEstimatedGoldAnnotation(true);
            }

            ge.setIsCommentAnnotation(false);
            if (annotation.getValue().get(0).equals("-1")
                    || annotation.getValue().get(1).equals("-1")) {
                ge.setIsCommentAnnotation(true);
            }
            ge.setNumberOfAnnotators(majorClaims.size());
            ge.addToIndexes(aJCas);
        }
        // We remove all major claim annotations from this xmi after processing them, since
        // they are now stored in the gold estimations. The annotation type major claim
        // is used for the gold estimations later on.
        for (MajorClaim mj : majorClaims) {
            mj.removeFromIndexes(aJCas);
        }
    }

    /**
     * Calculates statistics for the claim annotations.
     * Note, that one worker can assign several claims in the review text.
     * @param aJCas
     */
    private void claimStatistics(JCas aJCas)
    {
        Collection<Claim> claims = JCasUtil.select(aJCas, Claim.class);
        ArrayList<String> workerIDs = new ArrayList<String>();
        List<GoldEstimation> geList = new ArrayList<GoldEstimation>();
        for(Claim claim : claims){
            String workerID = claim.getProperties();
            if(workerIDs.contains(workerID)) {
                continue;
            }
            workerIDs.add(workerID);
        }
        // Create a list of annotation - workerids as follows:
        // < claimText : < startTokenID, endTokenID, tokenType, workerID_1, ..., workerID_n>>
        List<AnnotationPair<String, List<String>>> annotations = this.calcuateExactMatches(aJCas);
        // Create a list containing lists of overlapping annotations for binary and alpha_U calculation.
        // Since annotators are only able to mark one section by default, it is ensured that there are
        // no overlapping annotations coming from the same annotator. But note, that there might be
        // 2 overlapping annotations by a different annotator.
        List<List<AnnotationPair<String,List<String>>>> overlapAnnotations = new ArrayList<List<AnnotationPair<String,List<String>>>>();
        for(AnnotationPair<String, List<String>> pair : annotations){
            String tokenType = pair.getValue().get(2);
            // Ignore comment annotations
            if(tokenType.equals("token")){
                int begin = Integer.parseInt(pair.getValue().get(0).split("_")[1]);
                int end = Integer.parseInt(pair.getValue().get(1).split("_")[1]);
                List<AnnotationPair<String,List<String>>> overlapGrouping = new ArrayList<AnnotationPair<String,List<String>>>();
                overlapGrouping.add(pair);
                for(AnnotationPair<String, List<String>> innerPair : annotations){
                    // Ignore comment annotations
                    String innerTokenType = innerPair.getValue().get(2);
                    if(!innerPair.equals(pair) && (innerTokenType.equals("token"))){
                        int innerBegin = Integer.parseInt(innerPair.getValue().get(0).split("_")[1]);
                        int innerEnd = Integer.parseInt(innerPair.getValue().get(1).split("_")[1]);
                        // We have 4 cases (exact matches are already handled):
                        //  Either the claim annotation overlaps at the front or at the end
                        //  The annotations is fully contained in or contains fully the current one.
                        // It is easier to check if the annotation is not (completely before or after) the current one.
                        if (!(innerEnd < begin || innerBegin > end)) {
                            overlapGrouping.add(innerPair);
                        }
                    }
                }
                // Handle duplicates with different order
                if(!containsNoOrder(overlapAnnotations,overlapGrouping)){
                    overlapAnnotations.add(overlapGrouping);
                }
            }else{
                // Add comment annotation to the gold estimations
                Double commentBinArg = 0.2;
                GoldEstimation ge = new GoldEstimation(aJCas);
                ge.setAnnotationText(pair.getKey());
                // The workerID string array contains 2 elements (startTokenID, endTokenID) less,
                // but we have to keep them in mind when indexing from the value array.
                StringArray claimWorkerIDs = new StringArray(aJCas, pair.getValue().size() - 4);
                if(claimWorkerIDs.size() > 1){
                    for (int i = 0; i < pair.getValue().size() - 4; i++) {
                        claimWorkerIDs.set(i, pair.getValue().get(i + 4));
                    }
                    commentBinArg = (claimWorkerIDs.size())/5.0;
                }else{
                    claimWorkerIDs.set(0, pair.getValue().get(4));
                }
                ge.setStartTokenID(pair.getValue().get(0));
                ge.setEndTokenID(pair.getValue().get(1));
                ge.setAnnotatorID(claimWorkerIDs);
                String claimType = pair.getValue().get(3) + "_claim";
                ge.setAnnotationType(claimType);
                ge.setBinaryAgreement(commentBinArg);
                ge.setIsCommentAnnotation(true);
                ge.setEstimatedGoldAnnotation(false);
                ge.setNumberOfAnnotators(workerIDs.size());
                if(!geList.contains(ge)){
                    geList.add(ge);
                }
            }
        }

        // Now iterate through the list of overlapping claim annotations and check if the required
        // thresholds are ok to add the annotation as an actual claim annotations in the jcas
        List<AnnotationPair<String,List<String>>> goldPairs = new ArrayList<AnnotationPair<String,List<String>>>();
        List<AnnotationPair<String,List<String>>> alreadyAnnotated = new ArrayList<AnnotationPair<String,List<String>>>();

        for (List<AnnotationPair<String, List<String>>> checkThreshold : overlapAnnotations) {

            Double binAgree = calculateBinaryAgreementClaims(aJCas, checkThreshold,
                    workerIDs.size());
            Double alphaAgree = calculateAlphaUAgreementTokenClaims(aJCas, checkThreshold);

            // Check for cases where 3 or more annotators annotated an exact matching claim:
            for (AnnotationPair<String, List<String>> checkThresholdPair : checkThreshold) {
                if (checkThresholdPair.getValue().size() - 4 >= 3) {
                    if (!goldPairs.contains(checkThresholdPair)) {
                        goldPairs.add(checkThresholdPair);
                    }
                    else {
                        continue;
                    }
                     if(!alreadyAnnotated.contains(checkThresholdPair)){
                     alreadyAnnotated.add(checkThresholdPair);
                     }else{
                     continue;
                     }
                    // Add to gold standard
                    GoldEstimation ge = new GoldEstimation(aJCas);
                    ge.setAnnotationText(checkThresholdPair.getKey());
                    StringArray claimWorkerIDs = new StringArray(aJCas, checkThresholdPair
                            .getValue().size() - 4);
                    for (int i = 0; i < checkThresholdPair.getValue().size() - 4; i++) {
                        claimWorkerIDs.set(i, checkThresholdPair.getValue().get(i + 4));
                    }
                    ge.setStartTokenID(checkThresholdPair.getValue().get(0));
                    ge.setEndTokenID(checkThresholdPair.getValue().get(1));
                    ge.setAnnotatorID(claimWorkerIDs);
                    ge.setAlphaUAgreement(alphaAgree);
                    String claimType = checkThresholdPair.getValue().get(3) + "_claim";
                    ge.setAnnotationType(claimType);
                    ge.setBinaryAgreement(binAgree);
                    ge.setIsCommentAnnotation(false);
                    ge.setEstimatedGoldAnnotation(true);
                    ge.setNumberOfAnnotators(workerIDs.size());
                    if (!geList.contains(ge)) {
                        geList.add(ge);
                    }
                    break;
                }
            }
            // Now do gold estimations for the other annotations:
            if (binAgree >= Double.parseDouble(thresholdBinary)
                    && alphaAgree >= Double.parseDouble(thresholdAlpha)) {
                // Calculations for estimations when there are less than 3 exact matches
                int tieBreaker = 0;
                int maxVotes = -1;
                AnnotationPair<String, List<String>> bestAnnotation = null;
                for (AnnotationPair<String, List<String>> possibleClaimAnnotation : checkThreshold) {
                    int voters = possibleClaimAnnotation.getValue().size() - 4;
                    if (maxVotes < voters) {
                        maxVotes = voters;
                        tieBreaker++;
                        bestAnnotation = new AnnotationPair<String, List<String>>(
                                possibleClaimAnnotation.getKey(),
                                possibleClaimAnnotation.getValue());
                    }
                    if (maxVotes == voters && bestAnnotation != null
                            && !bestAnnotation.equals(possibleClaimAnnotation)) {
                        tieBreaker--;
                    }
                }
                for (AnnotationPair<String, List<String>> possibleClaimAnnotation : checkThreshold) {
                    // Ignore already contained gold standards
                    if (goldPairs.contains(possibleClaimAnnotation)) {
                        continue;
                    }

                    // Add to gold standard
                    GoldEstimation ge = new GoldEstimation(aJCas);
                    ge.setAnnotationText(possibleClaimAnnotation.getKey());
                    StringArray claimWorkerIDs = new StringArray(aJCas, possibleClaimAnnotation
                            .getValue().size() - 4);
                    for (int i = 0; i < possibleClaimAnnotation.getValue().size() - 4; i++) {
                        claimWorkerIDs.set(i, possibleClaimAnnotation.getValue().get(i + 4));
                    }
                    ge.setStartTokenID(possibleClaimAnnotation.getValue().get(0));
                    ge.setEndTokenID(possibleClaimAnnotation.getValue().get(1));
                    ge.setAnnotatorID(claimWorkerIDs);
                    ge.setAlphaUAgreement(alphaAgree);
                    String claimType = possibleClaimAnnotation.getValue().get(3) + "_claim";
                    ge.setAnnotationType(claimType);
                    ge.setBinaryAgreement(binAgree);
                    ge.setIsCommentAnnotation(false);
                    ge.setNumberOfAnnotators(workerIDs.size());
                    if (tieBreaker == 0 && claimWorkerIDs.size() == maxVotes) {
                        goldPairs.add(possibleClaimAnnotation);
                        // In case a gold estimation was already added using a wrong gold standard
                        // label, set it to the correct one.
                        for (GoldEstimation geTmp : geList) {
                            GoldEstimation newGe = geTmp;
                            if (geTmp.getAnnotationText().equals(possibleClaimAnnotation.getKey())
                                    && !geTmp.getEstimatedGoldAnnotation()) {
                                geList.remove(geTmp);
                                newGe.setEstimatedGoldAnnotation(true);
                                geList.add(newGe);
                                break;
                            }
                        }
                        ge.setEstimatedGoldAnnotation(true);
                    }
                    else {
                        if(alreadyAnnotated.contains(possibleClaimAnnotation)){
                            continue;
                        }
                        ge.setEstimatedGoldAnnotation(false);
                    }
                    if (!geList.contains(ge)) {
                        if(alreadyAnnotated.contains(possibleClaimAnnotation)){
                            continue;
                        }
                        geList.add(ge);
                    }
                    if(!alreadyAnnotated.contains(possibleClaimAnnotation)){
                        alreadyAnnotated.add(possibleClaimAnnotation);
                    }
                }

            }
            else {
                // Just add the claim to the gold estimation
                for (AnnotationPair<String, List<String>> noClaimAnnotation : checkThreshold) {
                    // Ignore already contained gold standards
                    if (goldPairs.contains(noClaimAnnotation)) {
                        continue;
                    }
                    if(!alreadyAnnotated.contains(noClaimAnnotation)){
                    alreadyAnnotated.add(noClaimAnnotation);
                    }else{
                    continue;
                    }
                    GoldEstimation ge = new GoldEstimation(aJCas);
                    ge.setAnnotationText(noClaimAnnotation.getKey());
                    StringArray claimWorkerIDs = new StringArray(aJCas, noClaimAnnotation
                            .getValue().size() - 4);
                    for (int i = 0; i < noClaimAnnotation.getValue().size() - 4; i++) {
                        claimWorkerIDs.set(i, noClaimAnnotation.getValue().get(i + 4));
                    }
                    ge.setStartTokenID(noClaimAnnotation.getValue().get(0));
                    ge.setEndTokenID(noClaimAnnotation.getValue().get(1));
                    ge.setAnnotatorID(claimWorkerIDs);
                    ge.setAlphaUAgreement(alphaAgree);
                    String claimType = noClaimAnnotation.getValue().get(3) + "_claim";
                    ge.setAnnotationType(claimType);
                    ge.setBinaryAgreement(binAgree);
                    ge.setIsCommentAnnotation(false);
                    ge.setEstimatedGoldAnnotation(false);
                    ge.setNumberOfAnnotators(workerIDs.size());
                    if (!geList.contains(ge)) {
                        geList.add(ge);
                    }
                }
            }
        }
        // Add gold estimations
        for(GoldEstimation goldData: new HashSet<GoldEstimation>(geList)){
                goldData.addToIndexes(aJCas);

        }
        for(Claim claim : claims){
            claim.removeFromIndexes(aJCas);
        }

    }

    private void premiseStatistics(JCas aJCas)
    {
        // TODO: Implement this!

    }

    /**
     * Returns a list of all annotations and the ids of the worker who marked it. This is used for
     * adding additional data to the gold estimation annotation, but not for calculating the alpha_u
     * value!
     *
     * @return A list of annotation pairs. An annotation pair consists of the marked annotation text
     *         and a list of the workerIDs of those who marked it.
     */
    private List<AnnotationPair<String, List<String>>> calcuateExactMatches(JCas aJCas)
    {
        List<AnnotationPair<String, List<String>>> majorityEstimations = new ArrayList<AnnotationPair<String, List<String>>>();
        if(this.annotationType.equals("m")){
            Collection<MajorClaim> majorClaims = JCasUtil.select(aJCas, MajorClaim.class);
            // Add AnnotationPairs<reviewAnnotation,<workerID>>
            for (MajorClaim majorClaim : majorClaims) {
                String reviewAnnotation = majorClaim.getCoveredText();
                boolean alreadyInIt = false;
                for (AnnotationPair<String, List<String>> pair : majorityEstimations) {
                    // If the annotation is already in the list, just add the workerID
                    if (pair.getKey().equals(reviewAnnotation)) {
                        alreadyInIt = true;
                        List<String> newContent = pair.getValue();
                        newContent.add(majorClaim.getProperties());
                        pair.setValue(newContent);
                    }
                }
                if (!alreadyInIt) {
                    List<String> entry = new ArrayList<String>();
                    // Add the startTokenID and endTokenID first:
                    entry.add(this.getStartTokenID(aJCas, majorClaim.getBegin(),majorClaim.getTypeValue()));
                    entry.add(this.getEndTokenID(aJCas, majorClaim.getEnd(),majorClaim.getTypeValue()));
                    entry.add(majorClaim.getProperties());
                    majorityEstimations.add(new AnnotationPair<String, List<String>>(reviewAnnotation,
                            entry));
                }
            }
        }
        if(this.annotationType.equals("c")){
            List<AnnotationPair<String, List<String>>> majorityEstimationsTmp = new ArrayList<AnnotationPair<String, List<String>>>();
            Collection<Claim> claims = JCasUtil.select(aJCas, Claim.class);
            // Add AnnotationPairs<reviewAnnotation,<workerID>>
            for (Claim claim : claims) {
                String reviewAnnotation = claim.getCoveredText();
                boolean alreadyInIt = false;
                String stance = claim.getStance();
                for (AnnotationPair<String, List<String>> pair : majorityEstimationsTmp) {
                    // If the annotation is already in the list, just add the workerID
                    if (pair.getKey().equals(reviewAnnotation)) {
                        alreadyInIt = true;
                        List<String> newContent = pair.getValue();
                        // Add the current stance to the stance list
                        newContent.set(3, newContent.get(3) + stance + ",");
                        newContent.add(claim.getProperties());
                        pair.setValue(newContent);
                    }
                }
                if (!alreadyInIt) {
                    List<String> entry = new ArrayList<String>();
                    // Add the startTokenID and endTokenID first:
                    entry.add(this.getStartTokenID(aJCas, claim.getBegin(),claim.getTypeValue()));
                    entry.add(this.getEndTokenID(aJCas, claim.getEnd(),claim.getTypeValue()));
                    entry.add(claim.getTypeValue());
                    entry.add(stance + ",");
                    entry.add(claim.getProperties());
                    majorityEstimationsTmp.add(new AnnotationPair<String, List<String>>(reviewAnnotation,
                            entry));
                }

            }
            // Now set the correct stance for each annotation pair:
            for(AnnotationPair<String, List<String>> forStance : majorityEstimationsTmp){
                String[] stanceArray = forStance.getValue().get(3).split(",");
                int stanceCheck = 0;
                for(String tmpStance : stanceArray){
                    if(tmpStance.equals("attack")){
                        stanceCheck ++;
                    } else if(tmpStance.equals("support")){
                        stanceCheck --;
                    }
                }
                List<String> newValue = forStance.getValue();
                if(stanceCheck > 0){
                    newValue.set(3, "attack");
                } else if(stanceCheck < 0){
                    newValue.set(3, "support");
                } else {
                    newValue.set(3, "tied");
                }
                majorityEstimations.add(new AnnotationPair<String, List<String>>(forStance.getKey(),
                        newValue));
            }

        }

        return majorityEstimations;
    }

    /**
     * Calculates the krippendorff's alpha u for inter annotator agreement and adds it to the
     * current jcas. The smallest possible unit here is a token, since the annotators can only mark
     * one token.
     */
    private Double calculateKrippendorffAlphaUTokensMC(JCas aJCas)
    {
        Collection<MajorClaim> majorClaims = JCasUtil.select(aJCas, MajorClaim.class);
        // Ignore comment annotations:
        List<MajorClaim> filteredMajorClaims = new ArrayList<MajorClaim>();
        for (MajorClaim mj : majorClaims) {
            String tokenType = mj.getTypeValue();
            if (tokenType.equals("token")) {
                filteredMajorClaims.add(mj);
            }
        }
        int numberOfAnnotators = filteredMajorClaims.size();
        // Since we can annotate the review title as well, the document length is the review text
        // length + review title length
        Collection<Token> tokens = JCasUtil.select(aJCas, Token.class);
        UnitizingAnnotationStudy study = new UnitizingAnnotationStudy(numberOfAnnotators,
                tokens.size());
        String[] raterIDs = new String[numberOfAnnotators]; // keep the rater index - rater id
                                                            // mapping
        int index = 0;
        List<Token> sortedTokenList = this.getSortedTokenList(aJCas);
        for (MajorClaim majorClaim : filteredMajorClaims) {
            List<Token> annotations = this.getCoveredTokens(aJCas, majorClaim.getBegin(),majorClaim.getEnd(),majorClaim.getTypeValue());
            study.addUnit(sortedTokenList.indexOf(this.getBeginToken(aJCas, majorClaim.getBegin())),
                    annotations.size(), index, "X");
            raterIDs[index] = majorClaim.getProperties();
            index++;
        }

        KrippendorffAlphaUnitizingAgreement alpha = new KrippendorffAlphaUnitizingAgreement(study);
        return alpha.calculateAgreement();
    }

    /**
     * Calculate the percentage agreement on the task: Is there an annotation in the given text?
     *
     * @return the percentage who many annotators were able to find a major claim in the text
     */
    private Double calculateBinaryAgreementMC(JCas aJCas)
    {
        Collection<MajorClaim> majorClaims = JCasUtil.select(aJCas, MajorClaim.class);
        int numberOfAnnotators = majorClaims.size();
        int numberOfFoundMJ = majorClaims.size();
        for (MajorClaim majorClaim : majorClaims) {
            String tokenType = majorClaim.getTypeValue();
            if (!(tokenType.equals("token"))) {
                numberOfFoundMJ--;
            }
        }
        Double rate = (double) (numberOfFoundMJ) / (double) (numberOfAnnotators);
        return rate;
    }

    /**
     * Calculates how many annotators think there should be some kind of
     * annotation at this point in the review and returns the ratio.
     *
     * @param aJCas
     *      the current jCas
     * @param annotations
     *      the annotations with overlap we want to evaluate
     * @param numberOfWorkers
     *      the overall number of annotators for this review
     * @return
     */
    private Double calculateBinaryAgreementClaims(JCas aJCas, List<AnnotationPair<String,List<String>>> annotations, int numberOfWorkers){
        int numberOfAnnotators = numberOfWorkers;
        List<String> annotatedWorkers = new ArrayList<String>();
        for (AnnotationPair<String,List<String>> pair : annotations) {
            List<String> claimAnnotators = pair.getValue().subList(4, pair.getValue().size());
            for(String element: claimAnnotators){
                if(!annotatedWorkers.contains(element)){
                    annotatedWorkers.add(element);
                }
            }
        }
        int numberOfFoundMJ = annotatedWorkers.size();
        Double rate = (double) (numberOfFoundMJ) / (double) (numberOfAnnotators);
        return rate;

    }

    /**
     * Calculates the krippendorff's alpha u for inter annotator agreement and adds it to the
     * current jcas. The smallest possible unit here is a token, since the annotators can only mark
     * one token.
     */
    private Double calculateAlphaUAgreementTokenClaims(JCas aJCas, List<AnnotationPair<String,List<String>>> annotations)
    {
        List<String> annotatedWorkers = new ArrayList<String>();
        for (AnnotationPair<String,List<String>> pair : annotations) {
            List<String> claimAnnotators = pair.getValue().subList(4, pair.getValue().size());
            for(String element: claimAnnotators){
                if(!annotatedWorkers.contains(element)){
                    annotatedWorkers.add(element);
                }
            }
        }
        int numberOfAnnotators = annotatedWorkers.size();
        // If we have only one annotator, return -1 since we cannot compute an alpha_u!
        if(numberOfAnnotators<=1){
            return -1.0;
        }
        // Since we can annotate the review title as well, the document length is the review text
        // length + review title length
        Collection<Token> tokens = JCasUtil.select(aJCas, Token.class);
        UnitizingAnnotationStudy study = new UnitizingAnnotationStudy(numberOfAnnotators,
                tokens.size());
        String[] raterIDs = new String[numberOfAnnotators]; // keep the rater index - rater id
                                                            // mapping
        int index = 0;
        List<Token> sortedTokenList = this.getSortedTokenList(aJCas);
        for (AnnotationPair<String,List<String>> claim : annotations) {
            int begin = Integer.parseInt(claim.getValue().get(0).split("_")[1]);
            int end = Integer.parseInt(claim.getValue().get(1).split("_")[1]);
            String type = claim.getValue().get(2);
            List<Token> annotationTokens = this.getCoveredTokens(aJCas, begin,end-1,type);
            for(String raterID : claim.getValue().subList(4, claim.getValue().size())){
                // If the rater is already in the list, take the actual index instead!
                if(Arrays.asList(raterIDs).contains(raterID)){
                    study.addUnit(sortedTokenList.indexOf(this.getBeginToken(aJCas, begin)),
                            annotationTokens.size(), Arrays.asList(raterIDs).indexOf(raterID), "X");
                    continue;
                }
            study.addUnit(sortedTokenList.indexOf(this.getBeginToken(aJCas, begin)),
                    annotationTokens.size(), index, "X");
                raterIDs[index] = raterID;
                index++;
            }
        }

        KrippendorffAlphaUnitizingAgreement alpha = new KrippendorffAlphaUnitizingAgreement(study);
        return alpha.calculateAgreement();
    }
    /* ==================== helper functions ==================== */


    /**
     * Simple function to check independent of order, if a list(list) contains a list.
     * @param overlapAnnotations
     * @param overlapGrouping
     * @return
     */
    private boolean containsNoOrder(List<List<AnnotationPair<String, List<String>>>> overlapAnnotations, List<AnnotationPair<String, List<String>>> overlapGrouping) {
        for(List<AnnotationPair<String, List<String>>> element : overlapAnnotations){
            if(listEqualsNoOrder(element,overlapGrouping)){
                return true;
            }
        }
        return false;
    }

    /**
     * Simple function to check for equality of lists independent of the order.
     * @param l1
     * @param l2
     * @return true if lists contain the same elements; else false
     */
    public static <T> boolean listEqualsNoOrder(List<T> l1, List<T> l2) {
        final Set<T> s1 = new HashSet<>(l1);
        final Set<T> s2 = new HashSet<>(l2);

        return s1.equals(s2);
    }

    /**
     * Returns a list of the covered token in the major claim annotation
     *
     * @return a list of the covered token. Returns an empty list, if there was no annotation at all
     */
    private List<Token> getCoveredTokens(JCas aJCas, int start, int end, String type)
    {
        String tokenType = type;
        List<Token> sortedTokenList = new ArrayList<Token>();
        // Return an empty token list, if we have annotation at all!
        if (!(tokenType.equals("token"))) {
            return sortedTokenList;
        }
        sortedTokenList = this.getSortedTokenList(aJCas);
        Token startToken = this.getBeginToken(aJCas, start);
        Token endToken = this.getEndToken(aJCas, end);
        // In case we have an annotation with only 1 token, return it instantly
        if(start>=end){
            sortedTokenList.add(startToken);
            return sortedTokenList;
        }
        sortedTokenList.subList(sortedTokenList.indexOf(startToken),
                sortedTokenList.indexOf(endToken));
        return sortedTokenList;
    }

    /**
     * Returns an array list of tokens which are sorted in the correct order.
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

    /**
     * Returns the end token of a major claim annotation
     */
    private Token getEndToken(JCas aJCas, int end)
    {
        int endIndex = end;
        FSIterator<Token> tokenIterator = aJCas.getAllIndexedFS(Token.class);
        tokenIterator.moveToFirst();
        while (tokenIterator.hasNext()) {
            Token token = tokenIterator.get();
            if (token.getId().contains("titleToken") && token.getEnd() == endIndex) {
                return token;
            }
            tokenIterator.moveToNext();
        }
        return null;
    }

    /**
     * Returns the start token of a major claim annotation
     */
    private Token getBeginToken(JCas aJCas, int start)
    {
        int startIndex = start;
        FSIterator<Token> tokenIterator = aJCas.getAllIndexedFS(Token.class);
        tokenIterator.moveToFirst();
        while (tokenIterator.hasNext()) {
            Token token = tokenIterator.get();
            if (token.getId().contains("reviewToken") && token.getBegin() == startIndex) {
                return token;
            }
            tokenIterator.moveToNext();
        }
        return null;
    }

    /**
     * Returns the id of the startToken of an annotation If the annotation was a comment, the
     * returned index will be -1.
     */
    private String getStartTokenID(JCas aJCas, int begin, String type)
    {
        String tokenType = type;
        int startIndex = begin;
        FSIterator<Token> tokenIterator = aJCas.getAllIndexedFS(Token.class);
        tokenIterator.moveToFirst();
        while (tokenIterator.hasNext()) {
            Token token = tokenIterator.get();
            if (token.getId().contains(tokenType) && token.getBegin() == startIndex) {
                return token.getId();
            }
            tokenIterator.moveToNext();
        }
        String errorString = "-1";
        return errorString;
    }

    /**
     * Returns the id of the endToken of an annotation If the annotation was a comment, the returned
     * index will be -1.
     */
    private String getEndTokenID(JCas aJCas, int end, String type)
    {
        String tokenType = type;
        int endIndex = end;
        FSIterator<Token> tokenIterator = aJCas.getAllIndexedFS(Token.class);
        tokenIterator.moveToFirst();
        while (tokenIterator.hasNext()) {
            Token token = tokenIterator.get();
            if (token.getId().contains(tokenType) && token.getEnd() == endIndex) {
                return token.getId();
            }
            if (tokenType.equals("titleToken_reviewToken")) {
                if ((token.getId().contains("titleToken") || token.getId().contains("reviewToken"))
                        && token.getEnd() == endIndex) {
                    return token.getId();
                }
            }
            tokenIterator.moveToNext();
        }
        String errorString = "-1";
        return errorString;
    }

}

final class AnnotationPair<K, V>
    implements Map.Entry<K, V>
{
    private final K key;
    private V value;

    public AnnotationPair(K key, V value)
    {
        this.key = key;
        this.value = value;
    }

    @Override
    public K getKey()
    {
        return key;
    }

    @Override
    public V getValue()
    {
        return value;
    }

    @Override
    public V setValue(V value)
    {
        V old = this.value;
        this.value = value;
        return old;
    }
}
