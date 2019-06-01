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

import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;

import de.tudarmstadt.ukp.dkpro.argumentation.types.ArgumentRelation;
import de.tudarmstadt.ukp.dkpro.argumentation.types.Attack;
import de.tudarmstadt.ukp.dkpro.argumentation.types.Claim;
import de.tudarmstadt.ukp.dkpro.argumentation.types.MajorClaim;
import de.tudarmstadt.ukp.dkpro.argumentation.types.Support;

public class ClaimRelationAdder extends JCasAnnotator_ImplBase{

	@Override
	public void process(JCas aJCas) throws AnalysisEngineProcessException {
		if(JCasUtil.select(aJCas, ArgumentRelation.class).isEmpty()){
			for(Claim claim: JCasUtil.select(aJCas, Claim.class)){
				ArgumentRelation relation = null;
				if(claim.getStance().equals("support")){
					relation = new Support(aJCas);
				}
				else if (claim.getStance().equals("attack")){
					relation = new Attack(aJCas);
				}
				if(relation != null){
					relation.setSource(claim);
		            MajorClaim majorClaim = JCasUtil.selectSingle(aJCas, MajorClaim.class);
		            relation.setTarget(majorClaim);
		            relation.addToIndexes(aJCas);
				}
			}
		}
		else{
			System.err.println("Results already converted");
			System.exit(-1);
		}
	}

}
