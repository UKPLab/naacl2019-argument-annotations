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

import static org.apache.uima.fit.factory.AnalysisEngineFactory.createEngineDescription;
import static org.apache.uima.fit.factory.CollectionReaderFactory.createReaderDescription;

import java.io.File;
import java.io.IOException;

import org.apache.uima.UIMAException;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.collection.CollectionReaderDescription;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.pipeline.SimplePipeline;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import de.tudarmstadt.ukp.dkpro.core.io.xmi.XmiReader;
import de.tudarmstadt.ukp.dkpro.core.io.xmi.XmiWriter;
import de.tudarmstadt.ukp.naacl2019.argannotation.crowdsourcing.goldestimator.mace.claim.MaceClaimPremiseFileWriter;
import de.tudarmstadt.ukp.naacl2019.argannotation.crowdsourcing.goldestimator.mace.claim.MaceClaimPremisePredictor;
import de.tudarmstadt.ukp.naacl2019.argannotation.crowdsourcing.goldestimator.mace.claim.MaceClaimResultAnnotator;
import de.tudarmstadt.ukp.naacl2019.argannotation.crowdsourcing.goldestimator.mace.majorclaim.MaceMajorClaimFileWriter;
import de.tudarmstadt.ukp.naacl2019.argannotation.crowdsourcing.goldestimator.mace.majorclaim.MaceMajorClaimPredictor;
import de.tudarmstadt.ukp.naacl2019.argannotation.crowdsourcing.goldestimator.mace.majorclaim.MaceMajorClaimResultAnnotator;
import de.tudarmstadt.ukp.naacl2019.argannotation.crowdsourcing.goldestimator.mace.premise.MacePremiseResultAnnotator;

public class MaceGoldEstimator
{

    /**
     * Do gold standard estimations for each given review in the input directory. Using the MACE estimator
     *
     * @param inputDir
     *            input directory
     * @param outputDir
     *            output directory
     * @param amtResultFile
     *            path to AMT results file
     * @param maceSplitInput
     *            path to MACE split input directory
     * @param maceWorkingFolder
     *            path to MACE working folder
     * @param annotationStep
     *            annotation step (major claim, claim, premise)
     */
    public static void runPipeline(File inputDir, File outputDir, File amtResultFile,
    		File maceSplitInput, File maceWorkingFolder, String annotationStep)
        throws UIMAException, IOException
    {
        AnalysisEngineDescription maceFileWriter;
        AnalysisEngineDescription maceResultAnnotator;
        CollectionReaderDescription reader;

        // Reader for XMI files
        reader = createReaderDescription(XmiReader.class,
                XmiReader.PARAM_SOURCE_LOCATION, inputDir, XmiReader.PARAM_PATTERNS,
                XmiReader.INCLUDE_PREFIX + "*.xmi");

        // TODO: This code could be greatly simplified, though it will require
        // creation of an abstract base class for MaceMajorClaimFileWriter and
        // MaceClaimFileWriter, and another abstract base class for
        // MaceMajorClaimResultAnnotator and MaceClaimResultAnnotator.
        switch (annotationStep.charAt(0)) {
        case 'm':
            // Initialize Mace file writer
            maceFileWriter = createEngineDescription(
                    MaceMajorClaimFileWriter.class,
                    MaceMajorClaimFileWriter.PARAM_SOURCE_LOCATION,
                    amtResultFile,
                    MaceMajorClaimFileWriter.PARAM_TARGET_LOCATION,
                    maceSplitInput);

            // Write the MACE input files
            SimplePipeline.runPipeline(reader, maceFileWriter);

            // Calculate the gold standard
            MaceMajorClaimPredictor.doMacePredict(maceSplitInput,
                    maceWorkingFolder, maceWorkingFolder.getPath());

            // Write the output
            maceResultAnnotator = createEngineDescription(
                    MaceMajorClaimResultAnnotator.class,
                    MaceMajorClaimResultAnnotator.PARAM_SOURCE_LOCATION,
                    maceWorkingFolder);

            SimplePipeline.runPipeline(reader, maceResultAnnotator,
                    AnalysisEngineFactory.createEngineDescription(
                            XmiWriter.class, XmiWriter.PARAM_TARGET_LOCATION,
                            outputDir, XmiWriter.PARAM_OVERWRITE, true));
            break;

        case 'c':
            // Initialize Mace file writer
            maceFileWriter = createEngineDescription(MaceClaimPremiseFileWriter.class,
                    MaceClaimPremiseFileWriter.PARAM_SOURCE_LOCATION, amtResultFile,
                    MaceClaimPremiseFileWriter.PARAM_TARGET_LOCATION, maceSplitInput);

            // Write the MACE input files
            SimplePipeline.runPipeline(reader, maceFileWriter);

            // Calculate the gold standard
            MaceClaimPremisePredictor.doMacePredict(maceSplitInput,
                    maceWorkingFolder, maceWorkingFolder.getPath());

            // Write the output
            maceResultAnnotator = createEngineDescription(
                    MaceClaimResultAnnotator.class,
                    MaceClaimResultAnnotator.PARAM_SOURCE_LOCATION,
                    maceWorkingFolder);

            SimplePipeline.runPipeline(reader, maceResultAnnotator,
                    AnalysisEngineFactory.createEngineDescription(
                            XmiWriter.class, XmiWriter.PARAM_TARGET_LOCATION,
                            outputDir, XmiWriter.PARAM_OVERWRITE, true));
            break;

        case 'p':
        	// Initialize Mace file writer
            maceFileWriter = createEngineDescription(MaceClaimPremiseFileWriter.class,
                    MaceClaimPremiseFileWriter.PARAM_SOURCE_LOCATION, amtResultFile,
                    MaceClaimPremiseFileWriter.PARAM_TARGET_LOCATION, maceSplitInput);

            // Write the MACE input files
            SimplePipeline.runPipeline(reader, maceFileWriter);

            // Calculate the gold standard
            MaceClaimPremisePredictor.doMacePredict(maceSplitInput,
                    maceWorkingFolder, maceWorkingFolder.getPath());

            // Write the output
            maceResultAnnotator = createEngineDescription(
                    MacePremiseResultAnnotator.class,
                    MacePremiseResultAnnotator.PARAM_SOURCE_LOCATION,
                    maceWorkingFolder);

            SimplePipeline.runPipeline(reader, maceResultAnnotator,
                    AnalysisEngineFactory.createEngineDescription(
                            XmiWriter.class, XmiWriter.PARAM_TARGET_LOCATION,
                            outputDir, XmiWriter.PARAM_OVERWRITE, true));
            break;

        default:
            throw new IllegalArgumentException(
                    "Invalid annotation step specified");
        }
    }

    @Option(name="-o",aliases = { "--output" },metaVar="dir",usage="output folder", required=true)
	private File outputDir;

	@Option(name="-i",aliases = { "--input" },metaVar="dir",usage="input folder", required=true)
	private File inputDir;

	@Option(name="-r",aliases = { "--resultFile" },metaVar="file",usage="AMT .result file", required=true)
	private File resultFile;

	@Option(name="-s",aliases = { "--splitDir" },metaVar="dir",usage="directory for MACE to output its split input files", required=true)
	private File splitDir;

	@Option(name="-w",aliases = { "--workDir" },metaVar="dir",usage="directory for MACE to output its predictions", required=true)
	private File workDir;

	@Option(name="-t",aliases = { "--step" },metaVar="char",usage="annotation step the results are from (m, c, or p)", required=true)
	private String step;

    public static void main(String[] args)
        throws IOException, UIMAException
    {
       new MaceGoldEstimator().doMain(args);

    }

    private void doMain(String[] args) throws UIMAException, IOException
    {
    	CmdLineParser parser = new CmdLineParser(this);
		try {
            // parse the arguments.
            parser.parseArgument(args);
            runPipeline(inputDir, outputDir,
            		resultFile, splitDir,workDir,step);
        } catch( CmdLineException e ) {
            System.err.println(e.getMessage());
            System.err.println("java "+this.getClass().getSimpleName()+" [options...] arguments...");
            parser.printUsage(System.err);
            System.err.println();
            return;
        }

    }

}
