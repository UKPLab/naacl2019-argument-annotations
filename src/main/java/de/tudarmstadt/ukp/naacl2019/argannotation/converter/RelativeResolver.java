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
import org.apache.commons.io.FileUtils;
import org.apache.uima.UIMAException;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

/**
 * Loads XMIs from path and saves them
 *
 */
public class RelativeResolver {
	@Option(name="-i",aliases = { "--inputXMI" },metaVar="dir",usage="input XMI directory", required=true)
	private File inputXMI;

	@Option(name="-o",aliases = { "--outputXMI" },metaVar="dir",usage="output XMI directory", required=true)
	private File outputXMI;

	public static void main(String[] args)
            throws ParseException, IOException, UIMAException
    {
    	new RelativeResolver().doMain(args);
    }

	private void doMain(String[] args) throws IOException, UIMAException {
		CmdLineParser parser = new CmdLineParser(this);
		try {
            // parse the arguments.
            parser.parseArgument(args);
            resolveRelatives();
        } catch( CmdLineException e ) {
            System.err.println(e.getMessage());
            System.err.println("java "+this.getClass().getSimpleName()+" [options...] arguments...");
            parser.printUsage(System.err);
            System.err.println();
            return;
        }
	}

	private void resolveRelatives() throws IOException {
		if(inputXMI.isDirectory() && outputXMI.isDirectory()){
		for (File xmi: inputXMI.listFiles()){
			if(xmi.getName().endsWith(".xmi")){
				File xmiFile = readXMI(xmi);
				File newXMIFile = new File(outputXMI.getPath()+"/"+xmiFile.getName());
				FileUtils.copyFile(xmiFile, newXMIFile);
			}
		}
		}
	}

	private File readXMI(File xmiFile) throws IOException{
		if(System.getProperty("os.name").contains("Windows")){
        	String path;
				path = FileUtils.readFileToString(xmiFile);
        	if(path.startsWith("..")){
        		xmiFile = new File(inputXMI.getPath() + "/" + path);
        	}
        }
		return xmiFile;
	}
}
