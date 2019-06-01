# Gold standard estimation
  
In each stage of the data collection process (major claims, claims,
and premises), we need to aggregate the annotations from multiple
workers by running the MaceGoldEstimator.class. This class can be run
with the following options:

* `-i <dir>` directory containing input XMI files
* `-o <dir>` directory to which the resulting XMI files will be
  written
* `-r <file>` AMT `.result` file
* `-w <dir>` directory for MACE to output its predictions
* `-s <dir>` directory for MACE to output its split input files
* `-t [m,c,p]` annotation step the results are from (**m**ajor claims,
  **c**laims, or **p**remises)

Example:
```
-i reviewdata/en/crowd/unannotated/all/ 
-o reviewdata/en/crowd/majorclaim/all/ 
-r mechanicalTurk/majorclaim_crowd_en_sample/majorclaim_crowd_en_sample.result 
-s target/mace_output/major_claim_training_en_sample 
-w target/mace_working/major_claim_training_en_sample 
-t m
```

The files in the `-w` MACE output directory include worker competence
estimates in `overallWorkerCompetence.csv`. For the claim annotation
step, there are separate competence estimates for span annotation (how
good the workers are at selecting the correct spans) and stance
recognition (how reliable the workers are at choosing between "attack"
and "support" in relation to the major claim). Competence can be
interpreted as the probability that a worker gets the right answer --
you can therefore use the `overallCompetence.csv` to deselect workers
below a particular threshold.

After running the `MaceGoldEstimator`, you can examine the gold and
worker annotations using `AnnotationsToHTML`. This class produces HTML
files that show the gold and worker annotations for each review. Run
it with the following options:

* `-i <dir>` directory containing input XMI files
* `-o <file>` output HTML file
* `-r <file>` AMT `.result` file
* `-c <file>` worker competence file (`overallWorkerCompetence.csv`)
* `-s` exclude assignments that have already been rejected or approved
* `-t [m,c,p]` annotation step the results are from (**m**ajor claims,
  **c**laims, or **p**remises)

Example:
```
-r mechanicalTurk/majorclaim_crowd_en/majorclaim_crowd_en_merged.result
-o target/summary_annotation.html
-i reviewdata/en/crowd/majorclaim/all
-c reviewdata/en/crowd/majorclaim/overallWorkerCompetence.csv
-s
```
