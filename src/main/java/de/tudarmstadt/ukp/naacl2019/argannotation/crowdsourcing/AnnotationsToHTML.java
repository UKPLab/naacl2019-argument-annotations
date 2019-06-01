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
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.xpath.XPathExpressionException;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.uima.UIMAException;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.fit.factory.JCasFactory;
import org.apache.uima.fit.util.CasIOUtil;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;
import org.codehaus.plexus.util.FileUtils;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.xml.sax.SAXException;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;

import de.tudarmstadt.ukp.dkpro.argumentation.types.ArgumentComponent;
import de.tudarmstadt.ukp.dkpro.argumentation.types.ArgumentRelation;
import de.tudarmstadt.ukp.dkpro.argumentation.types.Claim;
import de.tudarmstadt.ukp.dkpro.argumentation.types.MajorClaim;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token;

/**
 * Reads in a set of XMIs and an AMT results file and outputs the annotations in
 * a single HTML file for easy inspection
 *
 *
 */
public class AnnotationsToHTML
{

    static class Assignment
        implements Comparable<Assignment>
    {
        String hitID; // Review ID would also be OK here
        String reviewID;
        String assignmentStatus;
        String workerID;
        double competence;
        boolean intext;
        List<AnnotationSpan> spans = new ArrayList<AnnotationSpan>();
        String text = "";
        //String annotationType;
        /*String reviewTextBeforeAnnotation, reviewTextAnnotation,
                reviewTextAfterAnnotation;*/

        static DecimalFormat df = new DecimalFormat("0.0000");

        public String formatCompetence()
        {
            return df.format(competence);
        }

        @Override
        public int compareTo(Assignment anotherAssignment)
        {
            int compareHitID = anotherAssignment.hitID.compareTo(hitID);
            if (compareHitID == 0) {
                return anotherAssignment.workerID.compareTo(workerID);
            }
            else {
                return compareHitID;
            }
        }
    }

    private static final String TEMPLATE_FILENAME = "src/main/resources/templates/summary_annotation.html";

    @Option(name="-o",aliases = { "--output" },metaVar="file",usage="output HTML file", required=true)
	private File outputFile;

	@Option(name="-i",aliases = { "--inputXMI" },metaVar="dir",usage="input XMI directory", required=true)
	private File inputXMI;

	@Option(name="-r",aliases = { "--resultFile" },metaVar="file",usage="AMT .result file", required=true)
	private File resultFile;

	@Option(name="-c",aliases = { "--competence" },metaVar="file",usage="worker competence file", required=false)
	private File competence;

	@Option(name="-s",aliases = { "--submittedOnly" },usage="show only 'Submitted' and 'Gold' assignments")
    private boolean submittedOnly = false;

	@Option(name="-g",aliases = { "--hideGold" },usage="hide 'Gold' assignments")
    private boolean hideGold = false;

	@Option(name="-t",aliases = { "--annotationType" },metaVar="char",usage="m, c, or p for major claim, claim, or premise annotation", required=true)
	private String annotationType;

	public static void main(String[] args)
            throws Exception
    {
		new AnnotationsToHTML().doMain(args);

    }

    private void doMain(String[] args) throws UIMAException, IOException, InvalidKeyException, NoSuchAlgorithmException, XPathExpressionException, SAXException, ParserConfigurationException, TransformerException
    {
    	CmdLineParser parser = new CmdLineParser(this);
		try {
            // parse the arguments.
            parser.parseArgument(args);
            // Read AMT results file
            Set<Assignment> assignments = readAssignments();
            System.err.println("Read results for " + assignments.size() + " assignments.");

            addXMIcontext(assignments);

            // Read in MACE competence scores
            if (competence != null) {
                System.err.println("Reading MACE competence scores...");
                addMACEcompetenceScores(assignments);
            }

            // Read in MACE gold standard
            if (!hideGold) {
                System.err.println("Reading MACE gold standard...");
                addMACEgoldStandard(assignments);
                addGoldStandard(assignments);
            }

            createAssignmentTexts(assignments);

            // Render Mustache template to generate HTML table
            System.err.println("Rendering template...");
            BufferedReader templateReader = new BufferedReader(
                    new FileReader(TEMPLATE_FILENAME));
            DefaultMustacheFactory mf = new DefaultMustacheFactory();
            Mustache mustache = mf.compile(templateReader, "summaryTemplate");
            templateReader.close();
            Writer writer = new FileWriter(outputFile);
            mustache.execute(writer, assignments);
            writer.close();
        } catch( CmdLineException e ) {
            System.err.println(e.getMessage());
            System.err.println("java "+this.getClass().getSimpleName()+" [options...] arguments...");
            parser.printUsage(System.err);
            System.err.println();
            return;
        }
    }

    /**
     * Creates Assignment Texts of all assignments
     *
     * @param assignments
     */
    private void createAssignmentTexts(Set<Assignment> assignments) {
		for(Assignment assignment: assignments){
			if(assignment.intext && !assignment.spans.isEmpty()){
				StringBuilder tmptext = new StringBuilder();
				Collections.sort(assignment.spans);
				int i=0;
				for(AnnotationSpan span: assignment.spans){
                    if (span.gold == true && !"gold".equals(assignment.workerID)
                            && ((annotationType.equals("m") && span.type.equals("MajorClaim"))
                                    || (annotationType.equals("c") && span.type.equals("Claim"))
                                    || (annotationType.equals("p")
                                            && span.type.equals("Premise")))) {
                        continue;
                    }
                    if (i <= span.begin && span.begin <= assignment.text.length()) {
                        tmptext.append(assignment.text.substring(i, span.begin));
                        tmptext.append(
                                "<span class=\"intextannotation " + span.type + " " + span.stance
                                        + "\">" + assignment.text.substring(span.begin, span.end));
                        // Handling for pairs of claims / premises
                        if (span.pairing != -1) {
                            tmptext.append("<sub>" + span.pairing + "</sub>");
                        }
                        tmptext.append("</span>");
                        i = span.end;
                    }
				}
				tmptext.append(assignment.text.substring(i));
				assignment.text = tmptext.toString();
			}
		}
	}

	class AnnotationLengthComparator implements Comparator<Annotation> {
        @Override
        public int compare(Annotation a, Annotation b) {
          return new Integer(b.getCoveredText().length()).compareTo(a.getCoveredText().length());
        }
      }


    /**
     * Adds the gold Standard to assignments of Type Claim or Premise
     *
     * @param assignments
     * @throws UIMAException
     * @throws IOException
     */
	private void addGoldStandard(Set<Assignment> assignments) throws UIMAException, IOException {
		for (Assignment assignment: assignments) {
			JCas aJCas = loadJCas(assignment);
			int claimIndex = 0;
			for (ArgumentComponent arg: JCasUtil.select(aJCas, ArgumentComponent.class)){
				if(!annotationType.equals("m") ||
						(annotationType.equals("c") && arg.getType().getShortName().equals("MajorClaim")) ||
						(annotationType.equals("p") && !arg.getType().getShortName().equals("Premise"))){

					String stance = "";

	                for(ArgumentRelation rel: JCasUtil.select(aJCas, ArgumentRelation.class)){
	                	if(rel.getSource().equals(arg)){
	                		stance = rel.getType().getShortName();
	                	}
	                }
	                AnnotationSpan span = new AnnotationSpan(arg.getType().getShortName(), stance, true, arg.getBegin(), arg.getEnd());
	                if(arg.getType().getShortName().equals("Claim")){
	                	span.pairing = claimIndex++;
	                }
					assignment.spans.add(span);
				}
			}
		}
	}


	/**
     * <p>
     * Add the gold-standard annotations produced by MACE.
     * </p>
     *
     * @param assignments
     *            The set of assignments to which to add gold-standard
     *            annotations.
	 * @throws UIMAException
	 * @throws IOException
     */
    private void addMACEgoldStandard(Set<Assignment> assignments) throws UIMAException, IOException
    {
        HashMap<String, Assignment> goldAssignments = new HashMap<String, Assignment>();

        for (Assignment assignment : assignments) {
            if (goldAssignments.containsKey(assignment.hitID)) {
                continue;
            }
            JCas aJCas = loadJCas(assignment);

            if (JCasUtil.exists(aJCas, MajorClaim.class)) {
            	Collection<ArgumentComponent> annotations = JCasUtil.select(aJCas,ArgumentComponent.class);

            	for(ArgumentComponent annotation: annotations){
            		String type = annotation.getType().getShortName();
            		if(type.equals("MajorClaim") && annotationType.equals("m") ||
        				type.equals("Claim") && annotationType.equals("c") ||
        				type.equals("Premise") && annotationType.equals("p")){

            			Assignment goldAssignment;
            			if(goldAssignments.containsKey(assignment.hitID)){
            				goldAssignment = goldAssignments.get(assignment.hitID);
            			}
            			else{
            				goldAssignment =  new Assignment();
            			}


                        goldAssignment.hitID = assignment.hitID;
                        goldAssignment.reviewID = assignment.reviewID;
                        goldAssignment.workerID = "gold";
                        goldAssignment.assignmentStatus = "gold";
                        goldAssignment.competence = 1.0;

	                    int startIndex = annotation.getBegin();
	                    int endIndex = annotation.getEnd();

	                    goldAssignment.intext = true;
	                    goldAssignment.text = aJCas.getDocumentText();

	                    Properties annotationProps =  new Properties();
	                    annotationProps.load(new ByteArrayInputStream(annotation.getProperties().getBytes()));

	                    String stance = "";
	                    ArgumentComponent target = null;
	                    for(ArgumentRelation rel: JCasUtil.select(aJCas, ArgumentRelation.class)){
	                    	if(rel.getSource().equals(annotation)){
	                    		stance = rel.getType().getShortName();
	                    		target = (ArgumentComponent) rel.getTarget();
	                    	}
	                    }
	                    AnnotationSpan span = new AnnotationSpan(type, stance, true, startIndex, endIndex);

	                    if(target != null && type.equals("Premise")){
	                    	int i = 0;
	                    	for(Claim claim: JCasUtil.select(aJCas, Claim.class)){
	                    		if(claim.equals(target)){
	                    			span.pairing = i;
	                    		}
	                    		i++;
	                    	}
	                    }

	                    goldAssignment.spans.add(span);

	                    goldAssignments.put(goldAssignment.hitID, goldAssignment);
                	}
            	}
            }
            else {
                Assignment goldAssignment = new Assignment();
                goldAssignment.hitID = assignment.hitID;
                goldAssignment.workerID = "gold";
                goldAssignment.assignmentStatus = "gold";
                goldAssignment.competence = 1.0;
                goldAssignment.reviewID = assignment.reviewID;

                goldAssignment.intext = false;
                goldAssignment.text = "(no gold standard annotation)";
                goldAssignments.put(goldAssignment.hitID, goldAssignment);
            }
        }
        assignments.addAll(goldAssignments.values());
    }

    private JCas loadJCas(Assignment assignment) throws IOException, UIMAException{
    	String reviewPath = inputXMI.getPath() + "/"
                + assignment.reviewID + ".xmi";
        File xmiFile = new File(reviewPath);
        if (xmiFile.exists()) {

            JCas aJCas = JCasFactory.createJCas();
            if(System.getProperty("os.name").contains("Windows")){
            	String path = FileUtils.fileRead(xmiFile);
            	if(path.startsWith("..")){
            		xmiFile = new File(inputXMI.getPath() + "/" + path);
            	}
            }
            CasIOUtil.readJCas(aJCas, xmiFile);
            return aJCas;
        }
		return null;
    }

    /**
     * <p>
     * Assigns the worker competence to each assignment based on the MACE
     * output.
     * </p>
     *
     * @param assignments
     *            The set of assignments to which to apply competence values.
     * @throws IOException
     */
    private void addMACEcompetenceScores(Set<Assignment> assignments) throws IOException
    {
        Map<String, Double> competenceMap = new HashMap<String, Double>();
        Reader in = new FileReader(competence);
        Iterable<CSVRecord> records = CSVFormat.RFC4180.parse(in);
        for (CSVRecord record : records) {
            if (record.size() > 1) {
                competenceMap.put(record.get(0),
                        Double.parseDouble(record.get(1)));
            }
        }
        for (Assignment assignment : assignments) {
            Double competence = competenceMap.get(assignment.workerID);
            if (competence == null) {
                assignment.competence = 0.0;
            }
            else {
                assignment.competence = competence;
            }
        }
    }

    /**
     * <p>
     * Reads AMT results file.
     * </p>
     *
     * @param filename
     *            Result filename
     * @return A set of {@link Assignment}s read from the result file.
     * @throws IOException
     */
    private Set<Assignment> readAssignments()
        throws IOException
    {
        Set<Assignment> assignments = new TreeSet<Assignment>();
        Reader in = new BufferedReader(new FileReader(resultFile));
        String type = "UNKNOWN";
        String stance = "";
        if(annotationType.equals("m")){
        	type = "MajorClaim";
        }
        else if(annotationType.equals("c")){
        	type = "Claim";
        }
        else if(annotationType.equals("p")){
        	type = "Premise";
        }
        Iterable<CSVRecord> records = CSVFormat.TDF.withQuote('"').withHeader().parse(in);
        for (CSVRecord record : records) {
            if (record.get("workerid").isEmpty()) {
                continue;
            }
            if (record.get("assignmentstatus").equals("Rejected")
                    || record.get("reject").equals("y") ) {
                continue;
            }
            if (submittedOnly == true
                    && !record.get("assignmentstatus").equals("Submitted")) {
                continue;
            }
            Collection<String> answers = new ArrayList<String>();
            String rawAnswer = record.get("Answer.tokens");
            if(rawAnswer.contains("[")){
            	while(rawAnswer.contains("[")){
            		if(rawAnswer.indexOf("}")!=-1){
	            		String tmpAnswer = rawAnswer.substring(rawAnswer.indexOf("["),rawAnswer.indexOf("}")+1);
	            		rawAnswer = rawAnswer.replace(tmpAnswer, "");
	            		answers.add(tmpAnswer);
            		}
            		else{
            			break;
            		}
            	}
            }
            else{
            	answers.add(rawAnswer);
            }

            Assignment assignment = new Assignment();

            assignment.hitID = record.get("hitid");
            assignment.workerID = record.get("workerid");
            assignment.assignmentStatus = record.get("assignmentstatus");
            assignment.reviewID = record.get("annotation").replace(".html", "")
                    .replaceAll("(.*)-", "");
            String intext = record.get("Answer.intext");
            if ("false".equals(intext) || "nonsense".equals(intext)) {
                assignment.intext = false;
                assignment.text = record
                        .get("Answer.textinput");
            }
            else if ("true".equals(intext)) {
                assignment.intext = true;
            } else {
                throw new IllegalArgumentException("Unknown intext value: " + intext);
            }
            if (assignment.intext) {
            	for(String answer: answers){
	            	if(answer.contains("token")){
		                if(answer.contains("butid")){
		                	answer = answer.substring(answer.indexOf("{")+1,answer.indexOf("}"));
		                	stance = "Attack";
		                }
		                else if(answer.contains("becauseid")){
		                	answer = answer.substring(answer.indexOf("{")+1,answer.indexOf("}"));
		                	stance = "Support";
		                }
		                answer = answer.replaceAll("undefined", "").replaceAll(",,", ",").replaceAll("^,", "");

		                int begin;
		                int end;
		                if(answer.contains(",")){
		                	String[] tokens = answer.split(",");
		                	begin = Integer.parseInt(tokens[0].split("_")[1]);
		                	end = Integer.parseInt(tokens[tokens.length-1].split("_")[1]);
		                }
		                else{
		                	begin = Integer.parseInt(answer.split("_")[1]);
		                	end = begin;
		                }
		                AnnotationSpan span = new AnnotationSpan(type, stance, false, begin, end, true);
		                if(annotationType.equals("p")){
			                int target = getTarget(record.get("annotation"));
		                	span.pairing = target;
		                }
		                assignment.spans.add(span);
	            	}
	            }
            }
            assignments.add(assignment);
	    }
        in.close();

        return assignments;
    }

    private int getTarget(String annotation) {
    	String claimNumber = annotation.substring(annotation.indexOf("hit-premise-")+"hit-premise-".length(), annotation.indexOf("-review-"));
		return Integer.parseInt(claimNumber);
	}

	/**
     * Returns the token to a given tokenID. If none is found,
     * the returned result is null
     */
    private Token getToken(JCas aJCas, String tokenId)
    {
        FSIterator<Token> tokenIterator = aJCas.getAllIndexedFS(Token.class);
        tokenIterator.moveToFirst();
            while (tokenIterator.hasNext()) {
                Token token = tokenIterator.get();
                if (token.getId().equals(tokenId)) {
                    return token;
                }
                tokenIterator.moveToNext();
            }
            return null;
    }

    /**
     * Returns the token to a given offset. If none is found,
     * the returned result is null
     */
    private Token getToken(JCas aJCas, int offset)
    {
    	return getToken(aJCas, "token_"+offset);
    }

    /**
     * <p>
     * Add the tokens from the xmi based on the reviewID
     * </p>
     *
     * @param assignments
     *            The set of assignments to which to add the tokens.
     * @throws IOException
     * @throws UIMAException
     */
    private void addXMIcontext(Set<Assignment> assignments) throws UIMAException, IOException
    {
        int i = 0;
        for (Assignment assignment : assignments) {
            if (++i % 100 == 0) {
                System.err.println("Added contexts for " + i + " of "
                        + assignments.size() + " assignments");
            }
            String reviewPath = inputXMI.getPath() + "/"
                    + assignment.reviewID + ".xmi";
            File xmiFile = new File(reviewPath);
            if (xmiFile.exists()) {
                JCas aJCas = loadJCas(assignment);
                if(assignment.intext){
                	assignment.text = aJCas.getDocumentText();
                }
                if (assignment.intext && !assignment.spans.isEmpty()) {
                    int endIndex = 0;

                    for(int j = 0; j < assignment.spans.size(); j++){
                    	if(assignment.spans.get(j).tmpEnd){
                    		endIndex = getToken(aJCas, assignment.spans.get(j).end).getEnd();
                    		assignment.spans.get(j).end = endIndex;
                    		assignment.spans.get(j).tmpEnd = false;
                    	}
                    }
                }
            }
        }
    }


}
