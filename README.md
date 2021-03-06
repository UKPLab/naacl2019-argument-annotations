# A streamlined method for sourcing discourse-level argumentation annotations from the crowd

This project contains source code for a discourse-level argument
annotation pipeline, as well as crowdsourced data applied to a subset
of Julian McAuley's [Amazon product
data](http://jmcauley.ucsd.edu/data/amazon/index.html).

If you reuse this software or data, please use the following citation:

> Tristan Miller, Maria Sukhareva, and Iryna Gurevych. [A streamlined
> method for sourcing discourse-level argumentation annotations from
> the crowd](https://www.aclweb.org/anthology/N19-1177). In
> [Proceedings of the 17th Annual Conference of the North American
> Chapter of the Association for Computational Linguistics: Human
> Language Technologies (NAACL-HLT 2019)](https://naacl2019.org/),
> volume 1, pages 1790–1796, June 2019. ISBN 978-1-950737-13-0.

> **Abstract:** The study of argumentation and the development of
> argument mining tools depends on the availability of annotated data,
> which is challenging to obtain in sufficient quantity and
> quality. We present a method that breaks down a popular but
> relatively complex discourse-level argument annotation scheme into a
> simpler, iterative procedure that can be applied even by untrained
> annotators. We apply this method in a crowdsourcing setup and report
> on the reliability of the annotations obtained. The source code for
> a tool implementing our annotation method, as well as the sample
> data we obtained (4909 gold-standard annotations across 982
> documents), are freely released to the research community. These are
> intended to serve the needs of qualitative research into
> argumentation, as well as of data-driven approaches to argument
> mining.

```
@inproceedings{miller2019streamlined,
   author       = {Tristan Miller and Maria Sukhareva and Iryna Gurevych},
   title        = {A Streamlined Method for Sourcing Discourse-level
                   Argumentation Annotations from the Crowd},
   booktitle    = {Proceedings of the 17th Annual Conference of the North
                   American Chapter of the Association for Computational
		   Linguistics: Human Language Technologies
		   (NAACL-HLT 2019)},
   volume       = {1},
   pages        = {1790--1796},
   month        = jun,
   year         = {2019},
   isbn         = {978-1-950737-13-0},
}
```

## Using the data

The argument-annotated review data is contained in the
`data/xmi_stripped` directory. It consists of a set of XML Metadata
Interchange (XMI) files, one per review, containing stand-off UIMA
gold-standard argument annotations corresponding to argument
components (major claims, claims, and premises) and argument relations
(support and attack).  The UIMA type system is given in the
`typesystem.xml` file.  This type system wraps
the
[DKPro Argumentation type system](https://github.com/dkpro/dkpro-argumentation),
which is a superset of the original argumentation scheme described by
Stab and Gurevych (2014).

### Required step: Restore the original review texts

The XMI files cross-reference the original texts from McAuley.
Because the original review texts are not available under a free
licence, we do not include them in our distribution, but we provide a
script for extracting them from the original corpus and merging them
into our XMIs.

To reconstitute the data, you will need a Python 3 interpreter, the
Python 3 lxml library, and
the
["aggressively deduplicated" review data from Julian McAuley](http://jmcauley.ucsd.edu/data/amazon/) (`aggressive_dedup.json.gz`).  You can then run the script as follows:

```
python3 src/main/python/add_review_texts.py --input data/xmi_stripped --output data/xmi --dataset /path/to/aggressive_dedup.json.gz
```

Depending on the speed of your system, expect the process to take
about an hour.  The reconstituted data will be placed in the
`data/xmi` folder.

### Optional step: Convert the data to another format

If XMI files are not suitable for your workflow, you can convert them
to another format.  A sample class for converting the XMI files to a
tab-delimited format (similar to that used for
[CONLL](http://ufal.mff.cuni.cz/conll2009-st/task-description.html))
is available.  After building the software (see below) it can be
invoked as follows:

```
mvn exec:java -Dexec.mainClass="de.tudarmstadt.ukp.naacl2019.argannotation.crowdsourcing.XmiToConllConverter" -Dexec.args="arg0 arg1 ..."
```

Note that this converter is just a demonstration; it is lossy in that
it does not indicate the ID of the major claim that is supported or
attacked by a given claim, nor the ID of the claim that is supported
or attacked by a given premise.


## Building and deploying the software

If you would like to source your own argument annotations, you will
need to build and run our tools, and deploy the Amazon Mechanical Turk
HITs that they generate.

The code to sample and preprocess the McAuley data, to generate the
HITs for Amazon Mechanical Turk, and to postprocess the resulting
annotations, are Java applications built on the UIMA framework, with
several third-party dependencies (uimaFIT, MACE, args4j, DKPro Core,
Apache Commons, etc.)  Some of these dependencies are included in the
source code, but most are not.  We therefore recommend that the
project be built using [Maven](https://maven.apache.org/) using the
included POM; simply run `mvn install` and everything should get
built.  Thereafter, individual tools can be run via `mvn exec:java` --
for example:

```
mvn exec:java -Dexec.mainClass="de.tudarmstadt.ukp.naacl2019.argannotation.preprocessing.Step01DataSampling" -Dexec.args="arg0 arg1 ..."
```

Alternatively, you may find it more convenient to build and run the
tools using a Maven-capable IDE such as Eclipse.

The Amazon Mechanical Turk HITs generated by the aforementioned code
are web applications written in HTML, CSS, and JavaScript.  They must
be deployed to an Amazon Mechanical Turk requester account (in the AMT
sandbox, for example) to be used; see "[Producing and deploying
HITs](doc/hits.md)" for further details.


## Using the software

* System architecture
  * [Flow diagram](doc/argannotation-flow-diagram.draw.io.pdf)
* Instructions
  * [Data sampling and preprocessing](doc/datasampling.md)
  * [Producing and deploying HITs](doc/hits.md)
  * [Gold standard estimation](doc/goldestimation.md)


## Authors

Besides the third-party dependencies (whose authors are credited in
the respective source files), the authors of this software include the
following:

* **[Tristan Miller](https://logological.org/) (corresponding author)**
* Maria Sukhareva
* Can Diehl
* Ji-Ung Lee
* Ivan Habernal
* Edwin Simpson
* Liane Vogel


## Copyright and licensing

Copyright © 2019 Ubiquitous Knowledge Processing Lab, Technische
Universität Darmstadt.

The software contributed by the UKP Lab is licensed under the [Apache
License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0).
Other copyrights apply to parts of this software and are so noted in
the relevant source files.

The annotations and this documentation are licensed under
a
[Creative Commons Attribution 4.0 International License](https://creativecommons.org/licenses/by/4.0/) (CC-BY).

