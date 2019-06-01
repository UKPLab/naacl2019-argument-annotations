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

public class Review implements Comparable<Review>{
	private String reviewerID;
	private String asin;
	private String reviewerName;
	private Integer[] helpful;
	private String reviewText;
	private Double overall;
	private String summary;
	private Integer unixReviewTime;
	private String reviewTime;
	private Product product;

	public Product getProduct() {
		return product;
	}
	public String getReviewerID() {
		return reviewerID;
	}
	public String getAsin() {
		return asin;
	}
	public String getReviewerName() {
		return reviewerName;
	}
	public Integer[] getHelpful() {
		return helpful;
	}
	public String getReviewText() {
		return reviewText;
	}
	public void setProduct(Product product) {
		this.product = product;
	}
	public Double getOverall() {
		return overall;
	}
	public String getSummary() {
		return summary;
	}
	public Integer getUnixReviewTime() {
		return unixReviewTime;
	}
	public String getReviewTime() {
		return reviewTime;
	}

	@Override
	public int compareTo(Review o) {
		return o.getHelpful()[0].compareTo(helpful[0]);
	}
}
