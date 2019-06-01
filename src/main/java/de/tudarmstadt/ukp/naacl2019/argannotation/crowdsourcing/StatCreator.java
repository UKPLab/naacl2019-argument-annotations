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

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

public class StatCreator {

    public static void createStatistic(String filename, String outputfile) throws IOException
    {
        BufferedReader br = new BufferedReader(new FileReader(filename));
        String line;
        ArrayList<String[]> results = new ArrayList<String[]>();
        while ((line = br.readLine()) != null) {
            String[] result = line.split(";");
            results.add(result);
        }
        br.close();
        createThresholdStat(results, outputfile);
    }

	private static void createThresholdStat(ArrayList<String[]> results, String outputfile) throws IOException{
		int point5 = 0;
		int point6 = 0;
		int point7 = 0;
		int point8 = 0;
		int point9 = 0;
		int one = 0;
		int total = 0;
		for(String[] result: results){
			if(Double.parseDouble(result[1])>=0.5){
				point5 ++;
			}
			if(Double.parseDouble(result[1])>=0.6){
				point6 ++;
			}
			if(Double.parseDouble(result[1])>=0.7){
				point7 ++;
			}
			if(Double.parseDouble(result[1])>=0.8){
				point8 ++;
			}
			if(Double.parseDouble(result[1])>=0.9){
				point9 ++;
			}
			if(Double.parseDouble(result[1])>=1.0){
				one ++;
			}
			total++;
		}
		StringBuilder thresholdStat = new StringBuilder();
		thresholdStat.append("threshold\t# reviews\t% reviews"+"\n");
		thresholdStat.append("0.0\t"+total+"\t"+1.0+"\n");
		thresholdStat.append("0.5\t"+point5+"\t"+((double)point5/total)+"\n");
		thresholdStat.append("0.6\t"+point6+"\t"+((double)point6/total)+"\n");
		thresholdStat.append("0.7\t"+point7+"\t"+((double)point7/total)+"\n");
		thresholdStat.append("0.8\t"+point8+"\t"+((double)point8/total)+"\n");
		thresholdStat.append("0.9\t"+point9+"\t"+((double)point9/total)+"\n");
		thresholdStat.append("1.0\t"+one+"\t"+((double)one/total));
		FileWriter fw = new FileWriter(outputfile);
		fw.write(thresholdStat.toString());
		fw.close();
	}

	public static void main(String[] args) throws IOException
    {
		createStatistic("summary_statistics.csv","threshold_stat.tsv");
    }
}
