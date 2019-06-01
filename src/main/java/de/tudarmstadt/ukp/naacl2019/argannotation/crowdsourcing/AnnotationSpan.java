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

public class AnnotationSpan implements Comparable<AnnotationSpan>{

	String type = null;
	String stance = null;
	boolean gold = false;
	int begin;
	int end;
	boolean tmpEnd = false;
	int pairing = -1;

	public AnnotationSpan(String type, String stance, boolean gold, int begin, int end) {
		super();
		this.type = type;
		this.stance = stance;
		this.begin = begin;
		this.end = end;
		this.gold = gold;
	}

	public AnnotationSpan(String type, String stance, boolean gold, int begin, int end, boolean tmpEnd) {
		super();
		this.type = type;
		this.stance = stance;
		this.begin = begin;
		this.end = end;
		this.tmpEnd = tmpEnd;
        this.gold = gold;
	}

	@Override
    public int compareTo(AnnotationSpan anotherSpan)
    {
		return Integer.compare(begin, anotherSpan.begin);
    }
}
