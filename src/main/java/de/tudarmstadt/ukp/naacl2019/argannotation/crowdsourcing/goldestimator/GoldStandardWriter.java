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

import static org.apache.commons.io.IOUtils.closeQuietly;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CASRuntimeException;
import org.apache.uima.cas.impl.XmiCasSerializer;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.fit.descriptor.TypeCapability;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.TypeSystemUtil;
import de.tudarmstadt.ukp.naacl2019.argannotation.types.GoldEstimation;
import org.xml.sax.SAXException;

import de.tudarmstadt.ukp.dkpro.core.api.io.JCasFileWriter_ImplBase;
import de.tudarmstadt.ukp.dkpro.core.api.metadata.type.DocumentMetaData;
import de.tudarmstadt.ukp.dkpro.core.api.resources.CompressionUtils;

/**
 * Class for writing the calculated statistics into a statistics file at the output location. Does
 * also a splitting of the files into ones with a given threshold. Also writes the typesystem and
 * the xmi files. This is basically an extension of the
 * de.tudarmstadt.ukp.dkpro.core.io.xmi.XmiWriter
 *
 *
 */
@TypeCapability(inputs = { "de.tudarmstadt.ukp.dkpro.core.api.metadata.type.DocumentMetaData" })
public class GoldStandardWriter
    extends JCasFileWriter_ImplBase
{
    /**
     * Location to write the type system to. If this is not set, a file called TypeSystem.xml will
     * be written to the XMI output path. If this is set, it is expected to be a file relative to
     * the current work directory or an absolute file. <br>
     * If this parameter is set, the {@link #PARAM_COMPRESSION} parameter has no effect on the type
     * system. Instead, if the file name ends in ".gz", the file will be compressed, otherwise not.
     */
    public static final String PARAM_TYPE_SYSTEM_FILE = "typeSystemFile";
    @ConfigurationParameter(name = PARAM_TYPE_SYSTEM_FILE, mandatory = false)
    private File typeSystemFile;

    public static final String PARAM_THRESHOLD_BINARY = "0.6";
    @ConfigurationParameter(name = PARAM_THRESHOLD_BINARY, mandatory = false)
    private String thresholdBinary;

    public static final String PARAM_THRESHOLD_ALPHA = "0.5";
    @ConfigurationParameter(name = PARAM_THRESHOLD_ALPHA, mandatory = false)
    private String thresholdAlpha;

    public static final String PARAM_ANNOTATION_TYPE = "m";
    @ConfigurationParameter(name = PARAM_ANNOTATION_TYPE, mandatory = true)
    private String annotationType;

    private boolean typeSystemWritten;

    @Override
    public void initialize(UimaContext aContext)
        throws ResourceInitializationException
    {
        super.initialize(aContext);
        thresholdAlpha = aContext.getConfigParameterValue(PARAM_THRESHOLD_ALPHA).toString();
        thresholdBinary = aContext.getConfigParameterValue(PARAM_THRESHOLD_BINARY).toString();
        annotationType = aContext.getConfigParameterValue(PARAM_ANNOTATION_TYPE).toString();


        typeSystemWritten = false;
    }

    @Override
    public void process(JCas aJCas)
        throws AnalysisEngineProcessException
    {

        if(annotationType.equals("m")){
            // If we have major claims, we write the reports for them!
            if (!JCasUtil.select(aJCas, GoldEstimation.class).isEmpty()) {
                writeMajorClaimReport(aJCas);
            }
        }
        if(annotationType.equals("c")){
            if (!JCasUtil.select(aJCas, GoldEstimation.class).isEmpty()) {
                writeClaimReport(aJCas);
            }
        }
    }



    private void writeTypeSystem(JCas aJCas)
        throws IOException, CASRuntimeException, SAXException
    {
        OutputStream typeOS = null;

        try {
            if (typeSystemFile != null) {
                typeOS = CompressionUtils.getOutputStream(typeSystemFile);
            }
            else {
                typeOS = getOutputStream("TypeSystem", ".xml");
            }

            TypeSystemUtil.typeSystem2TypeSystemDescription(aJCas.getTypeSystem()).toXML(typeOS);
        }
        finally {
            closeQuietly(typeOS);
        }
    }

    private void writeMajorClaimReport(JCas aJCas)
        throws AnalysisEngineProcessException
    {
        // Fetch some data to write later
        // Some basic review data:
        String reviewID = DocumentMetaData.get(aJCas).getDocumentId();
        String reviewTitle = DocumentMetaData.get(aJCas).getDocumentTitle();
        String reviewText = aJCas.getDocumentText();
        // The gold estimations and inter annotator agreement
        Collection<GoldEstimation> goldEstimations = JCasUtil.select(aJCas, GoldEstimation.class);
        int NumberOfAnnotators = -1;
        Double kAlphaU = -1.0;
        Double binAgree = -1.0;
        List<String> annotations = new ArrayList<String>();
        for (GoldEstimation ge : goldEstimations) {
            // Set number of annotators:
            if (NumberOfAnnotators < 0) {
                NumberOfAnnotators = ge.getNumberOfAnnotators();
            }
            // Set inter annotator agreement
            if (kAlphaU < 0) {
                kAlphaU = ge.getAlphaUAgreement();
            }
            if (binAgree < 0) {
                binAgree = ge.getBinaryAgreement();
            }
            annotations.add("Annotation step: " + ge.getAnnotationType());
            annotations.add(System.getProperty("line.separator"));
            annotations.add("Marked text: " + ge.getAnnotationText());
            annotations.add(System.getProperty("line.separator"));
            String annotatorIDs = "";
            for (String workerID : ge.getAnnotatorID().toStringArray()) {
                annotatorIDs += workerID + "  ";
            }
            annotations.add("Annotator IDs: " + annotatorIDs);
            annotations.add(System.getProperty("line.separator"));
            if(ge.getIsCommentAnnotation()){
                annotations.add("This annotation is a comment.");
                annotations.add(System.getProperty("line.separator"));
            }
            // annotations.add(ge.getAnnotatorID().toString());
            annotations.add("Majority vote ratio: " + Integer.toString(ge.getAnnotatorID().size())
                    + "/" + Integer.toString(NumberOfAnnotators));
            annotations.add(System.getProperty("line.separator"));
            annotations.add(System.getProperty("line.separator"));
        }

        // Writing report files:
        System.out.println("Writing reports for " + reviewID);
        // First an extensive report for each individual report file:
        String path = this.getTargetLocation() + "/";
        String fileName = reviewID + "_extensive_report.txt";
        try (Writer writer = new BufferedWriter(new FileWriter(path + fileName))) {
            writer.write("Gold estimations for Amazon review annotations:");
            writer.write(System.getProperty("line.separator"));
            writer.write(System.getProperty("line.separator"));
            writer.write("ReviewID: " + reviewID);
            writer.write(System.getProperty("line.separator"));
            writer.write(System.getProperty("line.separator"));
            writer.write("Review Title: " + reviewTitle);
            writer.write(System.getProperty("line.separator"));
            writer.write(System.getProperty("line.separator"));
            writer.write("Review Text: " + reviewText);
            writer.write(System.getProperty("line.separator"));
            writer.write(System.getProperty("line.separator"));
            writer.write("Krippendorffs alpha u: " + kAlphaU.toString());
            writer.write(System.getProperty("line.separator"));
            writer.write("Binary agreement: " + binAgree.toString());
            writer.write(System.getProperty("line.separator"));
            writer.write(System.getProperty("line.separator"));
            for (String line : annotations) {
                writer.write(line);
            }
            writer.close();
        }
        catch (IOException e) {
            throw new AnalysisEngineProcessException(e);
        }

        // Write statistics csv file which can be used to calculate some statistics
        try (Writer writer = new BufferedWriter(new FileWriter(path + "summary_statistics.csv",
                true))) {
            writer.write(reviewID + ";" + kAlphaU.toString() + ";" + binAgree.toString());
            writer.write(System.getProperty("line.separator"));
            writer.close();
        }
        catch (IOException e) {
            throw new AnalysisEngineProcessException(e);
        }

        // Second an overview file containing a brief overview of each annotation above and below
        // the thresholds. A document only passes, when its agreement is above both threshold!
        if (Double.parseDouble(thresholdAlpha) <= kAlphaU
                && Double.parseDouble(thresholdBinary) <= binAgree) {
            path += "aboveThreshold" + thresholdAlpha + ".txt";
        }
        else {
            path += "belowThreshold" + thresholdAlpha + ".txt";
        }
        try (Writer writer = new BufferedWriter(new FileWriter(path, true))) {
            writer.write("ReviewID: " + reviewID);
            writer.write(System.getProperty("line.separator"));
            writer.append("Krippendorffs alpha u: " + kAlphaU.toString());
            writer.append(System.getProperty("line.separator"));
            writer.append("Binary Agreement: " + binAgree.toString());
            writer.append(System.getProperty("line.separator"));
            writer.append(System.getProperty("line.separator"));
            for (String line : annotations) {
                writer.append(line);
            }
            writer.append("--------------------------------------------------------");
            writer.append(System.getProperty("line.separator"));
            writer.append(System.getProperty("line.separator"));
            writer.close();
        }
        catch (IOException e) {
            throw new AnalysisEngineProcessException(e);
        }

        try (OutputStream docOS = getOutputStream(aJCas, ".xmi")) {
            XmiCasSerializer.serialize(aJCas.getCas(), docOS);

            if (!typeSystemWritten || typeSystemFile == null) {
                writeTypeSystem(aJCas);
                typeSystemWritten = true;
            }
        }
        catch (Exception e) {
            throw new AnalysisEngineProcessException(e);
        }
    }

    private void writeClaimReport(JCas aJCas) throws AnalysisEngineProcessException
    {
        // Fetch some data to write later
        // Some basic review data:
        String reviewID = DocumentMetaData.get(aJCas).getDocumentId();
        String reviewTitle = DocumentMetaData.get(aJCas).getDocumentTitle();
        String reviewText = aJCas.getDocumentText();
        // The gold estimations and inter annotator agreement
        Collection<GoldEstimation> goldEstimations = JCasUtil.select(aJCas, GoldEstimation.class);
        int NumberOfAnnotators = -1;
        Double kAlphaU = -1.0;
        Double binAgree = -1.0;
        List<String> annotations = new ArrayList<String>();
        for (GoldEstimation ge : goldEstimations) {
            // Set number of annotators:
            if (NumberOfAnnotators < 0) {
                NumberOfAnnotators = ge.getNumberOfAnnotators();
            }
            // Set inter annotator agreement
            if (kAlphaU < 0) {
                kAlphaU = ge.getAlphaUAgreement();
            }
            if (binAgree < 0) {
                binAgree = ge.getBinaryAgreement();
            }
            // Note that annotationType contains: stance_claim!
            annotations.add(System.getProperty("line.separator"));
            annotations.add("Annotation step: " + ge.getAnnotationType().split("_")[1]);
            annotations.add(System.getProperty("line.separator"));
            annotations.add("Marked text: " + ge.getAnnotationText());
            annotations.add(System.getProperty("line.separator"));
            annotations.add("Stance: " + ge.getAnnotationType().split("_")[0]);
            annotations.add(System.getProperty("line.separator"));
            annotations.add("Alpha_u: " + ge.getAlphaUAgreement());
            annotations.add(System.getProperty("line.separator"));
            annotations.add("Binary : " + ge.getBinaryAgreement());
            annotations.add(System.getProperty("line.separator"));
            String annotatorIDs = "";
            for (String workerID : ge.getAnnotatorID().toStringArray()) {
                annotatorIDs += workerID + "  ";
            }
            annotations.add("Annotator IDs: " + annotatorIDs);
            annotations.add(System.getProperty("line.separator"));
            if(ge.getIsCommentAnnotation()){
                annotations.add("This annotation is a comment.");
                annotations.add(System.getProperty("line.separator"));
            }
            // annotations.add(ge.getAnnotatorID().toString());
            annotations.add("Majority vote ratio: " + Integer.toString(ge.getAnnotatorID().size())
                    + "/" + Integer.toString(NumberOfAnnotators));
            annotations.add(System.getProperty("line.separator"));
            annotations.add("Gold Standard: " + ge.getEstimatedGoldAnnotation());
            annotations.add(System.getProperty("line.separator"));
            annotations.add(System.getProperty("line.separator"));
            annotations.add("-----------------------------------------------");
        }

        // Writing report files:
        System.out.println("Writing reports for " + reviewID);
        // First an extensive report for each individual report file:
        String path = this.getTargetLocation() + "/";
        String fileName = reviewID + "_extensive_report.txt";
        try (Writer writer = new BufferedWriter(new FileWriter(path + fileName))) {
            writer.write("Gold estimations for Amazon review annotations:");
            writer.write(System.getProperty("line.separator"));
            writer.write(System.getProperty("line.separator"));
            writer.write("ReviewID: " + reviewID);
            writer.write(System.getProperty("line.separator"));
            writer.write(System.getProperty("line.separator"));
            writer.write("Review Title: " + reviewTitle);
            writer.write(System.getProperty("line.separator"));
            writer.write(System.getProperty("line.separator"));
            writer.write("Review Text: " + reviewText);
            writer.write(System.getProperty("line.separator"));
            writer.write(System.getProperty("line.separator"));
            writer.write("Krippendorffs alpha u: " + kAlphaU.toString());
            writer.write(System.getProperty("line.separator"));
            writer.write("Binary agreement: " + binAgree.toString());
            writer.write(System.getProperty("line.separator"));
            writer.write(System.getProperty("line.separator"));
            for (String line : annotations) {
                writer.write(line);
            }
            writer.close();
        }
        catch (IOException e) {
            throw new AnalysisEngineProcessException(e);
        }

        // Write statistics csv file which can be used to calculate some statistics
//        try (Writer writer = new BufferedWriter(new FileWriter(path + "summary_statistics.csv",
//                true))) {
//            writer.write(reviewID + ";" + kAlphaU.toString() + ";" + binAgree.toString());
//            writer.write(System.getProperty("line.separator"));
//            writer.close();
//        }
//        catch (IOException e) {
//            throw new AnalysisEngineProcessException(e);
//        }

        // Second an overview file containing a brief overview of each annotation above and below
        // the thresholds. A document only passes, when its agreement is above both threshold!

            path += "suggestedClaims" + ".txt";

        try (Writer writer = new BufferedWriter(new FileWriter(path, true))) {
            writer.write("ReviewID: " + reviewID);
            writer.write(System.getProperty("line.separator"));
            for (GoldEstimation overall : goldEstimations) {
                if(overall.getEstimatedGoldAnnotation()){
                writer.write(System.getProperty("line.separator"));
                writer.append("Krippendorffs alpha u: " + overall.getAnnotationText());
                writer.write(System.getProperty("line.separator"));
                writer.append("Krippendorffs alpha u: " + overall.getAnnotatorID().toString());
                writer.write(System.getProperty("line.separator"));
                writer.append("Krippendorffs alpha u: " + overall.getAlphaUAgreement());
                writer.append(System.getProperty("line.separator"));
                writer.append("Binary Agreement: " + overall.getBinaryAgreement());
                writer.append(System.getProperty("line.separator"));
                writer.append("--------------------------------------------------------");
                writer.append(System.getProperty("line.separator"));
                }
            }
            writer.append("==========================================================");
            writer.append(System.getProperty("line.separator"));
            writer.append(System.getProperty("line.separator"));
            writer.close();
        }
        catch (IOException e) {
            throw new AnalysisEngineProcessException(e);
        }

        try (OutputStream docOS = getOutputStream(aJCas, ".xmi")) {
            XmiCasSerializer.serialize(aJCas.getCas(), docOS);

            if (!typeSystemWritten || typeSystemFile == null) {
                writeTypeSystem(aJCas);
                typeSystemWritten = true;
            }
        }
        catch (Exception e) {
            throw new AnalysisEngineProcessException(e);
        }

    }
}
