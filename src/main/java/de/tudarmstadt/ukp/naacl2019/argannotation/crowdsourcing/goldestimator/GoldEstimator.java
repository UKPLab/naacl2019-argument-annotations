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

import static org.apache.uima.fit.factory.AnalysisEngineFactory.createEngineDescription;
import static org.apache.uima.fit.factory.CollectionReaderFactory.createReaderDescription;

import java.io.File;
import java.io.IOException;

import org.apache.uima.UIMAException;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.collection.CollectionReaderDescription;
import org.apache.uima.fit.pipeline.SimplePipeline;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import de.tudarmstadt.ukp.dkpro.core.io.xmi.XmiReader;

/**
 * Reads a folder of converted MTurk results and calculates statistics to each review file. Will do
 * splitting of the files into threshold reached and not reached.
 *
 */
public class GoldEstimator
{

    /**
     * Do gold standard estimations for each given review in the input directory.
     *
     * @param inputDir
     *            full path to the input directory
     * @param outputDir
     *            full path to the output directory
     * @param thresholdBinary
     *            threshold for acceptable interannotator agreement (binary)
     * @param thresholdAlpha
     *            threshold for acceptable interannotator agreement (Krippendorff's alpha)
     * @param annotationType
     *            annotation type (major claim, claim, premise)
     */
    public static void runPipeline(File inputDir, File outputDir, String thresholdBinary,
            String thresholdAlpha, String annotationType)
        throws UIMAException, IOException
    {
        CollectionReaderDescription reader = createReaderDescription(XmiReader.class,
                XmiReader.PARAM_SOURCE_LOCATION, inputDir, XmiReader.PARAM_PATTERNS,
                XmiReader.INCLUDE_PREFIX + "*.xmi");

        AnalysisEngineDescription goldStandardCalculator = createEngineDescription(
                GoldStandardCalculator.class, GoldStandardCalculator.PARAM_THRESHOLD_BINARY, thresholdBinary,
                GoldStandardCalculator.PARAM_THRESHOLD_ALPHA, thresholdAlpha,
                GoldStandardCalculator.PARAM_ANNOTATION_TYPE, annotationType);
        AnalysisEngineDescription goldStandardWriter = createEngineDescription(
                GoldStandardWriter.class, GoldStandardWriter.PARAM_TARGET_LOCATION, outputDir,
                GoldStandardWriter.PARAM_OVERWRITE, true,
                GoldStandardWriter.PARAM_THRESHOLD_BINARY, thresholdBinary,
                GoldStandardWriter.PARAM_THRESHOLD_ALPHA, thresholdAlpha,
                GoldStandardWriter.PARAM_ANNOTATION_TYPE,annotationType);
        SimplePipeline.runPipeline(reader, goldStandardCalculator, goldStandardWriter);
    }

    @Option(name="-o",aliases = { "--output" },metaVar="dir",usage="output folder", required=true)
	private File outputDir;

	@Option(name="-i",aliases = { "--input" },metaVar="dir",usage="input folder", required=true)
	private File inputDir;

	@Option(name="-r",aliases = { "--resultFile" },metaVar="file",usage="AMT .result file", required=true)
	private File resultFile;

	@Option(name="-t",aliases = { "--annotationType" },metaVar="char",usage="m, c, or p for major claim, claim, or premise annotation", required=true)
	private String annotationType;

	@Option(name="-ta",aliases = { "--alphaThreshold" },metaVar="float",usage="Alpha threshold for the gold estimations (float)", required=true)
	private float alphaThreshold;

	@Option(name="-tb",aliases = { "--binaryThreshold" },metaVar="float",usage="Binary threshold for the gold estimations (float)", required=true)
	private float binaryThreshold;

	public static void main(String[] args)
            throws Exception
    {
		new GoldEstimator().doMain(args);

    }

    private void doMain(String[] args) throws UIMAException, IOException
    {
    	CmdLineParser parser = new CmdLineParser(this);
		try {
            // parse the arguments.
            parser.parseArgument(args);
            runPipeline(inputDir, outputDir,
            		Float.toString(binaryThreshold), Float.toString(alphaThreshold),annotationType);
        } catch( CmdLineException e ) {
            System.err.println(e.getMessage());
            System.err.println("java "+this.getClass().getSimpleName()+" [options...] arguments...");
            parser.printUsage(System.err);
            System.err.println();
            return;
        }
    }

}
