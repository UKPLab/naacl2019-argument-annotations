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
import de.tudarmstadt.ukp.dkpro.core.io.xmi.XmiWriter;

/**
 * Runs a pipeline that adds Annotations from MTurk to the XMIs
 */
public class MTurkAnnotator
{

    public static void runPipeline(File inputDir, File outputDir, String annotationStep, String resultFileLocation)
        throws UIMAException, IOException
    {
    	CollectionReaderDescription reader = createReaderDescription(
				XmiReader.class, XmiReader.PARAM_SOURCE_LOCATION, inputDir
				, XmiReader.PARAM_PATTERNS,
		XmiReader.INCLUDE_PREFIX + "*.xmi");

    	AnalysisEngineDescription mTurkAnnotationAdder = createEngineDescription(
				MTurkAnnotationAdder.class, MTurkAnnotationAdder.PARAM_SOURCE_LOCATION, resultFileLocation, MTurkAnnotationAdder.PARAM_ANNOTATION_TYPE, annotationStep);

    	AnalysisEngineDescription xmiWriter = createEngineDescription(
				XmiWriter.class, XmiWriter.PARAM_TARGET_LOCATION, outputDir,
				XmiWriter.PARAM_OVERWRITE,true);
        SimplePipeline.runPipeline(reader, mTurkAnnotationAdder,xmiWriter);
    }

    @Option(name="-o",aliases = { "--output" },metaVar="dir",usage="output folder", required=true)
	private File outputDir;

	@Option(name="-i",aliases = { "--input" },metaVar="dir",usage="input folder", required=true)
	private File inputDir;

	@Option(name="-r",aliases = { "--resultFile" },metaVar="file",usage="AMT .result file", required=true)
	private String resultFile;

	@Option(name="-t",aliases = { "--annotationType" },metaVar="char",usage="m, c, or p for major claim, claim, or premise annotation", required=true)
	private String annotationType;

	public static void main(String[] args)
            throws Exception
    {
		new MTurkAnnotator().doMain(args);

    }

    private void doMain(String[] args) throws UIMAException, IOException
    {
    	CmdLineParser parser = new CmdLineParser(this);
		try {
            // parse the arguments.
            parser.parseArgument(args);
            runPipeline(inputDir, outputDir,annotationType,resultFile);
        } catch( CmdLineException e ) {
            System.err.println(e.getMessage());
            System.err.println("java "+this.getClass().getSimpleName()+" [options...] arguments...");
            parser.printUsage(System.err);
            System.err.println();
            return;
        }
    }
}
