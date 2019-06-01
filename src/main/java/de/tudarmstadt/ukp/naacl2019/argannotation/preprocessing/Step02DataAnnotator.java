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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.apache.commons.io.FileUtils;
import org.apache.uima.UIMAException;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.factory.CollectionReaderFactory;
import org.apache.uima.fit.pipeline.SimplePipeline;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import de.tudarmstadt.ukp.dkpro.core.io.xmi.XmiWriter;
import de.tudarmstadt.ukp.dkpro.core.tokit.ParagraphSplitter;

/**
 * Reads files in the format specified by the UIMA reader and performs basic annotation, such as
 * tokenization an paragraphs. Output is stored in UIMA XMI format.
 *
 */
public class Step02DataAnnotator
{

	@Option(name="-o",aliases = { "--output" },metaVar="dir",usage="output folder", required=true)
	private File outputDir;

	@Option(name="-i",aliases = { "--input" },metaVar="dir",usage="input folder", required=true)
	private File inputDir;

	@Option(name="-l",aliases = { "--language" },metaVar="file",usage="path to language model", required=false)
	private String language = "/src/main/resources/openNLPModels/en-token.bin";

	@Option(name="-m",aliases = { "--moveFiles" },usage="moves files to separate subfolders")
    private boolean move = false;

	@Option(name="-c",aliases = { "--category" },usage="searches inputDir for category folders")
    private boolean category = true;

	@Option(name="-s",aliases = { "--symlink" },usage="creates symlinks")
    private boolean symlink = true;

    /**
     * Runs UIMA annotations
     *
     * @param inputDir
     *            The path to the input directory to read the files from. Please note, that
     *            the files to be already split into single reviews
     * @param outputDir
     *            The path to the output directory to write the files into. The structure the
     *            files are written to is as follows:
     *            outputDirectory/asinSubfolder/documentId.xmi
     *            Subfolders are created for each product asin.
     *            The output directory contains one TypeSystem.xml file, since it is the same
     *            for all review.xmi files.
     * @throws UIMAException
     *             exception
     * @throws IOException
     *             exception
     */
    public static void runPipeline(File inputDir, File outputDir, String language, boolean move)
        throws UIMAException, IOException
    {
        SimplePipeline.runPipeline(CollectionReaderFactory.createReader(
                AmazonReviewJSONReader.class, AmazonReviewJSONReader.PARAM_SOURCE_LOCATION,
                inputDir, AmazonReviewJSONReader.PARAM_PATTERNS, "*.json" // only include json files
        ), AnalysisEngineFactory
                .createEngineDescription(ParagraphSplitter.class,
                        ParagraphSplitter.PARAM_SPLIT_PATTERN,
                        ParagraphSplitter.SINGLE_LINE_BREAKS_PATTERN), AnalysisEngineFactory
                .createEngineDescription(OpenNLPTokenizerAnnotator.class,
                        OpenNLPTokenizerAnnotator.PARAM_LANGUAGE_MODEL, language),
                AnalysisEngineFactory
                        .createEngineDescription(XmiWriter.class, XmiWriter.PARAM_TARGET_LOCATION,
                                outputDir, XmiWriter.PARAM_OVERWRITE, true,
                                XmiWriter.PARAM_TYPE_SYSTEM_FILE, outputDir+"/TypeSystem.xml"));

        // Organize files into asin specific subfolders
        if(move){
	        File[] outputFiles = outputDir.listFiles();

	        for (File reviewFile : outputFiles) {
	            // Skip this step for the typesystem file
	            if(reviewFile.getName().equals("TypeSystem.xml")) {
	                continue;
	            }
	            // get the asin for moving into the subfolder:
	            String subfolder = outputDir.getAbsolutePath() + "/" + reviewFile.getName().split("_")[0];

	            // Create the subfolder, if it does not exist yet.
	            if (!new File(subfolder).exists()) {
	                new File(subfolder).mkdir();
	            }

	            Files.move(Paths.get(reviewFile.getAbsolutePath()),
	                    Paths.get(subfolder + "/" + reviewFile.getName()));
	        }
        }
    }

    public static void main(String[] args)
        throws Exception
    {
    	new Step02DataAnnotator().doMain(args);
    }

	private void doMain(String[] args) throws IOException, UIMAException {
		CmdLineParser parser = new CmdLineParser(this);
		try {
            // parse the arguments.
            parser.parseArgument(args);
            if(category){
            	for(File dir: inputDir.listFiles()){
            		if(dir.isDirectory()){
            			File categoryDir = new File(outputDir.getPath()+"/"+dir.getName());
            			if(!categoryDir.exists()){
            				categoryDir.mkdir();
            			}
            			runPipeline(dir, categoryDir, language, move);
            			createSymLink(categoryDir);

            		}
            	}
            }
            else {
            	runPipeline(inputDir, outputDir, language, move);
            	createSymLink(outputDir);
            }
        } catch( CmdLineException e ) {
            System.err.println(e.getMessage());
            System.err.println("java "+this.getClass().getSimpleName()+" [options...] arguments...");
            parser.printUsage(System.err);
            System.err.println();
            return;
        }
	}

	private void createSymLink(File linkDir) {
		try{
			if(symlink){
				for(File file: linkDir.listFiles()){
					if(move){
						if(file.isDirectory()){
							for (File subFile: file.listFiles()){
								if(subFile.getName().endsWith(".xmi")){
	    							String relativePath = "../"+linkDir.getName() + "/" +  file.getName() + "/" + subFile.getName();
	                        		FileUtils.write(new File(outputDir.getPath()+"/all/"+subFile.getName()), relativePath);
								}
							}
						}
					}
					else {
						if(file.getName().endsWith(".xmi")){
							String relativePath = "../"+linkDir.getName() + "/" + file.getName();
	                		FileUtils.write(new File(outputDir.getPath()+"/all/"+file.getName()), relativePath);
						}
					}
				}
			}
		} catch(Exception e){
			System.err.println(e.getMessage());
		}
	}
}
