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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;

import org.apache.commons.cli.ParseException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.csv.QuoteMode;
import org.apache.commons.io.FileUtils;
import org.apache.uima.UIMAException;
import org.apache.uima.fit.factory.JCasFactory;
import org.apache.uima.fit.util.CasIOUtil;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import de.tudarmstadt.ukp.naacl2019.argannotation.types.ReviewBody;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

/**
 * Converts token Amazon .result files to match new formating
 *
 */
public class ResultConverter
{
	@Option(name="-o",aliases = { "--output" },metaVar="file",usage="output file", required=true)
	private File outputFile;

	@Option(name="-r",aliases = { "--resultFile" },metaVar="file",usage="AMT .result file", required=true)
	private File resultFile;

	@Option(name="-i",aliases = { "--inputXMI" },metaVar="dir",usage="input XMI directory", required=true)
	private File inputXMI;

    void readData()
        throws IOException
    {
        String[] header = getHeader();
        Reader in = new FileReader(resultFile);

        Iterable<CSVRecord> records = CSVFormat.TDF.withQuote('"').withHeader().parse(in);

        Writer out = new FileWriter(outputFile);
        CSVPrinter printer = CSVFormat.TDF.withQuote('"').withQuoteMode(QuoteMode.ALL)
                .withHeader(header).print(out);

        for (CSVRecord record : records) {
            if (record.getRecordNumber() != 1) {
                printer.println();
            }
            if ((record.getRecordNumber() % 100) == 0) {
                System.err.println("Processed " + record.getRecordNumber() + " records");
            }
            String reviewID = record.get("annotation").replace(".html", "").replaceAll("(.*)-", "");

            String reviewPath = inputXMI.getPath() + "/" + reviewID + ".xmi";

            int bodyBegin = getBodyBegin(reviewPath);

            String rawAnswer = record.get("Answer.tokens");
            String textInput = record.get("Answer.textinput");

            String result;

            if (textInput.isEmpty()) {
                result = parseRawAnswer(rawAnswer, bodyBegin);
            }
            else {
                result = "";
            }

            int size = record.size();
            for (int i = 0; i < size; i++) {
                // System.out.println(record.get(i));
                if (record.get(i).equals(rawAnswer)) {
                    printer.print(result);
                }
                else {
                    printer.print(record.get(i));
                }
            }
        }
        in.close();
    }

	private String[] getHeader() throws IOException {
		Reader in = new FileReader(resultFile);

		BufferedReader br = new BufferedReader(in);

		String[] header = br.readLine().split("\t");
		for(int i=0; i < header.length; i++){
			header[i] = header[i].replaceAll("\"", "");
		}

		br.close();
		return header;
	}

	private String parseRawAnswer(String rawAnswer, int bodyBegin) {
		StringBuilder result = new StringBuilder();


		//
		if(!rawAnswer.contains("[")){
			result.append(convertTokenPositions(rawAnswer, bodyBegin));
		}
		else{
			String [] entries =  rawAnswer.split("\\[");
			for(int i=1; i < entries.length; i++){
				//If Element contains no { it is nonsense (discard it)
				if(!entries[i].contains("{")) {
                    continue;
                }

				//Fetch the type of the entry and the entry
				String type = entries[i].substring(0, entries[i].indexOf("{"));
				String tokens = entries[i].substring(entries[i].indexOf("{")+1, entries[i].indexOf("}"));

				//Remove trailing comma if there is one
				if(tokens.endsWith(",")) {
                    tokens = tokens.substring(0, tokens.length()-1);
                }

				//find position and write new entry
				String newTokens = convertTokenPositions(tokens, bodyBegin);
				String newEntry = "[" + type + "{" + newTokens + "}"+"]";

				//Add leading comma to all entries but the first
				if(result.length() != 0 ){
					result.append(",");
				}
				result.append(newEntry);
			}
		}
		return result.toString();
	}

	/**
	 * Converts all tokenPositions into the new formats without title- and reviewTokens
	 *
	 * @param tokensString The String containing only(!) the tokens separated by commas
	 * @param bodyBegin The begin of the body part of the review
	 * @return
	 */
	private String convertTokenPositions(String tokensString, int bodyBegin) {
		String [] tokens =  tokensString.split(",");
		for(int i=0; i < tokens.length; i++){
			tokens[i] = convertTokenPosition(tokens[i], bodyBegin);
		}
		String result = String.join(",", tokens);
		return result;
	}

	/**
	 * Converts one token into the new token type. Also calculates the position in the new document.
	 *
	 * @param token
	 * @param bodyBegin
	 * @return
	 */
	private String convertTokenPosition(String token, int bodyBegin) {
		if(token.contains("_")){
			String convertedToken;

			Integer pos = Integer.parseInt(token.split("_")[1]);
			if(token.startsWith("titleToken") || token.startsWith("token")){
				convertedToken = "token_"+pos;
			}
			else{
				convertedToken = "token_"+(pos + bodyBegin);
			}
			return convertedToken;
		}
        else {
            return token;
        }
	}

	/**
	 * Get the begin of the body based on the path to the xmi file containing the new reviews (title in text)
	 *
	 * @param reviewPath
	 * @return
	 */
	int getBodyBegin(String reviewPath){
		File xmiFile = new File(reviewPath);
		try {
			JCas aJCas = JCasFactory.createJCas();
            if(System.getProperty("os.name").contains("Windows")){
            	String path = FileUtils.readFileToString(xmiFile);
            	if(path.startsWith("..")){
            		xmiFile = new File(inputXMI.getPath() + "/" + path);
            	}
            }
            CasIOUtil.readJCas(aJCas, xmiFile);
            ReviewBody title = JCasUtil.selectSingle(aJCas,ReviewBody.class);
            return title.getBegin()+1;
        } catch (IOException | UIMAException e) {
				System.err.println("Error while reading XMI file" + xmiFile.getAbsolutePath());
				System.exit(-1);
				return -1;
		}
	}

    public static void main(String[] args)
            throws ParseException, IOException
    {
    	new ResultConverter().doMain(args);
    }

	private void doMain(String[] args) throws IOException {
		CmdLineParser parser = new CmdLineParser(this);
		try {
            // parse the arguments.
            parser.parseArgument(args);
            readData();
        } catch( CmdLineException e ) {
            System.err.println(e.getMessage());
            System.err.println("java "+this.getClass().getSimpleName()+" [options...] arguments...");
            parser.printUsage(System.err);
            System.err.println();
            return;
        }
	}

}
