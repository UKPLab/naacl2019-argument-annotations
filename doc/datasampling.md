# Data sampling and preprocessing

To sample reviews from
the [McAuley data set](http://jmcauley.ucsd.edu/data/amazon/), run the
`Step01DataSampling` class. This converts the McAuley loose .json into
.json files, containing only one review each. To do so multiple
commandline parameters are mandatory:

* i -- /path to the input directory containing one .json file from the
  McAuley data set for each category taken into account
* o -- /path to the output directory in which the resulting .xmi files
  should be written to
* p -- /path to the directory which contains the metadata about the
  categories from the input file

Please take care to add metadata-files for each category in the
metadata-directory, otherwise the program will not work properly.

Furthermore the sampling is customizable by optional commandline
parameters:

* n -- The number of reviews to be extracted for a category. If not
  enough reviews satisfy the criterias, less than n reviews are
  extracted. (Default: 2000)
* minl -- The minimum length which is permitted for reviews, shorter
  reviews are filtered. Length is determined by simple whitespace
  segmentation. (Default: 70)
* maxl -- The maximum length which is permitted for reviews, longer
  reviews are filtered. Length is determined by simple whitespace
  segmentation. (Default: 180)
* h -- The minimum amount of helpful votings of the review, reviews
  with less than this number of helpful ratings are
  filtered. (Default: 5)
* r -- The minimum ratio of helpful votings of the review, reviews
  with a lower ratio are filtered. The ratio is calculated by dividing
  the helpful votes by the overall votes for a review. (Default: 0.4)

Because the product information is not contained in the reviews,
acquiring them in the large metadata files is very
time-consuming. Therefore running the sampling at least takes a couple
of hours.
