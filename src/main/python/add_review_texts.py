#!/usr/bin/env python3

# Copyright 2019
# Ubiquitous Knowledge Processing (UKP) Lab
# Technische Universität Darmstadt
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

import os
import gzip
import time
from lxml import etree
from shutil import copy2
import argparse
import sys
from html import unescape

##### Requirements: Python 3, gzip, lxml

####################
# Script to add the review texts back into the stripped files (naacl2019-argannotation-data)
####################

def parse(path): 
    g = gzip.open(path, 'r') 
    for l in g: 
        yield eval(l)

def main(argv):
    start = time.time()

    input_dir = argv["input"]
    output_dir = argv["output"]
    complete_reviews_dir= argv["dataset"]

    ### sanity-check dirs:
    if not os.path.isdir(output_dir):
        os.mkdir(output_dir)
        print("Created Output directory: ", output_dir)

    if(os.path.isdir(input_dir)):
        files = [f for f in os.listdir(input_dir) if os.path.isfile(os.path.join(input_dir, f)) and ".xmi" in f]
        files_asins = set([f.split("_")[0] for f in files])
        print("Found ", len(files) , "input review files to add the review text to.")
        if len(files) != 982:
            print("Warning: You are using ", len(files), " xmi files as input, the complete dataset would have 982.")
    else:
        print("Error - Directory not found:")
        print("The input directory for the review files does not exist. Please check your arguments.")
        sys.exit()

    if not os.path.isfile(complete_reviews_dir):
        print("Error - File not found:")
        print("The McAuley dataset file ('*.json.gz') could not be found. Please check your arguments.")
        sys.exit()

    try: 
        # Read in the reviews file
        all_reviews = parse(complete_reviews_dir)

        # counters for statistics
        counter_found = 0
        counter_notfound = 0

        # loop through all reviews
        for review in all_reviews:
            # check if the review was annotated
            if(review["asin"] in files_asins):
                try:
                    #Each XMI filename has the following scheme: `asin_reviewerID_unixReviewTime.xmi`
                    review_annotation_filename = review["asin"] + "_" + review["reviewerID"] + "_" + str(review["unixReviewTime"]) + ".xmi"
                except Exception as e:
                    pass
                if(review_annotation_filename in files):
                    counter_found += 1
                    print("Added review text to ", counter_found , "/", len(files), "reviews.")
                else:
                    counter_notfound += 1
                    continue
            else:
                counter_notfound += 1
                continue
            tree = etree.parse(os.path.join(input_dir,review_annotation_filename))
            root = tree.getroot()
            try:
                cas_sofa = root.find('{http:///uima/cas.ecore}Sofa')
                review_text = review["summary"] +  "\n\n" + review["reviewText"] 
                review_text = unescape(review_text)
                cas_sofa.set("sofaString", review_text)
                tree.write(os.path.join(output_dir, review_annotation_filename), encoding="utf-8", xml_declaration=True)
            except Exception as e:
                print("The element 'cas:Sofa' could not be found in the xmi input files. Please check that you are using the correct files.")
                sys.exit()

    except Exception as e:
        print("An error occured: ")
        print(repr(e))

    # copy typeset file from input directory to output directory
    copy2(os.path.join(input_dir, "typesystem.xml"), output_dir)

    time_taken = time.time() - start
    print("Done! - Added review texts to ", counter_found, " reviews in ", time_taken , "seconds.")

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description='Fill the review text back into the stripped reviews. Takes about one hour to run.')
    parser.add_argument("-i", "--input", help="the name of a directory containing the 'stripped' version of the XMI data set")
    parser.add_argument("-d", "--dataset", help= "the path of the McAuley data set (`*.json.gz`)")
    parser.add_argument("-o", "--output", help="the name or path of an output directory")
    args = parser.parse_args()
    if len(sys.argv) == 1:
        parser.print_help()
        sys.exit()
    main(vars(args))
