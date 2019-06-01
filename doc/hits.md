# Generating HITs and deploying to Amazon Mechanical Turk

1. Run `MajorClaimHITProducer`, `ClaimHITProducer`, or
   `PremiseHITProducer` according to whether you want to generate HITs
   for major claim, claim, or premise annotation.  These classes read
   in XMI documents and use them to fill Mustache templates that
   generate HTML pages for use with Amazon Mechanical Turk (AMT).

    You must specify, using the `-i` command-line option, the path of a
    directory containing the XMI files to be input.  You must also
    specify, using `-o`, the path of a directory to output the HTML
    files.  The `-l` option specifies the document language (default
    `en`).  If the `-s` option is used, deployment will be to the AMT
    sandbox server; otherwise deployment will be to production.

    Example:

    ````
    $ mvn exec:java -Dexec.mainClass=de.tudarmstadt.ukp.naacl2019.argannotation.crowdsourcing.majorclaim.MajorClaimHITProducer -Dexec.args="\
      -l en \
      -s \
      -i reviewdata/en/training/unannotated/all \
      -o target/myTask"
    ````

2. Copy the generated HTML files to a directory in your web server.

3. Copy the `css`, `js`, and `fonts` directories to the parent
   directory of the web server location you selected in the previous
   step.

4. Use the AWS Command Line Interface to deploy the HITs, specifying
   the location of the HTML files you uploaded.
