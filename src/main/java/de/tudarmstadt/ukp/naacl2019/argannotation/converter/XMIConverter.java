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

import org.apache.commons.cli.ParseException;
import org.apache.uima.UIMAException;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.collection.CollectionReader;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.factory.CollectionReaderFactory;
import org.apache.uima.fit.pipeline.SimplePipeline;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import de.tudarmstadt.ukp.dkpro.core.io.xmi.XmiReader;
import de.tudarmstadt.ukp.dkpro.core.io.xmi.XmiWriter;

/**
 * Converts xmi files into the new format
 *
 */
public class XMIConverter {

	@Option(name="-i",aliases = { "--inputXMI" },metaVar="dir",usage="input XMI directory", required=true)
	private File inputXMI;

	@Option(name="-o",aliases = { "--outputXMI" },metaVar="dir",usage="output XMI directory", required=true)
	private File outputXMI;

	@Option(name="-u",aliases = { "--unannotatedXMI" },metaVar="dir",usage="unannotated XMI directory", required=true)
	private File unannotatedXMI;

	public static void main(String[] args)
            throws ParseException, IOException, UIMAException
    {
    	new XMIConverter().doMain(args);
    }

	private void doMain(String[] args) throws IOException, UIMAException {
		CmdLineParser parser = new CmdLineParser(this);
		try {
            // parse the arguments.
            parser.parseArgument(args);
            convertXMIs();
        } catch( CmdLineException e ) {
            System.err.println(e.getMessage());
            System.err.println("java "+this.getClass().getSimpleName()+" [options...] arguments...");
            parser.printUsage(System.err);
            System.err.println();
            return;
        }
	}

	private void convertXMIs() throws UIMAException, IOException {

		CollectionReader reader = CollectionReaderFactory.createReader(XmiReader.class,
				XmiReader.PARAM_SOURCE_LOCATION, unannotatedXMI, XmiReader.PARAM_PATTERNS, "*.xmi");

		AnalysisEngineDescription annotationConverter = AnalysisEngineFactory
                .createEngineDescription(AnnotationConverterPipeline.class,
                		AnnotationConverterPipeline.PARAM_ANNOTATION_PATH, inputXMI);

		AnalysisEngineDescription writer = AnalysisEngineFactory
                .createEngineDescription(XmiWriter.class, XmiWriter.PARAM_TARGET_LOCATION,
                		outputXMI, XmiWriter.PARAM_OVERWRITE, true,
                        XmiWriter.PARAM_TYPE_SYSTEM_FILE, outputXMI+"/TypeSystem.xml");

		SimplePipeline.runPipeline(reader, annotationConverter, writer);

	}
}
