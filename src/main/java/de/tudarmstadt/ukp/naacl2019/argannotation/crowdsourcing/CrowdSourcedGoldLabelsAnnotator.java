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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.component.JCasAnnotator_ImplBase;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;

import de.tudarmstadt.ukp.dkpro.core.api.metadata.type.DocumentMetaData;

/**
 */
public abstract class CrowdSourcedGoldLabelsAnnotator
        extends JCasAnnotator_ImplBase
{
    /**
     * Directory with files that contain estimated gold labels for the given task; each XMI
     * should have its respective gold data file
     */
    public static final String ESTIMATED_GOLD_DATA_FILES_LOCATION = "estimatedGoldDataFilesLocation";
    @ConfigurationParameter(name = ESTIMATED_GOLD_DATA_FILES_LOCATION, mandatory = true)
    File estimatedGoldDataFilesLocation;

    /**
     * Extension of files with estimated gold data; default: .xml
     */
    public static final String ESTIMATED_GOLD_DATA_FILE_EXTENSION = "estimatedGoldDataFileExtension";
    @ConfigurationParameter(name = ESTIMATED_GOLD_DATA_FILE_EXTENSION, defaultValue = ".xml")
    String estimatedGoldDataFileExtension;

    @Override
    public void initialize(UimaContext context)
            throws ResourceInitializationException
    {
        super.initialize(context);

        if (!estimatedGoldDataFilesLocation.exists()) {
            throw new ResourceInitializationException(
                    new IOException(
                            "Directory " + estimatedGoldDataFilesLocation.getAbsolutePath() +
                                    " does not exist"));
        }
    }

    @Override
    public void process(JCas jCas)
            throws AnalysisEngineProcessException
    {
        // find the corresponding file
        String id = DocumentMetaData.get(jCas).getDocumentId();

        // id + .xml
        File goldDataFile = new File(estimatedGoldDataFilesLocation,
                id + estimatedGoldDataFileExtension);

        if (!goldDataFile.exists()) {
            throw new AnalysisEngineProcessException(
                    new FileNotFoundException("File not found: " + goldDataFile));
        }

        // TODO now get what you want from the file and annotate in jCas
        annotateJCasWithGoldData(jCas, goldDataFile);
    }

    /**
     * Reads the estimated gold labels from the file and annotates the jCas accordingly
     *
     * @param jCas         jCas
     * @param goldDataFile file with estimated gold data
     */
    protected abstract void annotateJCasWithGoldData(JCas jCas, File goldDataFile);
}
