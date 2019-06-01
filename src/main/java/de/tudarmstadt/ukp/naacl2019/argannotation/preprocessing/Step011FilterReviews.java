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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.apache.commons.cli.ParseException;
import org.apache.commons.io.FileUtils;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import com.google.gson.Gson;

public class Step011FilterReviews {
	@Option(name="-o",aliases = { "--output" },metaVar="dir",usage="output folder", required=true)
	private File outputDir;

	@Option(name="-i",aliases = { "--input" },metaVar="dir",usage="input folder", required=true)
	private File inputDir;

	@Option(name="-r",aliases = { "--reviewdata" },metaVar="file",usage="review data folder", required=true)
	private File reviewdata;

	@Option(name="-n",aliases = { "--number" },metaVar="N",usage="number of reviews to be sampled per category")
	private int number = 4;

	private long seed = 123;

    /**
     * Randomly picks reviews from the jsons
     *
     * @throws IOException I/O exception
     */
    public void sampleData()
            throws IOException
    {
    	if(inputDir.isDirectory() && outputDir.isDirectory() && reviewdata.isDirectory()){
    		Collection<String> xmiNames = new ArrayList<>();
    		for(File file: reviewdata.listFiles()){
    			xmiNames.add(file.getName());
    		}
        	Gson gson = new Gson();
        	Map<String, Integer> categoryMap = new HashMap<>();

        	List<File> files = Arrays.asList(inputDir.listFiles());
        	Random random = new Random(seed);
        	Collections.shuffle(files, random);

    		for(File file: files){
    			if(file.isFile()){
        			Review review = gson.fromJson(FileUtils.readFileToString(file), Review.class);
        			String id = review.getProduct().getAsin()+"_"+review.getReviewerID()+"_"+review.getUnixReviewTime()+".xmi";
        			if(!xmiNames.contains(id)){
	        			String category = file.getName().substring(file.getName().indexOf("_") + 1, file.getName().lastIndexOf("_"));
	        			if(categoryMap.containsKey(category)){
	        				categoryMap.put(category, (categoryMap.get(category) + 1));
	        			}
	        			else{
	        				categoryMap.put(category, 1);
	        			}
	        			if(categoryMap.get(category) <= number){
	        				String categoryPath = outputDir.getPath()+"/"+category;
	        				File categoryDir = new File (categoryPath);
	        				if(!categoryDir.exists()){
	        					categoryDir.mkdir();
	        				}
	        				FileUtils.copyFile(file, new File(categoryPath+"/"+file.getName()));
	        			}
        			}
    			}
    		}
    	}
    	else{
    		System.out.println("The inputDir '"+inputDir+"' is not a directory!");
    		System.exit(1);
    	}
    }

    public static void main(String[] args)
            throws ParseException, IOException
    {
    	new Step011FilterReviews().doMain(args);
    }

	private void doMain(String[] args) throws IOException {
		CmdLineParser parser = new CmdLineParser(this);
		try {
            // parse the arguments.
            parser.parseArgument(args);
    		sampleData();
        } catch( CmdLineException e ) {
            System.err.println(e.getMessage());
            System.err.println("java "+this.getClass().getSimpleName()+" [options...] arguments...");
            parser.printUsage(System.err);
            System.err.println();
            return;
        }
	}
}
