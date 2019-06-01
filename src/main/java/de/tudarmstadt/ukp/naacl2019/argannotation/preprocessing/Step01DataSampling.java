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
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.cli.ParseException;
import org.apache.commons.io.FileUtils;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import com.google.gson.Gson;

/**
 * Reads input data and samples items for further processing. The current I/O format is JSON.
 *
 */
public class Step01DataSampling
{
	@Option(name="-o",aliases = { "--output" },metaVar="dir",usage="output folder", required=true)
	private File outputDir;

	@Option(name="-i",aliases = { "--input" },metaVar="dir",usage="input folder", required=true)
	private File inputDir;

	@Option(name="-p",aliases = { "--metadata" },metaVar="file",usage="path to the product metadata", required=true)
	private File metaDir;

	@Option(name="-n",aliases = { "--number" },metaVar="N",usage="number of reviews to be sampled")
	private int number = Integer.MAX_VALUE;

	@Option(name="-m",aliases = { "--minimum" },metaVar="N",usage="Minimum length of reviews")
	private int minLength = 150;

	@Option(name="-x",aliases = { "--maximum" },metaVar="N",usage="Maximum length of reviews")
	private int maxLength = 200;

	@Option(name="-h",aliases = { "--helpful" },metaVar="N",usage="Minimum amount of helpful votings of the review")
	private int minHelpful = 5;

	@Option(name="-r",aliases = { "--ratio" },metaVar="double",usage="Minimum ratio of helpful votings of the review")
	private double minRatio = 0.4;

    /**
     * Data sampling
     *
     * @throws IOException I/O exception
     */
    public void sampleData()
            throws IOException
    {
    	if(inputDir.isDirectory() && outputDir.isDirectory()){
    		for(File file: inputDir.listFiles()){
    			if(file.isFile()){
        			BufferedReader brMeta = new BufferedReader(new FileReader(metaDir+"/"+file.getName().replace("reviews_", "meta_")));
            		Gson gson = new Gson();
    				BufferedReader br = new BufferedReader(new FileReader(file));
    				String outputBase = outputDir.getPath()+"/"+file.getName().replace(".json", "");
    				List<Review> reviews = new ArrayList<Review>();
    				String line;
    				System.out.println("Acquiring reviews for "+file.getName().replace(".json", ""));
    				while((line = br.readLine()) != null){
    					Review review = gson.fromJson(line,Review.class);
    					if(review.getReviewText().split(" ").length > minLength
    							&& review.getHelpful()[0] > minHelpful
    							&& review.getReviewText().split(" ").length < maxLength
    							&& (review.getHelpful()[0] / review.getHelpful()[1]*1.0) > minRatio){
    						//FileUtils.writeStringToFile(new File(outputBase+"_"+i+++".json"), line);
    						reviews.add(review);
    					}
    				}
    				br.close();
    				Collections.sort(reviews);
    				int maxi = number <= reviews.size() ? number : reviews.size();
    				System.out.println("Acquire product data for " + maxi + " reviews");
    				Map<String,Product> map = new HashMap<String,Product>();
    				while((line = brMeta.readLine()) != null){
    					for(Review review: reviews){
    						if(line.contains("'asin': "+"'"+review.getAsin()+"'")){
    							Product product = gson.fromJson(line, Product.class);
    							map.put(review.getAsin(), product);
    							break;
    						}
    					}
    				}
    				System.out.println("Writing review data");
    				for(int i=0; i < maxi; i++){
    					Review review = reviews.get(i);
    					review.setProduct(map.get(review.getAsin()));
    					if(review.getProduct().getTitle() != null && !review.getProduct().getTitle().isEmpty()) {
                            FileUtils.writeStringToFile(new File(outputBase+"_"+i+".json"), gson.toJson(review));
                        }
    				}
        			System.out.println(file.getName().replace(".json", "")+" completed");
        			brMeta.close();
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
    	new Step01DataSampling().doMain(args);
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
