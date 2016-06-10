/*
 * @(#)Re.java       1.0 2016/04/29
 *
 * Copyright (c) 2016 Angelo Borsotti. All Rights Reserved.
 * This computer program is protected under Copyright.
 */

import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.net.*;

/*
Algorithms implemented here, measured and compared:

   - BSP (two implementations)
   - PAT
   - Sulzman 1
   - Earley
   - java.regex (partly implemented, oroginal measured)
   - karper (measured only, and now disabled because it takes 3 days to run)

Testing

  - the first matching of a string takes more time than the others, probably because of jit.
    To overcome this, testspeeds() makes a first spare run.
  - the comparison with java is not entirely fair: java is greedy, the other ones are Posix;
    however, it is done to have an indication.
  - I think that to measure the performance on inputs of increasing lengths, the RE cannot
    be kept constant, i.e. the same for such increasing lengths inputs.
    E.g. RE: a*aaaaaa  inputs: a's. When the input is short, a backtracking parser makes
    few attempts; when the input is larger it makes at least as many attampts as the a's
    ending the RE, and the same it does when the input is even larger, but the time spent
    in matching the tail is constant and is distributed over a larger input, making the
    parser appear faster.
    Here all the tests are done with REs that have an outermost star group so as to overcome
    this as possible.
  - the differences in performance due to the complexity of the RE could appear only when
    the string is ambiguous, and in PAT also when it is not. However, the complexity indicated
    in the Okui paper is only asymptotic; in real cases there can be little difference.
  - these parsers take a lot of time building the tree, while java does not build it, so when
    testing speed the build of the tree is disabled.
  - the code is instrumented to measure also some key parameter:

      - mean and max length of plists, total entries in plists/tokens
      - number of winner comparisons/tokens
      - number of proceedonestep calculations of both kinds / tokens

    The time spent to collect the data of memory usage has been measured and verified that it
    is negligible.

Measurements

  - run the measurements as:
    start /b /wait /affinity 0x1 java -Dfile.encoding=UTF8 -Xmx1200m -Xss10m  -cp .;..\.. Re 2>tmp
  - the time and also the memory are measured
  - the memory counters are instance fields and allow to report the summary of memory
    expended or garbaged for each test case
  - the measure of memory represents what the engine uses for a single match of the string
    or of the sequence of strings
  - to measure performance, a new regex is allocated for each test, otherwise the time for the
    allocation disappears, and also a gc is made first so as to remove the possible expenditure
    of time for a gc in the middle of the test
  - toks/ms and bytes/toks are computed
  - parse time is measured on short and also long strings (obtained by concatenating strings).

Measuring time intervals

  Time values can be taken using nanotime(). However, this is not precise enough for the
  range of values measured here. The cycle counter is used instead.

  Accuracy and precision of the cycle counter

  Accuracy is how small are systematic errors. The cycle counter is accurate since the reading
  of the cycle counter is done with a known number of cycles (I measured 151).
  Precision is how small are random errors: here it is the noise introduced when the cpu runs
  in system mode, and when a process is suspended and then resumed.
  When the process running on the core is suspended and then resumed, the cycle counter continues
  to run, and then if the switch occurs when a measure is being done, the measured value is useless.
  The same occurs if the core is interrupted and an iterrupt service routine executed.
  To cope with these nuisances, The same measure could be run, say, 10 times, the outliers
  discarded and the average of the others used.
  Moreover, the relative deviation could be computed once the outliers have been removed, and the
  maximum rekoned so as to have an idea of the precision of values.
  I have computed the median of the times of the parsing of the same string, and the absolute
  distance of each value from it (in percent of the median). Then, I have rekoned how many such
  distances are within 10%, 20%, ... over all the benchmark.
  The measurements show that out of 400000 measured times, 398929 have a distance from the median
  within 10%, and only 1071 are above 100% (the max relative distance is 177.06).
  This means that in the vast majority of cases the measures are consistent, and only in a few of
  them the effect of interruptions or context switches spoiled the measures.
  Instead of using the median, I run then the each measure 3 times and rekon the lowest values
  measured, which are the ones that have the least disturbance.
  All the measures (e.g. time of parsing strings) are done in sequence, and then restarting from
  the first one are redone, and so on 3 times.
  This is better than running the same measure (e.g. parse time of the matching of a string)
  10 times in a row because if there is a burst of cpu load, this could spoil all the 10 measures,
  while running in sequence the matching of different strings it would spoil just a measure of
  some, but not the same.

      - between a nanotime and one executed immediately after there can be from 2000 to 20000 ns,
        which means that the minimum that I can reliably measure is 200 ms.
        However, here the minimum measure is 3000 ns (i.e. 6000 cycles) which means that nanotime
        is not accurate enough
      - between a getcycle and one executed immediately (after 3 runs of warmup) there are at
        most 151 cycles; the min measure taken is 12000, so it is accurate enough.
        In order to mimimize the amount of random errors (due to process suspension), the lowest
        of a number of the very same parse (same RE and text) is taken. This could still leave
        some errors that would occur randomly on all the measures. Although they would add when
        summing up several measures (to draw the charts), they would not affect the speed since
        the percentage of their occurrence would not change (i.e. in a sum of measures there
        would be a greater number of such errors, but also the number of tokens would proportionally
        increase and their ratio remain the same on average).
        Moreover, here we are not interested in the absolute value of the speed, but to compare
        the speeds of the algoritms, and these speeds would be affected by random errors in the
        same way.
      - each cycle takes 1/2.21 = 0.45 ns, which is consistent with my processor speed of
        roughly 2 GHz
      - I have measured the cycle counter 151 value when each measure has been done, and seen
        that the relative standard deviation is 0.01, so the value is quite precise


Results

  - BSP is constantly faster than all the others (see the charts). BSPP is faster, but it is
    not linear. With specifif REs and texts BSP is much faster than java.regex, and with others
    it is the opposite.
  - BSPP is not linear (but it can be made so reverting to BSP when it becomes quadratic).
  - PAT is faster in compilation than BSP.
  - Sulzmann I: implemented to produce the Sulzmann representation of parse trees, which is
    somehow smaller than the other. It is so slow that there is no point in trying to
    optimize it
  - in some cases Java is faster. I think that this is because it uses a recursive descent
    backtracking algorithm, that is faster when it does not backtrack, i.e. when it succeeds
    at the first attempt (or one of the first).
    Implementing the greedy, when it finds that the first (or one of the first alternatives
    in a group) match and the remaining part of the string matches too, it does not backtrack.
    E.g.: RE: (a|b|aa|aaa|aaaa|aaaaa|aaaaab)*  text: aaaaab: it matches the first "a" in the
    RE as many times as possible, and then the "b". On the other hand, with the:
    RE: (a|aa|aaa|aaaa|aaaaa|aaaaab)*  and the same it must try all the alternatives.
    There are REs for which it makes catastrophic backtracking taking a very long time to match,
    e.g. (a|a)*b with text aaaa... I implemented a backtracking engine to catch these cases
    and avoid to call java.util.regex on them.
  - Karper is very slow on compilation and parsing. I have installed it in a subdirectory of
    re: re\\karper\\ch\\unibe\\scg\\regex and compiled with:
    javac -cp ..\\..\\..\\..;..\\..\\..\\..\\jparsec-2-1.0.jar *.java
    (double \\ are indeed single ones)
    I inserted at first time supervision, but have later seen that the slowness is not in parsing,
    but in compilation.
  - memory: BSPP lowest; then BSP. They differ only on short texts.
  - comparison of states: nr of states, BSP: 31945 BS: 26389 tot RE lengths: 50170, which means
    BSP: 0.64 states/char, BS: 0.53 states/char.

  Notes:

  - the measures with random REs and texts are done in a range of REs and text
    lengths in which the algoritms behave away from their worst asymptotic time (i.e.
    have parse times that are reasoneably short as to be measured)
  - the most frequent use of REs is to match strings that are shorter than 100 characters, so
    even a backtracking engine is good, but at times it behaves very badly
  - consider the use of REs in tools that locate strings in large texts, like, e.g. grep.
    the input is not consumed, but this is not the real use: Grep searches line by line (which
    would be enough to state that it searces small texts) skipping first the charactes that
    cannot begin a match, and then trying a match.
    This, however makes the algorithm O(N^2). Think, e.g. to the input xxx..x and RE x*y
    for which there is a need to try a match at every character.
    A better algorithm for grep is to use the RE (any)*(regex) and make the DFA stop when the
    first final state has been reached.
   

Some benchmarks:

      - http://www.boost.org/doc/libs/1_41_0/libs/regex/doc/gcc-performance.html: text search
        in books, html documents and some example texts
      - http://sljit.sourceforge.net/regex_perf.html: similar to the above one
      - http://lh3lh3.users.sourceforge.net/reb.shtml: search on a long document with 4
        REs to find URIs, emails, etc. It mentions also other benchmarks
      - https://swtch.com/~rsc/regexp/regexp3.html: searches of some REs in random text
      - https://rpubs.com/jonclayden/regex-performance: some patterns in a book

   These are the places that contain REs:

      - http://www.regexlib.com/  this contains a number of cryptic REs, difficult to
            extract
      - ftp://ftp.csx.cam.ac.uk/pub/software/programming/pcre/   this is a collection
            of tests that exercise the engine with a (perhaps) good coverage of all the
            RE constructs, and thus it is not fit for a benchmark
      - https://github.com/google/re2/tree/master/re2/testing  this contains tests fit
            for validation, but not benchmarking, and a generator of REs, but without
            documentation

 - REs are used in several fields. Perhaps it could be nice to provide REs
   and strings that are typical cases of such fields so as to benchmark them and tell what
   algorithms perform best.
   Some typical cases are e.g. password format validation, email addresses validation.
   In some usage fields the compilation time of REs is important, e.g. a search in google.
   However, also in text search a RE is compiled and used several times.

 - There is a question on whether there is a need to support character classes to perform some
   benchmarks. Some benchmarks match strings in a large text: to run them there is a need
   to implement algorithms that find the next match skipping tex that does not match.
   Character classes reduce the complexity of REs: (a|b|c...) is more complex than [a-z]
   and can be handled efficiently by a map that translates an input character in a class
   number. I think that this can be supported easily. Finding the next match could be done
   having the initial state recognize a (any)*. I am not sure that benchmarks for string
   finding add much to the overall performance test with respect to benchmarks that match
   entire strings.
   The treatment of character classes can be done by splitting them in subclassess in such
   a way that a character belongs to only one subclass and then transforming the occurrences
   of classes into alternatives of their component subclasses.
   I dis not implement this here because this is not a text search package.
   The extra features that are added to a core RE engine depend on the context in which
   REs are used. Look for example to the differences between lex and grep (e.g. on character
   classes).
   To parse the REs of some published benchmarks I have then removed the ones that contain
   additional constructs.


Benchmarking

   In order to produce a corpus of REs that contains REs that resemble the ones present in
   published benckmarks I have determined the frequency of constructs of REs in such benchmarks
   and used them to build mine.
   I have measured the occurrence frequenty of constructs, constructs nested in others, number
   of constructs nested in others, etc.

   I produced 100 REs of increasing lengths, and for each 1000 strings of different
   lengths. Then parsed the strings and measured the compilation time and the parse time.
   It is pointless to measure the compilation + parse time because the number of times a
   compiled RE is used to parse strings depends on the application. It is better to measure
   compile time only and parse time only.
   To this some real case could be added (e.g. find matches in some free book).

   I draw charts for increasing lengths so I avoid the problem of giving more importante to
   texts of simple REs (which are more frequent in practice): the performance with lower
   lengths means simply that some charts are more important than others.

   The corpus (samples) are made of 10 buckets, each with 100 REs with similar length, each
   having 10 buckets of texts, each with 10 strings long at most 100*bucket.

   I measured times with texts in the range 0:1000, and texts in the range 0:1000000.
   The lower range significant for the usages of REs in which the texts are rather small,
   and the higher one for the uses in which texts are very large.
   I generated texts in the lower range, and concatenated them to produce the ones in the
   higer range.

   The measures are done for the texts that do not strike the singularities of the algorithms
   (making them take a very long time to execute or aborting for lack or memory).
   This is done by using time supervision, or excluding such texts from the samples, or catching
   memory exhaustion exceptions and marking such cases as invalid measures.

   The following charts are produced:

      - speed vs RE length, one curve for each algorithm, obtained by computing the speed
        of parsing all the texts of the REs in each RE bucket and drawing a line chart that
        connects these points. Actually, two charts are produced, one for texts 0:1000 and
        another for texts 0:1M
      - speed vs text length, one curve for each algorithm, obtained by computing the speed
        of parsing all the texts of all the REs for each text bucket length and drawing a
        line chart as above. Two charts are produced for short and long texts.
      - one chart for each algorithm, speed vs text length, one curve for each RE length,
        obtained by computing the speed of parsing all the texts of all the REs in a RE
        bucket, and drawing the line chart as above.
        This relieves from the difficulty to weighting less the time of long strings since
        it shows how varies the speed for a given RE length and increasing text length.
      - these charts are for short texts; another set of charts is produced for long texts
      - compilation speed, one curve for each algorithm, obtained by computing the speed
        to compile all the REs in each text bucket, and drawing a line chart as above.

   This is the relative standard deviation of the values for texts with buckets of RE lenghts:
   texts 0:1000
   BSP     1.55    0.31    0.05    0.03    0.01    0.01    0.02    0.02    0.03    0.04
   BSPP    1.55    0.35    0.08    0.05    0.02    0.03    0.05    0.03    0.03    0.06
   pat     1.58    0.22    0.13    0.10    0.05    0.07    0.07    0.04    0.08    0.06
   Earley  2.10    0.06    0.10    0.11    0.08    0.10    0.07    0.07    0.09    0.09
   java    0.92    0.03    0.06    0.05    0.03    0.04    0.03    0.03    0.03    0.04
   texts 0:1M
   BSP     0.06    0.02    0.01    0.01    0.01    0.01    0.01    0.02    0.01    0.02
   BSPP    0.05    0.02    0.01    0.01    0.02    0.02    0.03    0.03    0.03    0.04
   pat     0.58    0.02    0.01    0.01    0.00    0.00    0.00    0.00    0.00    0.01
   Earley  0.96    0.07    0.04    0.06    0.01    0.09    0.07    0.07    0.05    0.09
   java    0.34    0.06    0.13    0.11    0.09    0.10    0.09    0.07    0.07    0.08

   The relative deviation does not vary much among the text buckets (I have measured this),
   so the table above reports how is varies among the RE buckets only.
   Except for the first RE bucket and small texts, for which the standard deviation is
   high (probably because memory allocation of some internal storage is done in some cases
   and not in others, which puts a penalty on them or because the lengths of the texts in
   buckets varies much: there are some whose length is twice that of others), in all other
   cases it is quite small.
   This means that the curves in the charts represent the majority of the measures done, and
   not simply interpolations for the average case of values that are dispersed.

   I analyzed the time of the parsing of each text in its bucket, knowing that there could
   be some variance since the time depends on the RE and the text, and in lower buckets the
   longest string could be twice the shortest and the RE length too.
   These are the results taken all the text buckets of the relative standard deviation:
   average: 0.22 relative standard deviation of it: 1.03.
   This means that the times in text buckets are rather close to each other since the
   average is in a range of deviations from 0..0.4.

   Ambiguity: ambiguous REs: 64 % ambiguous texts: 53 %

   I have run the benchmark a few times from the construction of REs, and seen that the results
   are similar.

   I have serialized the measures and charted them offline so as to change charting and quickly
   see the result (running measures takes more than one day).
   The method showMeasures() shows a map of all the text buckets parsed and the ones that were not
   parsed because of too long (or any other reason).


The problem of parses that take too much time

  Some algorithms are not linear: there are test cases for which they take too much time to
  parse. This makes benchmarking last for days. Knowing that the speed in such cases is one
  token per minute or hour is not much meaningful: it is unbeareably slow in any case.
  To cope with this I have introduced time supervision in such algorithms.
  Time supervision is done when enabled. However, even when enabled, it is not invasive.
  It is implemented using a counter (tally) that is incremented and tested in places where the
  algorithm at hand passes very often. Since it would be difficult to devise a threshold for
  tally (it would depend on the algorithm, RE and text), at each 1M (or a reasoneably high value)
  tally increments, the cycle counter is read and its value compared to the one read at the
  beginning of the measure being done to tell if a given parse time has been expended.
  This makes time supervision not much invasive and able to stop execution when a given time
  has passed.
  Another solution is to run a thread that interrupts the main one when a timeout expires.
  This needs to run a thread, or to reset it, at the beginning of each measure, to test the
  interruption in the main thread, and to stop or reset the thread at the end of each measure.
  This requires the execution of a primitive at the same places in which I read now the cycle
  counter. Its execution time could be unpredictable and certainly longer than reading the
  cycle counter, which is a few instructions without entering the OS kernel. Moreover, there
  would be a thread that would run on the same core as the main one, spoiling the values of
  the cycle counter (because there is no way to force each thread to run on a separate core).
  This solution then it is not practicable.

  The parse time is measured on entire text buckets.
  Let's say that the max time for each match be 10s; then in buckets of 10 texts, the last
  match should take at most 100s from the begining of the bucket. This could be avoided with
  a dry run, but doubles benchmarking time.

  When the matching of a text in a text bucket takes too long, the whole text bucket is not
  measured. Here is a map of the measures done:
          
          re bucket 0
             re0    text-bucket-0 ... text-bucket9
             re1    text-bucket-0 ... text-bucket9
             ...
             re99   text-bucket-0 ... text-bucket9
          re bucket 1
             ...

     speed vs text-length: sum by columns, so if a column has an invalid value, all the
          column is invalid (of all the buckets, or of a single bucket depending on the chart)
          and all the columns to its right are invalid too
     speed vs re-length: sum all values in each bucket, so if a column has an invalid value
          all the column is invalid, and if a RE bucket has an invalid value, all the bucket
          is invalid too, and also all the buckets below it

     It is possible to determine the speed with what measures are available instead of
     pretending that all the measures are there, and then probably there would be no need to
     make a special case in the generation of samples for java.
     If for a given text length I sum all the values and divide them by the number of values
     summed, then I would determine the speed. If no values are present, then I should stop
     the curve.
     If for a given RE bucket I sum all the values and divide them by their number I would
     obtain the speed for the bucket, and if no values are present then I should stop the curve.
     However, the REs whose texts are slow to parse are the ones that make the engine terminate
     because of lack of memory for the upper half of text buckets. As a result, these text
     buckets have only texts that are parsed fast. I think I should discard these parts of the
     curves: take the rows that contain at least one value, rekon the smallest row and cut all
     values in the columns beyond it.
     Note that in the samples there are texts that in the case of java do not take an almost
     infinite time to parse. This means that I have removed the samples that make the engine
     enter catastrophic backtracking, i.e. border cases, not just samples that take long.
     Then, I discard the REs that have no valid value, and taking the remaining ones, I
     search the first column (i.e. text bucket, starting from the lower ones) that has an
     invalid value, and I discard the values of all the (columns, aka text buckets) after it.
     Note that a value could be invalid also because the engine terminated because of lack
     of memory (stack or heap).

     The problem with java is that there are some REs for which all the texts require too much
     time to match, and this occurs randomly in the RE buckets >= 4, which invalidates too many
     measures. This is not due to the fact that matchJava is not optimized.
     This occurs with long texts.
     There is no need to do the same with Earley because it is so slow that there is no way to
     find texts in RE buckets after the first that are parsed in reasoneable time.
     For the other algorithms it is not a problem.
     I have reduced the ambiguity of long texts appending a character to each RE that does not
     occur in the RE itself and then enclosing all this in a *.

     In measure values, long.max_value-1 means measure not done, and this is the initial value
     of the matrix of measures. When a measure is in error, the value long.max_value is stored.
     The use of two values to denote two different cases allows to know what measures have been
     done and what not (even though this is not much used at the moment).


Measuring memory use

  Measuring memory needs to measure the stack and heap, the stack expecially for java.
  I tried to use Runtime.totalMemory() and Runtime.freeMemory(), having made a gc() before.
  Strangely, the measured memory for most algorithms are the same, meaning perhaps that the
  data returned by Runtime are coarse. Likely, the stack is allocated always at its full
  length so that we cannot know how much it is actually used.
  There is a java.lang.instrument, but it seems rather complex to use, and likely unable to
  measure the internals of java.regex.
  I then abandoned the idea of measuring java.regex, and measured only the algorithms implemented
  here.
  To make match() rekon the memory used, MEASURE must be defined. To obtain then the charts
  run the benchmark with MEASURE defined.
  I did not instrument compile() to measure the memory, and thus did not produce a chart for it.
  Since compilation methods use a lot of different objects, to instrument them it would be simpler
  to provide methods to allocate memory while rekoning it instead of using directly new().
  In this implementation of the algorithms I reuse the internal data. I create a new RE for each
  text bucket, so the memory used is that for the parse that is more memory greedy among the
  texts in the bucket. Dividing it by the cumulative lengths of the texts in the bucket tells how
  much memory has been used for repeated parses. Since the measurements serve to compare algorithms
  in something that mimics real life, this delivers a realistic picture of memory use.
  I chart memory use as bytes/tokens.
  I produces only the chart of bytes/tokens vs text length 0:1000, one curve for each algorithm
  because what is needed is a rough idea of the space complexity
  I have verified the the measuring of memory does not take a significant amount of time.

A note on the implementations

  I have implemented all the algorithms as optimized as possible. Actually each algorithm
  has been implemented several times choosing the one whose speed was the highest.
  This ensures that the comparison of algorithms is as fair as possible because it does not
  include optimization. Actually, the only one case in which this is not true is java.util.regex,
  for which the actual Oracle implementation has been used. However, java.util.regex is measured
  only to show the positioning of the other algorithms with respect to a similar and much used
  package. Note also that java.util.regex does not build the syntax trees, while the other
  algorithms do.

  I have used plain arrays to hold the data which grow with the text length (e.g. list of states,
  items, etc.).
  It is well known that enlarging plain arrays places a high penalty on execution time.
  This is a problem for all algos: if the input is expected to be very large, then block arrays
  should be used for all the data that grow with the input, and no attempt to allocate the max,
  like, e.g. in mark(), should be done; but if the input is not that big, using simple arrays is
  faster.
  However, the arrays here are reused at the next match, and several matches are done, so the
  time penalty of the first allocation is not very important (especially because I record the
  minimum time and perform the very same measure several times).
  Block arrays could be needed when the RE is that of the RHS of a syntax rule
*/


/**
 * The <code>Re</code> class provides regular expression parsing, measuring the speed
 * of a number of algorithms.
 *
 * @author  Angelo Borsotti
 * @version 1.0   28 March 2015
 */

public class Re {


    // time supervision for algorithms that might take too much time
    private boolean timeSupervise = true;   // time supervision on
    private int tally;                      // counter for time supervision
    private long matchStartTime;
    private static long maxMatchTime = 90000000000L;   // 45 sec


    public Re(){
    }

    /** Constants to denote the algorithms. */
    private static final int ALGO_BSP = 1;
    private static final int ALGO_BSPP = 2;
    private static final int ALGO_PAT = 3;
    private static final int ALGO_EARLEY = 4;
    private static final int ALGO_JAVA = 5;
    private static final int ALGO_SULZ = 6;
    /** The algorithm for this parser. */
    private int algo;

    /**
     * Construct a new <code>Re</code> that uses the specified algorithm.
     *
     * @param      algo number denoting the algorithm
     */

    public Re(int algo){
        this.algo = algo;
    }


    //---------------- Tracing ---------------------

    /** The trace stream. */

    private static class Trc {
        public static PrintStream out = System.err;
    }

    /**
     * Ordered Sets of integers.
     */

    private static class IntSet {

        /** The collection that backs up this set. */
        TreeSet<Integer> bs;

        /**
         * Construct an empty set.
         */

        IntSet(){
            this.bs = new TreeSet<Integer>();
        }

        /**
         * Deliver a string representing this set.
         *
         * @return     string
         */

        public String toString(){
            return this.bs.toString();
        }

        /**
         * Remove all the elements from this set.
         */

        void clear(){
            this.bs.clear();
        }

        /**
         * Deliver the number of elements of this set.
         */

        int size(){
            return this.bs.size();
        }

        /**
         * Tell if this set is empty.
         *
         * @return     <code>true</code> if it is, <code>false</code> otherwise
         */

        boolean isEmpty(){
            return this.bs.isEmpty();
        }

        /**
         * Add the specified element to this set.
         *
         * @param      el element
         */

        void add(int el){
            this.bs.add(el);
        }

        /**
         * Add all the elements of the specified element to this set.
         *
         * @param      s set
         */

        void add(IntSet s){
            if (s == null) return;
            this.bs.addAll(s.bs);
        }

        /**
         * Deliver an array containing all the elements of this set. The elements are stored
         * in the specified array if it is large enough, otherwise a new array is created.
         *
         * @param      arr array
         * @return     array
         */

        int[] toArray(int[] arr){
            int len = this.bs.size();
            if ((arr == null) || (len > arr.length)){
                arr = new int[len];
            }
            int j = 0;
            for (Iterator<Integer> i = this.bs.iterator(); i.hasNext();){
                int n = i.next();
                arr[j++] = n;
            }
            return arr;
        }

        /**
         * Deliver an array containing all the elements of this set.
         *
         * @return     array
         */

        int[] toArray(){
            int[] arr = new int[this.bs.size()];
            int j = 0;
            for (Iterator<Integer> i = this.bs.iterator(); i.hasNext();){
                int n = i.next();
                arr[j++] = n;
            }
            return arr;
        }
    }

    /**
     * Compressed representation of sparse matrices. This is a simple implementation
     * of my other one that actually compresses them.
     */

    private static class CombVector {
        /** Holes accessed mode. */
        static final int HOLES_ACCESSED = 0;

        /** Fold rows mode. */
        static final int FOLD_ROWS = 0;

        /** Input rows in (index,value) pairs format mode. */
        static final int PAIRS = 0;

        /** The comb-vector merge table. */
        public int[] tabMerged;

        /** The comb-vector base table. */
        public int[] base;

        /** The comb-vector check table. */
        public int[] check;

        /** The value which is considered the hole. */
        public int holeValue;

        /**
         * Construct a new <codeCombVector></code> with the specified hole value and mode.
         *
         * @param      hole hole value
         * @param      mode mode
         */

        CombVector(int hole, int mode){
            this.holeValue = hole;
        }

        /**
         * Compress the specified matrix.
         *
         * @param      tabs matrix
         */

        void merge(int[][] tabs){
            int len = 0;
            for (int i = 0; i < tabs.length; i++){
                len += tabs[i][0];
            }
            this.base = new int[tabs.length];
            Arrays.fill(this.base,-1);
            this.check = new int[len];
            Arrays.fill(this.check,-1);
            this.tabMerged = new int[len];
            Arrays.fill(this.tabMerged,this.holeValue);
            int loc = 0;
            for (int i = 0; i < tabs.length; i++){
                this.base[i] = loc;
                for (int j = 1; j < tabs[i].length; j++){
                    int k = tabs[i][j++];                // index
                    this.tabMerged[k+loc] = tabs[i][j];  // value
                    this.check[k+loc] = loc;
                }
                loc += tabs[i][0];
            }            
        }
        void statistics(){
        }
    }


    //---------------- Unique methods to compile and match ----------------------

    /** The reference to the DFA for BSP (if this is the current algorithm). */
    private BStateTable bsdfa;

    /** The reference to the NFA for PAT (if this is the current algorithm). */
    private PATStateTable patnfa;

    /** The reference to the parse tables for Earley (if this is the current algorithm). */
    private EarleyTables earleytab;

    /** The number of groups (if java is the current algorithm). */
    private int ngroups;

    /** The reference to the pattern (if java is the current algorithm). */
    private Pattern jpattern;

    /**
     * Compile the specified regular expression.
     *
     * @param      re string of the regular expression
     */

    public boolean compile(String re){
        this.error = false;
        this.re = re;
        switch (this.algo){
        case ALGO_BSP:
        case ALGO_BSPP:
            buildAst();
            if (!this.error) this.bsdfa = buildBS(astRoot);
            if (getsym() != -1){      // whole re not consumed
                this.error = true;
            }
            break;
        case ALGO_PAT:
            buildAst();
            if (!this.error) this.patnfa = buildPAT(this.astRoot);
            if (getsym() != -1){      // whole re not consumed
                this.error = true;
            }
            break;
        case ALGO_EARLEY:
            buildAst();
            if (!this.error) this.earleytab = astToTables(this.astRoot);
            break;
        case ALGO_JAVA:
            try {
                this.jpattern = Pattern.compile(re);
            } catch (PatternSyntaxException exc){
                this.error = true;
            }
            this.ngroups = 1;
            for (int j = 0; j < re.length(); j++){
                if (re.charAt(j) == '(') this.ngroups++;
            }
            break;
        case ALGO_SULZ:
            buildAst();
            if (getsym() != -1) break;   // error or whole re not consumed
            buildSulz(astRoot);
            break;
        }
        return !this.error;
    }

    /**
     * Parse the specified string against the currently compiled regular expression.
     *
     * @param      text string to be parsed
     */

    public boolean match(String text){
        this.error = false;
        this.errorKind = ERROR_NOMATCH;
        try {
            if (this.algo != ALGO_JAVA){         // for java done in matchJava
                this.tally = 0;
            }
            switch (this.algo){
            case ALGO_BSP:
                match(text,this.bsdfa,false,1);
                break;
            case ALGO_BSPP:
                match(text,this.bsdfa,false,2);
                break;
            case ALGO_PAT:
                matchPAT(text,this.patnfa,false);
                break;
            case ALGO_EARLEY:
                String tree = earleyParse(text,this.earleytab,false);
                if (tree == null) this.error = true;
                break;
            case ALGO_JAVA:
                this.tokens += text.length();
                Matcher m = this.jpattern.matcher(text);
                if (!m.matches()){
                    this.error = true;
                    break;
                }
                for (int j = 0; j < this.ngroups; j++){
                    m.group(j);
                }
                break;
            case ALGO_SULZ:
                matchSulz(text);
                break;
            }
        } catch (OutOfMemoryError exc){
            this.error = true;
            this.errorKind = ERROR_MEMORY;
        } catch (StackOverflowError exc){
            this.error = true;
            this.errorKind = ERROR_STACK;
        }
        if (!this.error){
            this.errorKind = 0;
        }
        return !this.error;
    }


    // ---------- Parenthetized expressions -----------------

    /**
     * Symbol part in BSP and PAT FA's items: made of a sequence of integers, that behaves much
     * the same as a string. Each element represents the entering or leaving of a node in a path
     * thru the AST. The elements are indexes of AST nodes with flags attached telling the
     * entering or leaving (see below).
     * Aka "finished string", "tag", "parenthetized expression".
     */

    private class BSsymbol {

        /** An array of indexes of ast nodes. */
        private int[] arr;

        /**
         * Deliver a string representing this symbol.
         *
         * @return     string
         */

        public String toString(){
            return toString(astMap,this.arr.length);
        }

        /**
         * Deliver a string representing this symbol using the specified map of indexes of
         * nodes.
         *
         * @param      astMap map
         * @return     string
         */

        public String toString(AstNode[] astMap){
            return toString(astMap,this.arr.length);
        }

        /**
         * Deliver a string long at most the specified length, representing this symbol using
         * the specified map of indexes of nodes.
         *
         * @param      astMap map
         * @param      length maximum length
         * @return     string
         */

        public String toString(AstNode[] astMap, int len){
            String str = "";
            if (this.arr != null){
                for (int i = 0; i < len; i++){
                    str += BSeleToString(this.arr[i],astMap);
                }
            }
            return str;
        }

        /**
         * Tell if this symbol is equal to the specified one.
         *
         * @param      other other symbol
         * @return     <code>true</code> if it is equal, <code>false</code> otherwise
         */

        public boolean equals(BSsymbol other){
            return Arrays.equals(this.arr,other.arr);
        }

        /**
         * Tell if this symbol is equal to the specified one.
         *
         * @see        #equals(BSsymbol)
         */

        public boolean equals(Object other){
            return equals((BSsymbol) other);
        }

        /**
         * Return the hashcode for this symbol.
         *
         * @return     hash code value
         */

        public int hashCode(){
            int res = 0;
            for (int i = 0; i < this.arr.length; i++){
                res += arr[i];
            }
            return res;
        }

        /**
         * Compare this symbol with the specified one. It is fit only for sorting because
         * it sorts elements on their encodings.
         *
         * @param      other the symbol to compare
         * @return     &lt; = or &gt; 0 if this symbol precedes, is equal or follows the other
         */

        public int compareTo(BSsymbol other){
            int n = this.arr.length;
            if (other.arr.length < n) n = other.arr.length;
            int i = 0;
            int j = 0;
            while (n-- != 0){
                int c1 = this.arr[i++];
                int c2 = other.arr[j++];
                if (c1 != c2) return c1 - c2;
            }
            return this.arr.length - other.arr.length;
        }

        /**
         * Deliver a new symbol with the specified substring of this one.
         *
         * @param      begin start index of the string (inclusive)
         * @param      end index (exclusive)
         * @return     symbol
         */

        public BSsymbol substring(int begin, int end){
            BSsymbol sym = new BSsymbol();
            if (end < 0) end = this.arr.length + end;
            sym.arr = Arrays.copyOfRange(this.arr,begin,end);
            return sym;
        }

        /**
         * Deliver the length of this symbol.
         *
         * @return     length
         */

        public int length(){
            return this.arr.length;
        }

        /**
         * Deliver the element at the specified index.
         *
         * @param      i index
         * @return     element
         */

        public int eleAt(int i){
            if (i < 0) return this.arr[this.arr.length+i];
            return this.arr[i];
        }

        /**
         * Deliver a trimmed symbol if the last element is not an open or a close, otherwise
         * this symbol.
         *
         * @return     symbol
         */

        public BSsymbol getTag(){
            int ele = this.arr[this.arr.length-1];
            int kind = BSeleKind(ele);
            if (kind != ELEOPEN && kind != ELECLOSE){
                return this.substring(0,-1);
            }
            return this;
        }

        /**
         * Deliver a trimmed symbol if the first or last element is not an open or a close,
         * otherwise this symbol.
         *
         * @return     symbol
         */

        public BSsymbol tag(){
            int start = 0;
            int end = this.arr.length;
            boolean trim = false;
            int ele = this.arr[this.arr.length-1];
            int kind = BSeleKind(ele);
            if (kind != ELEOPEN && kind != ELECLOSE){
                end = -1;
                trim = true;
            }
            ele = this.arr[0];
            kind = BSeleKind(ele);
            if (kind != ELEOPEN && kind != ELECLOSE){
                start = 1;
                trim = true;
            }
            if (trim){
                return this.substring(start,end);
            }
            return this;
        }

        /**
         * Append the elements of the specified symbol to this one.
         *
         * @param      other the other symbol
         */

        public void concat(BSsymbol other){
            if (this.arr == null) this.arr = new int[0];
            int len = this.arr.length;
            this.arr = Arrays.copyOf(this.arr,this.arr.length+other.arr.length);
            System.arraycopy(other.arr,0,this.arr,len,other.arr.length);
        }

        /**
         * Tell if this symbol starts with the specified one.
         *
         * @param      other the other symbol
         * @return     <code>true</code> if it does, <code>false</code> otherwise
         */

        public boolean startsWith(BSsymbol other){
            if (other.arr.length > this.arr.length) return false;
            int i = other.arr.length;
            while (i-- != 0){
                if (this.arr[i] != other.arr[i]) return false;
            }
            return true;
        }

        /**
         * Tell if this symbol ends with the specified one.
         *
         * @param      other the other symbol
         * @return     <code>true</code> if it does, <code>false</code> otherwise
         */

        public boolean endsWith(BSsymbol other){
            if (other.arr.length > this.arr.length) return false;
            int i = other.arr.length;
            int l = this.arr.length;
            while (i-- != 0){
                if (this.arr[--l] != other.arr[i]) return false;
            }
            return true;
        }

        /**
         * Trace this symbol.
         */

        public void trace(){
            for (int i = 0; i < this.arr.length; i++){
                int ele = this.arr[i];
                int kind = BSeleKind(ele);
                AstNode ast = BSeleToAst(ele);
                Trc.out.printf("%s %s %s %s |%s|\n",
                    i,ele,BSeleKindToString(kind),BSeleToPos(ele),BSeleToString(ele));
            }
        }
    }

    /* BSsymbol elements
     *
     * b0..b27:   number of ast
     * b28..b29:  kind field
     *            b28:  entering
     *            b29:  leaving
     *            these denote the entering and leaving of ast nodes in bssymbols.
     *            This allows to have parentheses that represent the entering and leaving
     *            of leaves without introducing extra nodes in the ASTs.
     */

    /** Number of shifts for the kind field. */
    private static final int ELEKINDSHIFT = 28;

    /** Mask for the kind field. */
    private static final int ELEKIND = 1 << ELEKINDSHIFT;

    /** Value of the kind field denoting entering. */
    private static final int ELEOPEN = 1;

    /** Value of the kind field denoting leaving. */
    private static final int ELECLOSE = 2;

    /** Mask for the AST nr field. */
    private static final int ELEMASK = (1 << ELEKINDSHIFT)-1;

    /** Entering flag. */
    private static final int ELEOPENB = 1 << ELEKINDSHIFT;

    /** Leaving flag. */
    private static final int ELECLOSEB = 1 << (ELEKINDSHIFT+1);

    /** Mask denoting the kind field. */
    private static final int ELEKMASK = ELEOPENB | ELECLOSEB;

    /**
     * Deliver a string representing the direction of the path (entering, leaving, passing).
     *
     * @param      kind value of the kind field
     * @return     String
     */

    private String BSeleKindToString(int kind){
        return new String[]{"leaf","open","close"}[kind];
    }

    /**
     * Deliver the reference to the AST node of the specified element.
     *
     * @param      ele element
     * @return     reference to the AST node
     */

    private AstNode BSeleToAst(int ele){
        return this.astMap[ele & ELEMASK];
    }

    /**
     * Deliver the the position of the specified element.
     *
     * @param      ele element
     * @return     reference to position
     */

    private int[] BSeleToPos(int ele){
        return this.astMap[ele & ELEMASK].pos;
    }

    /**
     * Deliver the number of the AST node of the specified element.
     *
     * @param      ele element
     * @return     number of the AST
     */

    private int BSeleToAstNum(int ele){
        return ele & ELEMASK;
    }

    /**
     * Deliver the kind of the specified element (direction of the path).
     *
     * @param      ele element
     * @return     find field
     */

    private int BSeleKind(int ele){
        return ele >> ELEKINDSHIFT;
    }

    /**
     * Deliver the element denoting to the specified AST and direction.
     *
     * @param      ast reference to the AST
     * @param      kind kind field
     * @return     element
     */

    private int astToBSele(AstNode ast, int kind){
        return ast.seq + (kind << ELEKINDSHIFT);
    }

    /**
     * Tell if the specified element is a terminal (different from an epsilon).
     *
     * @param      ele element
     * @return     <code>true</code> if it is, <code>false</code> otherwise
     */

    private boolean isBSeleTerminal(int ele){
        return (ele & ELEKMASK) == 0 && this.astMap[ele].kind == A_LEA;
    }

    /**
     * If the specified element has the specified kind and denotes an AST of a *|+ group,
     * then return its position, null otherwise.
     *
     * @param      ele element
     * @return     position, or null
     */

    private int[] isEleRep(int ele, int kind){
        int[] res = null;
        doit: {
            int k = BSeleKind(ele);
            if (k != kind) break doit;
            AstNode ast = BSeleToAst(ele);
            if (ast.kind == A_GRO && (ast.groupKind == G_RE0 || ast.groupKind == G_RE1)){
                res = ast.pos;
            }
        } // doit
        return res;
    }

    /**
     * If the specified element has the specified kind and denotes an AST of a *|+ group
     * which is enclosed in another a *|+ group, then return its position, null otherwise.
     *
     * @param      ele element
     * @return     position, or null
     */

    private int[] isRep(int ele, int kind){
        int[] res = null;
        doit: {
            int k = BSeleKind(ele);
            if (k != kind) break doit;
            res = isEleRep(ele,kind);
            if (res != null) break doit;
            AstNode ast = BSeleToAst(ele);
            if (ast.pos.length == 0) break doit;
            int[] pos = Arrays.copyOf(ast.pos,ast.pos.length-1);
            // visit all the ast and find the father of ele (the one that has one level less in its pos)
            for (int i = 0; i < this.astMap.length; i++){
                AstNode a = this.astMap[i];
                if (Arrays.equals(a.pos,pos)){
                    if (a.kind == A_GRO && (a.groupKind == G_RE0 || a.groupKind == G_RE1)){
                        res = a.pos;
                        break doit;
                    }
                }
            }
        } // doit
        return res;
    }

    /**
     * Deliver a string representing the specified element.
     *
     * @param      ele element
     * @return     String
     */

    private String BSeleToString(int ele){
        return BSeleToString(ele,this.astMap);
    }

    /**
     * Deliver a string representing the specified element using the specified map between
     * AST numbers and nodes.
     *
     * @param      ele element
     * @param      astmap map
     * @return     String
     */

    private String BSeleToString(int ele, AstNode[] astmap){
        String str = "";
        int eleKind = BSeleKind(ele);
        AstNode ast = astMap[ele & ELEMASK];
        if (eleKind == ELEOPEN){               // node entry
            str += "(";
        } else if (eleKind == ELECLOSE){       // node exit
            str += ")";
        } else {
            if (ast.kind == A_LEA){                // terminal
                str += ast.sym;
            } else if (ast.kind == A_ALT){
                str += "|";
            } else if (ast.kind == A_CON){
                str += "\u00b7";
            } else if (ast.kind == A_EMP){
                str += "\u03b5";
            } else if (ast.kind == A_NUL){
                str += "\u03a6";
            } else {
                str += "\u03b5";               // a group without open or close flags acts as empty
            }
        }
        str += posToString(ast.pos);
        return str;
    }

    /**
     * Deliver a new BSsymbol whose elements are obtained by concatenating the ones contained
     * in the arguments.
     *
     * @param      args BSsymbols, arrays of elements, or elements
     * @return     BSsymbol
     */

    private BSsymbol newBSsymbol(Object... args){
        BSsymbol sym = new BSsymbol();
        sym.arr = new int[0];
        for (Object arg : args){
            if (arg instanceof BSsymbol){
                BSsymbol bs = (BSsymbol)arg;
                int len = sym.arr.length;
                sym.arr = Arrays.copyOf(sym.arr,sym.arr.length+bs.arr.length);
                System.arraycopy(bs.arr,0,sym.arr,len,bs.arr.length);
            } else if (arg instanceof int[]){   // array
                int len = sym.arr.length;
                int[] arr = (int[])arg;
                sym.arr = Arrays.copyOf(sym.arr,sym.arr.length+arr.length);
                System.arraycopy(arr,0,sym.arr,len,arr.length);
            } else {
                sym.arr = Arrays.copyOf(sym.arr,sym.arr.length+1);
                sym.arr[sym.arr.length-1] = (int)arg;
            }
        }
        return sym;
    }

    /**
     * Deliver a new BSsymbol whose elements are the ones contained in the argument.
     *
     * @param      arr array of elements
     * @param      len number of elements
     * @return     BSsymbol
     */

    private BSsymbol newBSsymbol(int[] arr, int len){
        BSsymbol sym = new BSsymbol();
        sym.arr = Arrays.copyOf(arr,len);
        return sym;
    }

    /**
     * Deliver a new BSsymbol whose elements are the ones of the first symbol concatenated
     * with the ones of the second element.
     *
     * @param      bs1 first symbol
     * @param      bs2 second symbol
     * @return     BSsymbol
     */

    private BSsymbol newBSsymbolConc(BSsymbol bs1, BSsymbol bs2){
        BSsymbol sym = new BSsymbol();
        sym.arr = Arrays.copyOf(bs1.arr,bs1.arr.length+bs2.arr.length-1);
        System.arraycopy(bs2.arr,0,sym.arr,bs1.arr.length-1,bs2.arr.length);
        return sym;
    }


    // ---------- AST's -----------------

    // kinds of AST nodes

    /** The kind of an AST node for a leaf. */
    private static final int A_LEA = 0;

    /** The kind of an AST node for an alternative. */
    private static final int A_ALT = 1;

    /** The kind of an AST node for a concatenation. */
    private static final int A_CON = 2;

    /** The kind of an AST node for a group. */
    private static final int A_GRO = 3;

    /** The kind of an AST node for the empty string. */
    private static final int A_EMP = 4;

    /** The kind of an AST node for the empty set. */
    private static final int A_NUL = 5;

    // kinds for groups

    /** The group kind of an AST node for the normal () group. */
    private static final int G_GRO = 0;

    /** The group kind of an AST node for the optional [] group. */
    private static final int G_OPT = 1;

    /** The group kind of an AST node for the Kleene ()* group. */
    private static final int G_RE0 = 2;

    /** The group kind of an AST node for the positive ()+ group. */
    private static final int G_RE1 = 3;

    /** The group kind of an AST node for the normal group body. */
    private static final int G_BOD = 4;

    /** The group kind of an AST node for the optional group body. */
    private static final int G_BOO = 5;

    /** The group kind of an AST node for the *|+ group body. */
    private static final int G_BOR = 6;

    /**
     * Deliver a string representing the specified kind.
     *
     * @param      kind AST kind
     * @return     String
     */

    private String aKindToString(int kind){
        return new String[]{"term","alt","conc","group","empty","void"}[kind];
    }

    /**
     * Deliver a string representing the kind of the specified AST.
     *
     * @param      ast reference to the AST
     * @return     String
     */

    private String astKindToString(AstNode ast){
        String str = aKindToString(ast.kind);
        if (ast.kind == A_GRO){
            str += this.groupIcon[ast.groupKind];
        }
        return str;
    }

    /**
     * Deliver a string representing the kind of the specified AST in compact form.
     *
     * @param      ast reference to the AST
     * @return     String
     */

    private String astKindString(AstNode ast){
        String str = "";
        switch (ast.kind){
        case A_LEA: str += "\u03a3"; break;   // sigma
        case A_ALT: str += "|"; break;
        case A_CON: str += "\u2219"; break;   // bullet
        case A_GRO: str += this.groupIcon[ast.groupKind]; break;
        case A_EMP: str += "\u03b5"; break;   // epsilon
        case A_NUL: str += "\u03b5"; break;   // epsilon
        }
        if (ast.altnr != 0) str += " #|" + ast.altnr;
        return str;
    }

    /** The open parenthesis for the group opening. */
    private static final char GR_OPEN = '(';

    /** The close parenthesis for the group closing. */
    private static final char GR_CLOSE = ')';

    /** An AST node. */

    private class AstNode {

        /** The serial number. */
        int seq;

        /** The reference to the brother. */
        AstNode bro;

        /** The reference to the son. */
        AstNode son;

        /** The reference to the father. */
        AstNode fat;

        /** The kind of node: 0: leaf, 1: alt, 2: conc, 3: group, 4: empty, 5: void (empty set). */
        int kind;

        /** The symbol if the kind is leaf. */
        char sym;

        /** The kind of group (if group). */
        int groupKind;

        /** The position: sequence of indexes of level numbering. */
        int[] pos = new int[0];

        /** The index in the RE as string. */
        int cursor;

        /** Whether the node generates the empty string. */
        boolean isNull;

        /** The init set. */
        Set<BSsymbol> ini = new HashSet<BSsymbol>();

        /** The finish set. */
        Set<BSsymbol> fin = new HashSet<BSsymbol>();

        /** The digrams set. */
        Set<BSsymbol> dig = new HashSet<BSsymbol>();

        /** The number of alternative (when this node is an alternative). */
        int altnr;

        /** The origin: the AST from which this has been cloned, if any. */
        AstNode orig;

        /**
         * Deliver a string representing this node.
         *
         * @return     String
         */

        public String toString(){
            String str = "ast seq: " + this.seq;
            str += " pos:";
            str += posToString(this.pos);
            str += " ";
            if (this.altnr != 0) str += "|" + this.altnr + " ";
            if (this.kind == A_LEA){
                str += "leaf ";
                str += this.sym;
            } else if (this.kind == A_ALT){
                str += "alt ";
            } else if (this.kind == A_CON){
                str += "conc ";
            } else if (this.kind == A_EMP){
                str += "\u03b5";
            } else if (this.kind == A_NUL){
                str += "\u03a6";
            } else {
                str += " ";
                str += groupKindStr[this.groupKind];
            }
            str += " at: ";
            str += this.cursor;
            if (this.bro != null){
                str += " bro: ";
                str += this.bro.seq;
            }
            if (this.son != null){
                str += " son: ";
                str += this.son.seq;
            }
            if (this.fat != null){
                str += " fat: ";
                str += this.fat.seq;
            }
            str += " Null: " + this.isNull;
            str += " Ini: ";
            str += bsSetToString(this.ini);
            str += " Fin: ";
            str += bsSetToString(this.fin);
            str += " Dig: ";
            str += bsSetToString(this.dig);
            return str;
        }

        /**
         * Deliver a string representing the RE rooted in this node.
         *
         * @return     String
         */

        public String toRE(){
            return toRE(false);
        }

        /**
         * Deliver a string representing the RE rooted in this node.
         *
         * @param      nopos <code>true</code> if the positions are not included
         * @return     String
         */

        public String toRE(boolean nopos){
            String str = "";
            if (this.kind == A_LEA){          // leaf
                str += " ";
                str += this.sym;
                if (!nopos){
                    str += posToString(this.pos);
                }
            } else if (this.kind == A_ALT){   // alt
                for (AstNode i = this.son; i != null; i = i.bro){
                    if (i != this.son) str += " |";
                    if (i.kind == A_ALT){
                        str += "(";
                    }
                    str += i.toRE(nopos);
                    if (i.kind == A_ALT){
                        str += ")";
                    }
                }
            } else if (this.kind == A_CON){   // conc
                for (AstNode i = this.son; i != null; i = i.bro){
                    if (i.kind == A_ALT || i.kind == A_CON){
                        str += "(";
                    }
                    str += i.toRE(nopos);
                    if (i.kind == A_ALT || i.kind == A_CON){
                        str += ")";
                    }
                }
            } else if (this.kind == A_EMP){   // empty
                if (!nopos){
                    str += " \u03b5";
                    str += posToString(this.pos);
                }
            } else if (this.kind == A_NUL){   // empty set
                str += " \u03a6";
            } else {                          // group
                if (this.groupKind == G_OPT){
                    str += "[";
                } else if (this.groupKind == G_BOD){
                    str += "\u00ab";
                } else {
                    str += GR_OPEN;
                }
                if (!nopos){
                    str += posToString(this.pos);
                }
                if (this.son != null){
                    str += this.son.toRE(nopos);
                    if (this.groupKind == G_OPT){
                        str += "]";
                    } else if (this.groupKind == G_BOD){
                        str += "\u00bb";
                    } else {
                        str += GR_CLOSE;
                    }
                }
                str += groupSym[this.groupKind];
                if (!nopos){
                    str += posToString(this.pos);
                }
            }
            return str;
        }

        /**
         * Deliver a string representing shortly this node.
         *
         * @return     String
         */

        public String shortly(){
            String str = "ast seq: " + this.seq;
            str += " pos:";
            str += posToString(this.pos);
            str += " ";
            if (this.altnr != 0) str += "|" + this.altnr + " ";
            if (this.kind == A_LEA){
                str += "leaf ";
                str += this.sym;
            } else if (this.kind == A_ALT){
                str += "alt ";
            } else if (this.kind == A_CON){
                str += "conc ";
            } else if (this.kind == A_EMP){
                str += "\u03b5";
            } else if (this.kind == A_NUL){
                str += "\u03a6";
            } else {
                str += " ";
                str += groupKindStr[this.groupKind];
            }
            return str;
        }
    }

    /** Sequence number of ast nodes. */
    private int astSeq;

    /**
     * Deliver a new AST node with the specified kind.
     *
     * @param      kind kind
     * @return     reference to the node
     */

    private AstNode newAstNode(int kind){
        AstNode node = new AstNode();
        node.kind = kind;
        node.cursor = this.cursor;
        node.seq = this.astSeq++;
        return node;
    }

    /** String representing group kinds. */
    private static final String[] groupKindStr = new String[]{
        "GRO","OPT","RE0","RE1","BOD","BOO","BOR"};

    /** Strings representing group symbols in RE strings. */
    private static final String[] groupSym = new String[]{
        "","","*","+",""};

    /** Strings representing groups parentheses. */
    private static final String openClose = new String(new char[]{GR_OPEN,GR_CLOSE});

    /** String representing groups. */
    private static final String[] groupIcon = new String[]{
        openClose,"[]",openClose+"*",openClose+"+","\u00ab\u00bb"};

    /**
     * Deliver a string representing the specified set of BSsymbols.
     *
     * @param      set set of BSsymbols
     * @return     string
     */

    private String bsSetToString(Set<BSsymbol> set){
        String str = "";
        str += "{";
        if (set == null){
            str += "null";
        } else {
            boolean first = true;
            for (BSsymbol i : set){
                if (first){
                    first = false;
                } else {
                    str += ", ";
                }
                str += i.toString();
            }
        }
        str += "}";
        return str;
    }

    /**
     * Deliver a string representing the specified position.
     *
     * @param      pos reference to the position
     * @return     string
     */

    private static String posToString(int[] pos){
        if (pos == null) return "null";
        if (pos.length == 0) return "\u2227";
        if (pos[0] == Integer.MAX_VALUE) return "$";
        String str = "";
        for (int i = 0; i < pos.length; i++){
            if (i > 0) str += '.';
            str += pos[i];
        }
        return str;
    }

    /**
     * Trace the subtree rooted in the specified ast.
     *
     * @param      ast reference to the AST node
     */

    private void traceAst(AstNode ast){
        for (AstNode a = ast; a != null; a = a.bro){
            Trc.out.printf("node: %s\n",a);
            traceAst(a.son);
        }
    }

    /**
     * Visit all the nodes rooted in the specified one and set in them their position,
     * father and sequence number, ordered as the positions.
     *
     * @param      ast reference to the AST node
     * @param      pos reference to the position
     * @param      fat reference to the AST node father
     * @return     string
     */

    private void setPosAst(AstNode ast, int[] pos, AstNode fat){
        if (ast == null) return;
        ast.pos = pos;
        ast.fat = fat;
        ast.seq = this.astSeq++;
        this.astMap[ast.seq] = ast;
        int n = 1;
        for (AstNode a = ast.son; a != null; a = a.bro){
            int[] newpos = Arrays.copyOf(pos,pos.length+1);
            newpos[pos.length] = n;
            setPosAst(a,newpos,ast);
            n++;
        }
    }

    /**
     * Compare the specified positions.
     *
     * @param      p1 reference to the position
     * @param      p2 reference to the position
     * @return     &lt; = or &gt; 0 if p1 precedes, is equal or follows p2
     */

    private static int compareToPos(int[] p1,int[] p2){
        int n = p1.length;
        if (p2.length < n) n = p2.length;
        int i = 0;
        int j = 0;
        while (n-- != 0){
            int c1 = p1[i++];
            int c2 = p2[j++];
            if (c1 != c2) return c1 - c2;
        }
        return p1.length - p2.length;
    }

    /** Map from integers to ast nodes. */
    private AstNode[] astMap;

    /** The set of terminals. */
    private char[] alphabet;

    /**
     * Build the set of terminals present in the ast rooted in the specified node.
     *
     * @param      ast reference to the AST node
     */

    private void astAlphabet(AstNode ast){
        if (ast == null) return;
        add: if (ast.kind == A_LEA){
            for (int i = 0; i < this.alphabet.length; i++){
                if (ast.sym == this.alphabet[i]){
                    break add;
                }
            }
            this.alphabet = Arrays.copyOf(this.alphabet,this.alphabet.length+1);
            this.alphabet[this.alphabet.length-1] = ast.sym;
        } else {
            for (AstNode a = ast.son; a != null; a = a.bro){
                astAlphabet(a);
            }
        }
    }

    // ---------- Syntax analysis of a RE -----------------

    /* Syntax
     *
     *  - the syntax of RE as reported in papers is not a good one for applications.
     *    E.g. where a RE is allowed, a single terminal or a concatenations can occur.
     *    An application must instead have only one thing, which can contain one or several
     *    elements. Something like, e.g.:
     *
     *           r -> alt {| alt}
     *           alt -> {factor}+
     *           factor -> terminal | (r) | (r)+ | (r)*
     *
     *  - note also the syntax that is often used r -> r r | ... r* is ambiguous because in
     *    a RE: ab* it is not clear if the * applies to "b" only or to the whole.
     *    I think that in Posix * has an higher precedence of concatenation, and then it
     *    applies to the last character only.
     *    Moreover, concatenation and alternation are considered right-associative.
     *  - packages for REs seem not to provide metacharacters for the empty string and empty
     *    set.
     *    Actually, in practice there is no need for them. The empty sting can be represented
     *    as an empty alternative, e.g.: (a|), and the empty set is useless, it serves only
     *    to give REs a well-formed mathematical background.
     *    I support them just for testing theoretical REs.
     */

    /** The reference to the current node. */
    private AstNode curNode; 

    /** The reference to the root of the AST. */
    private AstNode astRoot;

    /** The index in RE (as a string). */
    private int cursor;

    /** The RE to be analysed. */
    private String re;

    /**
     * Build the AST from the specified string containing the RE.
     *
     * @param      re string
     * @return     reference to the root of the AST
     */

    private AstNode buildAst(String re){
        this.re = re;
        buildAst();
        return this.astRoot;
    }

    /**
     * Build the AST from the string containing the RE.
     *
     * @return     reference to the root of the AST
     */

    private void buildAst(){
        buildAst(true);                // create the BSP sets
    }

    /**
     * Build the AST from the string containing the RE.
     *
     * @param      bsp <code>true</code> to create the BSP sets, <code>false</code> the BS ones
     * @return     reference to the root of the AST
     */

    private void buildAst(boolean bsp){
        this.astSeq = 0;
        this.error = false;

        this.cursor = 0;
        this.curNode = null;
        expression();
        if (this.error || getsym() != -1){    // error or whole re not consumed
            return;
        }

        AstNode ast = this.curNode;
        this.eofAst = newAstNode(A_LEA);
        this.eofAst.sym = EOF;                    // eof
        this.eofAst.pos = new int[]{Integer.MAX_VALUE};

        this.astMap = new AstNode[this.astSeq];
        this.astSeq = 0;
        setPosAst(ast,new int[0],null);
        this.astMap[this.eofAst.seq] = this.eofAst;
        if (bsp){
            computeAstBSP(ast);                  // compute ini, fin, dig for BSP
        } else {
            computeAstBS(ast);                   // compute ini, fin, dig for BS
        }
        this.astRoot = ast;
        this.alphabet = new char[0];
        astAlphabet(ast);
    }

    /** The AST representing the end of text. */
    private AstNode eofAst;

    /** The char representing the end of text. */
    private static final char EOF = '\u22a3';

    /**
     * Parse an expression.
     */

    private void expression(){
        this.curNode = null;
        AstNode r = null;
        AstNode altnode = null;
        doit: {
            subexpression();                      // allow also nothing
            if (this.error) return;
            if (this.curNode == null){
                this.curNode = newAstNode(A_EMP);    // return empty
                this.curNode.sym = '\u03b5';
            }
            r = this.curNode;
            int n = 1;
            l: for (;;){
                int symv = getsym();
                char sym = (char)symv;
                if (symv < 0) break;
                if (sym != '|'){
                    this.cursor--;
                    break;
                }
                if (altnode == null){
                    altnode = newAstNode(A_ALT);
                    altnode.son = r;
                    r = altnode.son;
                    r.altnr = n;
                }
                AstNode q = this.curNode;                  // save
                n++;
                subexpression();                           // alternative, require a term
                if (this.error) return;
                if (this.curNode == null){
                    this.curNode = newAstNode(A_EMP);      // return empty
                    this.curNode.sym = '\u03b5';
                }
                r.bro = this.curNode;                      // store anyway
                r = this.curNode;
                r.altnr = n;
            } // l;
        } // doit
        if (altnode != null){
            this.curNode = altnode;
        }
    }

    /**
     * Parse a subexpression.
     */

    private void subexpression(){
        this.curNode = null;
        factor();
        if (this.error) return;
        AstNode concnode = null;
        AstNode first = this.curNode;
        if (this.curNode != null){
            AstNode q = this.curNode;
            int n = 1;
            l: for (;;){
                AstNode p = this.curNode;
                n++;
                factor();
                if (this.error) return;
                if (this.curNode != null){    // several terms
                    if (concnode == null){
                        concnode = newAstNode(A_CON);
                        concnode.son = p;
                        q = p;
                    }
                } else {
                    this.curNode = p;
                    break;
                }
                q.bro = this.curNode;
                q = this.curNode;
            } // l
        }
        if (concnode != null){
            this.curNode = concnode;
        }
    }

    /**
     * Parse a factor.
     */

    private void factor(){
        this.curNode = null;
        int symv = getsym();
        char sym = (char)symv;
        AstNode groupnode = null;
        doit: if (symv == -1){
            return;
        } else if (sym == '|' || sym == GR_CLOSE || sym == ']'){
            this.cursor--;
        } else if (sym == '['){
            expression();
            if (this.error) return;
            groupnode = newAstNode(A_GRO);
            groupnode.groupKind = G_OPT;
            groupnode.son = this.curNode;
            if (getsym() != ']'){
                this.error = true;
                return;
            }
        } else if (sym == GR_OPEN){
            expression();
            if (this.error) return;
            groupnode = newAstNode(A_GRO);
            groupnode.groupKind = G_GRO;
            groupnode.son = this.curNode;
            if (getsym() != GR_CLOSE){
                this.error = true;
                return;
            }
            symv = getsym();
            sym = (char)symv;
            if (sym == '*'){
                groupnode.groupKind = G_RE0;
            } else if (sym == '+'){
                groupnode.groupKind = G_RE1;
            } else if (symv != -1){
                this.cursor--;
            }
        } else {
            if (astEmpty){
                if (sym == '\u03b5'){
                    this.curNode = newAstNode(A_EMP);      // return empty
                    this.curNode.sym = sym;
                    break doit;
                }
                if (sym == '\u03a6'){
                    this.curNode = newAstNode(A_NUL);      // return empty set
                    this.curNode.sym = sym;
                    break doit;
                }
            }
            this.curNode = newAstNode(A_LEA);
            this.curNode.sym = sym;
        } // doit
        if (groupnode != null){
            this.curNode = groupnode;
        }
        for (;;){
            symv = getsym();
            sym = (char)symv;
            if (sym == '*'){
                groupnode = newAstNode(A_GRO);
                groupnode.groupKind = G_RE0;
                groupnode.son = this.curNode;
            } else if (sym == '+'){
                groupnode = newAstNode(A_GRO);
                groupnode.groupKind = G_RE1;
                groupnode.son = this.curNode;
            } else if (symv != -1){
                this.cursor--;
                break;
            } else {
                break;
            }
            this.curNode = groupnode;
        }
    }

    /** Whether epsilon and phi are accepted in the RE (as a string). */
    private static boolean astEmpty;

    /**
     * Get the next character from the RE.
     *
     * @return     character, or -1 if no more available
     */

    private int getsym(){
        int res;
        for (; this.cursor < this.re.length(); this.cursor++){
             if (this.re.charAt(this.cursor) != ' ') break;
        }
        if (this.cursor >= this.re.length()){   // eof
            res = -1;
        } else {
            res = this.re.charAt(this.cursor++);
        }
        return res;
    }


    //---------------- BSP ----------------------

    /*
     * Notes
     *
     *  - the iids are similar to the leftpointers in Earley parsers
     *
     * Optimizations:
     *  - use of compressed dfa tables
     *  - skip PAT calculations for nonambiguous strings. When the string is not ambiguous,
     *    i.e. its states do not have multiple iids, there is no need for choosing the tree.
     *    The tree can then be built visiting the list of states backwards.
     *  - all data are reused: if they are already available from a previous run they are
     *    reused
     *  - to avoid to make copies of B, D, I simply swap the old with the new ones
     *  - only two B, D copies, B and D are stored in only one array. First:
     *
     *          B[p][q] = <rho,rhop>
     *          B[q][p] = <rhop,rhop>
     *
     *    is redundant: it becomes:
     *
     *          B[p][q] = rho,  B[q][p] = rhop
     *
     *    Then B cannot be triangular. To use only one array:
     *
     *          B[len*p + q] = ...
     *          B[len*len + len*p + q] = ... for D
     *
     *    Then it could just be an array that is passed without a class.
     *    N.B. it is not possible to use only one B and D instead of two pairs to hold the
     *    past values.
     *    We could number the items, and when comparing two path nodes, compare them always
     *    with the smaller to the greater; this could save some elements in D. However, the
     *    B+D size is not big (1600 bytes in the examples here, for B plus D, which means
     *    that for D it is the half, to be doubled since there are two copies).
     *    Moreover, storing B and D as a flat array makes impossible to represent a triangular
     *    matrix.
     *  - the method pat_minsp that is applied to the tags of items is precomputed and stored
     *    in items
     *  - the methods: pat_subset_tags, pat_bp0, are precomputed for pairs of items (applied
     *    to their tags) and stored in states
     *  - it could be possible to use two vectors (like the ones used for the tables of
     *    operators precedence parsers) to store the results of comparisons, but these require
     *    time to be computed (they serve to save space), and I think that they cannot be
     *    computed statically, not enven computing all the combinations: their values depend
     *    on rho, and probably it is not worth.
     *  - I store sets of iids that have several element only once. I could number them and
     *    store in each state a table of them and then process them in effectivetrans instead
     *    of processing items. Each item could have an iid, or a number denoting a set of iids.
     *    Note that the winners are always the same for each item of which the same set is
     *    attached as set of iids because the sets of iids denote converging items in the from
     *    state. The issue is not much on saving memory, which can be done when the state
     *    table is converted to some form that stores only what is needed at parse time, but
     *    on saving time in effectivetrans.
     *    The problem is to avoid to visit convergences several times.
     *    I could give iid sets indexes after the ones of items in each state, and then use the
     *    dotter as I do now.
     *    However, when marking the items that belong to trees (i.e. putting them in a list to
     *    be visited) I should put also the sets of iids, making sure that they are not
     *    duplicated.
     *    To do it I must visit the items and use some means to check that their sets are not
     *    already in the list. But this is exactly what effectiveTrans does with the dotter.
     *    So, there is no point in numbering them and putting them in the list.
     *  - in order to avoid to process twice the same set of iids in effectivetrans I use an
     *    autoaging map (dotter) that indexed with the number of the first item of a set
     *    contains the index of the winner and the cursor in the string as autoaging value
     *  - if it were possible to order items in states in such a way that their iid sets
     *    contained each a range of indexes (instead of sparse idexes, possibly interleaving),
     *    some optimizations could be done.
     *    Unfortunately, this is not possible: to have sets of iids that are not interleaved,
     *    the items in a previous state must be ordered according to what sets they produce in
     *    a next state.
     *    The RE: (a+|ba|aba)*b shows that there is a state 4 in which the items are ordered
     *    correctly with sets of iids not interleaved and in increasing iid. Such state has a
     *    next state 7 that is ordered too, but has items ending with the same indexed terminal
     *    that are not contiguous, and cannot be made so (otherwise the sets of iids would no
     *    longer be ordered), thus leading to a next state in which the sets of iids are
     *    interleaved.
     *    So, it is not possible to order the items in such a way as to have noninterleaved,
     *    ordered sets of iids.
     *  - effectivetrans visits only sets of iids with more than one iid and only the sets
     *    that are attached at least to an active item.
     *  - note that in BSP effectivetrans and proceedonestep are called on the same set of
     *    transitions, which are already done: effectivetrans removes the converging ones
     *    leaving only one for each set, and proceedonestep computes B and D for the remaining
     *    ones. This can be done in one loop and saves 10% of time.
     *  - when a state is encountered in which there are no multiple iid's or in Okui a step
     *    in which K contains only one state, we can build a piece of the tree: it is a safe
     *    set. However, this could be useful only when there are very long strings
     *  - PAT cannot discard in proceedonestep paths because it does not know if they are dead
     *    or alive, but BSP could. E.g. in (a|aa)(aa|a) with text aaa it can discard the first
     *    alternative of the first group because it knows that there is another that is longer,
     *    and then discard what paths follows that alternative.
     *    The problem is how to detect it: there is perhaps a need to tell that a terminal is
     *    part of an alternative, even nested, but how can I tell that such an alternative
     *    will have a longer yield than another when they can produce a variable yield?
     *  - I have reduced the number of methods called for each char: done with computeStep1,
     *    but it gains very little
     *
     * The lists
     *
     *  - instead of building a list for each state in the stack of states I build a unique
     *    vector that holds all the iids of the tree items (with sentinels containing the
     *    number of the state) and save thus a lot of space.
     *    To avoid duplications when building the vector I use a dotter.
     *    Since in proceedonestep I would scan the vector, in the inner loop I can scan only
     *    the upper part, up to the current one of the outer loop.
     *    If the vector becomes too large I can use a two-level array.
     *    The vector is build in the backward pass.
     *    The use of the unique vector reduces time by 5.5%, whick allows to store states on
     *    the stack instead of objects containg states (and other things), that reduces time
     *    by another 4.3%.
     *  - the scanning of items occurs in effectivetrans and proceedonestep: in the first
     *    because I need to process convergences and do it looking to the items of the next
     *    pnode and their sets of iids (which are the convergences), and in the second because
     *    I must process only the active items and find them in the activeiids of the next
     *    pnode.
     *  - during matching I store states in a stack, and then in mark I scan the stack
     *    backwards and store the tree iids into a vector (separated by sentinels), and in
     *    the third pass I scan the vector (there is a need to link the frames so as to be
     *    able to find the previous one):
     *
     *         state s0        state s1                state sn
     *                         i1act                   i1act
     *                         i1                      i1
     *                         i2
     *
     *         effectiv: process i1.left               pnodetotree:  take sn, take i1act
     *                   items of s0, choose                         find item nr i1act in prev
     *                   prior among left,                           (so as to get its ixact)
     *                   store it in i1act
     *         proceed:  process i1act, build B,D
     *
     *    A solution that uses more memory is to have a vector for each state in the stack for
     *    the tree items and another for the active ones.
     *    The unique vector is plist:
     *
     *      len | FRAME_AMBIG   size of list, including the two len's, FRAME_AMBIG present if
     *                          one of the items below has several iids
     *      actIids             ordered as the items that have several iids in the state
     *      iid
     *      ...
     *      -len
     *
     *    The items in the states are ordered putting the ones that have several iids before the
     *    others.
     *    The activeIids in the lists are as many as the items in the corresponding state that
     *    have several iids.
     *    They are placed in the plist from the last to the first:
     *
     *      plist  0             len-l       ...                       plistStart
     *             last frame l  l-1 frame   f frame   f-1 frame  ...  frame 0     plistEnd
     *
     *    In the third pass I start from frame 0 and need to know the start of frame 1 (and
     *    likewise for the others).
     *    This is done using the second length. Moreover, I scan the items in a frame and
     *    need to know when there are no more. This is done using the negative second length
     *    as sentinel.
     *    When building the tree I scan the frames from the last, and from frame f I need to
     *    move to frame f-1. This is done using the first length.
     *
     *    Another layout for the plist has been implemented, in which the iid's come first and
     *    then the actIids, which are present only when one of the iid's represents an item
     *    that has several iids. The presence is marked as in the previous solution.
     *    To visit the list, the end of the iids must be computed adding the len to the start
     *    index of the list, and then subtracting 1 and the number of actIids if FRAME_AMBIG is
     *    present.
     *    This solution saves 8% of memory, but looses 12% of time, so it is not convenient.
     *
     *    Another layout is to store in each frame pairs: iid,left, but this does not allow to
     *    traverse the left chains backwards efficiently because to move from an item I in a
     *    frame to its left one when I has several iids, the frame must be accessed to find
     *    the active iid. This is easy at the very beginning because the active iid A is
     *    stored just after the item, but not after because at the next step we must search
     *    the next frame to seek A and then its active iid just after it.
     *
     *    Another layout again is to reserve two entries in each frame of the plist to be used
     *    to store the items of the trees to compare when choosing one. This uses 12% and
     *    saves only 3% time, so it is not convenient.
     *
     *  - it is not possible to avoid to allocate a plist frame when there are no multiple
     *    iids. Even without multiple iids a frame serves to contain all the items that belong
     *    to paths of trees, which are a subset of the items of the state. This serves to
     *    avoid to visit all the items of the state when making the choice pass.
     *
     *    Another solution is to represent items with bitsets (i.e. bits in integers, bit 1
     *    for each marked item). The max number of bits for each state is known, which means
     *    that we can reserve space in the pstack instead of having a dedicated plist.
     *    The stack would then have the following form:
     *
     *         length         number of integers of this frame
     *         items-bitset   as many integers as needed to represent the items in the state
     *         ...
     *         state-nr
     *
     *    To go back from a frame to the previous one, sum 2+size of items bitset, to go from
     *    a frame to the next one use the length.
     *    The activeiids represent the winner iids when items have sets of iids with more than
     *    one item. In computestep we unmark the items that are not winners.
     *    This can be done because the items sets are all disjoint or identical, which means that
     *    items that converge are either to be kept marked or to be unmarked.
     *    Then, in computestep when computing the prior ones we take only the marked items.
     *    However, scanning bitsets could be costly, which can be overcome in computestep by keeping
     *    a reusable array that is filled with the iids of the marked items that are visited of the
     *    next list storing the active ones for iids that have more than one (the winner).
     *    Once the winners are found (the only iid for sets that have only one), the array is
     *    scanned and B, D computed for each pair.
     *    Note that scanning the iids of the next list could make us compute more than once B and D
     *    for a same pair.
     *    Note also that to compute B and D for the next list we must know the transitions, i.e. the
     *    pairs curlist-item, nextlist-item  (being the first the active ones of the iids of the
     *    second), and this can only be done by scanning the next list.
     *    When building the tree we scan the stack backwards, following the chain of active items,
     *    which means that now we should take the item in each set that has the marked predecessor.
     *    There is also no longer a need for the dotter (and the plist).
     *    The dotter in computestep serves to avoid to find again the winner among the same set of items
     *    if that has already been done before (i.e. if the same set of items is being processed
     *    again because present in another item). We can do the same checking that the items to compare
     *    are marked, and if not, skip the set.
     *    I have measured the speed of this solution and seen that it is much lower than the
     *    one with the plist.
     *
     *    In computestep there is a need to determine the winner among items that converge on
     *    a same item and to compute B, D for all the items except the loosers.
     *    These two actions can be done in two separate loops, or in a same one, providing that
     *    they do not collide. I.e. we cannot process converging items and others together
     *    scanning the current list because we could take items that are marked first, and
     *    later discover that they must be unmarked because loosers.
     *    Processing pairs in the next list achieves it because we take the pairs of the
     *    items in the current list pointed by their iids (active iids when they have several).
     *    The only point to note is to skip the calculation when the same pair in the current
     *    list has already been processed.
     *
     * Ambiguity condition
     *
     *    A string is ambiguous if one of its items has several iids and not if one of its
     *    states is ambiguous: when a state is ambiguous the string can still be non-ambiguous
     *    There is a question on whether sharper ambiguity check could be done setting a flag
     *    to all the transitions for which there are items with several iids: taken a state
     *    and a terminal, there are items that lead to other items in another state. When one
     *    of such other items has several iids, the transition is ambiguous.
     *
     *         s1  ---a------> s2
     *         i1              i1 iid:i1,i2
     *         i2
     *
     *    The answer is negative, all the items in s2 are obtained by making a transition from
     *    items of s1 that end in the access terminal for s2. Therefore, flagging s2 is the
     *    same as flagging all its incoming transitions.
     *
     * BSP transitions vs PAT transitions
     *
     *    After matching, a sequence of states has been built, and in each state the items
     *    that belong to some tree have been marked.
     *    To choose the prior tree (according to the selected disambiguation criterion), we
     *    must take the PAT transitions that are implied by each BSP transition, pruning the
     *    ones that reach the same PAT state leaving only one among them.
     *    The final state could have several items that end in eof. There is a need to choose
     *    among them too.
     *    At the end the last state in the sequence must contain the number of the winning item.
     *    Note that the last BSP transition represents a PAT transition from the previous state
     *    to the $ state.
     *    The PAT transitions are here represented by the items whose numbers are the active
     *    iid's of the next state.
     *    Let's depict the PAT transitions implied in two states in the sequence (remember that
     *    each item is made of a "segment" (ending in an indexed (terminal) symbol and a set of
     *    iids):
     *
     *        s1            ----a----->   s2           ---b----->  s3
     *        i: [tag1+a,#h]              j: [tag2+b,#i]           k: [tag3+c,#j]
     *
     *    PAT transitions
     *        h_end_pos     ----a--->     i_end_pos    ----b-->    j_end_pos ....
     *
     *    E.g. items j and k, linked by the k's iid, represent PAT transitions from PAT
     *    state labelled with the position of the end symbol of i to PAT state labelled
     *    with the position of the end symbol of j.
     *
     *    We must determine which PAT transition wins when there are several leading to the
     *    same PAT state.
     *    To do it, we must compare all the PAT transitions that reach the same PAT state,
     *    i.e. the same end symbol (among all the "from" items) and find the one that wins.
     *    But these transitions can occur several times because there can be several items
     *    with the same end symbol and iids.
     *    Note, for example, the state 2 in the DFA for (ab|(a)*)*:
     *
     *        1:[)1.2.1(1.2.1a1.2.1,#1,#3]
     *        3:[)1.2.1)1.2)1(1(1.2(1.2.1a1.2.1,#1,#3]
     *
     *    These two items represent transitions for "a", leading again to state 2. State 2
     *    items 1 and 3 iids refer again to items 1 and 3. They thus represents 4 PAT transitions:
     *
     *        1.2.1 -- )1.2.1(1.2.1,             a --> 1.2.1     item 1 -> 1
     *        1.2.1 -- )1.2.1(1.2.1,             a --> 1.2.1     item 1 -> 3
     *        1.2.1 -- )1.2.1)1.2)1(1(1.2(1.2.1, a --> 1.2.1     item 3 -> 1
     *        1.2.1 -- )1.2.1)1.2)1(1(1.2(1.2.1, a --> 1.2.1     item 3 -> 3
     *
     *    The first item has iids that refer to items in state 1 and 2 that have the same sym, and
     *    so does the second.
     *    When state 1 is processed, the items 1 and 3 are compared for the transitions that they
     *    represent, and one of the two is deactivated. Then, when comparing the transitions in
     *    state 2 we disregard the ones originating from inactive items and here have no doublets.
     *    The question is on whether it is possible to deactivate an item.
     *    Actually, it is: since items represent transitions ending in PAT states that have the
     *    same name as the items' end symbols, only one of them must survive. But they generate the
     *    items for the next states. Now, let's think that an inactive item generates inactive
     *    items too (actually, new items or iids in existing items).
     *    This means that disregarding inactive iids, an item represents one PAT transition.
     *    Then, we start processing the first state in the sequence, the initial state.
     *    We take the items, and compare the ones that have the same position (the one of their
     *    end symbols), and determine which one wins. For all the others, we mark that they are
     *    inactive. The winning items are represented by means of the active iids.
     *    This goes on until the end. The last state has then an active item ending with eof,
     *    and having an active iid.
     *    The only problem is that in the case above we process 4 transitions finding the winner
     *    twice. However, the winner is the same, so it is only an issue of efficiency.
     *    Strictly speaking, when we have determined what item between 1 and 3 wins, we could
     *    delete the looser from all the iids of the "to" state avoiding thus to find the winner
     *    again, but scanning the "to" state to spot this would take time too, probably more than
     *    spotting the winner again (which is quite fast).
     *    Note that to avoid to have parallel PAT transitions (i.e. two with the same "from"
     *    states and the same "to" states, the iids have been purged.
     *    Note also that two items that end with the same numbered symbol produce items in the
     *    next state with multiple iids, and that this is the only case in which multiple iids
     *    occur. Since all items ending with the same symbol are referred to from the same set
     *    of iids, visiting it we take all the PAT transitions that end in the same PAT state
     *    (remember that a numbered symbol corresponds to a PAT state since there is one state
     *    for each position and a position indicates univocally a terminal).
     *    If that were not true, PAT would have choosen one, while here we keep both.
     *    These items will lead to a convergence later, possibly in a final state.
     *    There a choice will be made. But we would not be sure that such choice would be correct
     *    because it would be made on the basis of something in the trees that appears later
     *    disregarding then a difference that appeared before and thus was more important.
     *    Luckily, this does not occur.
     *
     * BSPP
     *
     * It is possible to compare the two trees at the convergence: build both trees (up to the
     * initial state or a join) and do something like tr() or computestep.
     * This does not need the B and D arrays.
     * Walking forwards requires extra memory, at the worst twice the input, but in reality
     * much less.
     * It is faster than the other: 46% time less, 6% memory more.
     * It makes fewer comparisons than the other in several cases. Suppose there is a graph of
     * items like, e.g.
     *
     *         i1 -.
     *              +-- i5
     *         i2 -'      \
     *                     +- i7
     *         i3 -.      /
     *              +-- i6
     *         i4 -'
     *
     * In the first list the Okui method makes 6 comparisons, but actually only 3 are needed
     * (i1-i2, i3-i4 and between the winners) and these are the ones done by computeStep1.
     * However, there are cases in which it is much slower because make it not linear, e.g.:
     * ((((h)*))*|(((((h)))*))+)*  text: repeated "h".
     * I have measured the lengths of the linearized subtrees that it compares at each convergence
     * and have seen that they are proportional with the length of the portion of the string at
     * which they occur, which makes the algorithm quadratic.
     * See Re.html for further details.
     * BSP makes more comparison than needed because many of them compare are useless since they
     * compare trees that are all not prior than some other or are dead ends. It does so because
     * it does not know that such comparisons will turn out to be useless.
     *
     * Parse-time tables
     * - the data in the dfa that are needed at parse time are the states (except for the
     *   transitions list that is converted to the transition table, and the suc field)
     * - possible optimization: use of a single int field for single iid's and, e.g., negative
     *   index when sets for compressed states pointing to some place in which such sets are
     *   kept without duplicates
     * - possible optimization: have an array of iids and minsp directly in states avoiding
     *   then objects for items
     * - todo: measure the size of the fa and its tables
     *
     * If BSPPP is enabled, then BSP compares the number of comparisons it has done so far and
     * if it discovers that it has done more than the ones BSP would have done, then it reverts
     * to BSP.
     * It keeps a counter of the number of comparisons, and updates it with the ones done, and
     * another counter that tells the number of the ones that BSP would have done.
     * With this it becomes 10% faster than BSP (but is more complex).
     * In the whole benchmark it never reverts to BSP, which it does instead with some specific
     * RE and text is passed to it. The tests needed, unfortunately, weight 20% on its performance.
     * I think that 10% is not worth the increased complexity.
     *
     *
     * Further work
     *
     * In PAT we do not know the lengths of the yields at each step, until convergences are found,
     * but in BSP we know them, so we can take the earliest difference in trees, pick up the trees
     * that have it, and then compute the lenghts of the yields and choose the prior without making
     * the Okui calculations on the whole sequence of states. The yield of a tree is the number of
     * elements in the sequence of states between the open parenthesis and the corresponding close,
     * but to compute it the path representing the tree must be visited.
     * Let's mark first the items and build the plist, and then scan the states forwards.
     * Then let's pick up the first item and compare it with the following one and if a difference
     * is found, compare the yields (this is not simple because the links in paths are backwards
     * and there could be forks). Let's take then the prior so far and proceed comparing the item
     * with the following ones. In so doing the prior can change again, but the last one is the
     * final one.
     * I think that this could be quadratic because at each step we must look forward until the
     * end and do it for each item.
     */

    // ---------- Building the BSP DFA  -----------------

    /**
     * Compute the BS attributes for the subtree rooted in the specified AST node
     * for the plain BS.
     *
     * @param      ast reference to the AST node
     */

    private void computeAstBS(AstNode ast){
        switch (ast.kind){
        case A_LEA:                           // terminal
            ast.isNull = false;
            ast.ini = new HashSet<BSsymbol>();
            ast.ini.add(newBSsymbol(ast.seq));
            ast.fin = new HashSet<BSsymbol>();
            ast.fin.add(newBSsymbol(ast.seq));
            ast.dig = new HashSet<BSsymbol>();
            break;
        case A_EMP:                           // empty
            ast.isNull = true;
            ast.ini = new HashSet<BSsymbol>();
            ast.fin = new HashSet<BSsymbol>();
            ast.dig = new HashSet<BSsymbol>();
            break;
        default:
            ast.ini = new HashSet<BSsymbol>();
            ast.fin = new HashSet<BSsymbol>();
            ast.dig = new HashSet<BSsymbol>();
            for (AstNode a = ast.son; a != null; a = a.bro){
                computeAstBS(a);
            }
        }
        switch (ast.kind){
        case 1:                                    // alternative
            ast.isNull = false;
            if (ast.son == null) ast.isNull = true;
            for (AstNode a = ast.son; a != null; a = a.bro){
                if (a.isNull) ast.isNull = true;
                ast.ini.addAll(a.ini);
                ast.fin.addAll(a.fin);
                ast.dig.addAll(a.dig);
            }
            break;
        case A_CON:                                // concatenation
            boolean iniDone = false;
            AstNode lastNotNull = ast.son;         // in case all are null
            HashSet<BSsymbol> digfin = new HashSet<BSsymbol>();
            ast.isNull = true;
            for (AstNode a = ast.son; a != null; a = a.bro){
                if (!a.isNull) lastNotNull = a;
                if (!a.isNull) ast.isNull = false;
                if (!iniDone){
                    ast.ini.addAll(a.ini);
                    if (!a.isNull){
                        iniDone = true;
                    }
                }
                ast.dig.addAll(a.dig);
                if (a != ast.son){                // not first
                    BSsymbol[] iniarr = a.ini.toArray(new BSsymbol[0]);
                    BSsymbol[] finarr = digfin.toArray(new BSsymbol[0]);
                    for (int i = 0; i < finarr.length; i++){
                        for (int j = 0; j < iniarr.length; j++){
                            ast.dig.add(newBSsymbol(finarr[i],iniarr[j]));
                        }
                    }
                }

                // compute the fin of all elements up to and including the current one
                digfin.clear();
                for (AstNode aa = lastNotNull; aa != null; aa = aa.bro){
                    digfin.addAll(aa.fin);
                    if (aa == a) break;
                }
            }
            for (AstNode a = lastNotNull; a != null; a = a.bro){
                ast.fin.addAll(a.fin);       // union of all those of the null tail + last not null
            }
            break;
        case A_GRO:                               // sub-re
            if (ast.groupKind == G_RE1 || ast.groupKind == G_GRO || ast.groupKind == G_BOD){
                ast.isNull = ast.son.isNull;
            } else {
                ast.isNull = true;
            }
            ast.ini.clear();
            BSsymbol[] iniarr = ast.son.ini.toArray(new BSsymbol[0]);
            ast.ini.addAll(ast.son.ini);
            ast.dig.addAll(ast.son.dig);

            ast.fin.clear();
            BSsymbol[] finarr = ast.son.fin.toArray(new BSsymbol[0]);
            ast.fin.addAll(ast.son.fin);

            ast.dig.addAll(ast.son.dig);
            digrams: {
                if (ast.groupKind != G_GRO && ast.groupKind != G_OPT){
                    for (int i = 0; i < finarr.length; i++){
                        for (int j = 0; j < iniarr.length; j++){
                            ast.dig.add(newBSsymbol(finarr[i],iniarr[j]));
                        }
                    }
                }
            } // digrams
            break;
        }
        if (ast.pos.length == 0){           // top node
            AstNode e = this.eofAst;
            if (ast.isNull){
                ast.ini.add(newBSsymbol(e.seq));
            }
            BSsymbol[] finarr = ast.fin.toArray(new BSsymbol[0]);
            for (int i = 0; i < finarr.length; i++){
                ast.dig.add(newBSsymbol(finarr[i],e.seq));
            }
        }
    }


    /**
     * Compute the BSP attributes for the subtree rooted in the specified AST node
     * for the augmented BSP.
     *
     * @param      ast reference to the AST node
     */

    private void computeAstBSP(AstNode ast){
        switch (ast.kind){
        case A_LEA:                               // terminal
            ast.isNull = false;
            ast.ini = new HashSet<BSsymbol>();
            ast.ini.add(newBSsymbol(astToBSele(ast,ELEOPEN),ast.seq));
            ast.fin = new HashSet<BSsymbol>();
            ast.fin.add(newBSsymbol(ast.seq,astToBSele(ast,ELECLOSE)));
            ast.dig = new HashSet<BSsymbol>();
            ast.dig.addAll(ast.ini);
            ast.dig.addAll(ast.fin);
            break;
        case A_EMP:                               // empty
            ast.isNull = true;                    // empty
            ast.ini = new HashSet<BSsymbol>();
            ast.ini.add(newBSsymbol(astToBSele(ast,ELEOPEN)));
            ast.fin = new HashSet<BSsymbol>();
            ast.fin.add(newBSsymbol(astToBSele(ast,ELECLOSE)));
            ast.dig = new HashSet<BSsymbol>();
            ast.dig.add(newBSsymbol(astToBSele(ast,ELEOPEN),astToBSele(ast,ELECLOSE)));
            break;
        default:
            ast.ini = new HashSet<BSsymbol>();
            ast.fin = new HashSet<BSsymbol>();
            ast.dig = new HashSet<BSsymbol>();
            for (AstNode a = ast.son; a != null; a = a.bro){
                computeAstBSP(a);
            }
        }
        switch (ast.kind){
        case A_ALT:                       // alternative
            ast.isNull = false;
            ast.ini.add(newBSsymbol(astToBSele(ast,ELEOPEN)));
            ast.fin.add(newBSsymbol(astToBSele(ast,ELECLOSE)));
            Set<BSsymbol> ini = new HashSet<BSsymbol>();
            Set<BSsymbol> fin = new HashSet<BSsymbol>();
            if (ast.son == null) ast.isNull = true;
            for (AstNode a = ast.son; a != null; a = a.bro){
                ast.isNull |= a.isNull;
                ini.addAll(a.ini);
                fin.addAll(a.fin);
                ast.dig.addAll(a.dig);
            }
            BSsymbol[] iniarr = ini.toArray(new BSsymbol[0]);
            for (int i = 0; i < iniarr.length; i++){
                ast.dig.add(newBSsymbol(astToBSele(ast,ELEOPEN),iniarr[i]));   // enter subexpression
            }
            BSsymbol[] finarr = fin.toArray(new BSsymbol[0]);
            for (int i = 0; i < finarr.length; i++){
                ast.dig.add(newBSsymbol(finarr[i],astToBSele(ast,ELECLOSE)));    // leave subexpression
            }
            break;
        case A_CON:                       // concatenation
            ast.isNull = true;
            ast.ini.add(newBSsymbol(astToBSele(ast,ELEOPEN)));
            ast.fin.add(newBSsymbol(astToBSele(ast,ELECLOSE)));
            AstNode prev = null;
            for (AstNode a = ast.son; a != null; a = a.bro){
                ast.isNull &= a.isNull;
                ast.dig.addAll(a.dig);
                if (a != ast.son){                // not first
                    iniarr = a.ini.toArray(new BSsymbol[0]);
                    finarr = prev.fin.toArray(new BSsymbol[0]);
                    for (int i = 0; i < finarr.length; i++){
                        for (int j = 0; j < iniarr.length; j++){
                            ast.dig.add(newBSsymbol(finarr[i],iniarr[j]));
                        }
                    }
                }
                prev = a;
            }
            iniarr = ast.son.ini.toArray(new BSsymbol[0]);
            for (int i = 0; i < iniarr.length; i++){
                ast.dig.add(newBSsymbol(astToBSele(ast,ELEOPEN),iniarr[i]));   // enter subexpression
            }
            finarr = prev.fin.toArray(new BSsymbol[0]);
            for (int i = 0; i < finarr.length; i++){
                ast.dig.add(newBSsymbol(finarr[i],astToBSele(ast,ELECLOSE)));    // leave subexpression
            }
            break;
        case A_GRO:                               // sub-re
            if (ast.groupKind == G_RE1 || ast.groupKind == G_GRO || ast.groupKind == G_BOD){
                ast.isNull = ast.son.isNull;
            } else {
                ast.isNull = true;
            }
            ast.ini.add(newBSsymbol(astToBSele(ast,ELEOPEN)));
            ast.fin.add(newBSsymbol(astToBSele(ast,ELECLOSE)));
            ast.dig.addAll(ast.son.dig);
            iniarr = ast.son.ini.toArray(new BSsymbol[0]);
            for (int i = 0; i < iniarr.length; i++){
                ast.dig.add(newBSsymbol(astToBSele(ast,ELEOPEN),iniarr[i]));   // enter subexpression
            }
            finarr = ast.son.fin.toArray(new BSsymbol[0]);
            for (int i = 0; i < finarr.length; i++){
                ast.dig.add(newBSsymbol(finarr[i],astToBSele(ast,ELECLOSE)));    // leave subexpression
            }
            if (ast.groupKind != G_GRO && ast.groupKind != G_OPT){
                iniarr = ast.son.ini.toArray(new BSsymbol[0]);
                finarr = ast.son.fin.toArray(new BSsymbol[0]);
                for (int i = 0; i < finarr.length; i++){
                    for (int j = 0; j < iniarr.length; j++){
                        ast.dig.add(newBSsymbol(finarr[i],iniarr[j]));
                    }
                }
            }
            if (ast.groupKind == G_RE0 || ast.groupKind == G_OPT){
                ast.dig.add(newBSsymbol(astToBSele(ast,ELEOPEN),astToBSele(ast,ELECLOSE))); // ( e )
            }
            break;
        }
        if (ast.pos.length == 0){           // top node
            AstNode e = this.eofAst;
            BSsymbol[] finarr = ast.fin.toArray(new BSsymbol[0]);
            for (int i = 0; i < finarr.length; i++){
                ast.dig.add(newBSsymbol(finarr[i],e.seq));
            }
        }
    }

    /**
     * Compute the n-grams for the tree rooted in the specified AST.
     *
     * @param      ast reference to the AST node
     * @param      dfa reference to the state table
     */

    /* N-grams
     *
     *      a || ^( .... !a   +  !a ...  
     *
     * I keep an array with the ngrams that start with a terminal or ^( and an array of arrays
     * of the ones that start with a symbol (that is not a terminal), subdivided by symbol.
     * Then I visit the first and build the next copy of it enlarging each ngram producing new
     * ones visiting the second. I terminate when no new ngrams are created.
     * The copies are actually kept in a unique one: each time only the portion of it that
     * has been built at the previous step is visited.
     * It is also easy to check duplicates because only the newly built part need be checked.
     *
     * The array of arrays is index by ast seq only because using directly the symbols in
     * bsstrings would make the index huge (bssymbols contain also the enter and leave flags).
     * When visiting the indexed array the entries that correspond to the desired one are
     * selected.
     */

    private void computeNgrams(AstNode ast, BStateTable dfa){
        BSsymbol[] arr = ast.dig.toArray(new BSsymbol[0]);
        BSsymbol[] list = new BSsymbol[10];
        BSsymbol[][] nexts = new BSsymbol[this.astMap.length][];
        int[] nextsi = new int[this.astMap.length];
        int last = 0;
        // build initial list and directory
        for (int i = 0; i < arr.length; i++){
            BSsymbol ele = arr[i];
            int start = ele.eleAt(0);
            int ending = ele.eleAt(-1);
            int kind = BSeleKind(start);
            if (isBSeleTerminal(start) || kind == ELEOPEN && BSeleToAst(start).pos.length == 0){  // starts with terminal or (^
                if (!isBSeleTerminal(ending)){       // does not terminate with terminal
                    if (last >= list.length){
                        list = Arrays.copyOf(list,list.length*2);
                    }
                    list[last++] = ele;
                }
            }
            if (!isBSeleTerminal(start)){            // add to directory
                int seq = start & ELEMASK;
                if (nexts[seq] == null){
                    nexts[seq] = new BSsymbol[10];
                } else if (nextsi[seq] >= nexts[seq].length){
                    nexts[seq] = Arrays.copyOf(nexts[seq],nexts[seq].length+10);
                }
                nexts[seq][nextsi[seq]++] = ele;
            }
        }
        // then process the list creating a new one, and repeating until no more can be created
        int curlist = 0;
        int end = last;
        for (;;){
            for (int i = curlist; i < end; i++){
                BSsymbol ele = list[i];
                int ending = ele.eleAt(-1);
                int seq = ending & ELEMASK;
                BSsymbol[] next = nexts[seq];
                for (int j = 0; j < nextsi[seq]; j++){
                    add: if (next[j].arr[0] == ending){             // next[j].eleAt(0)
                        if (!isAllowedSeq(ele,next[j])) break add;  // avoid endless strings ()()...
                        BSsymbol conc = newBSsymbolConc(ele,next[j]);
                        for (int k = end+1; k < last; k++){
                            if (list[k].equals(conc)) break add;    // no duplicates
                        }
                        if (last >= list.length){
                            list = Arrays.copyOf(list,list.length*2);
                        }
                        list[last++] = conc;
                    }
                }
            }
            if (last == end) break;         // empty next list
            curlist = end;
            end = last;
        }
        // add then the ones that start with a terminal or (^ and end with a terminal
        for (int i = 0; i < arr.length; i++){
            BSsymbol ele = arr[i];
            int start = ele.eleAt(0);
            int ending = ele.eleAt(-1);
            int kind = BSeleKind(start);
            if (isBSeleTerminal(start) || kind == ELEOPEN && BSeleToAst(start).pos.length == 0){  // starts with terminal or (^
                if (isBSeleTerminal(ending)){       // terminates with terminal
                    if (last >= list.length){
                        list = Arrays.copyOf(list,list.length*2);
                    }
                    list[last++] = ele;
                }
            }
        }

        // extract then the ones that start with (^ into Init, and the ones that start with
        // a terminal into Follow
        Set<BSsymbol> initSet = new HashSet<BSsymbol>();
        Set<BSsymbol> followSet = new HashSet<BSsymbol>();
        for (int i = 0; i < last; i++){
            BSsymbol ele = list[i];
            int start = ele.eleAt(0);
            int ending = ele.eleAt(-1);
            if (!isBSeleTerminal(ending)) continue;    // does not end with terminal
            if (!isBSeleTerminal(start)){              // does not start with terminal
                if (!purgedTag(initSet,ele,dfa.purge)){
                    initSet.add(ele);
                }
            } else {                                   // terminal
                if (!purgedTag(followSet,ele,dfa.purge)){
                    followSet.add(ele);
                }
            }
        }
        dfa.initSet = initSet;
        dfa.followSet = followSet;
    }

    /**
     * Check that the sequence obtained concatenating the first argument (trimmed by its last
     * element) with the second one is allowed, i.e. it does not contain the concatenation
     * of two bodies of the same *|+ group one of which generates the empty string.
     *
     * @param      s1 first BSsymbol
     * @param      s2 second BSsymbol
     * @return     <code>true</code> if allowed, <code>false</code> otherwise
     */

    private boolean isAllowedSeq(BSsymbol s1, BSsymbol s2){
        boolean res = true;
        BSsymbol s = newBSsymbol(s1.substring(0,-1),s2);
        doit: {
            // new paper condition: check that there are no duplicates
            for (int i = 0; i < s.length(); i++){
                int ele = s.eleAt(i);
                int k = BSeleKind(ele);
                if (k == 0) continue;         // not an open or close
                int seq = BSeleToAst(ele).seq;
                for (int j = i+1; j < s.length(); j++){
                    ele = s.eleAt(j);
                    if (k != BSeleKind(ele)) continue;
                    if (seq == BSeleToAst(ele).seq){
                        res = false;
                        break doit;
                    }
                }
            }
            break doit;

        } // doit
        return res;
    }

    /**
     * Tell if the element is not to be added because it has been purged.
     * N.B. the states reached from the initial one, when purged, have no convergences
     * because the paths in the AST that lead to a same terminal are unique.
     *
     * @param      set set
     * @param      el element
     * @param      purge "none" if no purge has to be done, "posix" if purge Posix has to be done
     * @return     <code>true</code> if not to be added, <code>false</code> otherwise
     */

    private boolean purgedTag(Set<BSsymbol> set, BSsymbol el, String purge){
        if (purge.equals("none")) return false;
        // el starts with a terminal and ends with a terminal, or starts with (^
        boolean res = false;
        int startseq = BSeleToAst(el.eleAt(0)).seq;
        int lastseq = BSeleToAst(el.eleAt(-1)).seq;

        BSsymbol tag = el.tag();
        for (Iterator<BSsymbol> i = set.iterator(); i.hasNext();){
            BSsymbol e = i.next();
            int starts = BSeleToAst(e.eleAt(0)).seq;
            int lasts = BSeleToAst(e.eleAt(-1)).seq;
            if (starts != startseq || lasts != lastseq) continue;

            // choose one of them
            BSsymbol t = e.tag();
            int cmp = 0;
            if (purge.equals("posix")){
                cmp = pat_comp_tags(tag,t);
            } else {
                compare: {
                    int bp0t1 = pat_bp0(tag,t);
                    int bp0t2 = pat_bp0(t,tag);
                    if (bp0t1 > bp0t2){
                        cmp = 1;
                        break compare;
                    }
                    if (bp0t2 > bp0t1){
                        cmp = -1;
                        break compare;
                    }
                    // bp0t1 == bp0t2
                    cmp = pat_subset_tags(tag,t);
                }
            }

            if (cmp > 0){                                     // tag of el less prior or equal, tell not to add
                res = true;
            } else if (cmp < 0){                              // remove the other
                set.remove(e);
            }
            break;
        }
        return res;
    }

    /**
     * Deliver a string of as many blanks * 2 as the argument.
     *
     * @param      lev nesting depth
     * @return     string
     */

    private String indent(int lev){
        String str = "";
        for (int i = 0; i < lev; i++){
            str += "  ";
        }
        return str;
    }

    /* Building the BSP DFA
     *
     * The BSP DFA is built in the usual way starting from the initial state containing
     * the items for the initial BSsymbols and computing all transitions to further states.
     * Items are represented by objects; another solution could be to enumerate the BSsymbols
     * (or even the items) and then use them instead of collecting items to create states.
     */

    /** An item of the DFA states. */

    private static class BSItem {

        /** The BSsymbol. */
        BSsymbol sym;

        /** The set of iids. */
        int[] left;

        /** The precomputed pat_minsp for tag of BSsymbol. */
        int minsp;
    }

    /** An edge (transition) of the DFA. */

    private static class BSTrans {

        /** The reference to the next transition. */
        BSTrans next;

        /** The reference to the next state (endpoint of this transition). */
        BState nextState;

        /** The terminal. */
        char sym;
    }

    /** A state of the DFA. */

    private static class BState {

        /** The reference to the next state in the list of states. */
        BState suc;

        /** The hash link. */
        BState hlink;
        /** The state number. */
        int number;

        /** The head of the list of transitions. */
        BSTrans transList;

        /** The items. */
        BSItem[] items;

        /** Whether this state is final. */
        boolean isFinal;

        /** Whether this state is ambiguous. */
        boolean isAmbig;

        /** The number of the last final item. */
        int finalItem;

        /** The precomputed pat_bp0 for each pair of items' tags. */
        int[][] bp0; 

        /** The precomputed pat_subset_tags for pair of items' tags. */
        int[][] subset;

        /** The number of items that have several iids. */
        int nrActiveIids;

        /**
         * Tell if this state contains the same items as the specified one.
         *
         * @param      other the other state
         * @return     <code>true</code> if it does, <code>false</code> otherwise
         */

        private boolean equals(BState other){
            if (this == other) return true;
            if (other == null) return false;
            return this.equals(other.items,other.items.length);
        }

        /**
         * Tell if this state contains the same items as the ones in the specified array.
         *
         * @param      other the array of items
         * @param      len the number of significant entries in the array
         * @return     <code>true</code> if it does, <code>false</code> otherwise
         */

        private boolean equals(BSItem[] other, int len){
            BSItem[] items = other;
            if (this.items.length != len){
                return false;
            }
            for (int i = 0; i < len; i++){
                if (!this.items[i].sym.equals(items[i].sym) ||
                    !Arrays.equals(this.items[i].left,items[i].left)){
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * Create a new state with the specified number and items.
     *
     * @param      n state number
     * @param      items array of items
     * @param      len the number of significant entries in the array
     * @return     reference to the created state
     */

    private BState newBState(int n, BSItem[] items, int len){
        BState s = new BState();
        s.number = n;
        if (items != null){
            s.items = Arrays.copyOfRange(items,0,len);
        }
        return s;
    }

    /** A state table for the BSP DFA. */

    private class BStateTable {

        /** The head of the list of states. */
        BState head;

        /** The tail of the list. */
        BState last;

        /** The last state added. */
        BState lastAdded;

        /** The hash directory. */
        BState[] hdir = new BState[64];
        /** The number of states. */
        int stateNr;

        /** The table of states. */
        BState[] table;

        /** The set of items of the initial state. */
        Set<BSsymbol> initSet; 

        /** The set of items that follow a terminal. */
        Set<BSsymbol> followSet;

        /** The disambiguation: none, posix, greedy. */
        String purge = "";

        /** The maximum number of items in states. */
        int maxItems;

        /** The map from integers to AST nodes. */
        AstNode[] astMap;

        /** The position field in the dotter. */
        int dshift;

        /** The mask to access the position field. */
        int dmask;
        /** The maximum symbol in edges. */
        char maxSymb;

        /** Whether there is an ambiguous state (i.e. the RE is ambiguous). */
        boolean isAmbig;

        /**
         * Search the state containing the specified items.
         *
         * @param      items array of items
         * @param      len number of significant entries in the array
         * @return     reference to the state, null if no such state
         */

        private BState search(BSItem[] items, int len){
            int hvalue = 0;
            for (int i = 0; i < len; i++){
                int[] arr = items[i].sym.arr;
                if (arr.length > 0){
                    hvalue += arr[0];
                }
            }
            hvalue &= 0x7FFFFFFF;
            hvalue %= this.hdir.length;
            for (BState h = this.hdir[hvalue]; h != null; h = h.hlink){
                if (h.equals(items,len)) return h;
            }
            return null;
        }

        /**
         * Add a state with the specified items, if not present.
         *
         * @param      items array of items
         * @param      len number of significant entries in the array
         */

        private boolean addUnique(BSItem[] items, int len){
            BState h = this.search(items,len);
            this.lastAdded = h;
            if (h != null){                           // found
                return false;
            }
            h = newBState(this.stateNr++,items,len);  // allocate entry
            if (this.last == null) this.head = h;     // append to list
            else this.last.suc = h;
            this.last = h;
            this.lastAdded = h;
            int hvalue = 0;
            for (int i = 0; i < len; i++){
                int[] arr = items[i].sym.arr;
                if (arr.length > 0){
                    hvalue += arr[0];
                }
            }
            hvalue &= 0x7FFFFFFF;
            hvalue %= this.hdir.length;
            h.hlink = this.hdir[hvalue];              // insert at beginning
            this.hdir[hvalue] = h;                    // link to last
            return true;
        }

        /**
         * Deliver a string representing the specified item's tag.
         *
         * @param      item item
         * @return     string
         */

        private String bsItemToTagString(BSItem item){
            return item.sym.substring(0,item.sym.length()-1).toString(this.astMap);
        }

        /**
         * Deliver a string representing the specified item.
         *
         * @param      item item
         * @return      string
         */

        private String bsItemToString(BSItem item){
            StringBuilder st = new StringBuilder();
            st.append("[");
            if (item.sym != null){
                st.append(item.sym.toString(this.astMap));
            }
            if (item.left != null){
                for (int j = 0; j < item.left.length; j++){
                    st.append(",#");
                    st.append(item.left[j]);
                }
            }
            st.append("]");
            st.append(" ");
            st.append(item.minsp);
            return st.toString();
        }

        /**
         * deliver a string representation of the specified state.
         *
         * @param      s reference to the state
         * @return     string
         */

        private String stateToString(BState s){
            StringBuilder st = new StringBuilder();
            st.append(s.number);
            if (s.isFinal) st.append(" final");
            if (s.isAmbig) st.append(" ambiguous");
            st.append(": ");
            for (int i = 0; i < s.items.length; i++){
                if (i > 0) st.append(", ");
                st.append(bsItemToString(s.items[i]));
            }
            for (BSTrans t = s.transList; t != null; t = t.next){
                st.append(" ");
                st.append(t.sym);
                st.append("->");
                st.append(t.nextState.number);
            }
            return st.toString();
        }

        /**
         * trace the specified state.
         *
         * @param      s reference to the state
         * @return     string 
         */

        private void traceState(BState s){
            Trc.out.printf("state: %s %s %s\n",s.number,
                s.isFinal ? "final" : "",
                s.isAmbig ? "ambiguous" : "");
            Trc.out.printf("  items:\n");
            for (int j = 0; j < s.items.length; j++){
                Trc.out.printf("    %s: %s\n",j,bsItemToString(s.items[j]));
            }
            Trc.out.printf("  transitions:\n");
            for (BSTrans t = s.transList; t != null; t = t.next){
                Trc.out.printf("    %s -> %s\n",t.sym,t.nextState.number);
            }
        }

        /**
         * Trace this table of states.
         */

        private void trace(){
            for (BState s = this.head; s != null; s = s.suc){
                traceState(s);
            }
        }

        /**
         * Deliver the tag of the specified item (a BSsymbol with the last terminal removed).
         *
         * @param      item item
         * @return     reference to the BSsymbol
         */

        private BSsymbol itemTag(BSItem item){
            return item.sym.substring(0,item.sym.length()-1);
        }

        /** The items of the state currently being built. */
        private BSItem[] bsitems; 

        /** Their number. */
        private int itmNr;

        /** the sets of iids of the items. */
        private IntSet[] iidSets;

        /**
         * Add an item with the specified BSsymbol and iids to the current items, if not present.
         *
         * @param      sym BSsymbol
         * @param      left iid
         * @return     index of the item
         */

        private int addbsItem(BSsymbol sym, int left){
            int itm = 0;
            int end = this.itmNr;
            int off = -1;
            while ((++off < end) &&          // search duplicates
                !this.bsitems[off].sym.equals(sym));
            if (off < end){                  // already present
                itm = off;
                BSItem item = this.bsitems[itm];
                this.iidSets[itm].add(left);
            } else {
                if (this.bsitems == null){
                    this.bsitems = new BSItem[10];
                    this.iidSets = new IntSet[10];
                } else if (this.bsitems.length <= this.itmNr){
                    this.bsitems = Arrays.copyOf(this.bsitems,this.itmNr+10);
                    this.iidSets = Arrays.copyOf(this.iidSets,this.itmNr+10);
                }
                BSItem item = new BSItem();
                this.bsitems[this.itmNr] = item;
                item.sym = sym;
                this.iidSets[this.itmNr] = new IntSet();
                this.iidSets[this.itmNr].add(left);
                item.minsp = sym.length() > 0 ? pat_minsp(itemTag(item)) : 0;
                itm = this.itmNr;
                this.itmNr++;
            }
            return itm;
        }

        /**
         * Add a new state with the items collected so far.
         */

        private BState addBState(){
            BState res = null;
            // scan the items, transform the iid sets in unique arrays and store in items
            // pointers to them
            for (int i = 0; i < this.itmNr; i++){
                int[] found = null;
                for (int j = 0; j < this.itmNr; j++){
                    if (this.iidSets[j].equals(this.iidSets[i])){
                        found = this.bsitems[j].left;
                        break;
                    }
                }
                if (found == null){
                    found = this.iidSets[i].toArray();
                }
                this.bsitems[i].left = found;
            }
            BSItem[] items = Arrays.copyOf(this.bsitems,this.itmNr);
            Arrays.sort(items,new Comparator<BSItem>(){
                public int compare(BSItem i1, BSItem i2){
                    if (i1.left.length != i2.left.length){
                        return i2.left.length - i1.left.length;
                    }
                    int n = i1.sym.arr.length;
                    if (i2.sym.arr.length < n) n = i2.sym.arr.length;
                    int i = 0;
                    int j = 0;
                    while (n-- != 0){
                        int c1 = i1.sym.arr[i++];
                        int c2 = i2.sym.arr[j++];
                        if (c1 != c2) return c1 - c2;
                    }
                    return i1.sym.arr.length - i2.sym.arr.length;
                }});
            if (addUnique(items,this.itmNr)){
                int nreof = 0;
                for (int i = 0; i < this.itmNr; i++){
                    if (items[i].sym.length() > 0){
                        int bssym = items[i].sym.eleAt(-1);     // last
                        char sym = BSeleToAst(bssym).sym;
                        if (sym == EOF){
                            this.lastAdded.isFinal = true;
                            this.lastAdded.finalItem = i;
                            nreof++;
                        }
                    }
                    if (items[i].left.length > 1){
                        this.lastAdded.isAmbig = true;
                        this.isAmbig = true;
                        this.lastAdded.nrActiveIids++;
                    }
                }
                if (nreof > 1){
                    this.lastAdded.isAmbig = true;
                    this.isAmbig = true;
                }
                // precomputed pat_bp0 and bat_subset_tags
                BState state = this.lastAdded;
                int len = state.items.length;
                if (len > 1){
                    state.bp0 = new int[len][];
                    state.subset = new int[len][];
                    for (int i = 0; i < len; i++){
                        state.bp0[i] = new int[len];
                        state.subset[i] = new int[len];
                        BSsymbol first = itemTag(state.items[i]);
                        for (int j = 0; j < len; j++){
                            BSsymbol second = itemTag(state.items[j]);
                            state.bp0[i][j] = pat_bp0(first,second);
                            state.subset[i][j] = pat_subset_tags(first,second);
                        }
                    }
                }
            } else {
            }
            if (this.itmNr > this.maxItems) this.maxItems = this.itmNr;
            res = this.lastAdded;
            return res;
        }

        /**
         * Add a transition from the specifed states with the specified symbol to the
         * specified state.
         *
         * @param      from start state
         * @param      to end state
         * @param      sym terminal
         * @return     reference to the created transition
         */

        private BSTrans addBSEdge(BState from, BState to, char sym){
            BSTrans t = null;
            sea: {
                BSTrans pr = null;
                for (t = from.transList; t != null; t = t.next){      // find edge or last
                    if ((t.nextState == to) &&                         // do not insert duplicates
                        (t.sym == sym)){
                        break sea;
                    }
                    pr = t;
                }
                t = new BSTrans();
                t.nextState = to;
                t.sym = sym;
                if (pr == null){                // append
                    from.transList = t;
                } else {
                    pr.next = t;
                }
            }
            return t;
        }

        /**
         * Create the compacted transition tables.
         */

        public void compactTables(){
            int[][] tabs = new int[this.stateNr][];            // tables before compression

            // find the max symbol to determine the length of the rows
            // and build table of states
            int maxsym = 0;
            this.table = new BState[this.stateNr];
            for (BState s = this.head; s != null; s = s.suc){
                this.table[s.number] = s;
                for (BSTrans t = s.transList; t != null; t = t.next){
                    if (t.sym > maxsym) maxsym = t.sym;
                }
            }
            int nsym = maxsym + 1;
            this.maxSymb = (char)maxsym;           // max symbol in edges

            // build the tables taking into account that the transitions are
            // already ordered by increasing symbol
            for (int i = 0; i < this.stateNr; i++){
                BState s = this.table[i];
                int nval = 0;
                for (BSTrans t = s.transList; t != null; t = t.next){
                    nval++;
                }
                tabs[i] = new int[nval*2 + 1];
                int k = 0;
                tabs[i][k++] = nsym;  // length
                for (BSTrans t = s.transList; t != null; t = t.next){
                    tabs[i][k++] = t.sym;
                    tabs[i][k++] = t.nextState.number;
                }
            }

            CombVector comb = new CombVector(0,
                CombVector.HOLES_ACCESSED |
                CombVector.FOLD_ROWS |
                CombVector.PAIRS);
            //comb.settrc("a");
            comb.merge(tabs);
            this.trtable = comb.tabMerged;
            this.check = comb.check;
            this.base = comb.base;

        }

        /**
         * Deliver a string representing the transition (action) of the compact transition
         * tables for the specified state and symbol.
         *
         * @param      state number of the state
         * @param      sym symbol
         * @return     string
         */

        private String transToString(int state, int sym){
            StringBuilder str = new StringBuilder();
            int bas = this.base[state];
            int start = bas+sym;
            int ele = this.check[start] == bas ? this.trtable[start] : 0;
            if (ele == 0) return null;
            int len = 1;
            if (ele < 0){            // array of actions
                ele = -ele;
                len = this.trtable[ele++];
                start = ele;
            }
            for (int k = 0; k < len; k++){
                if (str.length() > 0) str.append(" ");
                int val = this.trtable[start+k];
                str.append(val);
            }
            return str.toString();
        }

        /** The compacted table state,sym -> transitions. */
        int[] trtable;

        /** The check table. */
        int[] check;

        /** The base table. */
        int[] base;
    }


    /**
     * Build the BSP DFA.
     * It builds next states visiting all the symbols in the alphabet and taking the items
     * that end with them; then visits the follow set to pick up the ngrams that begin with
     * it.
     *
     * @param      ast reference to the root AST node
     * @return     reference to the created state table
     */

    private BStateTable buildBS(AstNode ast){
        return buildBS(ast,true);        // for BSP
    }

    /**
     * Build the BSP DFA.
     * It builds next states visiting all the symbols in the alphabet and taking the items
     * that end with them; then visits the follow set to pick up the ngrams that begin with
     * it.
     *
     * @param      ast reference to the root AST node
     * @param      bsp <code>true</code> BSP, <code>false</code> BS
     * @return     reference to the created state table
     */

    /* It orders the items by symbols so as to visit the ones that end with the same symbols,
     * and orders also the follow set so as to pick up quickly the ngrams that start with a
     * given symbol.
     */
    private BStateTable buildBS(AstNode ast, boolean bsp){
        BStateTable dfa = new BStateTable();
        dfa.astMap = this.astMap;
        dfa.purge = "posix";

        if (bsp){                     // BSP
            computeNgrams(ast,dfa);
        } else {                      // BS
            dfa.initSet = ast.ini;
            dfa.followSet = ast.dig;
        }

        BSsymbol[] arr = dfa.initSet.toArray(new BSsymbol[0]);
        for (int i = 0; i < arr.length; i++){
            dfa.addbsItem(arr[i],-1);
        }
        dfa.addBState();
        // build a table that links all the ngrams starting with the same symbol
        BSsymbol[][] digarr = new BSsymbol[dfa.astMap.length][];
        int[] digarri = new int[digarr.length];
        for (Iterator<BSsymbol> i = dfa.followSet.iterator(); i.hasNext();){
            BSsymbol e = i.next();
            int start = e.eleAt(0);
            if ((start & ELEKMASK) != 0) continue;
            AstNode a = this.astMap[start];
            if (a.kind != A_LEA) continue;
            if (start >= digarr.length){
//            if (digarr[start] == null){
                int newlen = digarr.length+10;
                if (start >= newlen) newlen = start + 10;
                digarr = Arrays.copyOf(digarr,newlen);
                digarri = Arrays.copyOf(digarri,newlen);
            }
            if (digarr[start] == null){
                digarr[start] = new BSsymbol[10];
            } else if (digarri[start] >= digarr[start].length){
                digarr[start] = Arrays.copyOf(digarr[start],digarr[start].length+10);
            }
            digarr[start][digarri[start]++] = e;
        }
        char[] endings = new char[this.alphabet.length+1];   // for the eof
        BSItem[][] buckets = new BSItem[endings.length][];
        int[][] bucketsid = new int[endings.length][];
        int[] bucketsi = new int[buckets.length];
        // visit the initial state and the ones generated after it
        // to create all states
        BState cur = dfa.head;                    // build the next ones
        while (cur != null){
            // determine the transitions to the next states
            // sort the items in buckets, in ascending ending symbol so as to keep
            // the transitions ordered
            int nele = 0;
            for (int i = 0; i < cur.items.length; i++){   // scan its items
                BSItem itm = cur.items[i];
                if (itm.sym.length() == 0) continue;
                int p = itm.sym.eleAt(-1);                // (())b: take b
                char sym = BSeleToAst(p).sym;
                int idx = Arrays.binarySearch(endings,0,nele,sym);
                if (idx >= 0){                            // bucket present
                    if (bucketsi[idx] >= buckets[idx].length){
                        buckets[idx] = Arrays.copyOf(buckets[idx],buckets[idx].length+10);
                        bucketsid[idx] = Arrays.copyOf(bucketsid[idx],bucketsid[idx].length+10);
                    }
                } else {
                    idx = -idx -1;
                    if (nele > 0 && idx < buckets.length){
                        System.arraycopy(buckets,idx,buckets,idx+1,nele-idx);
                        System.arraycopy(bucketsid,idx,bucketsid,idx+1,nele-idx);
                        System.arraycopy(bucketsi,idx,bucketsi,idx+1,nele-idx);
                        System.arraycopy(endings,idx,endings,idx+1,nele-idx);
                    }
                    endings[idx] = sym;
                    buckets[idx] = new BSItem[10];
                    bucketsid[idx] = new int[10];
                    bucketsi[idx] = 0;
                    nele++;
                }
                bucketsid[idx][bucketsi[idx]] = i;
                buckets[idx][bucketsi[idx]++] = itm;
            }
            for (int c = 0; c < nele; c++){                   // for all b the end an item
                dfa.itmNr = 0;
                for (int i = 0; i < bucketsi[c]; i++){        // scan items ending in same sym
                    BSItem itm = buckets[c][i];
                    int p = itm.sym.eleAt(-1);                // (())b: take b
                    for (int j = 0; j < digarri[p]; j++){
                        BSsymbol ele = digarr[p][j];
                        dfa.addbsItem(ele.substring(1,ele.length()),bucketsid[c][i]);
                    }
                }
                if (dfa.itmNr > 0){            // add the new state
                    BState next = dfa.addBState();
                    dfa.addBSEdge(cur,next,endings[c]);
                }
            }
            dfa.itmNr = 0;
            if (nele > 0 && endings[nele-1] == EOF){
                for (int i = 0; i < bucketsi[nele-1]; i++){   // scan items ending in eof
                    dfa.addbsItem(newBSsymbol(),bucketsid[nele-1][i]);
                }
            }
            if (dfa.itmNr > 0){            // add the new state
                BState next = dfa.addBState();
                dfa.addBSEdge(cur,next,EOF);
            }
            cur = cur.suc;
        }

        dfa.compactTables();

        dfa.dshift = 32-Integer.numberOfLeadingZeros(dfa.maxItems);
        dfa.dmask = (1 << dfa.dshift) - 1;
        return dfa;
    }

    /**
     * Deliver the depth in the tree denoted by the specified tag.
     *
     * @param      tag tag
     * @return     depth
     */

    private int pat_height(int tag){
        AstNode ele = BSeleToAst(tag);
        int res = ele.pos.length;
        if (BSeleKind(tag) == ELEOPEN){    // node entry
            res++;
        }
        // TRACE(O,"pat_height %s %s\n",BSeleToString(tag),res);
        return res;
    }

    /**
     * Deliver the minimum height of the elements of the specified BSsymbol,
     * possibly terminated with a terminal.
     *
     * @param      tag BSsymbol
     * @return     height
     */

    private int pat_minsp(BSsymbol tag){
        if (tag == null || tag.length() <= 1) return 0;
        int res = Integer.MAX_VALUE;
        for (int i = 0; i < tag.length()-1; i++){
            int ele = tag.eleAt(i);
            int kind = BSeleKind(ele);
            if (kind != ELEOPEN && kind != ELECLOSE){    // terminal
                break;
            }
            int v = pat_height(ele);
            if (v < res){
                res = v;
            }
        }
        return res;
    }

    /**
     * Deliver the minimum height of the BSsymbol alpha trimmed by the prefix common to beta
     * except for the last element of the prefix.
     *
     * @param      alpha BSsymbol
     * @param      beta BSsymbol
     * @return     height
     */

    private int pat_bp0(BSsymbol alpha, BSsymbol beta){
        int lastsp = 0;
        int minsp = 0;
        int lastidx = 0;
        int len = alpha.length();
        if (beta.length() < len) len = beta.length();
        for (int i = 0; i < len; i++){
            if (alpha.eleAt(i) != beta.eleAt(i)){    // found end of common prefix
                if (i > 0){                          // prefix non-empty
                    lastsp = pat_height(alpha.eleAt(i-1));
                    lastidx = i - 1;
                }
                minsp = Integer.MAX_VALUE;
                for (int j = i; j < alpha.length(); j++){
                    int v = pat_height(alpha.eleAt(j));
                    if (v < minsp){
                        minsp = v;
                    }
                }
                break;
            }
        }
        int res = lastsp;
        if (minsp < res){
            res = minsp;
        }
        return res;
    }

    /**
     * Tell which tag is prior between the two specified ones.
     *
     * @param      t1 first tag
     * @param      t2 second tag
     * @return     &lt; = or &gt; 0 if this tag precedes, is equal or follows the other
     */

    private int pat_comp_tags(BSsymbol t1, BSsymbol t2){
        int bp0t1 = pat_bp0(t1,t2);
        int bp0t2 = pat_bp0(t2,t1);
        if (bp0t1 > bp0t2){
            return -1;                    // t1 prior to t2: t1 <. t2
        }
        if (bp0t2 > bp0t1){
            return 1;                     // t2 prior to t1: t2 <. t1
        }
        // bp0t1 == bp0t2
        return pat_subset_tags(t1,t2);
    }

    /**
     * Tell if the first position is prior to the second one.
     *
     * @param      e1 first position
     * @param      e2 second position
     * @return     &lt; = or &gt; 0 if this tag precedes, is equal or follows the other
     */

    private int compareToBSelemPos(int e1, int e2){
        // simplified test for asts' seq's ordered as positions
        return BSeleToAst(e1).seq - BSeleToAst(e2).seq;
        /*
        int[] p1 = BSeleToAst(e1).pos;
        int[] p2 = BSeleToAst(e2).pos;
        int n = p1.length;
        if (p2.length < n) n = p2.length;
        int i = 0;
        int j = 0;
        while (n-- != 0){
            int c1 = p1[i++];
            int c2 = p2[j++];
            if (c1 != c2) return c1 - c2;
        }
        return p1.length - p2.length;
        */
    }

    /**
     * Tell if the first BSsymbol opens a construct before the second one
     * (unicode "square image of" relation symbol, latex \sqsubset, see Okui
     * paper).
     *
     * @param      t1 first tag
     * @param      t2 second tag
     * @return     -1 if t1 &x228f; t2, 1 if t2 &x228f; t1, 0 otherwise
     */

    /*                       .-------. a\(a |^| b)
     * t1   a1  a2 ... an  | x ...
     *                       ^ t1/t2
     * t2   a1  a2 ... an  | y ...
     *      `----v------'
     *      common prefix
     */

    private int pat_subset_tags(BSsymbol t1, BSsymbol t2){
        // take first element after common prefix
        int len = t1.length();
        if (t2.length() < len) len = t2.length();
        boolean incl1 = false;
        boolean incl2 = false;
        int prefix = -1;
        for (int i = 0; i < len; i++){
            if (t1.eleAt(i) != t2.eleAt(i)){           // found end of common prefix
                prefix = i;
                break;
            }
        }
        int firstt1 = -1;
        int firstt2 = -1;
        if (prefix >= 0){                              // there is a common prefix
            firstt1 = prefix;
            firstt2 = prefix;
        } else {
            if (len == t1.length() && len < t2.length()){         // t1 is a head of t2
                firstt2 = len;
            } else if (len == t2.length() && len < t1.length()){  // t2 is a head of t1
                firstt1 = len;
            }
        }
        if (firstt1 >= 0) 
        if (firstt2 >= 0) 
        // determine if t1 <. t2

        if (firstt1 >= 0 &&                    // t1/t2 exists
            BSeleKind(t1.eleAt(firstt1)) == ELEOPEN){    // and is (p
            if (firstt2 < 0){                  // t2/t1 does not exist
                incl1 = true;
            } else {
                if (BSeleKind(t2.eleAt(firstt2)) == ELEOPEN){      // it is (q
                    if (compareToBSelemPos(t1.eleAt(firstt1),t2.eleAt(firstt2)) < 0){
                        incl1 = true;
                    }
                } else {
                    incl1 = true;
                }
            }
        }
        if (!incl1){
            // determine if t2 <. t1
            if (firstt2 >= 0 &&                  // t2/t1 exists
                BSeleKind(t2.eleAt(firstt2)) == ELEOPEN){            // and is (p
                if (firstt1 < 0){                // t1/t2 does not exist
                    incl2 = true;
                } else {
                    if (BSeleKind(t1.eleAt(firstt1)) == ELEOPEN){   // it is (q
                        if (compareToBSelemPos(t2.eleAt(firstt2),t1.eleAt(firstt1)) < 0){
                            incl2 = true;
                        }
                    } else {
                        incl2 = true;
                    }
                }
            }
        }
        int res = 0;
        if (incl1) res = -1;                 // t1 prior to t2: t1 <. t2
        if (incl2) res = 1;                  // t2 prior to t1: t2 <. t1
        return res;
    }

    // ---------- Matching  -----------------


    /**
     * Deliver the action corresponding to the specified state and symbol in the specified
     * DFA compressed transition tables.
     *
     * @param      dfa reference to the transition tables
     * @param      state number of the state
     * @param      sym symbol
     * @return     action
     */

    private int faAction(BStateTable dfa, int state, int sym){
        int base = dfa.base[state];
        int nextstate = dfa.check[base+sym] == base ? dfa.trtable[base+sym] : 0;
        return nextstate;
    }

    /** The start index in the compressed table of the array of actions. */
    private int faStart;

    /**
     * Deliver the array of actions corresponding to the specified state and symbol in the
     * specified DFA compressed transition tables.
     *
     * @param      dfa reference to the transition tables
     * @param      state number of the state
     * @param      sym symbol
     * @return     length of the array, whose start is returned in faStart
     */

    private int faActions(PATStateTable dfa, int state, int sym){
        int len = dfa.base[state];
        this.faStart = len + sym;
        len = dfa.check[this.faStart] == len ? dfa.trtable[this.faStart] : 0;
        if (len > 0){
            len = 1;
        } else if (len < 0){
            this.faStart = -len;
            len = dfa.trtable[this.faStart++];
        }
        return len;
    }

    /** The stack of states and items encountered during matching. */
    private int[] pistack; 

    /**
     * Trace the specified frame of the pistack.
     *
     * @param      dfa reference to the state tables
     * @param      i index of the frame
     */

    private void tracePistack(BStateTable dfa, int i){
        int start = i;
        int si = i + 1;                 // start of set of items
        i += this.pistack[i] - 1;
        BState s = dfa.table[this.pistack[i]];
        //s.trace(dfa);
        Trc.out.printf("%s: len: %s state: %s, nr items %s items:",
            start,this.pistack[start],this.pistack[i],s.items.length);
        for (int j = 0; j < s.items.length; j++){
            if ((this.pistack[si + (j>>>INT_SHIFTS)] & (1 << (j & INT_MASK))) != 0){
                Trc.out.printf(" %s",j);
            }
        }
        Trc.out.printf("\n");
        checkPistack(dfa,start,"tracePistack");
    }

    /**
     * Check the consistence of the specified frame of the pistack.
     *
     * @param      dfa reference to the state tables
     * @param      i index of the frame
     * @param      msg text message to show the caller
     * @return     <code>true</code> if it is inconsistent, <code>false</code> otherwise
     */

    private boolean checkPistack(BStateTable dfa, int i, String msg){
        boolean err = false;
        if (this.pistack[i] < 2){
            err = true;
            Trc.out.printf("!!checkPistack %s at: %s short: %s\n",msg,i,this.pistack[i]);
        }
        int start = i;
        int si = i + 1;                 // start of set of items
        i += this.pistack[i] - 1;
        BState s = dfa.table[this.pistack[i]];
        for (int m = si, q = 0; m < i; m++, q = Integer.SIZE*(m-si)){
            int w = this.pistack[m];
            for (; w != 0; w = w>>>1, q++){
                if ((w & 1) == 0) continue;
                // q = item
                if (q >= s.items.length){
                    err = true;
                    Trc.out.printf("!!checkPistack %s at: %s %s w %s\n",msg,m,q,w);
                }
            }
        }
        return err;
    }

    /**
     * Check the consistence of the pistack.
     *
     * @param      dfa reference to the state tables
     * @param      msg text message to show the caller
     * @return     <code>true</code> if it is inconsistent, <code>false</code> otherwise
     */

    private boolean checkPistack(BStateTable dfa, String msg){
        boolean err = false;
        for (int i = 0; i < this.psp; i += this.pistack[i]){
            if (checkPistack(dfa,i,msg)){
                err = true;
            }
        }
        return err;
    }

    /**
     * Mark the specified item into the speficied frame of the pistack.
     *
     * @param      dfa reference to the state tables
     * @param      i index of the frame
     * @param      item number of the item
     */

    private void pistackAdd(BStateTable dfa, int frame, int item){
        pistackAssign(dfa,frame,item,true);
    }

    /**
     * Unmark the specified item into the speficied frame of the pistack.
     *
     * @param      dfa reference to the state tables
     * @param      i index of the frame
     * @param      item number of the item
     */

    private void pistackRemove(BStateTable dfa, int frame, int item){
        pistackAssign(dfa,frame,item,false);
    }

    /**
     * Mark or mark the specidied item into the speficied frame of the pistack.
     *
     * @param      dfa reference to the state tables
     * @param      i index of the frame
     * @param      item number of the item
     * @param      set <code>true</code> to mark, <code>false</code> to unmark
     */

    private void pistackAssign(BStateTable dfa, int frame, int item, boolean set){
        int next = frame + this.pistack[frame];
        BState s = dfa.table[this.pistack[next-1]];
        if (item >= s.items.length){
            Trc.out.printf("pistackSet frame %s state %s items %s, item %s\n",
                frame,s.number,s.items.length,item);
            new Throwable().printStackTrace(Trc.out);
            System.exit(1);
        }
        this.pistack[frame + 1 + (item>>>INT_SHIFTS)] |= (1 << (item & INT_MASK));
    }

    /**
     * Trace the pistack.
     *
     * @param      dfa reference to the state tables
     */

    private void tracePistack(BStateTable dfa){
        for (int i = 0; i < this.psp; i += this.pistack[i]){
            tracePistack(dfa,i);
        }
    }


    /** The stack of states encountered during matching. */
    private BState[] pstack; 
    /** The number of shifts to get the offset in a bitset represented with integers. */
    private static final int INT_SHIFTS = Integer.SIZE == 32 ? 5 : 6;

    /** The mask get the bit in a bitset represented with integers. */
    private static final int INT_MASK = Integer.SIZE == 32 ? 0x1f : 0x3f;

    /**
     * Deliver a new PathNode with the specified data.
     *
     * @param      from from state
     * @return     reference to the created PathNode
     */

    private void newPathNodeClassi(BState from){
        int setsize = (from.items.length + Integer.SIZE - 1) >>> INT_SHIFTS;
        if (this.pistack == null){
            this.pistack = new int[100];
            this.memory += 404;
            this.mempstack += 404;
            this.nrnew++;
        } else if (this.pistack.length - this.psp < setsize+2){
            this.garbage += this.pistack.length * 4 + 4;
            int newlen = this.pistack.length*2;
            if (newlen - this.pistack.length < setsize+2) newlen = this.pistack.length + setsize+2;
            this.pistack = Arrays.copyOf(this.pistack,newlen);
            this.memory += this.pistack.length * 4 + 4;
            this.mempstack += this.pistack.length * 4 + 4;
            this.nrcopyof++;
        }
        this.pistack[this.psp++] = setsize + 2;
        while (setsize-- > 0){
            this.pistack[this.psp++] = 0;
        }
        this.pistack[this.psp++] = from.number;
    }

    /**
     * Deliver a new PathNode with the specified data.
     *
     * @param      from from state
     * @return     reference to the created PathNode
     */

    private void newPathNodeClass(BState from){
        if (this.pstack == null){
            this.pstack = new BState[100];
            this.memory += 404;
            this.mempstack += 404;
            this.nrnew++;
        } else if (this.psp >= this.pstack.length){
            this.garbage += this.pstack.length * 4 + 4;
            this.pstack = Arrays.copyOf(this.pstack,this.pstack.length*2);
            this.memory += this.pstack.length * 4 + 4;
            this.mempstack += this.pstack.length * 4 + 4;
            this.nrcopyof++;
        }

        this.pstack[this.psp++] = from;
    }

    /** The stack pointer. */
    private int psp;

    /**
     * Match the text against the compiled RE. Upon return, <code>error</code> is <code>false</code>
     * if the match is successful, and <code>true</code> otherwise.
     *
     * @param      text string to be matched
     * @param      dfa reference to the transition tables
     * @param      maketree <code>true</code> to build alto the tree, <code>false</code> otherwise
     * @param      choose &gt; 0 to choose the prior tree: 1 BSP, 2 BSPP
     */

    private void match(String text, BStateTable dfa, boolean maketree, int choose){
        this.callsBSP++;
        this.tokens += text.length();
        this.error = false;
        this.psp = 0;                // init stack of states
        BState state = dfa.head;
        this.ambiguous = false;
        this.plistEnd = 0;                // init list of transitions
        for (int i = 0; i < text.length(); i++){
            char sym = text.charAt(i);
            if (sym > dfa.maxSymb){
                this.error = true;
                break;
            }
            BState next = null;
            int nextstate = faAction(dfa,state.number,sym);
            if (nextstate == 0){
                this.error = true;
                break;
            }
            next = dfa.table[nextstate];

            newPathNodeClass(state);
            state = next;
            if (state.isAmbig) this.ambiguous = true;
        }
        if (state.isAmbig) this.ambiguous = true;
        if (!state.isFinal){
            this.error = true;
        } else {
            int nextstate = faAction(dfa,state.number,EOF);
            BState next = dfa.table[nextstate];
            newPathNodeClass(state);
            if (this.ambiguous){
                newPathNodeClass(next);
            }
        }
        if (!this.error){
            if (choose > 0){
                compareBS(dfa,maketree,choose);
                // compareBS1(dfa,maketree,choose);
            }
        }
    }

    /** Whether the matched string is ambiguous. */
    private boolean ambiguous;

    // ---------- Choosing the prior  -----------------

    private void matchi(String text, BStateTable dfa, boolean maketree, int choose){
        this.callsBSP++;
        this.tokens += text.length();
        this.error = false;
        this.psp = 0;                // init stack of states
        BState state = dfa.head;
        this.ambiguous = false;
        this.plistEnd = 0;                // init list of transitions
        for (int i = 0; i < text.length(); i++){
            char sym = text.charAt(i);
            if (sym > dfa.maxSymb){
                this.error = true;
                break;
            }
            BState next = null;
            int nextstate = faAction(dfa,state.number,sym);
            if (nextstate == 0){
                this.error = true;
                break;
            }
            next = dfa.table[nextstate];

            //int si = this.psp;
            newPathNodeClassi(state);
            //checkPistack(dfa,si,"match");
            state = next;
            if (state.isAmbig) this.ambiguous = true;
        }
        if (state.isAmbig) this.ambiguous = true;
        if (!state.isFinal){
            this.error = true;
        } else {
            int nextstate = faAction(dfa,state.number,EOF);
            BState next = dfa.table[nextstate];
            newPathNodeClassi(state);
            if (this.ambiguous){
                newPathNodeClassi(next);
            }
        }
        //checkPistack(dfa,"match");
        if (!this.error){
            if (choose > 0){
                compareBSi(dfa,maketree,choose);
                // compareBS1(dfa,maketree,choose);
            }
        }
    }

    /**
     * Choose the tree using the Okui method applied to the BSP(P) DFA.
     *
     * @param      dfa reference to the DFA
     * @param      maketree <code>true</code> to build alto the tree, <code>false</code> otherwise
     * @param      choose &gt; 0 to choose the prior tree: 1 BSP, 2 BSPP
     */

    private void compareBSi(BStateTable dfa, boolean maketree, int choose){
        cmp: if (this.ambiguous){
            markPathsi(dfa);                         // mark items belonging to trees
            //checkPistack(dfa,"compareBSi mark");
            if (!this.ambiguous) break cmp;         // ambiguous recomputed on the iids

            // scan then the list of states and choose the prior tree
            if (choose == 1){
                BLEN = dfa.maxItems;
                DLEN = BLEN*BLEN;

                if (this.data1 == null || this.data1.length < DLEN * 2){
                    if (this.data1 != null) this.garbage += (this.data1.length * 4 + 4) * 2;
                    this.data1 = new int[DLEN * 2];
                    this.data2 = new int[DLEN * 2];
                    this.memory += (this.data1.length * 4 + 4) * 2;
                    this.memdata += (this.data1.length * 4 + 4) * 2;
                    this.nrnew++;
                }
            }

            int bspComp = 0;
            int lastframe = dfa.table[this.pistack[this.psp-1]].items.length;
            lastframe = this.psp - ((lastframe + Integer.SIZE - 1) >>> INT_SHIFTS) - 2;
            for (int i = 0; i < lastframe; i += this.pistack[i]){
                computeStepi(dfa,this.data1,this.data2,i);
                //checkPistack(dfa,"compareBS");

                int[] data = this.data1;
                this.data1 = this.data2;
                this.data2 = data;
                if (this.error) return;     // only in case of time supervision
            }
        }
        BSsymbol tree = null;
        if (maketree){
            tree = pnodeToTreeStringi(dfa);
        }
    }

    /**
     * Mark the items reacheable in the graph of items starting from the final ones.
     *
     * @param      dfa reference to the DFA
     */

    private void markPathsi(BStateTable dfa){
        this.ambiguous = false;
        // create item set for last state
        this.pistack[this.psp-2] = 1;         // item nr. 0
        int cur = this.psp - 3;               // index of last frame
        int prev = 0;
        BState p = null;
        if (cur > 0){
            p = dfa.table[this.pistack[cur-1]];
            prev = cur - ((p.items.length + Integer.SIZE - 1) >>> INT_SHIFTS) - 2;
        }
        for (; cur >= 0;){
            int st = cur + this.pistack[cur] - 1;
            p = dfa.table[this.pistack[st]];
            // mark the items that belong to trees
            int mrkitms = cur + 1;
            boolean amb = false;
            // visit the marked items and mark the ones of their items sets
            for (int m = mrkitms, j = 0; m < st; m++, j = Integer.SIZE*(m-mrkitms)){
                int w = this.pistack[m];
                for (; w != 0; w = w>>>1, j++){
                    int[] iids = null;
                    iids = p.items[j].left;
                    if (iids.length > 1) amb = true;
                    id: for (int k = 0; k < iids.length; k++){
                        marknr++;
                        int iid = iids[k];
                        this.pistack[prev + 1 + (iid>>>INT_SHIFTS)] |= (1 << (iid & INT_MASK));
                        //pistackAdd(dfa,prev,iid);
                    }
                }
            }
            //checkPistack(dfa,prev,"marki");
            if (amb){
                this.ambiguous = true;
            }
            cur = prev;
            if (cur > 0){
                p = dfa.table[this.pistack[cur-1]];
                prev = cur - ((p.items.length + Integer.SIZE - 1) >>> INT_SHIFTS) - 2;
            } else {
                break;
            }
        }

    }

    /**
     * Mark the steps done in the specified pnode on the active paths, choosing the most prior
     * one when there are several converging on a same node in the path and updating the values
     * that represent the previous comparisons.
     *
     * @param      dfa reference to the DFA
     * @param      prev previous data array
     * @param      data data array
     * @param      pindex index to the current stack frame
     */

    private int[] activeIids;

    private void computeStepi(BStateTable dfa, int[] prev, int[] data, int pindex){
        int nextframe = pindex + this.pistack[pindex];
        BState pnode = dfa.table[this.pistack[nextframe-1]];
        BState pnext = dfa.table[this.pistack[nextframe+this.pistack[nextframe]-1]];
        BState from = pnode;
        BSItem[] curitems = pnode.items;
        BSItem[] nextitems = pnext.items;
        int curset = pindex + 1;
        if (this.activeIids == null || this.activeIids.length < dfa.maxItems){
            if (this.activeIids != null) this.garbage += this.activeIids.length * 4 + 4;
            this.activeIids = new int[dfa.maxItems*2];
            this.memory += this.activeIids.length * 4 + 4;
        }
        int acti = 0;
        int endset = nextframe + this.pistack[nextframe] - 1;
        for (int m = nextframe+1, q = 0; m < endset; m++, q = Integer.SIZE*(m-nextframe-1)){
            int w = this.pistack[m];
            for (; w != 0; w = w>>>1, q++){
                if ((w & 1) == 0) continue;
                // q = item
                int[] arr = nextitems[q].left;
                int p = arr[0];                               // iid
                choose: if (arr.length > 1){                  // determine winner actIid
                    int winner = p;
                    // test if iid set already processed
                    if ((this.pistack[curset + (p>>>INT_SHIFTS)] & (1 << (p & INT_MASK))) == 0) break choose;
                    int p1 = arr[1];
                    if ((this.pistack[curset + (p1>>>INT_SHIFTS)] & (1 << (p1 & INT_MASK))) == 0) break choose;

                    int rho = -1;
                    int rhop = -1;
                    for (int j = 1; j < arr.length; j++){
                        // compare the winner and the current one
                        int ap = arr[j];
                        int app = winner;
                        rho = prev[BLEN*ap+app];
                        rhop = prev[BLEN*app+ap];
                        int mrho = 0;
                        mrho = curitems[ap].minsp;
                        int mrhop = curitems[app].minsp;
                        if (mrho < rho) rho = mrho;
                        if (mrhop < rhop) rhop = mrhop;
                        int D = prev[DLEN + BLEN*app + ap];
                        if (rho > rhop || (rho == rhop && D < 0)){
                            winner = ap;
                        }
                        this.joinnr++;
                    }
                    // unmark all the others
                    for (int j = 0; j < arr.length; j++){
                        p = arr[j];
                        if (p == winner) continue;
                        this.pistack[curset + (p>>>INT_SHIFTS)] &= ~(1 << (p & INT_MASK));
                        //pistackRemove(dfa,curset-1,p);
                    }
                    //checkPistack(dfa,pindex,"computeStepi");
                    p = winner;
                }  // choose
                this.activeIids[acti++] = q;
                this.activeIids[acti++] = p;
                // p is the winner item in the current frame among the ones that converge on
                // a same item in the next; determine the active item in the previous frame
                // that originated it
                int porig = 0;
                if (pindex > 0){
                    int[] lef = null;
                    lef = curitems[p].left;
                    porig = lef[0];          // origin, to check that pp below is has the same pat state
                    if (lef.length > 1){
                        // search the marked one in the previous frame
                        BState b = dfa.table[this.pistack[pindex-1]];
                        int pre = pindex - ((b.items.length + Integer.SIZE - 1) >>> INT_SHIFTS) - 1;
                        for (int j = 0; j < lef.length; j++){
                            porig = lef[j];
                            if ((this.pistack[pre + (porig>>>INT_SHIFTS)] & (1 << (porig & INT_MASK))) != 0){
                                break;
                            }
                        }
                    }
                }

                // visit the ones that are before the current one
                // compute here what depends only on p and q
                int[] subsetp = from.subset == null ? null : from.subset[p];
                int[] bp0p = from.bp0 == null ? null : from.bp0[p];
                int DLENp = DLEN+BLEN*p;
                int DLENq = DLEN+BLEN*q;
                int BLENp = BLEN*p;
                int BLENq = BLEN*q;
                int mrho = from.items[p].minsp;

                // process pairs of marked items of the current list

                // determine B, D for the next frame
                for (int k = 0; k < acti; k++){
                    int qp = this.activeIids[k++];
                    int pp = this.activeIids[k];
                    int rho;
                    int rhop;
                    // check that the items have the same origin in the previous pnode
                    int pporig = 0;
                    if (pindex > 0){
                        int[] lef = curitems[pp].left;
                        pporig = lef[0];          // origin, to check that pp below is has the same pat state
                        if (lef.length > 1){
                            // search the marked one in the previous frame
                            BState b = dfa.table[this.pistack[pindex-1]];
                            int pre = pindex - ((b.items.length + Integer.SIZE - 1) >>> INT_SHIFTS) - 1;
                            for (int j = 0; j < lef.length; j++){
                                pporig = lef[j];
                                if ((this.pistack[pre + (pporig>>>INT_SHIFTS)] & (1 << (pporig & INT_MASK))) != 0){
                                    break;
                                }
                            }
                        }
                    }
                    if (porig == pporig || pindex == 0){              // p = p' in the paper
                        // here we have two transitions from the same item leading to different items
                        // probably I could compare instead their active iid's
                        if (p == pp) continue;
                        int cmp = subsetp[pp];
                        // to compare two tags, take away the common prefix, then the first
                        // is prior if it starts with (p and the second: starts with )q or
                        // with (q with p <. q

                        rho = bp0p[pp];             // rho = bp0(alpha,alpha');
                        rhop = from.bp0[pp][p];     // rho' = bp0(alpha',alpha)
                        if (rho != rhop || cmp != 0){
                            data[DLENq+qp] = rho == rhop ? -cmp : rho-rhop;
                        }
                        this.forknr++;
                    } else {
                        rho = prev[BLENp+pp];
                        rhop = prev[BLEN*pp+p];
                        int mrhop = from.items[pp].minsp;
                        if (mrho < rho) rho = mrho;        // rho = min{rho, minsp(alpha)};
                        if (mrhop < rhop) rhop = mrhop;    // rho = min{rho, minsp(alpha)};
                        data[DLENq+qp] = rho == rhop ? prev[DLENp+pp] : rho-rhop;
                        this.parallelnr++;
                    }
                    data[DLEN+BLEN*qp+q] = -data[DLEN+BLEN*q+qp];        // D[q'][q] = -D[q][q']
                    data[BLENq+qp] = rho;
                    data[BLEN*qp+q] = rhop;
                }
            }
        }
    }

    /**
     * Deliver a string representing the tree of the active items in the pnodes list.
     *
     * @param      dfa reference to the DFA
     */

    private BSsymbol pnodeToTreeStringi(BStateTable dfa){
        int iid = 0;
        int len = 0;
        if (!this.ambiguous){
            BState p = dfa.table[this.pistack[this.psp-1]];
            iid = p.finalItem;                 // there is only one final item
        }

        // compute the length of the tree
        for (int i = this.psp; i > 0;){
            BState p = dfa.table[this.pistack[i-1]];
            i -= ((p.items.length + Integer.SIZE - 1) >>> INT_SHIFTS) + 2;  // start current frame
            BSItem item = p.items[iid];
            len += item.sym.arr.length;
            if (i > 0){
                if (item.left.length > 0){
                    BState pr = dfa.table[this.pistack[i-1]];
                    int iset = i - ((pr.items.length + Integer.SIZE - 1) >>> INT_SHIFTS) - 1;
                    for (int q = 0; q < item.left.length; q++){
                        iid = item.left[q];
                        if ((this.pistack[iset + (iid>>>INT_SHIFTS)] & (1 << (iid & INT_MASK))) != 0){
                            break;
                        }
                    }
                    iid = item.left[0];
                } else {
                    iid = item.left[0];
                }
            }
        }
        this.treeLen = len;
        iid = 0;
        if (!this.ambiguous){
            BState p = dfa.table[this.pistack[this.psp-1]];
            iid = p.finalItem;                 // there is only one final item
        }
        if (this.bsarr == null){
            this.bsarr = new int[len];
            this.memory += len * 4 + 4;
            this.membsarr += len * 4 + 4;
        } else if (this.bsarr.length < len){
            this.garbage += this.bsarr.length * 4 + 4;
            int newlen = this.bsarr.length < 1000000 ? this.bsarr.length*2 : this.bsarr.length+1000000;
            if (newlen < len) newlen = len;
            this.bsarr = new int[newlen];
            this.memory += newlen * 4 + 4;
            this.membsarr += newlen * 4 + 4;
            this.nrnew++;
        }
        for (int i = this.psp; i > 0;){
            BState p = dfa.table[this.pistack[i-1]];
            i -= ((p.items.length + Integer.SIZE - 1) >>> INT_SHIFTS) + 2;  // start current frame
            BSItem item = p.items[iid];
            len -= item.sym.arr.length;
            System.arraycopy(item.sym.arr,0,this.bsarr,len,item.sym.arr.length);
            if (i > 0){
                if (item.left.length > 0){
                    BState pr = dfa.table[this.pistack[i-1]];
                    int iset = i - ((pr.items.length + Integer.SIZE - 1) >>> INT_SHIFTS) - 1;
                    for (int q = 0; q < item.left.length; q++){
                        iid = item.left[q];
                        if ((this.pistack[iset + (iid>>>INT_SHIFTS)] & (1 << (iid & INT_MASK))) != 0){
                            break;
                        }
                    }
                    iid = item.left[0];
                } else {
                    iid = item.left[0];
                }
            }
        }
        if (this.tree == null){
            this.tree = new BSsymbol();
            this.memory += 8;
            this.nrnew++;
        }
        this.tree.arr = this.bsarr;
        return tree;
    }



    /**
     * Choose the tree using the Okui method applied to the BSP(P) DFA.
     *
     * @param      dfa reference to the DFA
     * @param      maketree <code>true</code> to build alto the tree, <code>false</code> otherwise
     * @param      choose &gt; 0 to choose the prior tree: 1 BSP, 2 BSPP
     */

    private void compareBS(BStateTable dfa, boolean maketree, int choose){
        cmp: if (this.ambiguous){
            markPaths(dfa);                         // mark items belonging to trees
            if (!this.ambiguous) break cmp;         // ambiguous recomputed on the iids
            Arrays.fill(this.dotter,-1);            // reinitialize to be used for effectivetrans
            // scan then the list of states and choose the prior tree
            if (choose == 1){
                BLEN = dfa.maxItems;
                DLEN = BLEN*BLEN;

                if (this.data1 == null || this.data1.length < DLEN * 2){
                    if (this.data1 != null) this.garbage += (this.data1.length * 4 + 4) * 2;
                    this.data1 = new int[DLEN * 2];
                    this.data2 = new int[DLEN * 2];
                    this.memory += (this.data1.length * 4 + 4) * 2;
                    this.memdata += (this.data1.length * 4 + 4) * 2;
                    this.nrnew++;
                }
            }

            int curlist = this.plistStart;
            //pstack   0            1   final fake psp
            //plist    plistStart   ..     0
            int bspComp = 0;
            this.bsppComp = 0;
            int nextlistlen = 0;
            int bspPairs = 0;
            for (int i = 0; i < this.psp-1; i++){
                int nextlist = curlist + this.plist[curlist-1]; // second list
                if (choose == 2){
                    nextlistlen = this.plist[nextlist];
                    bspPairs = ((nextlistlen >= 0) ? nextlistlen : -nextlistlen) -
                        this.pstack[i+1].nrActiveIids - 2;      // nr of BSP comparisons
                    bspComp += (bspPairs*bspPairs)/2;
                    boolean res = true;

                    if (
                        nextlistlen
                        < 0){
                        res = computeStep1(dfa,i,curlist,nextlist,bspComp);
                    }
                    if (!res){              // BSPP makes too many comparisons, revert to BSP
                        this.revertBSP++;
                        compareBS(dfa,maketree,1);
                        return;
                    }
                } else {
                    computeStep(dfa,this.data1,this.data2,i,curlist,nextlist);
                    int[] data = this.data1;
                    this.data1 = this.data2;
                    this.data2 = data;
                }
                curlist = nextlist;
                if (this.error) return;     // only in case of time supervision
            }
        }
        BSsymbol tree = null;
        if (maketree){
            tree = pnodeToTreeString(dfa);
        }
    }

    /** The autoaging vector to speed up the checking of doublets. */
    private int[] dotter;
    // deliver all the paths
    /** The list of active items. */
    private int[] plist;

    /** The index of the last frame (the one for the first state). */
    private int plistStart;

    /** The index of the final frame. */
    private int plistEnd;

    // actIids are before the items in the frames

    // The implementation of the dotter with a Briggs and Torczon sparse set is 3.8% slower
    // than that with a plain array if used only in markPaths, and slower by 6.7% if used
    // also computeStep.

    /**
     * Mark the items reacheable in the graph of items starting from the final ones.
     *
     * @param      dfa reference to the DFA
     */

    private void markPaths(BStateTable dfa){
        if (this.dotter == null || this.dotter.length < dfa.maxItems){
            if (this.dotter != null) this.garbage += this.dotter.length * 4 + 4;
            this.dotter = new int[dfa.maxItems];
            this.memory += dfa.maxItems * 4 + 4;
            this.memdotter += dfa.maxItems * 4 + 4;
            this.nrnew++;
        }
        Arrays.fill(this.dotter,Integer.MAX_VALUE);
        BState p = this.pstack[this.psp-1];
        if (this.plist == null){
            this.plist = new int[100];
            this.memory += 404;
            this.memplist += 404;
            this.nrnew++;
        }

        this.ambiguous = false;
        // create list for last state
        int plisti = 1;                         // leave room for -len
        int start = 0;
        plisti += p.nrActiveIids;               // leave room for activeIids
        this.plist[plisti++] = 0;               // item
        int listlen = - plisti - 1;
        this.plist[plisti++] = listlen;         // -len
        this.plist[0] = -listlen;               // len
        start = 0;
        for (int i = this.psp-1; i > 0; i--){
            p = this.pstack[i];
            // mark the items that belong to trees

            // allocate arrays for marking items in previous state
            BState pr = this.pstack[i-1];
            int next = plisti;

            plisti++;                           // leave room for -len
            plisti += pr.nrActiveIids;          // leave room for activeIids
            int maxlen = plisti + pr.items.length + 1;
            // check space for the whole frame
            if (maxlen >= this.plist.length){
                this.garbage += this.plist.length * 4 + 4;
                int newlen = this.plist.length;
                newlen = newlen < 1000000 ? newlen*2 : newlen + 1000000;
                if (newlen < maxlen) newlen = maxlen;
                this.plist = Arrays.copyOf(this.plist,newlen);
                this.memory += this.plist.length * 4 + 4;
                this.memplist += this.plist.length * 4 + 4;
                this.nrcopyof++;
            }
            boolean amb = false;
            for (int j = start+1+p.nrActiveIids;; j++)
                {
                int item = this.plist[j];
                if (item < 0) break;
                int[] iids = p.items[item].left;
                if (iids.length > 1) amb = true;
                id: for (int k = 0; k < iids.length; k++){
                    marknr++;
                    int iid = iids[k];
                    if (this.dotter[iid] <= i){
                        continue;                       // already in list
                    }
                    this.dotter[iid] = i;
                    this.plist[plisti++] = iid;
                }
            }
            if (amb){
                this.plist[start] = -this.plist[start];
                this.ambiguous = true;
            }
            listlen = next - plisti - 1;
            this.plist[next] = -listlen;
            start = next;
            this.plist[plisti++] = listlen;    // -len
        }
        this.plistStart = start;
        this.plistEnd = plisti;

    }

    /**
     * Trace the lists of active items.
     */

    private void traceLists(){
        int pi = this.psp;
        for (int i = 0; i < this.plistEnd;){
            BState pn = this.pstack[--pi];
            traceList(pn,i);
            i += Math.abs(this.plist[i]);         // go to next
        }
        Trc.out.printf("first list: %s\n",this.plistStart);
    }

    /**
     * Trace the i-th list, which contains the active items of the specified state.
     *
     * @param      pn state
     * @param      i index of the list
     */

    private void traceList(BState pn, int i){
        traceList(pn,i,-1);
    }

    /**
     * Trace the i-th list, which contains the active items of the specified state, and
     * show also the corresponding element in the stack.
     *
     * @param      pn state
     * @param      i index of the list
     * @param      sp stack pointer of the corresponding element
     */

    private void traceList(BState pn, int i, int sp){
        int start = i;
        Trc.out.printf("list for state: %s at %s",pn.number,i);
        if (sp >= 0){
            Trc.out.printf(" stack: %s",sp);
        }
        Trc.out.printf("\n    len at: %s: %s%s\n",i,Math.abs(this.plist[i]),
            this.plist[i] < 0 ? " ambig" : "");
        i++;
        for (int j = 0; j < pn.nrActiveIids; j++, i++){
            Trc.out.printf("    %s: act for %s: %s\n",i,j,this.plist[i]);
        }
        for (; this.plist[i] >= 0; i++){
            int item = this.plist[i];
            Trc.out.printf("    %s: %s",i,item);
            Trc.out.printf("    left %s",Arrays.toString(pn.items[item].left));
            Trc.out.printf("\n");
        }
        Trc.out.printf("    len at: %s: %s\n",i,this.plist[i]);
    }

    /** The B and D arrays of Okui. */
    private int[] data1;

    /** The next B and D arrays of Okui. */
    private int[] data2;

    /* For each final state in the dfa there is a transition for eof to a state that has only
     * one item with left pointing to all items in the previous one that end in eof.
     * This is useful in effectivetrans.
     * For nonambiguous strings this is not needed because in the final state there is only one
     * final item, while it is not so for the ambiguous ones, or better, for the states that
     * have several final items.
     * In the stack of states there is an extra entry only when the string is ambiguous so as
     * to make the other methods process states that have sets of iids without making a special
     * case for the final ones (that would otherwise be scanned to find the items ending in EOF).
     */

    /** The length of B's row. */
    private int BLEN;

    /** The offset of D in data'1 and data2. */
    private int DLEN;

    /**
     * Mark the steps done in the specified pnode on the active paths, choosing the most prior
     * one when there are several converging on a same node in the path and updating the values
     * that represent the previous comparisons.
     *
     * @param      dfa reference to the DFA
     * @param      prev previous data array
     * @param      data data array
     * @param      pindex index in the stack
     * @param      curlist index of the current list
     * @param      nextlist index of the next list
     */

    private void computeStep(BStateTable dfa, int[] prev, int[] data, int pindex, int curlist, int nextlist){
        BState pnode = this.pstack[pindex];
        BState pnext = this.pstack[pindex+1];
        BState from = pnode;
        BSItem[] curitems = pnode.items;
        BSItem[] nextitems = pnext.items;
        for (int i = nextlist+1+pnext.nrActiveIids;; i++)
            {
            int q = this.plist[i];                        // item
            if (q < 0) break;
            int[] arr = nextitems[q].left;
            int p = arr[0];                               // iid
            choose: if (arr.length > 1){                  // determine winner actIid
                int winner = p;
                if 
                    (this.dotter[winner] >> dfa.dshift >= pindex)
                    {   // already done
                    winner = this.dotter[winner] & dfa.dmask;
                    this.plist[nextlist+1+q] = winner;              // active iid
                    p = winner;
                    break choose;
                }
                int rho = -1;
                int rhop = -1;
                for (int j = 1; j < arr.length; j++){
                    // compare the winner and the current one
                    int ap = arr[j];
                    int app = winner;
                    rho = prev[BLEN*ap+app];
                    rhop = prev[BLEN*app+ap];
                    int mrho = curitems[ap].minsp;
                    int mrhop = curitems[app].minsp;
                    if (mrho < rho) rho = mrho;
                    if (mrhop < rhop) rhop = mrhop;
                    int D = prev[DLEN + BLEN*app + ap];
                    if (rho > rhop || (rho == rhop && D < 0)){
                        winner = ap;
                    }
                    this.joinnr++;
                }
                this.dotter[arr[0]] = (pindex << dfa.dshift) | winner;
                this.plist[nextlist+1+q] = winner;           // active iid
                p = winner;
            }  // choose
            int porig = 0;
            if (pindex > 0){
                porig = curitems[p].left[0];  // origin, to check that pp below is has the same pat state
                if (curitems[p].left.length > 1){
                    porig = this.plist[curlist+1+p];
                }
            }

            // visit the ones that are before the current one
            // compute here what depends only on p and q
            int[] subsetp = from.subset == null ? null : from.subset[p];
            int[] bp0p = from.bp0 == null ? null : from.bp0[p];
            int DLENp = DLEN+BLEN*p;
            int DLENq = DLEN+BLEN*q;
            int BLENp = BLEN*p;
            int BLENq = BLEN*q;
            int mrho = from.items[p].minsp;

            for (int k = nextlist+1+pnext.nrActiveIids; k < i; k++)
                {
                int qp = this.plist[k];
                int[] lef = nextitems[qp].left;
                int pp = lef.length == 1 ? lef[0] : this.plist[nextlist+1+qp];   // actid
                int rho;
                int rhop;
                // check that the items have the same origin in the previous pnode
                int pporig = 0;
                if (pindex > 0){
                    lef = curitems[pp].left;
                    pporig = lef.length == 1 ? lef[0] : this.plist[curlist+1+pp]; // origin, to check that pp below is has the same pat state
                }
                if (porig == pporig || pindex == 0){              // p = p' in the paper
                    // here we have two transitions from the same item leading to different items
                    // probably I could compare instead their active iid's
                    if (p == pp) continue;
                    int cmp = subsetp[pp];
                    // to compare two tags, take away the common prefix, then the first
                    // is prior if it starts with (p and the second: starts with )q or
                    // with (q with p <. q

                    rho = bp0p[pp];             // rho = bp0(alpha,alpha');
                    rhop = from.bp0[pp][p];     // rho' = bp0(alpha',alpha)
                    if (rho != rhop || cmp != 0){
                        data[DLENq+qp] = rho == rhop ? -cmp : rho-rhop;
                    }
                    this.forknr++;
                } else {
                    rho = prev[BLENp+pp];
                    rhop = prev[BLEN*pp+p];
                    int mrhop = from.items[pp].minsp;
                    if (mrho < rho) rho = mrho;        // rho = min{rho, minsp(alpha)};
                    if (mrhop < rhop) rhop = mrhop;    // rho = min{rho, minsp(alpha)};
                    data[DLENq+qp] = rho == rhop ? prev[DLENp+pp] : rho-rhop;
                    this.parallelnr++;
                }
                data[DLEN+BLEN*qp+q] = -data[DLEN+BLEN*q+qp];        // D[q'][q] = -D[q][q']
                data[BLENq+qp] = rho;
                data[BLEN*qp+q] = rhop;
            }
        }
    }

    /** An array of items. */
    private int[] itemarr;
    /** The number of BSPP comparisons. */
    private int bsppComp;

    /** The number of times BSPP reverts to BSP. */
    private static int revertBSP;

    /** The number of calls to BSP(P). */
    private static int callsBSP;
    ;

    /**
     * Choose the most prior path when there are several converging on a same node.
     *
     * @param      dfa reference to the DFA
     * @param      prev previous data array
     * @param      data data array
     * @param      pindex index in the stack
     * @param      curlist index of the current list
     * @param      nextlist index of the next list
     * @param      bspComp number of comparisons BSP would have done from the begining since the next list
     * @return     <code>true</code>: success, <code>false</code> if the number of comparisons
     *             is higher than that of BSP
     */

    /* It compares the trees at convergence. It does not need the B, D arrays, its uses local
     * variables.
     * Here the problem is that if I build the arrays of tags completely, then I can avoid
     * to build that of the winner at the next comparison, but if I build the two to compare
     * together I can avoid to build them completely if they join.
     * If I build them completely then I must seek the join. So, let's build them together.
     * I build an array of items because then I need to use the precalculated functions.
     */

    private boolean computeStep1(BStateTable dfa, int pindex, int curlist, int nextlist, int bspComp){
        BState pnode = this.pstack[pindex];
        BState pnext = this.pstack[pindex+1];
        BState from = pnode;
        BSItem[] nextitems = pnext.items;
        int nrcomp = this.bsppComp;
        for (int i = nextlist+1+pnext.nrActiveIids;; i++)
            {
            int q = this.plist[i];                        // item
            if (q < 0) break;
            int[] arr = nextitems[q].left;
            int p = arr[0];                               // iid
            if (this.timeSupervise){
                this.tally++;
                if (this.tally % 10000 == 0){
                    long t1 = getCycles();
                    if (t1-this.matchStartTime > maxMatchTime){
                        this.error = true;
                        this.errorKind = ERROR_TIME;      // too long time
                        return true;
                    }
                }
            }
            choose: if (arr.length > 1){                  // determine winner actid
                int winner = p;
                if (this.dotter[winner] >> dfa.dshift >= pindex){   // already done
                    winner = this.dotter[winner] & dfa.dmask;
                    this.plist[nextlist+1+q] = winner;              // active iid
                    p = winner;
                    break choose;
                }
                // the prior tree is computed forward; this is why I keep a vector of pairs
                // that can be visited forward

                for (int j = 1; j < arr.length; j++){
                    // compare the winner and the current one
                    int ap = arr[j];
                    // build the tree for the candidate ap
                    int iid1 = winner;
                    int iid2 = ap;
                    int curl = curlist;
                    int itemarri = 0;
                    if (this.itemarr == null){
                        this.itemarr = new int[(pindex+2)*2];
                        this.memory += itemarr.length * 4 + 4;
                        this.nrnew++;
                    } else if (this.itemarr.length < (pindex+2)*2){
                        int newlen = this.itemarr.length < 1000000 ? this.itemarr.length*2 : this.itemarr.length+1000000;
                        if (newlen < (pindex+2)*2) newlen = (pindex+2)*2;
                        this.itemarr = new int[newlen];
                        this.memory += newlen * 4 + 4;
                        this.nrnew++;
                    }
                    int startsp = 0;
                    for (int k = pindex; k >= 0; k--){
                        BState s = this.pstack[k];
                        BSItem item1 = s.items[iid1];
                        BSItem item2 = s.items[iid2];
                        if (iid1 == iid2) break;
                        startsp = k;
                        this.itemarr[itemarri++] = iid1;
                        this.itemarr[itemarri++] = iid2;
                        int ln = this.plist[curl];
                        int nextl = curl + (ln < 0 ? -ln : ln);   // next list
                        if (k > 0){
                            if (s.items[iid1].left.length > 1)
                                {
                                iid1 = this.plist[curl+1+iid1];
                            } else {
                                iid1 = item1.left[0];
                            }
                            if (s.items[iid2].left.length > 1)
                                {
                                iid2 = this.plist[curl+1+iid2];
                            } else {
                                iid2 = item2.left[0];
                            }
                        }
                        curl = nextl;   // go to next list
                    }
                    nrcomp += itemarri/2;
                    if (nrcomp > bspComp){            // more comparisons than BSP, revert to it ..
                        this.bsppComp = nrcomp;       // .. so as to avoid quadratic time
                        return false;
                    }
                    ;

                    // now compute rho and D on the items of itemarr
                    int Dp = 0;

                    // start with the fork
                    int p0 = itemarr[itemarri-2];
                    int pp = itemarr[itemarri-1];
                    BState s = this.pstack[startsp];
                    int cmp = s.subset[p0][pp];
                    int rho = s.bp0[p0][pp];             // rho = bp0(alpha,alpha');
                    int rhop = s.bp0[pp][p0];            // rho' = bp0(alpha',alpha)
                    if (rho != rhop || cmp != 0){
                        Dp = rho == rhop ? -cmp : rho-rhop;
                    }
                    this.forknr++;
                    // then advance with the intermediate ones
                    int sp = startsp+1;
                    for (itemarri -= 4; itemarri > 0; itemarri -= 2, sp++)
                        {
                        p0 = itemarr[itemarri];
                        pp = itemarr[itemarri+1];
                        s = this.pstack[sp];
                        int mrho = s.items[p0].minsp;
                        int mrhop = s.items[pp].minsp;
                        if (mrho < rho) rho = mrho;        // rho = min{rho, minsp(alpha)};
                        if (mrhop < rhop) rhop = mrhop;    // rho = min{rho, minsp(alpha)};
                        if (rho != rhop) Dp = rho-rhop;
                        this.parallelnr++;
                    }

                    // compare the winner and the current one
                    p0 = itemarr[0];
                    pp = itemarr[1];
                    s = this.pstack[pindex];
                    int mrho = s.items[p0].minsp;
                    int mrhop = s.items[pp].minsp;
                    if (mrho < rho) rho = mrho;
                    if (mrhop < rhop) rhop = mrhop;
                    if (rho < rhop || (rho == rhop && Dp < 0)){
                        winner = ap;
                    }
                    this.joinnr++;
                }
                this.dotter[arr[0]] = (pindex << dfa.dshift) | winner;
                this.plist[nextlist+1+q] = winner;         // active iid
            } // choose
        }
            this.bsppComp = nrcomp;
        ;
        return true;
    }

    /**
     * Choose the tree using the Okui method applied to the BSP DFA.
     * It integrates computeStep1.
     *
     * @param      dfa reference to the DFA
     * @param      maketree <code>true</code> to build alto the tree, <code>false</code> otherwise
     * @param      choose &gt; 0 to choose the prior tree
     */

    private void compareBS1(BStateTable dfa, boolean maketree, int choose){
        cmp: if (this.ambiguous){
            markPaths(dfa);                         // mark items belonging to trees
            if (!this.ambiguous) break cmp;         // ambiguous recomputed on the iids
            Arrays.fill(this.dotter,-1);            // reinitialize to be used for effectivetrans
            // scan then the list of states and choose the prior tree
            if (choose == 1){
                BLEN = dfa.maxItems;
                DLEN = BLEN*BLEN;

                if (this.data1 == null || this.data1.length < DLEN * 2){
                    if (this.data1 != null) this.garbage += (this.data1.length * 4 + 4) * 2;
                    data1 = new int[DLEN * 2];
                    data2 = new int[DLEN * 2];
                    this.memory += (data1.length * 4 + 4) * 2;
                    this.memdata += (data1.length * 4 + 4) * 2;
                    this.nrnew++;
                }
            }

            int curlist = this.plistStart;
            //pstack   0            1   final fake psp
            //plist    plistStart   ..     0
            for (int i = 0; i < this.psp-1; i++){
                int nextlist = curlist + this.plist[curlist-1]; // second list
                if (choose == 2){
                    if (this.plist[nextlist] < 0){
                        int pindex = i;

                        BState pnode = this.pstack[pindex];
                        BState pnext = this.pstack[pindex+1];
                        BState from = pnode;
                        BSItem[] nextitems = pnext.items;
                        for (int ii = nextlist+1+pnext.nrActiveIids;; ii++)
                            {
                            int q = this.plist[ii];                       // item
                            if (q < 0) break;
                            int[] arr = nextitems[q].left;
                            int p = arr[0];                               // iid
                            choose: if (arr.length > 1){                  // determine winner actid
                                int winner = p;
                                if (this.dotter[winner] >> dfa.dshift >= pindex){   // already done
                                    winner = this.dotter[winner] & dfa.dmask;
                                    this.plist[nextlist+1+q] = winner;              // active iid
                                    p = winner;
                                    break choose;
                                }
                                // the prior tree is computed forward; this is why I keep a vector of pairs that can be
                                // visited forward
                
                                for (int j = 1; j < arr.length; j++){
                                    // compare the winner and the current one
                                    int ap = arr[j];
                                    // build the tree for the candidate ap
                                    int iid1 = winner;
                                    int iid2 = ap;
                                    int curl = curlist;
                                    int itemarri = 0;
                                    if (this.itemarr == null){
                                        this.itemarr = new int[(pindex+2)*2];
                                        this.memory += itemarr.length * 4 + 4;
                                        this.nrnew++;
                                    } else if (this.itemarr.length < (pindex+2)*2){
                                        int newlen = this.itemarr.length < 1000000 ? this.itemarr.length*2 : this.itemarr.length+1000000;
                                        if (newlen < (pindex+2)*2) newlen = (pindex+2)*2;
                                        this.itemarr = new int[newlen];
                                        this.memory += newlen * 4 + 4;
                                        this.nrnew++;
                                    }
                                    int startsp = 0;
                                    for (int k = pindex; k >= 0; k--){
                                        BState s = this.pstack[k];
                                        BSItem item1 = s.items[iid1];
                                        BSItem item2 = s.items[iid2];
                                        if (iid1 == iid2) break;
                                        startsp = k;
                                        this.itemarr[itemarri++] = iid1;
                                        this.itemarr[itemarri++] = iid2;
                                        int ln = this.plist[curl];
                                        int nextl = curl + (ln < 0 ? -ln : ln);   // next list
                                        if (k > 0){
                                            if (s.items[iid1].left.length > 1){
                                                iid1 = this.plist[curl+1+iid1];
                                            } else {
                                                iid1 = item1.left[0];
                                            }
                                            if (s.items[iid2].left.length > 1){
                                                iid2 = this.plist[curl+1+iid2];
                                            } else {
                                                iid2 = item2.left[0];
                                            }
                                        }
                                        curl = nextl;   // go to next list
                                    }
                                    // now compute rho and D on the items of itemarr
                                    int Dp = 0;
                
                                    // start with the fork
                                    int p0 = itemarr[itemarri-2];
                                    int pp = itemarr[itemarri-1];
                                    BState s = this.pstack[startsp];
                                    int cmp = s.subset[p0][pp];
                                    int rho = s.bp0[p0][pp];             // rho = bp0(alpha,alpha');
                                    int rhop = s.bp0[pp][p0];            // rho' = bp0(alpha',alpha)
                                    if (rho != rhop || cmp != 0){
                                        Dp = rho == rhop ? -cmp : rho-rhop;
                                    }
                                    this.forknr++;
                                    // then advance with the intermediate ones
                                    int sp = startsp+1;
                                    for (itemarri -= 4; itemarri > 0; itemarri -= 2, sp++){
                                        p0 = itemarr[itemarri];
                                        pp = itemarr[itemarri+1];
                                        s = this.pstack[sp];
                                        int mrho = s.items[p0].minsp;
                                        int mrhop = s.items[pp].minsp;
                                        if (mrho < rho) rho = mrho;        // rho = min{rho, minsp(alpha)};
                                        if (mrhop < rhop) rhop = mrhop;    // rho = min{rho, minsp(alpha)};
                                        if (rho != rhop) Dp = rho-rhop;
                                        this.parallelnr++;
                                    }
                
                                    // compare the winner and the current one
                                    p0 = itemarr[0];
                                    pp = itemarr[1];
                                    s = this.pstack[pindex];
                                    int mrho = s.items[p0].minsp;
                                    int mrhop = s.items[pp].minsp;
                                    if (mrho < rho) rho = mrho;
                                    if (mrhop < rhop) rhop = mrhop;
                                    if (rho < rhop || (rho == rhop && Dp < 0)){
                                        winner = ap;
                                    }
                                    this.joinnr++;
                                }
                                this.dotter[arr[0]] = (pindex << dfa.dshift) | winner;
                                this.plist[nextlist+1+q] = winner;         // active iid
                            } // choose
                        }
                    }
                } else {
                    computeStep(dfa,this.data1,this.data2,i,curlist,nextlist);
                    int[] data = this.data1;
                    this.data1 = this.data2;
                    this.data2 = data;
                }
                curlist = nextlist;
            }
        }
        BSsymbol tree = null;
        if (maketree){
            tree = pnodeToTreeString(dfa);
        }

    }

    /** Temporary array to hold the tree. */
    private int[] bsarr;

    /** The tree. */
    private BSsymbol tree;

    /** The length of the tree. */
    private int treeLen;

    /**
     * Deliver a string representing the tree of the active items in the pnodes list.
     *
     * @param      dfa reference to the DFA
     */

    private BSsymbol pnodeToTreeString(BStateTable dfa){
        BState head = this.pstack[this.psp-1];
        int iid = 0;
        int len = 0;
        if (this.ambiguous){
            // implementation without a link that allows to move from a list to the one
            // of the previous state
            int curlist = 0;
            for (int i = this.psp-1; i >= 0; i--){
                BState p = this.pstack[i];
                BSItem item = p.items[iid];
                len += item.sym.arr.length;
                if (i > 0){
                    if (p.items[iid].left.length > 1)
                        {
                        iid = this.plist[curlist+1+iid];
                    } else {
                        iid = item.left[0];
                    }
                }
                int ln = this.plist[curlist];
                curlist += ln > 0 ? ln : -ln;   // go to next list
            }
            this.treeLen = len;
            iid = 0;
            curlist = 0;
            if (this.bsarr == null){
                this.bsarr = new int[len];
                this.memory += len * 4 + 4;
                this.membsarr += len * 4 + 4;
            } else if (this.bsarr.length < len){
                this.garbage += this.bsarr.length * 4 + 4;
                int newlen = this.bsarr.length < 1000000 ? this.bsarr.length*2 : this.bsarr.length+1000000;
                if (newlen < len) newlen = len;
                this.bsarr = new int[newlen];
                this.memory += newlen * 4 + 4;
                this.membsarr += newlen * 4 + 4;
                this.nrnew++;
            }
            for (int i = this.psp-1; i >= 0; i--){
                BState p = this.pstack[i];
                BSItem item = p.items[iid];
                len -= item.sym.arr.length;
                System.arraycopy(item.sym.arr,0,this.bsarr,len,item.sym.arr.length);
                if (i > 0){
                    if (p.items[iid].left.length > 1)
                        {
                        iid = this.plist[curlist+1+iid];
                    } else {
                        iid = item.left[0];
                    }
                }
                int ln = this.plist[curlist];
                curlist += ln > 0 ? ln : -ln;   // go to next list
            }
        } else {
            iid = head.finalItem;                 // there is only one final item
            for (int i = this.psp-1; i >= 0; i--){
                BState p = this.pstack[i];
                BSItem item = p.items[iid];
                len += item.sym.arr.length;
                if (i > 0) iid = item.left[0];
            }
            this.treeLen = len;
            iid = head.finalItem;
            if (this.bsarr == null){
                this.bsarr = new int[len];
                this.memory += len * 4 + 4;
                this.membsarr += len * 4 + 4;
            } else if (this.bsarr.length < len){
                this.garbage += this.bsarr.length * 4 + 4;
                int newlen = this.bsarr.length < 1000000 ? this.bsarr.length*2 : this.bsarr.length+1000000;
                if (newlen < len) newlen = len;
                this.bsarr = new int[newlen];
                this.memory += newlen * 4 + 4;
                this.membsarr += newlen * 4 + 4;
                this.nrnew++;
            }
            for (int i = this.psp-1; i >= 0; i--){
                BState p = this.pstack[i];
                BSItem item = p.items[iid];
                len -= item.sym.arr.length;
                System.arraycopy(item.sym.arr,0,this.bsarr,len,item.sym.arr.length);
                if (i > 0) iid = item.left[0];
            }
        }

        if (this.tree == null){
            this.tree = new BSsymbol();
            this.memory += 8;
            this.nrnew++;
        }
        this.tree.arr = this.bsarr;

        return tree;
    }


    //----------------PAT----------------------

    /*
     * Notes:
     *
     * - I build PATs generating garbage states; then I renumber them and discard the garbage.
     * - there is a need to use temporary initial and final states when building the sub-PATs that
     *   must thereafter be parenthetized (i.e. [delta]p) because otherwise the tags for the
     *   parenteses are added also to the other transitions that depart or end in such states, but
     *   that do not belong to the sub-PAT. The sub-PATs must then be joined uniting the start
     *   states and the end states.
     * - I remove transitions that are less prior then others: two transitions that have the same
     *   start state and end state, take the one that has a tag that is lexicographically greater.
     *   It is done at the end because when adding transitions the tags are not entirely known.
     * - there is no need to have fields for the numbering of nodes or for the sub-nfas: the
     *   numbering of nodes can be handed down as argument to the recursive procedures, and the
     *   sub-nfas returned back. However, it would be clearer.
     * - edges have here sets of symbols (actually, either a symbol or all symbols) and also tags
     *   and the EOF.
     *
     * PAT optimizations:
     * - incidence matrix: 8% gain
     * - transition dotter: 2% gain
     * - PATproceedOneStep visits only the upper part of the list: 10% gain
     */

    /** The character representina any symbols. */
    private static final char ANYSYMB = '\u25a1';     // any symbols

    /** A transition (edge) of the PAT NFA. */

    private static class PATrans {

        /** The reference to the next transition. */
        PATrans next;

        /** The next state (endpoint of this transition). */
        PATState nextState;

        /** The terminal. */
        char sym;

        /** The tag. */
        BSsymbol tag; 

        /** The precomputed pat_minsp. */
        int minsp;
    }

    /** A state of the PAT NFA. */

    private static class PATState {

        /** The reference to the next state in the list of states. */
        PATState suc;

        /** The state number. */
        int number;

        /** The head of the list of transitions. */
        PATrans transList;

        /** Whether this state is final. */
        boolean isFinal;

        /** Whether it is reacheable from the initial state. */
        boolean isReacheable;

        /** The name, i.e. the position. */
        int[] name;
        /**
         * Tell if this state is equal to the specified other one.
         *
         * @param      other the other state
         * @return     <code>true</code> if it is, <code>false</code> otherwise
         */

        private boolean equals(PATState other){
            if (this == other) return true;
            if (other == null) return false;
            return this.number == other.number;
        }

        /**
         * Compare the position of this state with that of the other one.
         *
         * @param      other the other state
         * @return     &lt; = or &gt; 0 if this symbol precedes, is equal or follows the other
         */

        private int compareTo(PATState other){
            int res = 0;
            if (this.isFinal && other.isFinal) return res;
            if (this.isFinal) return 1;
            if (other.isFinal) return -1;
            return compareToPos(this.name,other.name);
        }

        /**
         * Deliver a string representing the name of this state.
         *
         * @return     string
         */

        private String stateName(){
            String name = "";
            if (this.isFinal){
                name = "$";
            } else {
                name = posToString(this.name);
            }
            return name;
        }

        /**
         * Deliver a string with the number and name of this state.
         *
         * @return     string
         */

        private String toId(){
            String str = "" + this.number;
            if (this.name != null){
                str += ":" + this.stateName();
            }
            return str;
        }
    }

    // In the NFA there is a matrix of precomputed pat_subset_tags() and pat_bp0();
    // not all the entries are used, but it is difficult to tell the ones that are needed.
    // It is accessed with the edges numbers; each edge is given a unique number.

    /** The state table of the PAT NFA. */

    private class PATStateTable {

        /** The head of the list of states. */
        PATState head;

        /** The tail of the list. */
        PATState last;

        /** The last state added. */
        PATState lastAdded;

        /** The number of states. */
        int stateNr;

        /** The table of states. */
        PATState[] table;

        /** The map from integers to AST nodes. */
        AstNode[] astMap;

        /** The number of edges of reacheable states. */
        int nrEdges;

        /** The table of transitions. */
        PATrans[] transTable;

        /** The table of precomputed pat_subset_tags(). */
        int[][] subsTable;

        /** The table of precomputed pat_bp0(). */
        int[][] bp0Table;

        /** The maximum symbol in edges. */
        char maxSymb;

        /** The number of the eofAst. */
        int eofseq;

        /**
         * Add a new state with a null position.
         *
         * @param      msg string for tracing
         * @return     reference to the state
         */

        private PATState add(String msg){
            return add(msg,null);
        }

        /**
         * Add a new state with the specified position.
         *
         * @param      msg string for tracing
         * @param      pos position
         * @return     reference to the state
         */

        private PATState add(String msg, int[] pos){
            PATState h = new PATState();
            h.number = this.stateNr++;
            h.name = pos;
            if (this.last == null) this.head = h;     // append to list
            else this.last.suc = h;
            this.last = h;
            this.lastAdded = h;
            return h;
        }

        /**
         * Deliver a string representing the specified tag.
         *
         * @param      tag tag
         * @return     string
         */

        private String tagToString(BSsymbol tag){
            StringBuilder st = new StringBuilder();
            if (tag == null){
                st.append("ε");
            } else {
                st.append(tag.toString(this.astMap));
            }
            return st.toString();
        }

        /**
         * Deliver a string representing the specified transition.
         *
         * @param      t tag
         * @return     string
         */

        private String transToString(PATrans t){
            StringBuilder st = new StringBuilder();
            st.append(t.sym);
            st.append(",");
            st.append(tagToString(t.tag));
            st.append("->");
            st.append(t.nextState.toId());
            return st.toString();
        }

        /**
         * Deliver a string representing the specified state.
         *
         * @param      s state
         * @return     string
         */

        private String stateToString(PATState s){
            StringBuilder st = new StringBuilder();
            st.append(s.number);
            st.append(": ");
            st.append(s.stateName());
            if (s.isFinal) st.append(" final");
            st.append(":");
            for (PATrans t = s.transList; t != null; t = t.next){
                st.append(" -");
                st.append(transToString(t));
            }
            return st.toString();
        }

        /**
         * Trace the specified state.
         *
         * @param      s state
         */

        private void traceState(PATState s){
            Trc.out.printf("state: %s %s\n",s.toId(),
                s.isFinal ? "final" : "");
            Trc.out.printf("  transitions:\n");
            for (PATrans t = s.transList; t != null; t = t.next){
                Trc.out.printf("    %s\n",transToString(t));
            }
        }

        /**
         * Trace this table of states.
         */

        private void trace(){
            for (int i = 0; i < this.stateNr; i++){
                PATState s = this.table[i];
                traceState(s);
            }
        }

        /**
         * Add a transition from the specifed states with the specified symbol to the
         * specified state.
         *
         * @param      from state
         * @param      to state
         * @param      sym symbol
         * @param      tag tag
         * @return     string
         */

        private PATrans addEdge(PATState from, PATState to, char sym, BSsymbol tag){
            PATrans t = null;
            sea: {
                PATrans pr = null;
                for (t = from.transList; t != null; t = t.next){      // find edge or last
                    pr = t;
                }
                t = new PATrans();
                t.nextState = to;
                t.sym = sym;
                t.tag = tag;
                if (pr == null){                // append
                    from.transList = t;
                } else {
                    pr.next = t;
                }
            }
            return t;
        }

        /**
         * Trace the states reacheable from the specified one.
         *
         * @param      is state
         */

        private void strace(PATState is){
            strace(is,false);
        }

        /**
         * Trace the states reacheable from the specified one.
         *
         * @param      is state
         * @param      showstate <code>true</code> to trace also the state
         */

        private void strace(PATState is, boolean showstate){
            int dp = 0;
            int qp = 0;
            PATState[] queue = new PATState[this.stateNr];
            queue[qp++] = is;                           // start from it
            while (dp != qp){                           // while queue not empty
                PATState s = queue[dp++];
                if (showstate) Trc.out.printf("state %s\n",s.toId());
                loop: for (PATrans t = s.transList; t != null; t = t.next){  // find edge or last
                    Trc.out.printf("  %s\n",transToString(t));
                    for (int j = 0; j < qp; j++){
                        if (queue[j] == t.nextState) continue loop;          // already visited
                    }
                    queue[qp++] = t.nextState;          // enqueue it
                }
            }
        }

        /**
         * Create the compacted transition tables.
         */

        public void compactTables(){
            // collect the reacheable states and renumber them
            this.stateNr = 0;
            for (PATState s = this.head; s != null; s = s.suc){
                if (!s.isReacheable) continue;
                s.number = this.stateNr++;
            }

            // find the max symbol to determine the length of the rows
            // and build table of states, and for each state the table of its transitions
            int maxsym = 0;
            this.table = new PATState[this.stateNr];
            for (PATState s = this.head; s != null; s = s.suc){
                if (!s.isReacheable) continue;
                this.table[s.number] = s;
                for (PATrans t = s.transList; t != null; t = t.next){
                    if (t.sym > maxsym && t.sym != ANYSYMB) maxsym = t.sym;
                    this.nrEdges++;
                    t.minsp = pat_minsp(t.tag);
                }
            }
            this.nrEdges++;                         // edge o: fake, all others > 0, good for comb
            this.transTable = new PATrans[this.nrEdges];
            maxsym++;                               // + 1 for eof
            int nsym = maxsym + 1;
            this.maxSymb = (char)maxsym;            // max symbol in edges
            int[][] tabs = new int[this.stateNr][]; // tables before compression
            int[] arrays = new int[100];            // overflow area for value arrays
            int i_arrays = 1;                       // 0 index reserved
            int[] arrindex = new int[nsym];         // indexes of value arrays into arrays
            int[][] row = new int[nsym][];       // row before compression
            int[] rowi = new int[nsym];          // indexes in row's arrays
            for (int j = 0; j < row.length; j++){
                row[j] = new int[10];
            }

            int nrtra = 0;
            this.transTable[nrtra] = new PATrans();
            this.transTable[nrtra++].nextState = this.head;
            for (int i = 0; i < this.stateNr; i++){
                PATState s = this.table[i];

                // build the row for this state, with a slot for each symbol
                // containing the transitions
                for (int j = 0; j < row.length; j++){
                    rowi[j] = 0;
                }
                add: for (PATrans t = s.transList; t != null; t = t.next){
                    if (t.sym == ANYSYMB){                 // applies to all symbols
                        adda: for (int k = 0; k < nsym; k++){
                            int[] r = row[k];
                            for (int l = 0; l < rowi[k]; l++){
                                if (r[l] == nrtra) continue adda;
                            }
                            if (rowi[k] >= r.length){
                                r = Arrays.copyOf(r,r.length+10);
                                row[k] = r;
                            }
                            r[rowi[k]++] = nrtra;
                        }
                    } else {
                        int[] r = row[t.sym];
                        for (int k = 0; k < rowi[t.sym]; k++){
                            if (r[k] == nrtra) continue add;
                        }
                        if (rowi[t.sym] >= r.length){
                            r = Arrays.copyOf(r,r.length+10);
                            row[t.sym] = r;
                        }
                        r[rowi[t.sym]++] = nrtra;
                    }
                    this.transTable[nrtra++] = t;
                }

                // now we have in row the arrays, let's store the ones that have
                // more than one value
                Arrays.fill(arrindex,-1);
                int[] arr = new int[10];        // temporary arrays for values in cells
                int nval = 0;
                for (int j = 0; j < row.length; j++){
                    int size = rowi[j];
                    if (size == 0) continue;
                    if (size > 1){
                        if (i_arrays + size + 1 >= arrays.length){  // enlarge
                            arrays =  Arrays.copyOf(arrays,i_arrays + size + 100);
                        }
                        arrindex[j] = i_arrays;
                        arrays[i_arrays++] = size;
                        arr = row[j];
                        System.arraycopy(arr,0,arrays,i_arrays,size);
                        i_arrays += size;
                    }
                    nval++;
                }

                tabs[i] = new int[nval*2 + 1];
                int k = 0;
                tabs[i][k++] = row.length;  // length
                for (int j = 0; j < row.length; j++){
                    int size = rowi[j];
                    if (size == 0) continue;
                    arr = row[j];
                    if (size == 1){
                        tabs[i][k++] = j;
                        tabs[i][k++] = arr[0];
                    } else {
                        tabs[i][k++] = j;
                        tabs[i][k++] = -arrindex[j];
                    }
                }
            }

            CombVector comb = new CombVector(0,
                CombVector.HOLES_ACCESSED |
                CombVector.FOLD_ROWS |
                CombVector.PAIRS);
            //comb.settrc("a");
            comb.merge(tabs);
            if (i_arrays > 1){                    // there is an overflow table
                int len = comb.tabMerged.length;
                comb.tabMerged = Arrays.copyOf(comb.tabMerged,len + i_arrays);
                System.arraycopy(arrays,0,comb.tabMerged,len,i_arrays);
                for (int i = 0; i < len; i++){
                    if (comb.tabMerged[i] < 0){   // relocate references to arrays
                        comb.tabMerged[i] -= len;
                    }
                }
            }
            this.trtable = comb.tabMerged;
            this.check = comb.check;
            this.base = comb.base;

            // build the adiacence matrix, which is useful to detect convergences
            // cell[i,j] tells the number of the edge that goes from i to j
            int[] adj = new int[this.stateNr];
            nrtra = 1;                           // transition nr 0 fake
            for (int i = 0; i < this.stateNr; i++){
                PATState s = this.table[i];
                // build the row for this state, with 1 denoting the reached states
                Arrays.fill(adj,0);
                int nval = 0;
                for (PATrans t = s.transList; t != null; t = t.next){
                    if (adj[t.nextState.number] == 0){
                        nval++;
                        adj[t.nextState.number] = nrtra;
                    }
                    nrtra++;
                }
                tabs[i] = new int[nval*2 + 1];
                int k = 0;
                tabs[i][k++] = adj.length;  // length
                for (int j = 0; j < adj.length; j++){
                    if (adj[j] == 0) continue;
                    tabs[i][k++] = j;
                    tabs[i][k++] = adj[j];
                }
            }
            comb = new CombVector(0,
                CombVector.HOLES_ACCESSED |
                CombVector.FOLD_ROWS |
                CombVector.PAIRS);
            //comb.settrc("a");
            comb.merge(tabs);
            this.adjtable = comb.tabMerged;
            this.adjcheck = comb.check;
            this.adjbase = comb.base;
            // create a table of precomputed pat_subset_tags and pat_bp0
            this.subsTable = new int[this.nrEdges][];
            this.bp0Table = new int[this.nrEdges][];
            for (int i = 1; i < this.nrEdges; i++){         // skip fake edge
                this.subsTable[i] = new int[this.nrEdges];
                this.bp0Table[i] = new int[this.nrEdges];
                for (int j = 1; j < this.nrEdges; j++){
                    this.subsTable[i][j] = pat_subset_tags(this.transTable[i].tag,this.transTable[j].tag);
                    this.bp0Table[i][j] = pat_bp0(this.transTable[i].tag,this.transTable[j].tag);
                }
            }

            this.eofseq = eofAst.seq;
        }

        /**
         * Deliver a string representing the transition from the specified state and symbol.
         *
         * @param      state state number
         * @return     sym symbol
         */

        private String transToString(int state, int sym){
            StringBuilder str = new StringBuilder();
            int bas = this.base[state];
            int start = bas+sym;
            int ele = this.check[start] == bas ? this.trtable[start] : 0;
            if (ele == 0) return null;
            int len = 1;
            if (ele < 0){            // array of actions
                ele = -ele;
                len = this.trtable[ele++];
                start = ele;
            }
            for (int k = 0; k < len; k++){
                if (str.length() > 0) str.append(" ");
                int val = this.trtable[start+k];
                str.append(val);
            }
            return str.toString();
        }

        /** The compressed table state,sym -> transition. */
        int[] trtable;

        /** The check table. */
        int[] check;

        /** The base table. */
        int[] base;

        /** The compressed table state,sym -> transition. */
        int[] adjtable;

        /** The check table. */
        int[] adjcheck;

        /** The base table. */
        int[] adjbase;
    }

    /**
     * Build the PAT.
     *
     * @param      ast reference to the root AST
     * @return     reference to the PAT
     */

    private PATStateTable buildPAT(AstNode ast){
        PATStateTable nfa = new PATStateTable();
        nfa.astMap = this.astMap;
        PATState si = nfa.add("build_pat",ast.pos);     // allocate its initial state
        PATState sf = nfa.add("build_pat",ast.pos);     // allocate its final state
        sf.isFinal = true;
        patBuild(nfa,ast,si,sf);                        // visit the ast recursively to build PAT 
        pat_purge(nfa,si);
        nfa.compactTables();

        return nfa;
    }

    /**
     * Visit the specified AST recursively and build the PAT NFA adding edges and states
     * to the specified initial and final states.
     *
     * @param      nfa reference to the state table
     * @param      ast reference to the AST
     * @param      is reference to the initial state
     * @param      fs reference to the final state
     */

    private void patBuild(PATStateTable nfa, AstNode ast, PATState is, PATState fs){
        if (ast.kind == A_LEA){                          // leaf
            PATState cx = nfa.add("leaf",ast.pos);       // allocate intermediate state
            nfa.addEdge(is,cx,ast.sym,newBSsymbol(astToBSele(ast,ELEOPEN)));
            nfa.addEdge(cx,fs,ANYSYMB,newBSsymbol(astToBSele(ast,ELECLOSE)));
        } else if (ast.kind == A_ALT){                   // alt
            for (AstNode i = ast.son; i != null; i = i.bro){
                PATState ci = nfa.add("alt start");      // allocate temporary initial state
                PATState cf = nfa.add("alt end");        // allocate temporary final state
                patBuild(nfa,i,ci,cf);
                pat_tags(nfa,ci,cf,ast);                 // parenthetize the tags
                pat_join(is,fs,ci,cf);                   // join
           }
        } else if (ast.kind == A_CON){                   // conc
            PATState ts = nfa.add("conc start");         // allocate temporary initial state for first
            PATState cf = nfa.add("conc end");           // allocate final state for first
            PATState curs = ts;
            PATState curf = cf;
            for (AstNode i = ast.son; i != null; i = i.bro){
                if (i != ast.son){
                    curs = nfa.add("conc cur start");    // allocate initial state for second
                    curf = nfa.add("conc cur end");      // allocate temporary final state for second
                }
                patBuild(nfa,i,curs,curf);
                if (i != ast.son){
                    pat_concatenate(nfa,ts,cf,curs);     // handle concatenation
                    cf = curf;
                }
            }
            pat_tags(nfa,ts,cf,ast);                     // parenthetize the tags
            pat_join(is,fs,ts,cf);                       // join
        } else if (ast.kind == A_EMP){                   // empty
            nfa.addEdge(is,fs,ANYSYMB,newBSsymbol(astToBSele(ast,ELEOPEN),astToBSele(ast,ELECLOSE)));
        } else {                                         // group
            PATState ci = nfa.add("group start");        // allocate temporary initial state
            PATState cf = nfa.add("group end");          // allocate temporary final state
            switch (ast.groupKind){
            case G_GRO:
                patBuild(nfa,ast.son,ci,cf);             // expand its graph
                pat_tags(nfa,ci,cf,ast);                 // parenthetize the tags
                pat_join(is,fs,ci,cf);                   // join
                break;
            case G_OPT:                                  // optional group
                patBuild(nfa,ast.son,ci,cf);             // expand its graph
                nfa.addEdge(ci,cf,ANYSYMB,newBSsymbol());   // bypass
                pat_tags(nfa,ci,cf,ast);                 // parenthetize the tags
                pat_join(is,fs,ci,cf);                   // join
                break;
            case G_RE0:                                  // kleene group
                patBuild(nfa,ast.son,ci,cf);                 // expand its graph
                pat_plus(nfa,ci,cf);
                nfa.addEdge(ci,cf,ANYSYMB,newBSsymbol());   // bypass
                pat_tags(nfa,ci,cf,ast);                 // parenthetize the tags
                pat_join(is,fs,ci,cf);                   // join
                break;
            case G_RE1:                                  // +
                patBuild(nfa,ast.son,ci,cf);             // expand its graph
                pat_plus(nfa,ci,cf);
                pat_tags(nfa,ci,cf,ast);                 // parenthetize the tags
                pat_join(is,fs,ci,cf);                   // join
                break;
            }
        }
    }

    /**
     * Join the specified two pieces of PAT NFAs.
     *
     * @param      is1 reference to the initial state of the first piece
     * @param      fs1 reference to the final state of the first piece
     * @param      is2 reference to the initial state of the second piece
     * @param      fs2 reference to the final state of the second piece
     */

    private void pat_join(PATState is1, PATState fs1, PATState is2, PATState fs2){
        PATrans pr = null;
        for (PATrans t = is1.transList; t != null; t = t.next){  // find last transition
            pr = t;
        }
        for (PATrans t = is2.transList; t != null; t = t.next){  // add start transitions of second to first
            if (pr == null){
                is1.transList = t;
            } else {
                pr.next = t;
            }
            pr = t;
        }
            
        int dp = 0;
        int qp = 0;
        PATState[] queue = new PATState[100];
        queue[qp++] = is1;   // start from it
        while (dp != qp){                           // while queue not empty
            PATState s = queue[dp++];
            loop: for (PATrans t = s.transList; t != null; t = t.next){  // find edge or last
                PATState ts = t.nextState;
                if (ts == fs2){                     // end state of second
                    t.nextState = fs1;
                } else if (ts == is2){
                    t.nextState = is1;
                }
                for (int j = 0; j < qp; j++){
                    if (queue[j] == t.nextState) continue loop;    // already visited
                }
                if (qp >= queue.length){
                    queue = Arrays.copyOf(queue,queue.length+100);
                }
                queue[qp++] = t.nextState;        // enqueue it
            }
        }
    }

    /**
     * Parenthetize the tags of the arcs that start from the specified initial state
     * and the ones of the arcs that end in the specified final state.
     *
     * @param      nfa reference to the state table
     * @param      is reference to the initial state
     * @param      fs reference to the final state
     * @param      ast reference to the AST
     */

    private void pat_tags(PATStateTable nfa, PATState is, PATState fs, AstNode ast){
        int dp = 0;
        int qp = 0;
        PATState[] queue = new PATState[100];
        queue[qp++] = is;   // start from it
        while (dp != qp){                           // while queue not empty
            PATState s = queue[dp++];
            loop: for (PATrans t = s.transList; t != null; t = t.next){  // find edge or last
                BSsymbol newtags = null;
                PATState ts = t.nextState;
                if (s == is){                       // start state
                    if (ts == fs){                  // end state
                        newtags = newBSsymbol(astToBSele(ast,ELEOPEN),t.tag,astToBSele(ast,ELECLOSE));
                    } else {
                        newtags = newBSsymbol(astToBSele(ast,ELEOPEN),t.tag);
                    }
                    t.tag = newtags;
                } else if (ts == fs){
                    newtags = newBSsymbol(t.tag,astToBSele(ast,ELECLOSE));
                    t.tag = newtags;
                }
                for (int j = 0; j < qp; j++){
                    if (queue[j] == t.nextState) continue loop;    // already visited
                }
                if (qp >= queue.length){
                    queue = Arrays.copyOf(queue,queue.length+100);
                }
                queue[qp++] = t.nextState;        // enqueue it
            }
        }
    }

    /**
     * Concatenate the sub-nfas, the first starting at the specified is state and ending at
     * the specified fs state, and the second starting at is2 and ending at its final state.
     *
     * @param      nfa reference to the state table
     * @param      is reference to the initial state of the first sub-nfa
     * @param      fs reference to the final state of the first sub-nfa
     * @param      is2 reference to the initial state of the second sub-nfa
     */

    private void pat_concatenate(PATStateTable nfa, PATState is, PATState fs, PATState is2){
        int dp = 0;
        int qp = 0;
        PATState[] queue = new PATState[100];
        queue[qp++] = is;   // start from it
        while (dp != qp){                              // while queue not empty
            PATState s = queue[dp++];
            loop: for (PATrans t = s.transList; t != null; t = t.next){  // find edge or last
                PATState ts = t.nextState;
                if (s == is2) continue;                // start state of second
                if (ts == fs){                         // end state
                    BSsymbol ttag = t.tag;
                    for (PATrans u = is2.transList;
                        u != null; u = u.next){        // transitions of second from initial state
                        // is2 there can be many: reuse trans t, and create new ones for the others
                        PATrans tr = t;
                        if (u != is2.transList){       // not first: create a new one
                            tr = new PATrans();
                        }
                        tr.nextState = u.nextState;
                        tr.tag = newBSsymbol(ttag,u.tag);
                        tr.sym = u.sym;
                        if (u != is2.transList){       // not first: create a new one
                            tr.next = t.next;
                            t.next = tr;
                        }
                    }
                }
                for (int j = 0; j < qp; j++){
                    if (queue[j] == t.nextState) continue loop;    // already visited
                }
                if (qp >= queue.length){
                    queue = Arrays.copyOf(queue,queue.length+100);
                }
                queue[qp++] = t.nextState;        // enqueue it
            }
        }
    }

    /**
     * Build the PAT NFA for a repetition group addind edges and states to the specified
     * initial and final states.
     *
     * @param      nfa reference to the state table
     * @param      is reference to the initial state
     * @param      fs reference to the final state
     */

    private void pat_plus(PATStateTable nfa, PATState is, PATState fs){
        int dp = 0;
        int qp = 0;
        PATState[] queue = new PATState[100];
        queue[qp++] = is;   // start from it
        while (dp != qp){                              // while queue not empty
            PATState s = queue[dp++];
            loop: for (PATrans t = s.transList; t != null; t = t.next){  // find edge or last
                PATState ts = t.nextState;
                if (s != is && ts == fs){                          // end state
                    for (PATrans u = is.transList;
                        u != null; u = u.next){                    // edges from initial state
                        if (u.nextState == fs) continue;
                        nfa.addEdge(s,u.nextState,u.sym,newBSsymbol(t.tag,u.tag));  // loopback
                    }
                }
                for (int j = 0; j < qp; j++){
                    if (queue[j] == t.nextState) continue loop;    // already visited
                }
                if (qp >= queue.length){
                    queue = Arrays.copyOf(queue,queue.length+100);
                }
                queue[qp++] = t.nextState;        // enqueue it
            }
        }
    }

    /**
     * Purge the transitions of the specified state.
     *
     * @param      nfa reference to the state table
     * @param      s reference to the state
     */

    private void pat_purge_trans(PATStateTable nfa, PATState s){
        PATrans pr = null;
        outer: for (PATrans t = s.transList; t != null; t = t.next){      // visit transitions
            PATrans pu = t;
            for (PATrans u = t.next; u != null; u = u.next){      // visit all others
                if (t.nextState != u.nextState ||                 // take only the ones that ..
                    t.sym != u.sym){                              // .. have same states and symbols
                    pu = u;
                    continue;
                }
                int cmp = pat_comp_tags(t.tag,u.tag);
                if (cmp > 0){                                     // tag of first less prior or equal
                    if (pr == null){                              // remove it
                        s.transList = t.next;
                    } else {
                        pr.next = t.next;
                    }
                    continue outer;
                } else if (cmp < 0){                              // remove the other
                    if (pu == t){
                        t.next = u.next;
                    } else {
                        pu.next = u.next;
                    }
                }
                pu = u;
            }
            pr = t;
        }
    }

    /**
     * Purge the transitions of the states that can be reached from the specified state.
     *
     * @param      nfa reference to the state table
     * @param      is reference to the state
     */

    private void pat_purge(PATStateTable nfa, PATState is){
        int dp = 0;
        int qp = 0;
        PATState[] queue = new PATState[100];
        queue[qp++] = is;   // start from it
        while (dp != qp){                           // while queue not empty
            PATState s = queue[dp++];
            s.isReacheable = true;
            pat_purge_trans(nfa,s);
            loop: for (PATrans t = s.transList; t != null; t = t.next){  // find edge or last
                for (int j = 0; j < qp; j++){
                    if (queue[j] == t.nextState) continue loop;    // already visited
                }
                if (qp >= queue.length){
                    queue = Arrays.copyOf(queue,queue.length+100);
                }
                queue[qp++] = t.nextState;        // enqueue it
            }
        }
    }

    /*
     * Here follows the description of how the transitions are stored. Currently, effectivetrans
     * is called with a set of states, and a set of transitions and delivers a set of transitions:
     *
     *       list                    list
     *         from, to <--------------from, to
     *         ...
     *
     *       K                        K
     *         state                    all the to states of the new trans
     *         ...
     *
     * Effectivetrans could process the current list and take the to states instead of K.
     * There is no need to avoid duplicates because converging transitions are not present.
     * I could represent transitions as entries in the plist:
     *
     *         trans-nr
     *         prev       (index in pstack)
     *         ...
     *         sentinel
     *
     * The problem is that effectivetrans must take all states in the current list (to states),
     * out of them take the ones that have transitions for the current symbol, and then take the
     * ones that have transitions that converge on a same state. This is not simple.
     * Suppose I have a table that for each state tells what states have an edge to it (note that
     * in a PAT all incoming edges of a state have the same symbol). Sort of incidence matrix.
     * I must still visit the list to spot such states. So, there is no way.
     * However, at least I would avoid to scan the transitions of the from-state candidates
     * for a convergence.
     *
     *        tr1--.s1--.--s4
     *        tr2--'s1   `-s5
     *        ...  |
     *        tr3--'s1
     *        tr4---s2
     *
     * Visiting tr1 we must ensure that we skip tr2, but also that when visiting tr2 we skip it.
     * So, the outer loop should have a dotter.
     * Let's take tr1: we visit all edges of s1 and taken one, we visit the other tr's (skipping
     * the ones to s1) and find the ones whose end-state has an edge to s4: this is a convergence.
     * Each time a convergence is found, the winner edge is determined (and this is known rekoning
     * the its state), to be used when finding the next convergence to determine again the winner.
     */

    /** The autoaging vector to check for duplicated transitions. */
    private int[] trdotter;
    /**
     * Match the text against the compiled RE. Upon return, <code>error</code> is <code>false</code>
     * if the match is successful, and <code>true</code> otherwise.
     *
     * @param      text string to be matched
     * @param      nfa reference to the state table
     * @param      maketree <code>true</code> to build alto the tree, <code>false</code> otherwise
     */

    private void matchPAT(String text, PATStateTable nfa, boolean maketree){
        this.tokens += text.length();
        this.error = false;
        this.plistEnd = 0;                // init list of transitions
        if (this.plist == null){
            int len = nfa.nrEdges * 2 + 1;
            if (len < 100) len = 100;
            this.plist = new int[len];
            this.memory += len * 4 + 4;
            this.nrnew++;
        }
        this.plist[this.plistEnd++] = 0;    // fake transition to initial state
        this.plist[this.plistEnd++] = 0;    // leftpointer
        this.plist[this.plistEnd++] = -3;   // sentinel
        
        BLEN = nfa.stateNr;
        DLEN = BLEN*BLEN;
        if (this.data1 == null || this.data1.length < DLEN * 2){
            if (this.data1 != null) this.garbage += (this.data1.length * 4 + 4) * 2;
            data1 = new int[DLEN * 2];
            data2 = new int[DLEN * 2];
            this.memory += (data1.length * 4 + 4) * 2;
            this.memdata += (data1.length * 4 + 4) * 2;
            this.nrnew++;
        }

        if (this.dotter == null || this.dotter.length < nfa.stateNr){
            if (this.dotter != null) this.garbage += this.dotter.length * 4 + 4;
            this.dotter = new int[nfa.stateNr];
            this.memory += this.dotter.length * 4 + 4;
            this.memdotter += this.dotter.length * 4 + 4;
            this.nrnew++;
        }
        Arrays.fill(this.dotter,-1);
        if (this.trdotter == null || this.trdotter.length < nfa.nrEdges){
            if (this.trdotter != null) this.garbage += this.dotter.length * 4 + 4;
            this.trdotter = new int[nfa.nrEdges];
            this.memory += this.trdotter.length * 4 + 4;
            this.memdotter += this.dotter.length * 4 + 4;
            this.nrnew++;
        }
        Arrays.fill(this.trdotter,-1);
        int curlist = 0;
        int nextlist = 0;
        for (int i = 0; i <= text.length(); i++){
            char sym;
            if (i == text.length()){
                sym = nfa.maxSymb;
            } else {
                sym = text.charAt(i);
                if (sym >= nfa.maxSymb){
                    this.error = true;
                    break;
                }
            }
            nextlist = this.plistEnd;
            PATeffectiveTrans(nfa,this.data1,sym,i,curlist,nextlist);  // choose transitions available for this step
            if (this.plistEnd == nextlist){
                break;
            }
            if (sym == nfa.maxSymb) break;
            PATproceedOneStep(nfa,this.data1,this.data2,nextlist);      // update D, P, K and B accordingly
            int[] data = this.data1;
            this.data1 = this.data2;
            this.data2 = data;
            curlist = nextlist;
        }
        boolean finalFound = false;
        for (int i = nextlist; this.plist[i] >= 0; i +=2){
            PATrans t = nfa.transTable[this.plist[i]];
            PATState s = t.nextState;                        // to state
            if (s.isFinal){
                finalFound = true;
                break;
            }
        }
        if (!finalFound){
            this.error = true;
        }
        // build the tree
        if (!error && maketree){
            int len = 0;
            for (int i = nextlist; i > 0; i = this.plist[i+1]){
                len += nfa.transTable[this.plist[i]].tag.arr.length+1;
            }
            if (this.tree == null){
                this.tree = new BSsymbol();
                this.memory += 8;
                this.nrnew++;
            }
            this.treeLen = len;
            if (this.bsarr == null){
                this.bsarr = new int[len];
                this.memory += len * 4 + 4;
                this.membsarr += len * 4 + 4;
            } else if (this.bsarr.length < len){
                this.garbage += this.bsarr.length * 4 + 4;
                int newlen = this.bsarr.length < 10000 ? this.bsarr.length*2 : this.bsarr.length+10000;
                if (newlen < len) newlen = len;
                this.bsarr = new int[newlen];
                this.memory += newlen * 4 + 4;
                this.membsarr += newlen * 4 + 4;
                this.nrnew++;
            }
    
            for (int i = nextlist; i > 0; i = this.plist[i+1]){
                // the symbol is actually the ast node with the same pos as the last element
                // of the tag, except when the symbol is eof, which is matched only in ANYSMB
                // edges, in which case it is the eof ast
                PATrans t = nfa.transTable[this.plist[i]];
                int[] arr = t.tag.arr;
                int s = arr[arr.length-1] & ELEMASK;
                if (t.sym == ANYSYMB) s = nfa.eofseq;
                this.bsarr[len-1] = s;
                len -= arr.length + 1;
                System.arraycopy(arr,0,this.bsarr,len,arr.length);
            }
            this.tree.arr = this.bsarr;
        }
    }

    /**
     * Trace the list that starts at the specified start index.
     *
     * @param      nfa reference to the state table
     * @param      start start index of the list
     */

    private int tracePatList(PATStateTable nfa, int start){
        int i = start;
        for (; this.plist[i] >= 0; i += 2){
            PATrans t = nfa.transTable[this.plist[i]];
            int left = this.plist[i+1];
            PATState from = nfa.head;
            if (left != 0){
                PATrans prev = nfa.transTable[this.plist[left]];
                from = prev.nextState;
            }
            Trc.out.printf("%s: trans %s %s--%s left %s\n",
                i,this.plist[i],from.toId(),nfa.transToString(t),left);
        }
        Trc.out.printf("%s: len: %s\n",i,this.plist[i]);
        return i;
    }

    /**
     * Trace all the lists.
     *
     * @param      nfa reference to the state table
     */

    private int tracePatLists(PATStateTable nfa){
        int i = 0;
        for (; i < this.plistEnd; i++){
            i = tracePatList(nfa,i);
        }
        return i;
    }

    /**
     * Determine the transitions to the next states, choosing when there are some that
     * converge to a same state.
     *
     * @param      nfa reference to the state table
     * @param      data data array
     * @param      sym symbol
     * @param      n index of the first unexpended symbol in the text
     * @param      curlist index of the current list
     * @param      nextlist index of the next list
     */

    private void PATeffectiveTrans(PATStateTable nfa, int[] data, char sym, int n,
        int curlist, int nextlist){
        for (int i = curlist; this.plist[i] >= 0; i += 2){
            PATState s = nfa.transTable[this.plist[i]].nextState;   // take to state of trans in current list
            int p = s.number;
            if (this.dotter[p] >= n){
                continue;
            }
            this.dotter[p] = n;
            int len = faActions(nfa,p,sym);                // take its edges
            int start = this.faStart;
            if (len == 0) continue;                        // no transitions for sym
            for (int k = 0; k < len; k++){
                int trn = nfa.trtable[start+k];            // take an edge for sym, its number
                PATrans pt = nfa.transTable[trn];
                PATState sq = pt.nextState;
                int q = sq.number;
                // for all state q in Qa such that tag(p, q) != _|_ for some p in K
                // i.e. take a state p in K, then all states q reached transiting from p with sym
                // such that there is at least an edge with tag defined

                // for all states p' in K' such that tag(p',q) != _|_

                int rho = -1;
                int rhop = -1;
                int newi = i;
                int newp = p;
                int newt = trn;
                // there is no way to avoid to scan all the list because if we do not
                // do it, we could miss the comparison with some state that has a winner
                // edge and then think that the current one is the winner instead
                for (int j = curlist; this.plist[j] >= 0; j += 2){
                    PATState sp = nfa.transTable[this.plist[j]].nextState;  // take to state
                    int pp = sp.number;
                    if (pp == p) continue;          // K' = K\{p}: do not consider p

                    // check that there is an edge from pp to q (which certainly is for sym)
                    int adjbas = nfa.adjbase[pp];
                    int adjstart = adjbas+q;
                    int tp = nfa.adjcheck[adjstart] == adjbas ? nfa.adjtable[adjstart] : 0;
                    if (tp == 0) continue;               // no transitions reaching q
                    // this is never the case on the first step because if there are two edges
                    // from the start state that converge, they were removed when the NFA was purged
                    rho = data[BLEN*newp+pp];
                    rhop = data[BLEN*pp+newp];     // <rho, rho'> = B[p][p']
                    int mrho = nfa.transTable[newt].minsp;
                    int mrhop = nfa.transTable[tp].minsp;
                    if (mrho < rho) rho = mrho;
                    if (mrhop < rhop) rhop = mrhop;
                    int D = data[DLEN + BLEN*pp + newp];
                    // p = p' if rho < rho' or rho = rho' and D[p'][p] = 1
                    if (rho < rhop || rho == rhop && D == 1){
                        newp = pp;
                        newi = j;
                        newt = tp;
                    }
                }
                // add <p, q, tag(p, q)> to trans
                add: {
                    if (this.trdotter[newt] >= n){
                        break add;
                    }
                    this.trdotter[newt] = n;
                    if (this.plist.length - this.plistEnd < 2){
                        this.garbage += this.plist.length * 4 + 4;
                        this.plist = Arrays.copyOf(this.plist,this.plist.length*2);
                        this.memory += this.plist.length * 4 + 4;
                        this.memplist += this.plist.length * 4 + 4;
                        this.nrcopyof++;
                    }
                    this.plist[this.plistEnd++] = newt;
                    this.plist[this.plistEnd++] = newi;
                }
            }
        }
        if (this.plist.length - this.plistEnd < 2){
            this.garbage += this.plist.length * 4 + 4;
            this.plist = Arrays.copyOf(this.plist,this.plist.length*2);
            this.memory += this.plist.length * 4 + 4;
            this.memplist += this.plist.length * 4 + 4;
            this.nrcopyof++;
        }
        this.plist[this.plistEnd++] = nextlist - this.plistEnd;    // sentinel
      
    }

    /**
     * Update the PAT data that record what paths are prior to what others.
     *
     * @param      nfa reference to the state table
     * @param      prev index of the previous list
     * @param      data data array
     * @param      nextlist index of the next list
     */

    private void PATproceedOneStep(PATStateTable nfa, int[] prev, int[] data, int nextlist){
        // It is also possible to treat the case q_$ separately.
        // Note that minsp(alpha), and hence the rho-value, is always zero for any
        // incoming transition alpha of q_$ (e.g., A11, A12, A4 ...).
        // Hence, the calculation at line 3 to 14 is significantly simplified in case
        // either q or q'  is q_$.  

        //for all <p, q, alpha>, <p', q', alpha'> in trans such that q <. q' {
        loop: for (int i = nextlist; this.plist[i] >= 0; i +=2){
            PATrans t = nfa.transTable[this.plist[i]];
            PATState qs = t.nextState;                        // to state
            int q = qs.number;
            int p = this.plist[this.plist[i+1]];
            PATState ps = nfa.transTable[p].nextState;        // from state
            p = ps.number;
            // visit only the following part of the list so as not to process
            // twice the same pairs, albeit swapped
            for (int j = i+2; this.plist[j] >= 0; j += 2)
                {
                if (j == i) continue;
                PATrans tp = nfa.transTable[this.plist[j]];
                PATState qsp = tp.nextState;                  // to state
                int qp = qsp.number;
                int pp = this.plist[this.plist[j+1]];
                PATState psp = nfa.transTable[pp].nextState;  // from state
                pp = psp.number;
                int rho = 0;
                int rhop = 0;
                if (ps == psp){              // p = p'
                    // here we have two transitions from the same state leading to different
                    // states: in a NFA from a state there can be several arcs with the same
                    // symbol
                    int cmp = nfa.subsTable[this.plist[i]][this.plist[j]];
                    // to compare two tags, take away the common prefix, then the first
                    // is prior if it starts with (p and the second: starts with )q or
                    // with (q with p <. q
                    // bp0(a,b): take away the common prefix from "a" except for the last element,
                    // bp0 is the minimum height in it
                    rho = nfa.bp0Table[this.plist[i]][this.plist[j]];    // rho = bp0(alpha,alpha');
                    rhop = nfa.bp0Table[this.plist[j]][this.plist[i]];   // rho' = bp0(alpha',alpha)
                    int newD = 0;
                    if (rho > rhop){
                        newD = 1;               // D[q][q'] = 1 if rho > rho';
                    } else if (rho < rhop){
                        newD = -1;              // D[q][q'] = -1 if rho < rho';
                    } else if (cmp < 0){
                        newD = 1;               // D[q][q'] = 1 if alpha |= alpha';
                    } else if (cmp > 0){
                        newD = -1;              // D[q][q'] = -1 if alpha' |= alpha;
                    }
                    if (newD != 0){
                        data[DLEN+BLEN*q+qp] = newD;
                    }
                } else {
                    rho = prev[BLEN*p+pp];
                    rhop = prev[BLEN*pp+p];
                    int mrho = t.minsp;
                    int mrhop = tp.minsp;
                    if (mrho < rho) rho = mrho;        // rho = min{rho, minsp(alpha)};
                    if (mrhop < rhop) rhop = mrhop;    // rho = min{rho, minsp(alpha)};
                    int newD = 0;
                    if (rho > rhop){
                        newD = 1;                      // D[q][q'] = 1 if rho > rho';
                    } else if (rho < rhop){
                        newD = -1;                     // D[q][q'] = -1 if rho < rho';
                    } else {
                        newD = prev[DLEN+BLEN*p+pp];   // D[q][q'] = D'[p][p']
                    }
                    data[DLEN+BLEN*q+qp] = newD;
                }
                data[DLEN+BLEN*qp+q] = -data[DLEN+BLEN*q+qp];  // D[q'][q] = -D[q][q']
                data[BLEN*q+qp] = rho;
                data[BLEN*qp+q] = rhop;
            }
        }
    }


    //--------------- Sulzmann I--------------------------------------

    /**
     * Construct the derivatives for the specified RE and text and deliver a string
     * representing them.
     *
     * @param      re string of the RE
     * @param      text string
     * @return     string
     */

    private String makeDeriv(String re, String text){
        String res = null;
        doit: {
            this.re = re;
            buildAst();
            if (getsym() != -1){    // error or whole re not consumed
                this.error = true;
            }
            if (this.error) break doit;
            buildSulz(this.astRoot);
            res = matchSulz(text);
        }
        return res;
    }

    /**
     * Convert the specified n-ary AST into a binary one.
     *
     * @param      ast AST
     */

    private void buildSulz(AstNode ast){
        astToBinary(astRoot);
    }

    /**
     * Convert the specified n-ary AST into a binary one.
     *
     * @param      ast AST
     */

    private void astToBinary(AstNode ast){
        switch (ast.kind){
        case A_LEA:      // leaf
            break;
        case A_ALT:      // alt
            if (ast.son.bro.bro == null){                          // two alternatives
                astToBinary(ast.son);
                astToBinary(ast.son.bro);
                break;
            }
            astToBinary(ast.son);
            AstNode bro = null;
            AstNode last = null;
            for (AstNode i = ast.son.bro; i.bro != null; i = bro){     // visit the 2..n ones
                last = i.bro;
                astToBinary(i);
                AstNode a = newAstNode(i.kind);
                a.groupKind = i.groupKind;
                a.son = i.son;
                a.bro = i.bro;
                a.sym = i.sym;
                a.altnr = 1;
                i.son = a;
                bro = i.bro;
                i.bro = null;
                i.kind = A_ALT;
                i.altnr = 2;
            }
            astToBinary(last);
            break;
        case A_CON:      // conc
            if (ast.son.bro.bro == null){                          // two alternatives
                astToBinary(ast.son);
                astToBinary(ast.son.bro);
                break;
            }
            astToBinary(ast.son);
            bro = null;
            last = null;
            for (AstNode i = ast.son.bro; i.bro != null; i = bro){     // visit the 2..n ones
                last = i.bro;
                astToBinary(i);
                AstNode a = newAstNode(i.kind);
                a.groupKind = i.groupKind;
                a.son = i.son;
                a.bro = i.bro;
                a.sym = i.sym;
                a.altnr = 0;
                i.son = a;
                bro = i.bro;
                i.bro = null;
                i.kind = A_CON;
                i.altnr = 0;
            }
            astToBinary(last);
            break;
        case A_GRO:      // group
            astToBinary(ast.son);
        case A_EMP:      // empty
        case A_NUL:      // phi
            break;
        }
    }

    /** The list of AST nodes that represent the progress of matching . */
    private AstNode[] astList;

    /**
     * Match the text against the compiled RE. Upon return, <code>error</code> is <code>false</code>
     * if the match is successful, and <code>true</code> otherwise.
     *
     * @param      text string to be matched
     */

    private String matchSulz(String text){
        this.tokens += text.length();
        this.error = false;
        this.astList = new AstNode[100];
        this.psp = 0;
        this.astList[this.psp++] = this.astRoot;
        AstNode astRoot = this.astRoot;
        TreeNode root = null;
        // recognize
        doit: {
            for (int i = 0; i < text.length(); i++){
                this.astSeq = 0;
                AstNode der = null;
                der = derivative(astRoot,text.charAt(i));  // process the ast and build a new one that is the derivative
                this.astMap = new AstNode[this.astSeq];
                this.astSeq = 0;
                setPosAst(der,new int[0],null);
                if (this.astList == null){
                    this.astList = new AstNode[100];
                    this.memory += 404;
                    this.mempstack += 404;
                    this.nrnew++;
                } else if (this.psp >= this.astList.length){
                    this.garbage += this.astList.length * 4 + 4;
                    this.astList = Arrays.copyOf(this.astList,this.astList.length*2);
                    this.memory += this.astList.length * 4 + 4;
                    this.mempstack += this.astList.length * 4 + 4;
                    this.nrcopyof++;
                }
                this.astList[this.psp++] = der;
                astRoot = der;
            }
            if (!isNullable(astRoot)){
                this.error = true;
                break doit;
            }

            treeNodeSeq = 0;
            treeNodeHead = null;
            root = mkeps(astRoot);
            String injs = "";
            for (int j = text.length()-1; j >= 0; j--){
                String treebefore = treeToSulz(root);
                inj(astList[j],text.charAt(j),root);
                if (this.error){
                    break doit;
                }
                injs += "(" + astList[j].toRE(true) + ")\\" + text.charAt(j) + " " +
                   treebefore + " = " + treeToSulz(root);
            }
            return treeToString(root);
        } // doit
        return "";
    }

    /**
     * Trace the AST rooted in the specified AST node.
     *
     * @param      node root AST node
     */

    private void astTrace(AstNode node){
        astTrace(node,0);
    }

    /**
     * Trace the AST rooted in the specified AST node indenting the output at the specified
     * nesting depth.
     *
     * @param      node root AST node
     * @param      lev nesting depth
     */

    private void astTrace(AstNode node, int lev){
        String indent = "";
        for (int i = 0; i < lev; i++) indent += "    ";
        Trc.out.printf("%s%s\n",indent,astNodeToString(node));
        for (AstNode i = node.son; i != null; i = i.bro){
            astTrace(i,lev+1);
        }
    }

    /**
     * Trace the specified AST node.
     *
     * @param      node AST node
     */

    private String astNodeToString(AstNode node){
        String str = node.seq + ": ";
        str += astKindString(node);
        if (node.kind == 0){
            str += " " + node.sym;
        }
        if (node.son != null) str += " son: " + node.son.seq;
        if (node.bro != null) str += " bro: " + node.bro.seq;
        return str;
    }

    /**
     * Deliver an AST which is the derivative of the specified one taking away
     * the specified symbol.
     *
     * @param      ast AST node
     * @param      sym symbol
     */

    private AstNode derivative(AstNode ast, char sym){
        AstNode node = null;
        switch (ast.kind){
        case A_LEA:      // leaf
            if (sym == ast.sym){
                node = newAstNode(A_EMP);       // epsilon
                node.sym = '\u03b5';
            } else {
                node = newAstNode(A_NUL);       // phi
                node.sym = '\u03a6';
            }
            break;
        case A_ALT:      // alt
            node = newAstNode(A_ALT);
            node.son = derivative(ast.son,sym);
            node.son.altnr = 1;
            node.son.bro = derivative(ast.son.bro,sym);
            node.son.bro.altnr = 2;
            break;
        case A_CON:      // conc
            /*
            (abc)\l =
            (a\l)(bc) +
            if (a nullable) (b\l)c +
            if (b nullable) c\l
            */
            if (!isNullable(ast.son)){
                node = newAstNode(A_CON);
                node.son = derivative(ast.son,sym);
                node.son.bro = cloneConc(ast.son.bro);
            } else {
                node = newAstNode(A_ALT);
                node.son = newAstNode(A_CON);
                node.son.altnr = 1;
                node.son.son = derivative(ast.son,sym);
                node.son.son.bro = cloneConc(ast.son.bro);
                node.son.bro = derivative(ast.son.bro,sym);
                node.son.bro.altnr = 2;
            }
            break;
        case A_GRO:      // group
            switch (ast.groupKind){
            case G_GRO:
            case G_OPT:
                node = newAstNode(A_GRO);
                node.son = derivative(ast.son,sym);
                break;
            case G_RE0:
            case G_RE1:
                node = newAstNode(A_CON);
                node.son = derivative(ast.son,sym);
                node.son.bro = astDeepClone(ast);
                node.son.bro.groupKind = G_RE0;           // r+\l = (r\l)r*
                break;
            }
            break;
        case A_EMP:      // empty
        case A_NUL:      // phi
            node = newAstNode(A_NUL);
            node.sym = '\u03a6';
            break;
        }
        return node;
    }

    /**
     * Deliver a clone of the specified AST, clong also its subtree.
     *
     * @param      ast AST node
     * @return     reference to the root node of the cloned AST
     */

    private AstNode astDeepClone(AstNode ast){
        AstNode node = newAstNode(ast.kind); 
        node.sym = ast.sym;
        node.groupKind = ast.groupKind;
        node.pos = ast.pos;
        node.altnr = ast.altnr;
        AstNode prev = null;
        for (AstNode i = ast.son; i != null; i = i.bro){
            if (i == ast.son){        // first
                node.son = astDeepClone(i);
                prev = node.son;
            } else {
                prev.bro = astDeepClone(i);
                prev = prev.bro;
            }
        }
        return node;
    }

    /**
     * Deliver a list of clones of the specified AST and its brothers.
     *
     * @param      ast AST node
     * @return     reference to the cloned AST
     */

    private AstNode cloneConc(AstNode ast){
        AstNode node = null;
        AstNode prev = null;
        for (AstNode i = ast; i != null; i = i.bro){
            if (i == ast){        // first
                prev = astDeepClone(i);
                node = prev;
            } else {
                prev.bro = astDeepClone(i);
                prev = prev.bro;
            }
        }
        return node;
    }

    /**
     * Tell if the specified AST represents a RE that generates the empty string.
     *
     * @param      ast AST node
     * @return     <code>true</code> if it does, <code>false</code> otherwise
     */

    private boolean isNullable(AstNode ast){
        boolean res = false;
        doit: switch (ast.kind){
        case 0:      // leaf
            res = false;
            break;
        case 1:      // alt
            for (AstNode i = ast.son; i != null; i = i.bro){
                if (isNullable(i)){
                    res = true;
                    break doit;
                }
            }
            break;
        case A_CON:      // conc
            for (AstNode i = ast.son; i != null; i = i.bro){
                if (!isNullable(i)){
                    res = false;
                    break doit;
                }
            }
            res = true;
            break;
        case A_GRO:      // group
            switch (ast.groupKind){
            case G_GRO:
            case G_RE1:
                res = isNullable(ast.son);
                break;
            case G_OPT:
            case G_RE0:
                res = true;
                break;
            }
            break;
        case A_EMP:      // empty
            res = true;
            break;
        case A_NUL:      // phi
            res = false;
            break;
        }
        return res;
    }

    /** A node in the parse tree of the string. */

    private static class TreeNode {

        /** The serial number. */
        int seq;

        /** The reference to the brother. */
        TreeNode bro;

        /** The reference to the son. */
        TreeNode son;

        /** The reference to the father. */
        TreeNode fat;

        /** The kind of node: 0: leaf, 1: alt, 2: conc, 3: group, 4: empty, 5: void (empty set). */
        int kind;

        /** The kind of group (if group). */
        int groupKind;

        /** The reference to the value: either a subtree or an ast denoting a leaf. */
        AstNode ele;

        /** The reference to the next node in the list of nodes. */
        TreeNode next;

        /** The number of alternative (when this node is an alternative). */
        int altnr;

        /**
         * Deliver a string representing this node.
         *
         * @return     string
         */

        public String toString(){
            String str = "";
            str += this.seq + ": ";
            str += "kind: " + this.kind + " ";
            if (this.altnr != 0) str += "|" + this.altnr + " ";
            if (this.kind == 0){
                if (this.ele.kind == A_GRO){   // group with no sons: empty
                    str += "&epsilon;";
                } else {
                    str += this.ele.sym;
                }
            } else {
                str += "subtree kind: " + this.kind + " groupKind: " + this.groupKind;
                str += " ast: " + this.ele;
            }
            if (this.son != null) str += " son: " + this.son.seq;
            if (this.bro != null) str += " bro: " + this.bro.seq;
            return str;
        }
    }

    /** The serial number of nodes. */
    private int treeNodeSeq;

    /** The head of the list of nodes. */
    private TreeNode treeNodeHead;

    /**
     * Construct a new tree node object with the specified kind.
     *
     * @param      kind kind
     * @return     reference to the constructed node
     */

    private TreeNode newTreeNode(int kind){
        TreeNode node = new TreeNode();
        node.seq = this.treeNodeSeq++;
        node.next = this.treeNodeHead;
        this.treeNodeHead = node;
        node.kind = kind;
        this.memory += 9 * 4 + 4;
        return node;
    }

    /**
     * Deliver an empty Posix tree for the specified AST. It generates here a tree that is
     * more structured than that of Sulzman: inj then has been adapted to it.
     *
     * @param      ast reference to the root AST node
     * @return     reference to the tree
     */

    private TreeNode mkeps(AstNode ast){
        TreeNode node = null;
        switch (ast.kind){
        case A_LEA:      // leaf
            break;
        case A_ALT:      // alt
            node = newTreeNode(ast.kind);
            node.ele = ast;
            if (isNullable(ast.son)){
                node.son = mkeps(ast.son);
            } else {
                node.son = mkeps(ast.son.bro);
            }
            break;
        case A_CON:      // conc
            node = newTreeNode(ast.kind);
            node.ele = ast;
            node.son = mkeps(ast.son);
            node.son.bro = mkeps(ast.son.bro);
            break;
        case A_GRO:      // group
            node = newTreeNode(ast.kind);
            node.ele = ast;
            switch (ast.groupKind){
            case G_GRO:
                node.son = mkeps(ast.son);
                break;
            case G_RE1:
            case G_OPT:
            case G_RE0:
            }
            break;
        case A_EMP:      // empty
            node = newTreeNode(ast.kind);
            node.ele = ast;
            break;
        case A_NUL:      // phi
            break;
        }
        return node;
    }

    /*
     * Concatenation: inj r1r2
     *
     *    tree      updated          tree          updated
     *
     *       |          .              |               .
     *      /left      / \              \ right       / \
     *      .         /   v2             v2          /   inj r2\l v2
     *     / \     inj r1\l                       mkeps r1
     *    v1 v2
     *
     * -  since it calls itself only as the last action, it should be possible to transform it
     *    into a loop without recursive calls
     *
     * -  not sure that simple groups show up properly in the final tree
     * -  for sure there are extra simple groups in the final tree, which is perhaps due to the
     *    fact that derivative() adds extra groups, which should be removed by inj (not by makeps
     *    because it is unable to attach to the son of the fake group an ast pointer that contains
     *    the alternative number)
     * -  not introducing an extra group produces an ast in which sequences of alternatives and
     *    concatenations have nodes that are themselves alternatives and concatenations (and not
     *    groups), which is a bit strange, but seems to work
     *
     * -  what needs to be assessed is the make sure that () show up properly in the final tree;
     *    and to n-arize the final tree
     * -  the tree is built adding a leaf at a time, and possibly intermediate nodes, using the
     *    ast of the derivative that matches that leaf. This means that the ast references in nodes
     *    refer to nodes in different ast's. There would be a need to have a final tree in which
     *    these pointers refer nodes of the ast of the RE. Perhaps this could be done having a
     *    pointer in the ast nodes that point to the nodes of the RE's one from which they originated.
     *    However, the form of the final tree follows that of the RE's ast (except that * have several
     *    sons and | have only one), so the pairing of the two (i.e. linking nodes in the tree to
     *    the ones in the ast) should be simple.
     */

    /**
     * Inject the symbol as a leaf in the specified tree at the position specified by the AST.
     *
     * @param      ast reference to the AST node
     * @param      sym symbol
     * @param      tree reference to the tree
     */

    private void inj(AstNode ast, char sym, TreeNode tree){
        doit: switch (ast.kind){
        case A_LEA:      // leaf
            if (tree.kind != A_EMP){
                this.error = true;
                break;
            }
            tree.kind = ast.kind;         // change the node: put the leaf in it
            tree.ele = ast;
            break;
        case A_ALT:      // alt
            if (tree.kind != A_ALT){
                this.error = true;
                break;
            }
            if (tree.son.ele.altnr == 1){
                inj(ast.son,sym,tree.son);
            } else {
                inj(ast.son.bro,sym,tree.son);
            }
            if (this.error) break doit;
            break;
        case A_CON:      // conc
            if (tree.kind == A_CON){
                inj(ast.son,sym,tree.son);
                if (this.error) break;
            } else if (tree.kind == A_ALT){
                /*
                (a(bc))\l = 
                (a\l)bc + ((b\l)c + c\l)
                left(v1,v2) -> (inj r1\l v1,v2)
                   .. remove alternative
                right v2 -> (mkeps r1,inj r2\l v2)
                   .. change alt in conc
                i.e. mkeps for all the preceding ones
                */
                tree.kind = A_CON;             // change kind
                tree.ele = ast;
                TreeNode son = tree.son;
                if (tree.son.ele.altnr == 1){  // Left(v1,v2)
                    inj(ast.son,sym,son.son);
                    tree.son = son.son;
                } else {                       // Right v2
                    tree.son = mkeps(ast.son);
                    inj(ast.son.bro,sym,son);
                    tree.son.bro = son;
                }
            } else {
                this.error = true;
                break;
            }
            break;
        case A_GRO:      // group
            switch (ast.groupKind){
            case G_GRO:
            case G_OPT:
                inj(ast.son,sym,tree.son);
                if (this.error) break;
                break;
            case G_RE1:
            case G_RE0:
                if (tree.kind != A_CON){
                    this.error = true;
                    break;
                }
                // restructure this tree node: if the following node is an empty *,
                // then the conc must become a leaf; if the tree is a leaf, then it must become a conc
                // and if it is a conc with true leaves, then a node must be prepended to the sons
                inj(ast.son,sym,tree.son);
                if (this.error) break;
                if (tree.son.bro.kind == A_GRO && tree.son.bro.ele.groupKind == G_RE0 && tree.son.bro.son == null){
                    tree.son.bro = null;
                    break;
                }
                if (tree.son.bro.kind == A_CON){
                    tree.son.bro = tree.son.bro.son;
                }
                break;
            }
            break;
        case A_EMP:      // empty
            break;
        case A_NUL:      // phi
            this.error = true;
            break;
        }
    }    

    /**
     * Trace the specified tree.
     *
     * @param      tree reference to the root of the tree
     */

    private void treeTrace(TreeNode tree){
        treeTrace(tree,0);
    }

    /**
     * Trace the specified tree indenting the nodes in accordance with their nesting depth.
     *
     * @param      tree reference to the root of the tree
     * @param      lev nesting depth
     */

    private void treeTrace(TreeNode tree, int lev){
        String indent = "";
        for (int i = 0; i < lev; i++) indent += "    ";
        Trc.out.printf("%s %s\n",indent,treeNodeToString(tree));
        for (TreeNode i = tree.son; i != null; i = i.bro){
            treeTrace(i,lev+1);
        }
    }

    /**
     * Deliver a string representing the specified tree node.
     *
     * @param      tree reference to the node
     * @return     string
     */

    private String treeNodeToString(TreeNode node){
        String str = node.seq + ": ";
        AstNode ast = node.ele;
        str += astKindString(ast);
        if (node.kind == A_LEA){
            if (ast.kind == A_GRO){   // group with no sons: empty
                str += " ()";
            } else {
                str += " " + ast.sym;
            }
        }
        if (node.son != null) str += " son: " + node.son.seq;
        if (node.bro != null) str += " bro: " + node.bro.seq;
        return str;
    }

    /**
     * Deliver a string representing the specified tree.
     *
     * @param      tree reference to the root of the tree
     * @return     string
     */

    private String treeToString(TreeNode tree){
        String str = "";
        AstNode ast = tree.ele;
        boolean open = false;
        boolean bropen = false;
        switch (ast.kind){
        case A_LEA: str += tree.ele.sym; break;
        case A_ALT:
            switch (tree.son.ele.kind){
            case A_LEA: case A_ALT: case A_NUL:
                str += "(";
                open = true;
                break;
            }
            break;
        case A_CON: str += "("; break;
        case A_GRO:
            if (ast.groupKind == G_RE0 || ast.groupKind == G_RE1){
                str += "(";
                bropen = true;
            }
            break;
        case A_EMP: str += "\u03b5"; break;
        case A_NUL: str += "\u03b5"; break;
        }
        for (TreeNode i = tree.son; i != null; i = i.bro){
            str += treeToString(i);
        }
        switch (ast.kind){
        case A_ALT:
            if (open) str += ")";
            break;
        case A_CON:
            str += ")";
            break;
        case A_GRO:
            if (bropen) str += ")";
            break;
        }
        return str;
    }

    /**
     * Deliver a string representing the specified tree using the Sulzman's notation.
     *
     * @param      tree reference to the root of the tree
     * @return     string
     */

    private String treeToSulz(TreeNode tree){
        String str = "";
        AstNode ast = tree.ele;
        boolean open = false;
        boolean bropen = false;
        switch (ast.kind){
        case A_LEA: str += tree.ele.sym; break;
        case A_ALT: str += (tree.son.ele.altnr == 1) ? "Left" : "Right";
            switch (((TreeNode)tree.son).ele.kind){
            case A_LEA: case A_ALT: case A_NUL:
                str += "(";
                open = true;
                break;
            default:
                str += " ";
            }
            break;
        case A_CON: str += "("; break;
        case A_GRO:
            if (ast.groupKind == G_RE0 || ast.groupKind == G_RE1){
                str += "[";
                bropen = true;
            }
            break;
        case A_EMP: str += "()"; break;
        case A_NUL: str += "\u03b5"; break;
        }

        for (TreeNode i = tree.son; i != null; i = i.bro){
            if (i != tree.son) str += ",";
            str += treeToSulz(i);
        }
        switch (ast.kind){
        case A_ALT:
            if (open) str += ")";
            break;
        case A_CON:
            str += ")";
            break;
        case A_GRO:
            if (bropen) str += "]";
            break;
        }
        return str;
    }

    // ---------- Earley RE  -----------------

    /*
     * Notes
     *
     *  - it is possible to use dotted rules and move the pointer as I did with myEarley,
     *    but I must build the tree, even if the tree does not need to have exactly the same
     *    form as that produced by BSP
     *  - I represent each group with a dedicated nt, more or less as I did with myEarley
     *  - there is no need for several pedigrees since I can choose the prior when I detect
     *    the ambiguity
     *  - from the AST I produce the encoded grammar
     *  - an Earley engine that builds a forest and then chooses when the forest is complete
     *    using something similar to the Okui calculation can be implemented, but this is PAT
     *  - an Earley engine that uses states containing sets of item as the split-dfa, which
     *    perhaps is faster, but this is BSP
     *  - note that in order to detect the structure of the string parsed, in Earley I represent
     *    each group with a nonterminal. I could avoid this, and advance the dot on the RE, but
     *    I must anyway represent the groups somehow so as to be able, e.g. in a a(b)*c to tell
     *    where is the beginning of (b)* so as to move the dot in front of it once it has been
     *    moved after it. This could be done by keeping also the parenteses in the internal
     *    representation of the RE so as to be able to place the dot in front of them.
     *    In so doing, left chains would be linearized trees with parentheses as the PAT or
     *    BSP have.
     */

    /* Translate the AST into an encoded grammar.
     *
     * In the encoded grammar, each nonterminal (nt), terminal and rule are given unique
     * numbers. The grammar is then represented with a vector in which each rule is stored
     * as a sequence of its elements followed by a sentinel containing its rule number.
     */

    private class EarleyTables {

        /** The map from nt number to the number of its first rule. */
        private int[] ntToRule;

        /** The table of the length of rules. */
        private int[] ruleLen;

        /** The map from rule numbers to the number of their lhs nts. */
        private int[] ruleToNt;

        /** The map from rule numbers to the indexes of their representation in the encoded grammar. */
        private int[] ruleIndex;

        /** The table of the kinds of nts. */
        private int[] ntKind;

        /** The vector containing the encoded grammar. */
        private int[] grammar;

        /** The vector containing the number of the AST of the corresponding element in the grammar vector. */
        private int[] grammartoast;

        /** The number of terminals. */
        private int numOfToks;

        /** The number of nts. */
        private int numOfNts;

        /** The number of rules. */
        private int numOfRules;

        /** The number denoting the first terminal. */
        private int tokBase;

        /** The number denoting the first rule. */
        private int ruleBase;

        /** The array that tells if the nt at the index generates the empty string. */
        private boolean[] nullable;

        /** The compressed table of lookaheads. */
        private int[] latable;

        /** The base for latable. */
        private int[] labase;

        /** The check vector for latable. */
        private int[] lacheck;

        /** The lowest symbol. */
        private int minsym;

        /** The index of the start rule. */
        private int startRule;

        /** The map from nts to AST numbers. */
        private int[] ntToAst;

        /** The root of the AST. */
        private AstNode astRoot;

        /** The map from AST numbers to AST nodes. */
        private AstNode[] astMap;

        /**
         * Trace this grammar.
         */

        private void traceGrammar(){
            for (int i = 1; i < this.grammar.length; i++){
                int gp = this.grammar[i];
                Trc.out.printf("%s: ",i);
                if (gp < this.tokBase){
                    Trc.out.printf(" nt: &%s",gp);
                } else if (gp < this.ruleBase){
                    Trc.out.printf(" term: %s",gramSymToString(gp));
                } else {
                    int ruleNr = gp - this.ruleBase;
                    int nt = this.ruleToNt[ruleNr];
                    Trc.out.printf(" %s nt: &%s rule: %s",gp,nt,ruleNr);
                }
                int astnr = this.grammartoast[i];
                if (astnr != -1){
                    if (gp < this.ruleBase){
                        Trc.out.printf(" ast: %s",astnr);
                    } else {
                        int altast = (astnr >> 16) & 0xffff;
                        int concast = astnr & 0xffff;
                        if (altast != 0xffff){
                            Trc.out.printf(" altast: %s",altast);
                        }
                        if (concast != 0xffff){
                            Trc.out.printf(" concast: %s",concast);
                        }
                    }
                }
                Trc.out.printf("\n");
                if (gp >= this.ruleBase){
                    Trc.out.printf("\n");
                }
            }
            Trc.out.printf("tokBase %s ruleBase %s startRule %s\n",
                this.tokBase,this.ruleBase,this.startRule);
            Trc.out.printf("-- nonterminals --\n");
            for (int i = 0; i < this.numOfNts; i++){
                Trc.out.printf("%s %s kind: %s first rule: %s nullable: %s ast: %s\n",
                    i,gramSymToString(i),groupKindStr[this.ntKind[i]],this.ntToRule[i],
                    this.nullable[i],this.ntToAst[i]);
            }
            Trc.out.printf("-- rules -- %s\n",this.numOfRules);
            for (int i = 0; i < this.ruleToNt.length; i++){
                Trc.out.printf("%s at: %s len: %s: %s\n",
                    i,this.ruleIndex[i],this.ruleLen[i],
                    ruleToString(this.ruleIndex[i],false));
            }
            if (this.labase == null) return;
            Trc.out.printf("-- lookahead tables --\n");
            for (int i = 0; i < this.numOfNts; i++){
                int idx = this.labase[i];
                if (idx == -1) continue;
                Trc.out.printf("nt: %s\n",gramSymToString(i));
                for (int j = 0; j <= this.numOfToks; j++){     // scan tokens
                    if (this.lacheck[idx+j] != idx) continue;  // no rule
                    int ri = this.latable[idx+j];              // rule / ovf zone
                    int nru = 1;
                    int a;
                    if (ri >= 0){                              // rule
                        a = this.ruleIndex[ri];
                    } else {                                   // ovf zone
                        ri = -ri;
                        nru = this.latable[ri++];
                        a = this.ruleIndex[this.latable[ri++]];
                    }
                    do {                                       // print rules
                        Trc.out.printf("    %s ->rule: %s\n",
                            gramSymToString(j+this.tokBase),
                            ruleToString(a,false));
                        if (--nru == 0) break;
                        a = this.ruleIndex[this.latable[ri++]];
                    } while (true);
                }
            }
            for (int i = this.minsym; i <= this.numOfToks; i++){
                int idx = this.labase[i+this.tokBase];
                if (idx == -1) continue;
                Trc.out.printf("term: %s",gramSymToString(i+this.tokBase));
                for (int j = 0; j <= this.numOfToks; j++){     // scan tokens
                    if (this.lacheck[idx+j] != idx) continue;  // terminal
                    Trc.out.printf(" %s",gramSymToString(j+this.tokBase));
                }
                Trc.out.printf("\n");
            }
            for (int i = 0; i < this.numOfRules; i++){
                int idx = this.labase[i+this.ruleBase];
                if (idx == -1) continue;
                Trc.out.printf("rule: %s follow:",gramSymToString(i+this.ruleBase));
                for (int j = 0; j <= this.numOfToks; j++){     // scan tokens
                    if (this.lacheck[idx+j] != idx) continue;  // terminal
                    Trc.out.printf(" %s",gramSymToString(j+this.tokBase));
                }
                Trc.out.printf("\n");
            }
        }

        /**
         * Deliver a string representing the specified rule, possibly with the dot in it.
         *
         * @param      rule index of the rule in the grammar
         * @param      dot <code>true</dot> to include the dot
         * @return     string
         */

        private String ruleToString(int rule, boolean dot){
            String st = "";
            int end = rule;                             // find the end
            while (this.grammar[end] < this.ruleBase) end++;
            int start = end - this.ruleLen[this.grammar[end]-this.ruleBase];
            int nt = this.ruleToNt[this.grammar[end] - 
                this.ruleBase];                         // gramRule
            st += gramSymToString(nt) + " ::= ";
            boolean first = true;
            for (int p = start; this.grammar[p] <
                this.ruleBase; p++){                    // gramRule
                if (!first){
                    st += " ";
                }
                first = false;
                if (dot && (p == rule)){
                    st += "\u00b7";                     // centered dot
                }
                int v = this.grammar[p];
                st += gramSymToString(v);
            }
            if (dot && (this.grammar[rule] >= this.ruleBase)){     // dot at the end
                st += "\u00b7";
            }
            return st;
        }

        /**
         * Deliver a string representing the specified terminal, nonterminal or rule.
         *
         * @param      e unique number denoting the terminal, nonterminal or rule.
         * @return     string
         */

        private String gramSymToString(int e){
            if (e == this.ruleBase-1){
                return "" + EOF;
            } else if (e < this.tokBase){
                return "<&" + e + ">";
            } else if (e < this.ruleBase){
                return "" + (char)(e - this.tokBase);
            } else {
                return "rule: " + (e-this.ruleBase);
            }
        }

        /**
         * Deliver the literalized name of the specified token.
         *
         * @param      tok token number
         * @return     string
         */

        String tokLitName(int tok){
            String st = "";
            if (tok == this.numOfToks){
                st = "EOF";
                return st;
            }
            if ((tok < 0) || (this.numOfToks < tok)){
                st = "unknown";
                return st;
            }
            return st + (char)tok;
        }

        /**
         * Tell if the specified nonterminal has more than one rule.
         *
         * @param      nt nonterminal
         * @return     <code>true</code> if it has, <code>false</code> otherwise
         * @exception  IllegalArgumentException if there is no such rule
         */

        boolean hasSeveralRules(int nt){
            int rule1 = this.ntToRule[nt];
            if (rule1+1 < this.ruleToNt.length &&
                this.ruleToNt[rule1+1] == nt){     // has several rules
                return true;
            }
            return false;
        }
    }

    /** A resizable integer array. */

    private static class IntArray {

        /** The array that backs up this resizable array. */
        int[] arr = new int[100];

        /** The number of significant elements in it. */
        int length;

        /**
         * Append the specified element to this array.
         *
         * @param     value element 
         */

        void add(int value){
            if (this.length >= this.arr.length){
                this.arr = Arrays.copyOf(this.arr,this.arr.length+100);
            }
            this.arr[this.length++] = value;
        }

        /**
         * Deliver the number of elements.
         *
         * @return     number of elements
         */

        int size(){
            return this.length;
        }

        /**
         * Deliver the specified element.
         *
         * @param      i index of the element
         * @return     element
         */

        int get(int i){
            return this.arr[i];
        }

        /**
         * Deliver an array containing the elements.
         *
         * @return     reference to a newly created array
         */

        int[] toArray(){
            return Arrays.copyOf(this.arr,this.length);
        }
    }

    /** The parse tables for this Earley engine. */
    private EarleyTables tab;

    /**
     * Convert the specified AST into parse tables.
     *
     * @param      root reference to the root node of the AST
     * @return     reference to the created parse tables
     */

    private EarleyTables astToTables(AstNode root){
        this.tab = new EarleyTables();
        this.tab.astRoot = root;
        this.tab.astMap = this.astMap;
        int maxsym = -1;
        int minsym = Integer.MAX_VALUE;
        for (int i = 0; i < this.alphabet.length; i++){
            if (this.alphabet[i] > maxsym) maxsym = this.alphabet[i];
            if (this.alphabet[i] < minsym) minsym = this.alphabet[i];
        }
        this.tab.numOfToks = maxsym + 1;
        this.tab.minsym = minsym;

        IntArray nttorule = new IntArray();
        IntArray ntkind = new IntArray();
        IntArray ruletont = new IntArray();
        IntArray ruleindex = new IntArray();
        IntArray rulelen = new IntArray();
        IntArray gram = new IntArray();
        IntArray gramtoast = new IntArray();
        // the grammmar is first built storing in it values that are changed later,
        // when the number of nonterminals and rules become known and the encoding of each
        // nonterminal too. This first encoding is:
        // b31: terminal: its char value
        // b30: nonterminal: its number
        // b29: nonterminal: its ast
        // b28: rule nr
        int TERM = 1 << 31;
        int NTERM = 1 << 30;
        int NAST = 1 << 29;
        int NRULE = 1 << 28;
        int MASK = 0xffffff;
        int GRO = 1 << 31;
        int nrnt = -1;
        int nrrule = -1;
        gram.add(NRULE);
        gramtoast.add(-1);
        int n;
        int[] seqtont = new int[this.astSeq];
        IntArray nttoast = new IntArray();

        int AST1 = 0xffff0000;

        // create start nt
        nrnt++;
        nttorule.add(nrrule+1);
        ntkind.add(G_GRO);
        seqtont[root.seq] = nrnt;
        nttoast.add(root.seq);
        int dp = 0;
        int qp = 0;
        int[] queue = new int[this.astSeq];
        queue[qp++] = root.seq;               // start from it
        while (dp != qp){                     // while queue not empty
            int astnr = queue[dp++];
            AstNode ast = null;
            if (astnr < 0){                   // group rules
                astnr &= 0xfffffff;
                ast = this.astMap[astnr];
                if (ast.groupKind != G_GRO){
                    // create new nt
                    nrnt++;
                    nttorule.add(nrrule+1);
                    ntkind.add(ast.groupKind);
                    seqtont[ast.seq] = nrnt;
                    nttoast.add(ast.seq);
                    // create its rules
                    nrrule++;
                    ruletont.add(nrnt);
                    ruleindex.add(gram.size());
                }
                int bodykind = G_GRO;
                switch (ast.groupKind){
                case G_OPT:
                    gram.add(NTERM | nrnt+1);      // &0 ::= &1
                    gram.add(NRULE | nrrule);
                    gramtoast.add(-1);
                    gramtoast.add(-1);
                    rulelen.add(1);
                    nrrule++;
                    ruletont.add(nrnt);
                    ruleindex.add(gram.size());
                    gram.add(NRULE | nrrule);      // &0 ::= ""
                    gramtoast.add(-1);
                    rulelen.add(0);
                    bodykind = G_BOO;
                    break;
                case G_RE0:
                    gram.add(NTERM | nrnt+1);      // &0 = &1 &1
                    gram.add(NTERM | nrnt+1);
                    gram.add(NRULE | nrrule);
                    gramtoast.add(-1);
                    gramtoast.add(-1);
                    gramtoast.add(-1);
                    rulelen.add(2);
                    nrrule++;
                    ruletont.add(nrnt);
                    ruleindex.add(gram.size());

                    gram.add(NRULE | nrrule);      // &0 ::= ""
                    gramtoast.add(-1);
                    rulelen.add(0);
                    bodykind = G_BOR;
                    break;
                case G_RE1:
                    gram.add(NTERM | nrnt+1);      // &0 = &1 &1
                    gram.add(NTERM | nrnt+1);
                    gram.add(NRULE | nrrule);
                    gramtoast.add(-1);
                    gramtoast.add(-1);
                    gramtoast.add(-1);
                    rulelen.add(2);
                    bodykind = G_BOR;
                    break;
                }
                nrnt++;                            // &1  (or &0 for simple groups)
                nttorule.add(nrrule+1);
                int kind = 0;
                switch (ast.groupKind){
                case G_GRO: kind = G_GRO; break;
                case G_OPT: kind = G_BOO; break;
                case G_RE0: kind = G_BOR; break;
                case G_RE1: kind = G_BOR; break;
                }
                ntkind.add(kind);
                if (ast.groupKind == G_GRO){
                    seqtont[ast.seq] = nrnt;
                }
                nttoast.add(ast.seq);
                // create its rules
                ast = ast.son;
            } else {
                ast = this.astMap[astnr];
            }
            switch (ast.kind){
            case A_LEA:
                nrrule++;
                ruletont.add(nrnt);
                ruleindex.add(gram.size());
                gram.add(TERM | ast.sym);
                gram.add(NRULE | nrrule);
                gramtoast.add(ast.seq);
                gramtoast.add(-1);
                rulelen.add(1);
                break;
            case A_ALT:
                for (AstNode body = ast.son; body != null; body = body.bro){
                    nrrule++;
                    ruletont.add(nrnt);
                    ruleindex.add(gram.size());
                    int nele = 0;
                    if (body.kind == A_CON){
                        for (AstNode a = body.son; a != null; a = a.bro){
                            if (a.kind == A_LEA){
                                gram.add(TERM | a.sym);
                                gramtoast.add(a.seq);
                            } else if (a.kind == A_GRO){
                                gram.add(NAST | a.seq);
                                gramtoast.add(a.seq);
                                queue[qp++] = a.seq | GRO;    // enqueue it
                            } else {                          // empty
                            }
                            nele++;
                        }
                        gram.add(NRULE | nrrule);
                        gramtoast.add((ast.seq << 16) | body.seq);
                    } else {
                        if (body.kind == A_LEA){
                            gram.add(TERM | body.sym);
                            gramtoast.add(body.seq);
                        } else if (body.kind == A_GRO){
                            gram.add(NAST | body.seq);
                            gramtoast.add(body.seq);
                            queue[qp++] = body.seq | GRO;     // enqueue it
                        } else {                              // empty
                        }
                        nele++;
                        gram.add(NRULE | nrrule);
                        gramtoast.add((ast.seq << 16) | 0xffff);
                    }
                    rulelen.add(nele);
                }
                break;
            case A_CON:
                nrrule++;
                ruletont.add(nrnt);
                ruleindex.add(gram.size());
                int nele = 0;
                for (AstNode a = ast.son; a != null; a = a.bro){
                    if (a.kind == A_LEA){
                        gram.add(TERM | a.sym);
                        gramtoast.add(a.seq);
                    } else if (a.kind == A_GRO){
                        gram.add(NAST | a.seq);
                        gramtoast.add(a.seq);
                        queue[qp++] = a.seq | GRO;    // enqueue it
                    } else {                          // empty
                    }
                    nele++;
                }
                gram.add(NRULE | nrrule);
                gramtoast.add(ast.seq | AST1);
                rulelen.add(nele);
                break;
            case A_EMP:
                nrrule++;
                ruletont.add(nrnt);
                ruleindex.add(gram.size());
                gram.add(NRULE | nrrule);
                gramtoast.add(ast.seq | AST1);   // for the empty RE
                rulelen.add(0);
                break;
            case A_GRO:
                nrrule++;
                ruletont.add(nrnt);
                ruleindex.add(gram.size());
                gram.add(NAST | ast.seq);
                gram.add(NRULE | nrrule);
                gramtoast.add(ast.seq);
                gramtoast.add(-1);
                rulelen.add(1);
                queue[qp++] = ast.seq | GRO;    // enqueue it
            }
        }
        // add the enclosing one
        nrnt++;
        nttorule.add(nrrule+1);
        ntkind.add(G_BOD);
        nttoast.add(-1);
        nrrule++;
        ruletont.add(nrnt);
        ruleindex.add(gram.size());
        tab.startRule = gram.size();
        gram.add(NTERM | 0);
        gram.add(TERM | (maxsym+1));   // eof is translated to the max terminal + 1
        gram.add(NRULE | nrrule);
        gramtoast.add(-1);
        gramtoast.add(-1);
        gramtoast.add(-1);
        rulelen.add(2);
        this.tab.numOfNts = nttorule.size();
        this.tab.numOfRules = ruleindex.size() - 1;    // excluding the enclosing one

        // then relocate the grammar: convert the unfinished values into final ones
        this.tab.grammar = gram.toArray();
        this.tab.grammartoast = gramtoast.toArray();
        this.tab.ntToRule = nttorule.toArray();
        this.tab.ntKind = ntkind.toArray();
        this.tab.ruleToNt = ruletont.toArray();
        this.tab.ruleIndex = ruleindex.toArray();
        this.tab.ruleLen = rulelen.toArray();
        this.tab.tokBase = this.tab.numOfNts;
        this.tab.ruleBase = this.tab.tokBase + maxsym + 2;        // 1 for the eof
        for (int i = 0; i < gram.size(); i++){
            int gp = this.tab.grammar[i];
            int val = gp & MASK;
            if ((gp & TERM) != 0){
                this.tab.grammar[i] = val + this.tab.tokBase;
            } else if ((gp & NTERM) != 0){
                this.tab.grammar[i] = val;
            } else if ((gp & NAST) != 0){
                this.tab.grammar[i] = seqtont[val];
            } else if ((gp & NRULE) != 0){
                this.tab.grammar[i] = val + this.tab.ruleBase;
            }
        }

        computeAstBSP(root);                  // compute ini, fin, dig
        // compute nullable
        this.tab.nullable = new boolean[this.tab.numOfNts];
        for (int i = 0; i < this.tab.numOfNts-1; i++){    // all nts except the enclosing, which is not nullable
            AstNode ast = this.astMap[nttoast.get(i)];
            switch (this.tab.ntKind[i]){
            case G_OPT:
            case G_RE0:
                this.tab.nullable[i] = true;
                break;
            case G_GRO:
            case G_RE1:
            case G_BOO:
            case G_BOR:
                this.tab.nullable[i] = ast.isNull;
                break;
            }
        }

        this.tab.ntToAst = nttoast.toArray();
        int first = this.tab.ntToRule[0];            // number of its first rule
        this.tab.ntToAst[0] = -1;
        for (int i = 0; i < this.tab.ntToAst.length; i++){
            int astnr = this.tab.ntToAst[i];
            if (astnr == -1) continue;
            if (this.tab.ntKind[i] == G_BOR || this.tab.ntKind[i] == G_BOO){
                this.tab.ntToAst[i] = -1;
            }
        }

        predictTablesEnc();
        buildDirSet();
        return this.tab;
    }

    /**
     * Deliver a string representing the specified set of terminals.
     *
     * @param      s reference to the set of terminals
     * @return     string
     */

    private String termSetToString(IntSet s){
        String res = "";
        int[] arr = s.toArray();
        for (int i = 0; i < arr.length; i++){
            if (res.length() > 0) res += " ";
            if (arr[i] == this.tab.ruleBase-1-this.tab.tokBase){
                res += EOF;
            } else {
                res += (char)arr[i];
            }
        }
        return "{" + res + "}";
    }

    /**
     * Compute the First relation on the encoded grammar.
     *
     * @return     array of the sets of terminals for each nonterminal
     */

    private IntSet[] doFirstEnc(){
        boolean[][] ntMatrix = new boolean[this.tab.numOfNts][this.tab.numOfNts];
        IntSet[] first = new IntSet[this.tab.numOfNts];
        for (int i = 0; i < this.tab.ntToRule.length; i++){   // scan all nonterminals
            for (int rulenr = this.tab.ntToRule[i];           // visit all its rules
                (rulenr < this.tab.ruleToNt.length) &&
                (this.tab.ruleToNt[rulenr] == i); rulenr++){
                for (int p = this.tab.ruleIndex[rulenr]; this.tab.grammar[p] < this.tab.ruleBase; p++){  // visit rule
                    int v = this.tab.grammar[p];
                    if (v >= this.tab.tokBase){               // terminal
                        if (first[i] == null){
                            first[i] = new IntSet();
                        }
                        first[i].add(v-this.tab.tokBase);     // h starts also with it
                        break;
                    }
                    // nonterminal
                    ntMatrix[i][v] = true;
                    if (!this.tab.nullable[v]){               // not a nullable nt: stop at the first ..
                        break;                            // .. not transparent one
                    }
                }
            }
        }
        closure(ntMatrix);                               // perform closure
        for (int i = 0; i < this.tab.ntToRule.length; i++){  // scan all nonterminals and synthetize FIRST
            for (int j = 0; j < this.tab.ntToRule.length; j++){
                if (!ntMatrix[i][j]){                    // j does not occur as FIRST
                    continue;                            // in some alternatives of i
                }
                if (first[i] == null){
                    first[i] = new IntSet();
                }
                first[i].add(first[j]);                  // add all k's FIRST to h's
                    
            }
        }
        for (int i = 0; i < first.length; i++){
            if (first[i] == null){                   // create always one
                first[i] = new IntSet();
            }
        }
        return first;
    }

     /**
     * Compute the Follow relation on the encoded grammar.
     *
     * @return     array of the sets of terminals for each nonterminal
     */

    private IntSet[] doFollowEnc(IntSet[] first){
        boolean[][] ntMatrix = new boolean[this.tab.numOfNts][this.tab.numOfNts];
        IntSet[] follow = new IntSet[this.tab.numOfNts];

        for (int i = 0; i < this.tab.ntToRule.length; i++){   // scan all nonterminals
            for (int rulenr = this.tab.ntToRule[i];           // visit all its rules
                (rulenr < this.tab.ruleToNt.length) &&
                (this.tab.ruleToNt[rulenr] == i); rulenr++){
                for (int p = this.tab.ruleIndex[rulenr]; this.tab.grammar[p] < this.tab.ruleBase; p++){  // visit rule
                    int v = this.tab.grammar[p];
                    if (v >= this.tab.tokBase){               // terminal
                        continue;
                    }
                    // nonterminal
                    int s = p + 1;
                    scan: for (; this.tab.grammar[s] < this.tab.ruleBase; s++){   // scan all successors
                        int gs = this.tab.grammar[s];
                        if (gs >= this.tab.tokBase){                         // terminal
                            if (follow[v] == null){
                                follow[v] = new IntSet();
                            }
                            follow[v].add(gs - this.tab.tokBase);      // v followed by it
                            break scan;
                        } else {
                            if (follow[v] == null){
                                follow[v] = new IntSet();
                            }
                            follow[v].add(first[gs]);       // v followed by the FIRSTs of gs
                            if (!this.tab.nullable[gs]){    // not a nullable nt: stop at the first ..
                                break scan;                 // .. not transparent one
                            }
                        }
                    }
                    if (this.tab.grammar[s] >= this.tab.ruleBase){  // all successors of gs EM
                        ntMatrix[i][v] = true;              // add to FOLLOW i
                    }
                }
            }
        }

        closure(ntMatrix);                                // perform closure
        for (int i = 0; i < this.tab.ntToRule.length; i++){   // scan all nonterminals and synthetize FOLLOW
            for (int j = 0; j < this.tab.ntToRule.length; j++){
                if (!ntMatrix[i][j]){                     // j does not occur as FOLLOW
                    continue;                             // in some alternatives of i
                }
                if (follow[j] == null){
                    follow[j] = new IntSet();
                }
                follow[j].add(follow[i]);            // add all i's FOLLOW to j's
            }
        }
        if (follow[this.tab.numOfNts-1] == null){           // put EOF in FOLLOW of enclosing start
            follow[this.tab.numOfNts-1] = new IntSet();
        }
        follow[this.tab.numOfNts-1].add(this.tab.numOfToks);
        for (int i = 0; i < follow.length; i++){
            if (follow[i] == null){                  // create always one
                follow[i] = new IntSet();
            }
        }

        return follow;
    }

    /**
     * Compute the transitive closure on the specified matrix.
     *
     * @param      mat matrix
     */

    private void closure(boolean[][] mat){
        // Warshall
        int l = mat.length;
        for (int j = 0; j < l; j++){
            for (int i = 0; i < l; i++){
                if (mat[i][j]){
                    for (int k = 0; k < l; k++){
                        mat[i][k] |= mat[j][k];
                    }
                }
            }
        }
        for (int i = 0; i < l; i++){  // set all elements in diagonal
            mat[i][i] = true;
        }
    }

    /** The First relation. */
    private IntSet[] first;

    /** The Follow relation. */
    private IntSet[] follow;

    /** The map from rule numbers to the sets of their LL(1) lookaheads. */
    private IntSet[] directors;

    /** The map from nt numbers to the sets of their LL(1) lookaheads. */
    private IntSet[] directorsNt;

    /**
     * Build the maps (directors) that tell what terminals start what rules and nonterminals.
     */

    private void predictTablesEnc(){
        this.first = doFirstEnc();                  // compute FIRST
        this.follow = doFollowEnc(this.first);      // compute FOLLOW

        // compute DIRECTOR

        this.directors = new IntSet[this.tab.ruleIndex.length];  // allocate directors
        this.directorsNt = new IntSet[this.tab.numOfNts];        // allocate the nt's ones
        int ruleCounter = 0;
        for (int i = 0; i < this.tab.ntToRule.length; i++){    // scan all nonterminals
            this.directorsNt[i] = new IntSet();
            this.directorsNt[i].add(this.first[i]);

            if (this.tab.nullable[i]){                         // generates the empty
                this.directorsNt[i].add(this.follow[i]);
            }

            // compute then the director for each rule
            for (int rulenr = this.tab.ntToRule[i];            // visit all its rules
                (rulenr < this.tab.ruleToNt.length) &&
                (this.tab.ruleToNt[rulenr] == i); rulenr++){
                IntSet director = null;
                int s = this.tab.ruleIndex[rulenr];
                scan: for (; this.tab.grammar[s] < this.tab.ruleBase; s++){  // visit rule
                    int gs = this.tab.grammar[s];
                    if (gs >= this.tab.tokBase){                    // terminal
                        if (director == null){
                            director = new IntSet();
                        }
                        director.add(gs - this.tab.tokBase);        // starts with itself
                        break scan;
                    } else {                              // nonterminal
                        if (director == null){
                            director = new IntSet();
                        }
                        director.add(first[gs]);          // starts with the first of v
                        if (!this.tab.nullable[gs]){      // not a nullable nt: stop at the first ..
                            break scan;                   // .. not transparent one
                        }
                    }
                }
                if (this.tab.grammar[s] >= this.tab.ruleBase){    // alternative which
                    if (director == null){                // .. produces empty
                        director = new IntSet();
                    }
                    director.add(this.follow[i]);         // starts with what follows
                }
                this.directors[rulenr] = director;
            }
        }
        for (int i = 0; i < this.directors.length; i++){
            if (this.directors[i] == null){               // create always one
                this.directors[i] = new IntSet();
            }
        }
    }

    /**
     * Build the director sets.
     */

    private void buildDirSet(){
        // build the comb-vector for the director sets

        int hole = Integer.MAX_VALUE;
        int[][] tabs = new int[this.tab.ruleBase+this.tab.numOfRules+1][];   // tables before compression
        int maxsym = this.tab.numOfToks + 1;
        int[] arrays = new int[100];             // overflow area for value arrays
        int i_arrays = 1;                        // 0 index reserved
        int[] arrindex = new int[maxsym];        // indexes of value arrays into arrays
        IntSet[] row = new IntSet[maxsym];       // row before compression
        for (int i = 0; i < row.length; i++){
            row[i] = new IntSet();
        }
        for (int i = 0; i < this.tab.ntToRule.length; i++){    // scan all nonterminals
            // build the row for this nt, with a slot for each symbol
            // containing the rules that are headed by it
            for (int j = 0; j < row.length; j++){
                row[j].clear();
            }
            for (int rulenr = this.tab.ntToRule[i];            // visit all its rules
                (rulenr < this.tab.ruleToNt.length) &&
                (this.tab.ruleToNt[rulenr] == i); rulenr++){
                IntSet director = this.directors[rulenr];
                if (director.isEmpty()) continue;
                int[] arr = director.toArray();
                for (int j = 0; j < arr.length; j++){
                    row[arr[j]].add(rulenr);
                }
            }
            // now we have in row the arrays, let's store the ones that have
            // more than one value
            Arrays.fill(arrindex,-1);
            int[] arr = new int[10];        // temporary arrays for values in cells
            int nval = 0;
            for (int j = 0; j < row.length; j++){
                int size = (int)row[j].size();
                if (size == 0) continue;
                if (size > 1){
                    if (i_arrays + size + 1 >= arrays.length){  // enlarge
                        arrays =  Arrays.copyOf(arrays,i_arrays + size + 100);
                    }
                    arrindex[j] = i_arrays;
                    arrays[i_arrays++] = size;
                    arr = row[j].toArray(arr);
                    System.arraycopy(arr,0,arrays,i_arrays,size);
                    i_arrays += size;
                }
                nval++;
            }
            tabs[i] = new int[nval*2 + 1];
            int k = 0;
            tabs[i][k++] = row.length;  // length
            for (int j = 0; j < row.length; j++){
                int size = (int)row[j].size();
                if (size == 0) continue;
                arr = row[j].toArray(arr);
                if (size == 1){
                    tabs[i][k++] = j;
                    tabs[i][k++] = arr[0];
                } else {
                    tabs[i][k++] = j;
                    tabs[i][k++] = -arrindex[j];
                }
            }
        }

        // then build lookaheads for tokens
        for (int i = 0; i < this.tab.numOfToks + 1; i++){    // tokens
            int j = i + this.tab.tokBase;
            tabs[j] = new int[3];
            int k = 0;
            tabs[j][k++] = row.length;  // length
            tabs[j][k++] = i;
            tabs[j][k++] = 1;
        }

        // build follow lists for nonterminals as a table of (index,value) sequences,
        // using 1 as value (any value is good since it is not stored). This is needed
        // to build the follow lists for rules

        int[][] lists = new int[this.follow.length][];
        int[] full = null;
        for (int i = 0; i < this.follow.length; i++){ // build follow for ..
            IntSet set = this.follow[i];
            if (set == null) continue;
            int n = (int)set.size();                       // determine length
            int[] t = new int[n*2 + 1];               // allocate the table
            lists[i] = t;
            t[0] = this.tab.numOfToks + 1;                 // fill it
            n = 1;
            int[] arr = set.toArray();
            for (int j = 0; j < arr.length; j++){
                t[n++] = arr[j];
                t[n++] = 1;
            }
        }

        // then build follow for rules
        for (int i = 0; i < this.tab.numOfRules+1; i++){
            tabs[i+this.tab.ruleBase] = lists[this.tab.ruleToNt[i]];
        }

        CombVector comb = new CombVector(Integer.MAX_VALUE,
            CombVector.HOLES_ACCESSED |
            CombVector.FOLD_ROWS |
            CombVector.PAIRS);
        //comb.settrc("a");
        comb.merge(tabs);
        if (i_arrays > 1){                    // there is an overflow table
            int len = comb.tabMerged.length;
            comb.tabMerged = Arrays.copyOf(comb.tabMerged,len + i_arrays);
            System.arraycopy(arrays,0,comb.tabMerged,len,i_arrays);
            for (int i = 0; i < len; i++){
                if (comb.tabMerged[i] < 0){   // relocate references to arrays
                    comb.tabMerged[i] -= len;
                }
            }
        }
        tab.latable = comb.tabMerged;;
        tab.labase = comb.base;
        tab.lacheck = comb.check;
    }

    /*
     * Notes:
     * - only the improvements present in a full-fledged Earley parser that are useful for
     *   the special case of parsing REs are introduced here
     * - the completer and the nuller here must process all the items that could be advanced
     *   because here we must find the prior one
     */

    /** The number of items. */
    private int itemsNr;

    /** The number of shifts to get the block number. */
    private static final int NSHF = 10;

    /** The size of blocks. */
    private static final int QUANTUM = 1 << NSHF;

    /** The dot. */
    private int[] dot;

    /** The back pointer. */
    private int[] back;

    /** The predecessor (aka left) pointer. */
    private int[] left;

    /** The causal (aka sub) pointer. */
    private int[] sub;

    /** The current list. */
    private int cur;

    /** The index of the last item added. */
    private int last;

    /** The number of the current list. */
    private int listNr;

    /** The hash directory to add items. */
    private int[] hdir;

    /** The hash link. */
    private int[] hlink;

    /** The container for the items to be added by the scanner. */
    private int[] potter;

    /**
     * Enlarge the space for the lists of items. When they are small, the space is doubled,
     * otherwise it becomes 1.5 of the original one.
     */

    void enlargeLists(){
        int[] cur = this.dot;
        int curlen = 0;
        int newlen = QUANTUM;
        if (cur == null){
            this.dot = new int[newlen];
            this.back = new int[newlen];
            this.left = new int[newlen];
            this.sub = new int[newlen];
            this.memory += (newlen * 4 + 4) * 2;
        } else {
            curlen = cur.length;
            if (curlen < (1 << 14)){
                newlen = curlen * 2;
            } else {
                newlen = curlen + (curlen >>> 1);
            }
            if (newlen < 0){
                throw new OutOfMemoryError();
            }
            this.dot = Arrays.copyOf(this.dot,newlen);
            this.back = Arrays.copyOf(this.back,newlen);
            this.left = Arrays.copyOf(this.left,newlen);
            this.sub = Arrays.copyOf(this.sub,newlen);
            this.garbage += (curlen * 4 + 4) * 2;
            this.memory += (newlen * 4 + 4) * 2;
        }
        this.itemsNr = newlen;
    }

    /** The flag that tells that the value in the subpointer is a token. */
    private static final int TOKFLAG = 0x80000000;

    /** The token stored in a subpointer. */
    private static final int TOKMASK = 0x7fffffff;

    /**
     * Deliver a string representing the specified item.
     *
     * @param      itm index of the item
     * @return     string
     */

    private String itemToString(int itm){
        int dt = dot[(itm)] & 0x3fffffff;        // for methods that mark it
        int ba = back[(itm)];
        int le = left[(itm)];
        int sb = sub[(itm)];
        String sbs = Integer.toString(sb);
        if (sb < 0){                           // token
            sb &= TOKMASK;
            sbs = "tok: " + (char)sb;
        }
        String s = null;
        s = Integer.toString(itm) +
            " [" + this.tab.ruleToString(dt,true) +
            ", " + ba + "]" + " l: " + le + " s: " + sbs;
        return s;
    }

    /**
     * Trace the items of the current list.
     */

    private void traceItems(){
        int su = sub[(this.cur-1)];
        if (su > 0){
            while (dot[(su)] > 0) su--;
        }
        Trc.out.printf("I%s\n",back[(this.cur-1)]);
        for (int i = this.cur; dot[(i)] > 0; i++){  // trace items
            Trc.out.printf("%s\n",itemToString(i));     // .. of current list
        }
    }

    /**
     * Trace the lists of items starting at the specified index.
     *
     * @param      itm index
     */

    private void traceLists(int itm){
        Trc.out.printf("items lists\n");
        for (int i = itm; i <= this.last; i++){      // scan the lists
            if (dot[(i)] == 0){
                if (i == this.last) break;
                Trc.out.printf("I%s\n",back[(i)]);
                i++;
            }
            Trc.out.printf("%s\n",itemToString(i));  // trace this item
        }
    }

    /**
     * Match the text against the compiled RE.
     *
     * @param      text string to be matched
     * @param      tab reference to the parse tables
     * @param      maketree <code>true</code> to build alto the tree, <code>false</code> otherwise
     * @return     string representing the tree, null if the text does not match
     */

    public String earleyParse(String text, EarleyTables tab, boolean maketree){
        this.tokens += text.length();
        int eoftok = tab.numOfToks;

        if (this.itemsNr - 1 < 3){
            enlargeLists();            // allocate initial arrays
        }
        int curlist = 1;               // make indexes always != 0, 0 is null
        int prevlist = 1;              // index of previous list
        this.listNr = 0;
        this.cur = 1;
        this.last = 0;

        if (this.dotter == null || this.dotter.length < tab.grammar.length){
            if (this.dotter != null) this.garbage += this.dotter.length * 4 + 4;
            this.dotter = new int[tab.grammar.length];
            this.memory += this.dotter.length * 4 + 4;
        } else {
            Arrays.fill(this.dotter,0);
        }
        boolean res = false;

        int hfunct;                           // hash function
        if (this.hdir == null){
            this.hdir = new int[512];         // allocate hash directory
            this.memory += this.hdir.length * 4 + 4;
        } else {
            Arrays.fill(this.hdir,0);
        }
        if (this.hlink == null){
            this.hlink = new int[512];        // allocate hash links
            this.memory += this.hlink.length * 4 + 4;
        } else {
            Arrays.fill(this.hlink,0);
        }
        int hdirlen1 = this.hdir.length - 1;  // current length of hash directory - 1

        int curtok = -1;
        int nexttok = -1;

        int idx = 0;
        int p = 0;
        int sy = 0;
        int gp = 0;

        //dot[0] = 0;                      // sentinel
        this.listNr++;                     // build the first list
        this.last++;                       // add(this.startRule,curlist,0,0);
        dot[(1)] = tab.startRule;            // gramRule
        this.dotter[tab.startRule] = 1;
        //back[1] = curlist;               // zero
        if (this.potter == null){
            this.potter = new int[240];
            this.memory += this.potter.length * 4 + 4;
        }
        // temporary for tracing items lists
        //left[0] = nexttok;
        this.cursor = 0;
        // get next token
        if (this.cursor == text.length()){
            nexttok = eoftok;
        } else {
            nexttok = text.charAt(this.cursor++);
            if (nexttok >= tab.numOfToks) return null;
        }

        int las = 0;
        int nt = 0;
        int curback = 0;
        int itm = 0;
        int l = 0;
        int potlen = 0;
        build: for (;;){
            // closure
            potlen = 0;                            // initialise pot for next list
            clo: for (int i = curlist;             // apply predictor, completer ..
                i <= this.last; i++){              // .. and scanner
                if (this.timeSupervise){
                    this.tally++;
                    if (this.tally % 100000 == 0){
                        if (getCycles()-this.matchStartTime > maxMatchTime){
                            this.error = true;
                            this.errorKind = ERROR_TIME;        // too long time
                            return null;
                        }
                    }
                }
                p = dot[(i)];
                gp = tab.grammar[p];
                int j = 0;
                boolean complet = true;
                closure: {
                    if (gp >= tab.ruleBase){             // dot at end
                        // the completer
                        nt = tab.ruleToNt[gp-tab.ruleBase];
                        j = back[(i)];
                        break closure;
                    }

                    l = gp;
                    predi: if (l < tab.tokBase){       // alpha . <A> beta
                        // the predictor
                        idx = tab.labase[l];           // access predictor tables
                        if (tab.lacheck[idx+nexttok] != idx) continue;
                        int ri = tab.latable[idx+nexttok];
                        int nru = 1;
                        int a;
                        int rulenr = ri;
                        if (ri >= 0){                  // one rule only
                            a = tab.ruleIndex[ri];
                        } else {                       // several rules
                            ri = -ri;
                            nru = tab.latable[ri++];
                            rulenr = tab.latable[ri++];
                            a = tab.ruleIndex[rulenr];
                        }
                        boolean onealready = false;
                        step6: do {                   // add all predictees
                            itm = this.dotter[a];     // add(a,i,0,0) no check on back, gramRule
                            if (itm >= curlist){      // already present
                                int b = back[(itm)];
                                onealready = true;
                                break;
                            } else {
                                itm = ++this.last;
                                this.dotter[a] = itm;
                                if (this.itemsNr - itm < 3){
                                    enlargeLists();
                                }
                                dot[(itm)] = a;
                                back[(itm)] = i;
                                left[(itm)] = 0;        // sentinel
                                sub[(itm)] = 0;
                            }
                            if (--nru == 0) break;    // predicted rules
                            rulenr = tab.latable[ri++];
                            a = tab.ruleIndex[rulenr];
                        } while (true);  // step6

                        // the item has the dot in front of a nullable nonterminal
                        // and it is thus completable
                        nullb: {
                            if (!onealready) break nullb;    // do it only if predicted is already in list
                            if (!tab.nullable[l]) break nullb;
                            // check that the advanced item has its lookahead o.k.
                            sy = tab.grammar[p+1];
                            idx = tab.labase[sy];
                            if (tab.lacheck[idx+nexttok] != idx) break nullb;
                            j = curlist;
                            curback = back[(i)];
                            complet = false;
                        } // nullb
                        break closure;
                    } // predi

                    // the scanner

                    sca: {
                        if (l - tab.tokBase != nexttok){
                            break sca;                   // item not applicable
                        }
                        sy = tab.grammar[p+1];
                        scan: {
                            if (potlen >= potter.length){
                                this.potter = Arrays.copyOf(this.potter,this.potter.length<<1);
                            }
                            // store in potter
                            potter[potlen++] = i;              // left item
                            potter[potlen++] = p+1;            // new dot
                            potter[potlen++] = l;
                        } // scan
                    } // sca
                } // closure

                if (j == 0) continue;                     // no completions to be done
                boolean group = false;
                int le = i;
                int su = i;

                step5: for (; j < i; j++){                // complete
                    if (!group){
                        if (complet){                     // in completer
                            p = dot[(j)];
                            if (p == 0) break;            // end of items
                            gp = tab.grammar[p];
                            if (gp != nt) continue;       // dot not at the nt just completed
                            le = j;
                        } else {                          // in nuller
                            if (back[(j)] < curlist){
                                continue;                 // in a previous list
                            }
                            int g = tab.grammar[dot[(j)]];
                            if (g < tab.ruleBase) continue;   // dot not at end
                            nt = tab.ruleToNt[g-tab.ruleBase];
                            if (nt != l) continue;        // not a final of l
                            su = j;
                        }
                    }
                    sy = tab.grammar[p+1];
                    compl: {
                        if (complet){                     // in completer, check lookahead
                            // check that the advanced item has its lookahead o.k.
                            idx = tab.labase[sy];
                            if (tab.lacheck[idx+nexttok] != idx) break compl;
                            curback = back[(j)];
                        }
                        found: {                           // add(p+1,curback,le,su);
                            int dt = p+1;
                            itm = dotter[dt];
                            if (itm >= curlist){           // in this list
                                if (back[(itm)] == curback) break found;
                                hfunct = (dt + curback) & hdirlen1;
                                for (int z = this.hdir[hfunct];
                                    z >= curlist; z = this.hlink[z-curlist]){
                                    itm = z;
                                    if ((dot[(itm)] == dt) &&
                                        (back[(itm)] == curback)){    // found
                                        break found;
                                    }
                                }
                                int z = this.last+1 - curlist;      // relative to list
                                if (z >= this.hlink.length){        // enlarge link array
                                    this.hlink = Arrays.copyOf(this.hlink,this.hlink.length<<1);
                                }
                                this.hlink[z] = this.hdir[hfunct];  // not found, insert at beginning
                                this.hdir[hfunct] = this.last+1;
                            } else {
                                dotter[dt] = this.last + 1;         // not in dotter: insert it
                            }
                            las = ++this.last;                      // insert new
                            if (this.itemsNr - las < 3){
                                enlargeLists();
                            }
                            dot[(las)] = dt;
                            back[(las)] = curback;
                            left[(las)] = le;
                            sub[(las)] = su;
                            itm = -las;
                        } // found
                        if (itm > 0){                               // already there
                            if (left[(itm)] != le){
                                combiAmbiguity(itm,le,su,tab);
                            } else {
                                if (sub[(itm)] != su){
                                    int suitm = sub[(itm)];
                                    if (dot[(su)] < dot[(suitm)]){    // new item for an earlier rule
                                        sub[(itm)] = su;
                                    }
                                }
                            }
                        } else {
                            itm = -itm;
                        }
                    } // compl
                    if (!group){
                        gr: if (tab.ntKind[gp] == G_BOR){
                            if (sy < tab.ruleBase){           // old item ... . &1 &1
                                if (tab.grammar[p+2] <        // . &1 &1 &1 ...
                                    tab.ruleBase) break gr;
                                p++;                          // insert a &1 &1 .
                            } else {                          // old item ... &1 . &1
                                if (j >= curlist) break gr;
                                // if the old item is predicted, advance it
                                p--;                          // insert a &1 . &1
                            }
                            group = true;
                            j--;                              // back up
                            continue;
                        }
                    }
                    group = false;
                } // step5

            } // clo

            las = ++this.last;                    // space for sentinel
            dot[(las)] = 0;                         // place a sentinel

            // resize now the directory if necessary
            int floor = las - curlist;         // closest lower power of 2 of listLen 
            floor |= (floor >> 1);
            floor |= (floor >> 2);
            floor |= (floor >> 4);
            floor |= (floor >> 8);
            floor |= (floor >> 16);
            floor -= (floor >>> 1);
            floor >>>= 2;                      // divide by 4
            if (this.hdir.length < floor){
                int curlen = this.hdir.length;
                this.hdir = new int[floor];
                hdirlen1 = hdir.length - 1;
                this.garbage += curlen * 4 + 4;
                this.memory += this.hdir.length * 4 + 4;
            }

            prevlist = this.cur;                  // index of the previous list
            this.cur = las+1;                     // one after the sentinel
            curlist = this.cur;                   // new current item list
            back[(las)] = this.listNr++;            // store the list number
            // temporary for tracing items lists
            //LEFT(las) = nexttok;

            // build the kernel

            if (potlen > 0){
                // current matched, get lookahead
                // get next token
                if (this.cursor == text.length()){
                    curtok = eoftok;
                } else {
                    curtok = text.charAt(this.cursor++);
                    if (curtok >= tab.numOfToks) return null;
                }

            }

            for (int j = 0; j < potlen;){       // scan the potter
                int i = potter[j++];            // left item
                p = potter[j++];                // updated dot
                sy = tab.grammar[p];
                l = potter[j++];

                // check that the advanced item has its lookahead o.k.
                idx = tab.labase[sy];
                if (tab.lacheck[idx+curtok] != idx){
                    continue;
                }
                curback = back[(i)];                 // add(p+1,this.back[i],i,0)
                int dt = p;
                int su = (l - tab.tokBase) | TOKFLAG;
                int le = i;

                found: {                           // add(p+1,curback,le,su);
                    ins: {
                        itm = dotter[dt];
                        if (itm >= curlist){               // in this list
                            if (back[(itm)] == curback) break found;
                            hfunct = (dt + curback) & hdirlen1;
                            for (int z = this.hdir[hfunct];
                                z >= curlist; z = this.hlink[z-curlist]){
                                itm = z;
                                if ((dot[(itm)] == dt) &&
                                    (back[(itm)] == curback)){      // found
                                    break found;
                                }
                            }
                            int z = this.last + 1 - curlist;      // relative to list
                            if (z >= this.hlink.length){          // enlarge link array
                                this.hlink = Arrays.copyOf(this.hlink,this.hlink.length<<1);
                            }
                            this.hlink[z] = this.hdir[hfunct];    // not found, insert at beginning
                            this.hdir[hfunct] = this.last + 1;
                        } else {
                            dotter[dt] = this.last + 1;           // not in dotter, insert it
                        }
                    } // ins

                    las = ++this.last;                 // no need to check duplicates ..
                                                       // .. here, or ambiguity when no chains
                    if (this.itemsNr - las < 3){
                        enlargeLists();
                    }
                    dot[(las)] = p;
                    back[(las)] = curback;
                    left[(las)] = i;
                    sub[(las)] = su;
                    itm = -las;
                } // found
            }
            las = this.last+1;
            dot[(las)] = 0;                  // place a sentinel

            if (this.last < curlist){      // empty item list: stop
                break build;
            }
            if (nexttok == eoftok){        // eof
                this.last++;
                back[(this.last)] = this.listNr++;        // store the list number
                res = true;
                break;
            }
            nexttok = curtok;

        } // build

        int goalItem = this.cur;
        String resTree = null;
        ret: if (res){
            if (maketree){
                resTree = linearizeTree(goalItem,0,tab);
                break ret;
            }
            resTree = "";
        }

        return resTree;
    }

    /*
     * I build a vector, paired to the grammar, in which I store the ast number of each element
     * of the grammar, including the rules.
     * To do it, I generate in the grammar the ast references for the positions that must
     * be generated, and leave the others -1, and here generate the ones that are defined.
     * E.g. for a nt that has several rules generate the ast reference, for a nt that has only
     * one rule do not generate it.
     *
     * ... x ....       .... x ....   ... &0 ...
     *          |                 |            |            (&0 ... )&0        paired ast to rulenr in grammar
     *         leaf              ( )           &0 ::= a     (.a.).             -1
     *                                         &0 ::= a b   (.(.a.).(.b.).).   conc ast
     *                                         &0 ::= a|bc      ...            conc ast, alt ast
     *                           ( )*          |
     *                                         &0 ::= ""                       -1
     *                                         &0 ::= &1 &1                    -1
     *                                         &1 ::= a                        -1
     *                                         &1 ::= a b                      conc ast
     *                                         &1 ::= a|bc      ...            conc ast, alt ast
     */

    /**
     * Build the linearized tree for the derivation rooted in the specified item.
     *
     * @param      item index of the item
     * @param      lev nesting depth
     * @param      tab reference to the parse tables
     * @return     string representing the linearized tree
     */

    private String linearizeTree(int item, int lev, EarleyTables tab){
        boolean top = lev == 0;
        String str = "";
        int dt = dot[(item)];
        int elem = tab.grammar[dt];
        String encl = null;
        if (elem >= tab.ruleBase){                  // dot at end
            int astnr = tab.grammartoast[dt];
            encl = "";
            String pos = null;
            int ruleNr = tab.grammar[dt] - tab.ruleBase;
            int ntNr = tab.ruleToNt[ruleNr];
            if (tab.ntToAst[ntNr] != -1){
                pos = posToString(tab.astMap[tab.ntToAst[ntNr]].pos);
                str += "(" + pos;
                encl = ")" + pos + encl;
            }
            if (astnr != -1){
                int altast = (astnr >> 16) & 0xffff;
                int concast = astnr & 0xffff;
                if (altast != 0xffff){
                    pos = posToString(tab.astMap[altast].pos);
                    str += "(" + pos;
                    encl = ")" + pos + encl;
                }
                if (concast != 0xffff){
                    pos = posToString(tab.astMap[concast].pos);
                    str += "(" + pos;
                    encl = ")" + pos + encl;
                }
            }
            lev++;
        }

        String der = "";
        for (int le = item; le > 0; le = left[(le)]){
            int su = sub[(le)];
            if (su < 0){                                // token
                dt = dot[(le)];
                int e = tab.grammar[dt-1];              // test what is at the dot
                int astnr = tab.grammartoast[dt-1];
                if (astnr >= 0){
                    String pos = posToString(tab.astMap[astnr].pos);
                    der = "(" + pos + tab.gramSymToString(e) + pos + ")" + pos + der;
                } else {
                    der = tab.gramSymToString(e) + der;
                }
            } else if (su > 0){
                der = linearizeTree(su,lev,tab) + der;  // generate sub item
            }
        }
        str += der;
        if (encl != null){
            str += encl;
        }
        if (top) str += "$";
        return str;
    }

    /**
     * Trace the tree of the derivation rooted in the specified item.
     *
     * @param      item item
     * @param      lev nesting depth
     * @param      tab reference to the parse tables
     */

    private void showTree(int item, int lev, EarleyTables tab){
        if (item < 0){
            int sym = -item + tab.tokBase;
            for (int i = 0; i < lev; i++) Trc.out.print("  ");
            Trc.out.printf("%s\n",tab.gramSymToString(sym));
            return;
        }
        int dt = dot[(item)];
        int elem = tab.grammar[dt];
        if (elem >= tab.ruleBase){                  // dot at end
            int ruleNr = tab.grammar[dt] - tab.ruleBase;
            int ntNr = tab.ruleToNt[ruleNr];
            int first = tab.ntToRule[ntNr];         // number of its first rule
            for (int i = 0; i < lev; i++) Trc.out.print("  ");
            int astnr = tab.grammartoast[dt];
            Trc.out.printf("%s rule %s alternative: %s (%s)\n",
                tab.gramSymToString(ntNr),ruleNr,ruleNr-first,item);
            lev++;
        }
        int le = left[(item)];
        int su = sub[(item)];
        if (le != 0){                               // a left item present
            showTree(le,lev,tab);                   // trace it
        }
        if (su < 0){                                // token
            int e = tab.grammar[dt-1];
            for (int i = 0; i < lev; i++) Trc.out.printf("  ");
            Trc.out.printf("%s (%s) lst: ",
                tab.gramSymToString(e),item);
            int astnr = tab.grammartoast[dt-1];
            if (astnr >= 0){
                String pos = posToString(tab.astMap[astnr].pos);
                Trc.out.printf("(%s%s%s)%s\n",
                    pos,tab.gramSymToString(e),pos,pos);
            } else {
                Trc.out.printf("%s\n",tab.gramSymToString(e));
            }
        }
        if (su > 0){
            showTree(su,lev,tab);                   // trace sub item
        }
    }

    /* The stack to visit the first tree. */
    private int[] stackVisit1;

    /* The stack to visit the second tree. */
    private int[] stackVisit2;


    /**
     * Choose the prior derivation when there is a combi ambiguous one, i.e. when an
     * attempt is being done to add an item with the same dot and backpointer as the
     * ones of an existing one, and with a different pedigree (leftpointer, subpointer).
     * The chosen pedigree is set in the item.
     *
     * @param      item index of the item
     * @param      lef1 new leftpointer
     * @param      sub1 new subpointer
     * @param      tab reference to the parse tables.
     */

    /* This is a resolution of the combi ambiguity that visits the top sons of the two trees
     * (determined by the old and the new pedigrees) comparing their norms (lengths of the yields,
     * i.e. the frontiers).
     *
     * Let's state before that taken two trees, and the distinct position, the corresponding
     * subtrees rooted in the ancestors from the distinct position to the root satisfy the
     * same condition that specify what tree is prior. This means that parsers that build trees
     * bottom up can apply the disambiguation criteria piecewise at each level instead of applying
     * them when the trees are complete.
     * Let's take then two trees (which are subtrees of a larger parse tree); there is no need to
     * go inside the subtrees of their sons since the comparison of the norms of such subtrees
     * is sufficient. There is no point in visiting the sons of a node when the node itself has a
     * norm greater than that of the corresponding one of the other tree.
     * The two trees can be different because they have a different number of sons, e.g. extra empty
     * ones, or fewer matching more (i.e. having different norms).
     * Remember that the trees represent the same rule, which could be one with a fixed number of
     * elements or one of a repetition group with a variable number of the same elements (body).
     * Let's take two subtrees of one of such elements, that have the same norm, but that are
     * different in some node down from the root: such difference is not visible in piecewise
     * building because it has been resolved before in some previous step.
     * Think, e.g. of two trees for a**.
     * If they are topmost sons, there is no problem, they have different norms.
     * Let's take the case a** and text aa: the ambiguity is resolved when two trees are compared:
     * the first with a son with norm 2, and the second with 2 sons with norm 1. The first wins.
     * Let's take the case {a|{a}}: the first alternative is placed in a list before the second,
     * and this ordering is kept also in the following lists when they are advanced until their
     * final list. Thus, the first alternative is chosen. Should the parser disregard this ordering,
     * we could test the rules of the sons (when they have equal norms) and take the earlier.
     * The case where the two alternatives have different norms instead leads to different trees
     * with different sons, and the longest is chosen.
     * The ordering of the comparisons of sons is from left to right: discard the empty one in
     * repetition groups if there are other ones, and then compare the norms, take the one that
     * has the bigger son, or has no empty ones in repetition groups.
     * Note that there is no need to compare the positions because we are here processing two
     * derivations of a same rule, and are not stepping inside (where there could be derivations
     * of different rules).
     * This method to compare trees is thus equivalent to the parallel visit up to the distinct
     * point, but simpler and more fit to parsers that build trees bottom-up.
     *
     * Let's see firse the correspondence between items and ast nodes.
     * A leftchain represents a concatenation, each element represent one ast node, a son.
     * Non-leaf ast nodes are represented by items, their sons are the leftchains pointed by
     * their SUB.
     * There is no equivalent of AST alternative, but each list of son has attached also its
     * rule number (that allows to tell what alternative it is when the nt has several).
     * AST nodes that are groups are here nodes for nts of groups. To tell what a non-leaf node
     * is we can probe its DOT or its SUB.
     *
     * Note that when comparing two trees for the same rule, there is no need to test that the
     * corresponding nodes denote the same subexpressions because they certainly do since parse
     * trees mirror ASTs, being different only in the number of sons of repetition groups and
     * the son of alternatives (that can be distinguished by their rule, or place in the RE or
     * grammar).
     *
     * Repetition groups could have (ideally) several empty bodies, i.e. the so-called "problematic
     * cases". E.g. RE: (ab|(a)*)*, text aba: there could be two trees, one a subset of the other:
     * the second with an extra subtree for an empty (a)*.
     * This empty body in addition to a nonempty one (or even an empty one) is disallowed by
     * Posix rules, and it is also of little use (not to mention that there could be an infinite
     * number of trees with as many empty bodies).
     * When comparing a tree that has a nonempty body son and another tree that has an empty
     * body and nonempty body sons, the first is prior because the distinct position is the
     * nonempty body since the first has it, while the second does not (the ancestor is the group,
     * that has a son in the first tree, and no sons in the second), and the norm of the first
     * ancestor is greater than that of the second, that is zero.
     * The opposite case is different: the first tree with a nonempty body and the other with
     * a nonempty body plus an empty one. The second would be the prior one because the distinct
     * position is the empty body, and its norm is zero, but the other one is missing, and its
     * norm is -1. However, here we are comparing trees that have the same norm, and an empty
     * body could only occur in a *|+ group. Thus, it is easy to define priority conditions that
     * cope with this case: a tree that has all top sons with the same norm as the corresponding
     * ones of another tree is prior to the other one if the other one has extra empty top sons.
     * The case of star that has a nullable body, i.e. that generate the empty string is
     * different: in this case we have to compare a tree with no sons against another with one
     * son for an empty body. However, this is not a case of combi ambiguity, but one in which
     * there are two rules, which is resolved by letting the one that generates the son have
     * priority over the other (by defining its rule before the other and letting the rule
     * defined earlier take precedence).
     * As a general rule we can state that a tree that has one empty body son is prior to another
     * that has no sons at all.
     */

    private void combiAmbiguity(int item, int lef1, int sub1, EarleyTables tab){
        if (this.stackVisit1 == null){
            this.stackVisit1 = new int[100];
            this.stackVisit2 = new int[100];
        }

        // transform the left chains into arrays
        int sp1 = 0;
        for (int i = item; left[(i)] != 0; i = left[(i)]){
            if (sp1 >= this.stackVisit1.length){
                this.stackVisit1 = Arrays.copyOf(this.stackVisit1,this.stackVisit1.length<<1);
            }
            this.stackVisit1[sp1++] = i;
        }

        int off1 = this.last+1;               // build a fake item
        dot[(off1)] = dot[(item)];
        back[(off1)] = back[(item)];
        left[(off1)] = lef1;
        sub[(off1)] = sub1;
        int other = this.last+1;
        int sp2 = 0;
        for (int i = other; left[(i)] != 0; i = left[(i)]){
            if (sp2 >= this.stackVisit2.length){
                this.stackVisit2 = Arrays.copyOf(this.stackVisit2,this.stackVisit2.length<<1);
            }
            this.stackVisit2[sp2++] = i;
        }

        boolean second = false;
        boolean done = false;
        for (; sp1 > 0 && sp2 > 0;){
            int ln1 = phraseLength(this.stackVisit1[--sp1]);
            int ln2 = phraseLength(this.stackVisit2[--sp2]);
            if (ln1 == ln2) continue;
            done = true;
            if (ln2 > ln1){
                second = true;
            }
            break;
        }
        if (!done){
            if (sp1 > 0 && sp2 == 0){   // the first has more
                second = true;             // has empty sons
            }
        }

        if (second){
            left[(item)] = lef1;
            sub[(item)] = sub1;
        }
        dot[(off1)] = 0;
    }

    /**
     * Compute the length of the yield of the specified item.
     *
     * @param      item index of the item
     * @return     length
     */

    private int phraseLength(int item){
        item = sub[(item)];
        if (item < 0) return 1;        // token
        int itm = item;                // advance to top of its list
        int ba = back[(item)];           // the same with its back
        while (dot[(itm)] > 0) itm--;
        while (dot[(ba)] > 0) ba--;
        itm = back[(itm)];               // list nr stored here
        ba = back[(ba)];
        return itm - ba;
    }
    //------------ Recursive descent backtracking ----------------

    /* This is an implementation of a recursive descent backtracking matching algorithm similar
     * to the java.util.regex one.
     * Unlike the java one, it has an upper limit to its execution time. It is called before
     * calling the java one in benchmarking so as to avoid the latter to enter an almost
     * endless execution when it falls in catastrophic backtracking.
     *
     * It java builds a NFA and traverses it with a recursive procedure that calls itself at
     * each transition. It would be possible to use a PAT, but here there is a need to mimic
     * closely java.util.regex so as to spot when java.util.regex would enter catastrophic
     * backtracking.
     * Each node matches a sub-expression (the matched symbols are in the nodes instead than
     * the arcs).
     * Matching starts by calling match() on the first node, which matches the node and then
     * calls the match() on the next one, and so on. This means that matching is done with a
     * long list of recursive calls. When there are alternatives, or groups with an unspecified
     * number of bodies, the match() that handles them scans all the alternatives until it
     * finds the proper one.
     * It might be possible to do the same visiting the ast: match() could be started on the
     * root and go down to the first leaf or alternative or group, and when it matches it
     * it should call itself on the next one. However, finding the next one is rather involved.
     *
     * It is not possible to use a recursive descent on the AST because when an alternative fails
     * there is a need to try the next, but this must be known from within the execution of the
     * recursive procedure that handles alternatives, which means that an execution must match
     * an alternative and also what follows. Consider, e.g. the example (a|ab)c and the text "abc".
     * After trying the first alternative, matching "a", and seeing that it it not followed by "c",
     * there is a need to try the second alternative. This can be done using a stack that records
     * what alternatives have been tried, or a recursive method, that embeds such a stack in the
     * call stack, but such a method needs to match "c" in an execution that is called from within
     * the one that matches the alternatives so as to be able in it to detect that the first failed
     * and then to move to the second one.
     * To implement this, there is a need to know what ast node follows the current one.
     * This can be done with an extra field in AST nodes, or with a method. The complex thing
     * is to handle greedy *+, for which the recursion must go until no more matches are done
     * and then try with less matches. There is a need to keep track of the number of iterations
     * because with or without recursion matching must behave differently for the first mandatory
     * iterations and the following optional ones, and this probably must be done separately
     * for each *+ nested in another one.
     * E.g. in ((a|ab)*)* we can have 3 iterations of the outer one, and each 2 of the inner one,
     * and then if there is no match, we must try one iteration of the inner one in the third
     * of the outer.
     * Java does not do this because it uses counters that are local to groups, while here
     * there is a need to have counters that are local to each instantiation of groups.
     * With the greedy rule (and also with the others) there is no need because we cannot have
     * the case above: if the inner group has iterations in several of the outer one, then there
     * is also a parse in which all such iterations of the inner one are inside the first iteration
     * of the outer one.
     * Another reason to convert an AST into a NFA is to add nodes that help in the matching of *+,
     * that otherwise becomes involved.
     * The use of recursion makes the algorithm simpler at the cost of having as limit the size
     * of the call stack (and indeed java.util.regex cannot parse long ambiguous texts).
     * Note that the method that matches the alternatives, when finding that one failed and then
     * moves to the next can easily pass to it the start cursor, that it keeps in a loca variable.
     *
     * These are the classes used for the nodes by java.util.regex:
     *
     *    Ques:        option, seems not used
     *    Curly:       {}* when the body does not generate the empty string
     *    GroupCurly:  {}* for deterministic body
     *    Branch:      alternative
     *    BranchConn:  placed after each atom in alternative to proceed without optimizations
     *    GroupHead:   to store the begin of a group
     *    GroupTail:   to store the end of a group
     *    Prolog:      start of *
     *    Loop:        *
     *
     * To make the code more compact, one single class for the nodes is used here. Each AST
     * node is trasformed into:
     *
     *            node kind      next          sons
     *    leaf:   BTRK_LEA       next
     *    alt:    BTRK_AOPEN     BTRK_ACLO     alternatives (null = empty alternative)
     *                                         last of each son with next to BTRK_ACLO
     *            BTRK_ACLO      next
     *    conc:   any            next in conc
     *    empty:  none
     *    void:   none
     *    gro:    BTRK_GOPEN     first in body, last node in body pointing to BTRK_GCLO
     *            BTRK_GCLO      next
     *    opt:    BTRK_AOPEN     BTRK_ACLO     BTRK_GOPEN and a null one (meaning empty)
     *                                         BTRK_GOPEN with next pointing to body, last of
     *                                         body pointing to BTRK_GCLO, its next = BTRK_ACLO
     *            BTRK_ACLO      next
     *    re0/1:  BTRK_ROPEN     BTRK_RLOOP    BTRK_RLOOP
     *            BTRK_RLOOP     next          BTRK_GOPEN with next to body, last next BTRK_GCLO
     *                                         BTRK_GCLO next = BTRK_RLOOP
     *
     *    Group nodes bear the number of the corresponding AST node, which allows to tell
     *    the kind of group.
     *    Java uses also dedicated classes for groups whose body is deterministic or nonempty
     *    to optimize the execution. Here the general case is always used, which means that
     *    it could take a matching time that is greater than that of java. This is not a problem
     *    because its time must not be lower than that of java. There could be some tests that
     *    are not done while java would match them.
     *
     * Java remembers the parse-dependent data, such as the number of iterations done in a *
     * (needed in groups that have a minimum number of iterations) in matcher.locals data.
     * Here, they are kept in arrays (large as the number of ASTs) that are allocated when
     * matching and passed to the match method, indexed with the AST seq of the groups (kept
     * in the nodes of groups).
     * If these data (well, the number of iterations and the start cursor) were in variables
     * local to methods, it would be difficult for other method executions to access them
     * To avoid infinite empty iterations, iterations are done only if the cursor has advanced
     * with respect to what was at the beginning of the matching of the group.
     * In java, all the calls to match() have an argument, that is the same matcher object,
     * which contains data of the match being done.
     */

    /**
     * Match the text against the specified RE.
     *
     * @param      re string of the RE
     * @param      text string to be matched
     * @return     <code>true</code> if it matches, <code>false</code> otherwise
     */

    public boolean matchJava(String re, String text){
        boolean res = false;
        this.error = false;
        this.re = re;
        buildAst();
        if (getsym() != -1){      // whole re not consumed
            this.error = true;
        } else {
            BtrkNode root = astToBtrack();
            res = matchJava(root,text);
        }
        return res;
    }

    /**
     * Match the text against the compiled RE. Upon return, <code>error</code> is <code>false</code>
     * if the match is successful, and <code>true</code> otherwise.
     *
     * @param      root reference to the NFA
     * @param      text string to be matched
     * @return     <code>true</code> if it matches, <code>false</code> otherwise
     */

    public boolean matchJava(BtrkNode root, String text){
        boolean res = false;
        this.error = false;
        this.tokens += text.length();
        try {
            this.tally = 0;
            res = matchBtrack(root,text);
            if (!res){
                /*
                System.out.printf("!!!!!\n");
                System.out.printf("RE: %s\n",this.re);
                System.out.printf("text: %s\n",text);
                new Throwable().printStackTrace(System.out);
                System.exit(1);
                */
                this.error = true;
                this.errorKind = ERROR_NOMATCH;
            }
        } catch (OutOfMemoryError exc){
            this.error = true;
            this.errorKind = ERROR_MEMORY;
        } catch (StackOverflowError exc){
            this.error = true;
            this.errorKind = ERROR_STACK;
        } catch (UnknownError exc){
            this.error = true;
            this.errorKind = ERROR_TIME;              // too long time
        }
        return !this.error;
    }

    /** The kind of a leaf node. */
    private static final int BTRK_LEA = 0;

    /** The kind of an accept node. */
    private static final int BTRK_ACC = 1;

    /** The kind of a group open node. */
    private static final int BTRK_GOPEN = 2;

    /** The kind of a group close node. */
    private static final int BTRK_GCLO = 3;

    /** The kind of an alt open node. */
    private static final int BTRK_AOPEN = 4;

    /** The kind of an alt close node. */
    private static final int BTRK_ACLO = 5;

    /** The kind of a *+ open node. */
    private static final int BTRK_ROPEN = 6;

    /** The kind of a *+ loop node. */
    private static final int BTRK_RLOOP = 7;

    /** The kind of an end input node. */
    private static final int BTRK_END = 8;

    /** The string representation of the node kinds. */
    private static final String[] BTRK_KINDS = new String[]{
        "leaf","accept","group-open","group-close","alt-open","alt-close","*+open","*+loop","end"};

    /** The head of the nodes list. */
    private BtrkNode btrkHead;

    /** The tail of the nodes list. */
    private BtrkNode btrkTail;

    /** The sequence numbers of nodes. */
    private int btrkSeq;

    /**
     * Create a new node with the specified kind.
     *
     * @param      kind kind
     * @return     reference to the node
     */

    private BtrkNode newBtrkNode(int kind){
        BtrkNode res = new BtrkNode();
        res.kind = kind;
        res.seq = this.btrkSeq++;
        if (this.btrkHead == null){
            this.btrkHead = res;
        } else {
            this.btrkTail.link = res;
        }
        this.btrkTail = res;
        return res;
    }

    /**
     * Trace all the nodes.
     *
     * @param      root reference to the root node
     */

    private void btrkTrace(BtrkNode root){
        Trc.out.printf("-- backrack nfa -- root %s\n",root.seq);
        for (BtrkNode b = this.btrkHead; b != null; b = b.link){
            Trc.out.printf("%s\n",b);
        }
        Trc.out.printf("-- end --\n");
    }

    /** A node of the NFA of the backtracking engine. */

    private class BtrkNode {

        /** The sequence number. */
        int seq;

        /** The link to next node in the sequence. */
        BtrkNode link;

        /** The link to the next node. */
        BtrkNode next;

        /** The array of sons. */
        BtrkNode[] sons;

        /** The bypass node. */
        BtrkNode bypass;

        /** The number of the corresponding AST node. */
        int astseq;

        /** The kind of node. */
        int kind;

        /** The symbol. */
        char sym;

        /**
         * Deliver a string representing this node.
         *
         * @return     string
         */

        public String toString(){
            String res = "seq: " + this.seq + " " + BTRK_KINDS[this.kind];
            if (this.sons != null){
                res += " sons:";
                for (int i = 0; i < this.sons.length; i++){
                    String str = this.sons[i] == null ? "*" : Integer.toString(this.sons[i].seq);
                    res += " " + str;
                }
            }
            switch (this.kind){
            case BTRK_LEA:
                res += " " + sym;
                break;
            case BTRK_ACC:
                break;
            case BTRK_GOPEN:
                res += " ast " + this.astseq;
                break;
            case BTRK_GCLO:
                res += " ast " + this.astseq;
                break;
            case BTRK_AOPEN:
                break;
            case BTRK_ACLO:
                break;
            case BTRK_ROPEN:
                res += " ast " + this.astseq;
                break;
            case BTRK_RLOOP:
                res += " ast " + this.astseq;
                break;
            case BTRK_END:
                break;
            }
            if (this.next != null){
                res += " next " + this.next.seq;
            }
            return res;
        }

        /**
         * Match the portion of the specified text starting at the specified index up to
         * its end against the current node and the ones reacheable by it.
         *
         * @param      cursor start index in the text
         * @param      text text
         * @param      iterations number of iterations of groups
         * @param      groupstart start indexes of groups
         * @return     <code>true</code> if it matches, <code>false</code> otherwise
         */

        private boolean match(int cursor, String text, int[] iterations, int[] groupstart){
            if (timeSupervise){
                tally++;
                if (tally % 1000000 == 0){
                    long t1 = getCycles();
                    if (t1-matchStartTime > maxMatchTime){
                        throw new UnknownError();
                    }
                }
            }
            int cnt = matchBtrackCnt++;
            boolean res = false;
            switch (this.kind){
            case BTRK_LEA:
                if (cursor < text.length()){
                    res = (text.charAt(cursor) == this.sym) &&
                        this.next.match(cursor+1,text,iterations,groupstart);
                } else {
                    res = false;
                }
                break;
            case BTRK_ACC:
                res = true;
                break;
            case BTRK_GOPEN:
                int savestart = groupstart[this.astseq];
                groupstart[this.astseq] = cursor;
                if (this.next != null){        // no body
                    res = this.next.match(cursor,text,iterations,groupstart);
                } else {
                    res = true;
                }
                groupstart[this.astseq] = savestart;
                break;
            case BTRK_GCLO:
                res = this.next.match(cursor,text,iterations,groupstart);
                break;
            case BTRK_AOPEN:
                for (int i = 0; i < this.sons.length; i++){
                    if (this.sons[i] == null){          // empty alternative
                        if (this.next.match(cursor,text,iterations,groupstart)){
                            res = true;
                            break;
                        }
                    } else {
                        if (this.sons[i].match(cursor,text,iterations,groupstart)){
                            res = true;
                            break;
                        }
                    }
                }
                break;
            case BTRK_ACLO:
                res = this.next.match(cursor,text,iterations,groupstart);
                break;
            case BTRK_ROPEN:
                int saveiter = iterations[this.astseq];
                iterations[this.astseq] = 1;
                res = this.sons[0].sons[0].match(cursor,text,iterations,groupstart);
                if (astMap[this.astseq].groupKind == G_RE0){
                    if (res == false){
                        res = this.sons[0].match(cursor,text,iterations,groupstart);
                    }
                }
                iterations[this.astseq] = saveiter;
                break;
            case BTRK_RLOOP:
                if (cursor > groupstart[this.astseq]){
                    saveiter = iterations[this.astseq];
                    iterations[this.astseq]++;
                    res = this.sons[0].match(cursor,text,iterations,groupstart);
                    if (!res){
                        iterations[this.astseq] = saveiter;
                    } else {
                        res = true;
                        break;
                    }
                }
                res = this.next.match(cursor,text,iterations,groupstart);
                break;
            case BTRK_END:
                res = cursor == text.length();
                break;
            }
            return res;
        }
    }

    /** The counter of the calls of the match method since the match of a string started. */
    private int matchBtrackCnt;

    /**
     * Convert the AST tree into a backtracking NFA.
     *
     * @return     reference to the start node of the created NFA
     */

    private BtrkNode astToBtrack(){
        btrkHead = null;
        btrkTail = null;
        btrkSeq = 0;
        BtrkNode acc = newBtrkNode(BTRK_ACC);
        BtrkNode next = newBtrkNode(BTRK_END);
        next.next = acc;
        BtrkNode res = astToBtrack(this.astRoot,next);
        if (res == null) res = next;
        return res;
    }

    /**
     * Convert the specified AST node into a backtracking NFA.
     *
     * @return     reference to the start node of the created NFA
     */

    private BtrkNode astToBtrack(AstNode ast, BtrkNode next){
        BtrkNode b = null;
        BtrkNode c = null;
        switch (ast.kind){
        case A_LEA:
            b = newBtrkNode(BTRK_LEA);
            b.sym = ast.sym;
            b.next = next;
            break;
        case A_ALT:
            b = newBtrkNode(BTRK_AOPEN);
            c = newBtrkNode(BTRK_ACLO);
            c.next = next;
            b.next = c;
            int nsons = 0;
            for (AstNode a = ast.son; a != null; a = a.bro){
                nsons++;
            }
            b.sons = new BtrkNode[nsons];
            nsons = 0;
            for (AstNode a = ast.son; a != null; a = a.bro){
                BtrkNode ab = astToBtrack(a,c);
                b.sons[nsons++] = ab;
            }
            break;
        case A_CON:
            BtrkNode prev = null;
            for (AstNode a = ast.son; a != null; a = a.bro){
                c = astToBtrack(a,null);
                if (b == null) b = c;
                if (prev != null){
                    BtrkNode p = prev;
                    for (; p.next != null; p = p.next);
                    p.next = c;
                }
                prev = c;
            }
            for (; c.next != null; c = c.next);
            c.next = next;
            break;
        case A_EMP:
        case A_NUL:
            break;
        case A_GRO:
            b = newBtrkNode(BTRK_GOPEN);
            c = newBtrkNode(BTRK_GCLO);
            c.next = next;
            b.astseq = ast.seq;
            switch (ast.groupKind){
            case G_GRO:
                b.next = astToBtrack(ast.son,c);
                break;
            case G_OPT:
                // create an alternative with an empty branch and a group
                BtrkNode co = newBtrkNode(BTRK_ACLO);
                co.next = next;
                BtrkNode bo = newBtrkNode(BTRK_AOPEN);
                bo.next = co;
                bo.sons = new BtrkNode[2];
                bo.sons[0] = b;
                b.next = astToBtrack(ast.son,c);
                // bo.next = co;                      // unused
                c.next = co;
                b = bo;
                break;
            case G_RE0:
            case G_RE1:
                BtrkNode l = newBtrkNode(BTRK_RLOOP);
                l.astseq = ast.seq;                 // this allows to distingguish between * and +
                l.sons = new BtrkNode[1];
                l.sons[0] = b;
                l.next = next;
                BtrkNode r = newBtrkNode(BTRK_ROPEN);
                r.sons = new BtrkNode[1];
                r.sons[0] = l;
                r.next = l;                         // used only in building
                r.astseq = ast.seq;
                b.next = astToBtrack(ast.son,c);
                c.next = l;
                b = r;
                break;
            }
        }
        return b;
    }

    /**
     * Match the specified text against the specified backtracking NFA.
     *
     * @param      root reference to the start node of the NFA
     * @param      text text
     * @return     <code>true</code> if it mathces, <code>false</code> otherwise
     */

    public boolean matchBtrack(BtrkNode root, String text){
        int[] iterations = new int[this.astSeq];
        int[] groupstart = new int[this.astSeq];
        Arrays.fill(iterations,-1);
        Arrays.fill(groupstart,-1);
        this.matchBtrackCnt = 0;
        boolean res = root.match(0,text,iterations,groupstart);
        return res;
    }

    //-----------------------------------------------

    /** Whether an error has occurred. */
    private boolean error;

    /** The kind of error. */
    private int errorKind;

    /** The too-long-time error. */
    private static final int ERROR_TIME = 1;

    /** The stack overflow error. */
    private static final int ERROR_STACK = 2;

    /** The heap overflow error. */
    private static final int ERROR_MEMORY = 3;

    /** The failed match error. */
    private static final int ERROR_NOMATCH = 4;


    // ---------- Testing -----------------

    /** The name of the feature under testing. */
    private static String featureName;

    /** The flag to enable the trace of the tests. */
    private static boolean ckshow = false;

    /**
     * Start the testing of a feature. Record its name and trace it
     * if the tracing of tests is enabled.
     *
     * @param      s feature name
     */

    private static void feature(String s){
        featureName = s;
        if (ckshow) Trc.out.printf("%s\n",featureName);
    }

    /**
     * Trace a test of a feature.
     *
     * @param      t number of the test
     */

    private static void showTest(int t){
        if (ckshow) Trc.out.printf("-- test %s %s ---\n",featureName,t);
    }

    /**
     * Report the failure of a test.
     *
     * @param      t number of the test
     */

    private static void trcfail(int t){
        Trc.out.printf("\n");
        Trc.out.printf("-- test %s %s failed---\n",featureName,t);
    }

    /** The total memory used. */
    private int memory;

    /** The total garbage created. */
    private int garbage;

    /** The number of new()'s done. */
    private int nrnew;

    /** The number of copyof()'s done. */
    private int nrcopyof;

    /** The size of pstack. */
    private int mempstack;

    /** The sise of the dotter. */
    private int memdotter;

    /** The size of the bsarr. */
    private int membsarr;

    /** The size of PAT data B and D. */
    private int memdata;

    /** The size of the plist. */
    private int memplist;

    /** The size of the vector of active items. */
    private int memactive;

    /** The size of pnodes. */
    private int mempnode;

    /** The number of tokens parsed. */
    private int tokens;

    /** The number of paths joined by PAT. */
    private int joinnr;

    /** The number of paths that fork in PAT. */
    private int forknr;

    /** The number of parallel paths in PAT. */
    private int parallelnr;

    /** The number of edges visited when marking the paths. */
    private int marknr;
    /**
     * Trace the usage of memory and the other data of the algorithms.
     *
     * @param     dfa reference to the BSP dfa 
     */

    private void trcMeasures(BStateTable dfa){
        Trc.out.printf("memory %s garbage %s\n",this.memory,this.garbage);
        Trc.out.printf("pstack %s mempnode %s\n",pstack.length*4+4,this.mempnode);
        Trc.out.printf("mempstack %s membsarr %s memdata %s\n",this.mempstack,this.membsarr,this.memdata);
        Trc.out.printf("maxItems %s\n",dfa.maxItems);
        Trc.out.printf("tokens %s\n",this.tokens);
        Trc.out.printf("plist %s memplist %s\n",
            this.plist == null ? 0 : this.plist.length*4+4,this.memplist);
        Trc.out.printf("plist entries %s entries/toks %.2f\n",
            this.plistEnd,(double)this.plistEnd/(double)this.tokens);

        int nrlists = 0;
        int nractive = 0;
        int maxactive = 0;
        int nritems = 0;
        int maxitems = 0;
        int pi = this.psp;
        for (int i = 0; i < this.plistEnd;){
            nrlists++;
            BState pn = this.pstack[--pi];
            int n = pn.nrActiveIids;
            nractive += n;
            if (n > maxactive) maxactive = n;
            int len = this.plist[i];
            if (len < 0) len = -len;
            n = len - 2 - pn.nrActiveIids;   // without two lengths and active
            nritems += n;
            if (n > maxitems) maxitems = n;
            i += len;    // go to next
        }
        Trc.out.printf("plists: %s items: %s maxitems %s mean: %.2f active: %s maxactive %s\n",
            nrlists,nritems,maxitems,(double)nritems/(double)nrlists,nractive,maxactive);
        Trc.out.printf("marknr %s\n",this.marknr);
        Trc.out.printf("new %s copyof %s\n",this.nrnew,this.nrcopyof);
        Trc.out.printf("tree %s B,D %s\n",
            this.bsarr == null ? 0 : this.bsarr.length*4+4+4,
            this.data1 == null ? 0 : (this.data1.length*4+4)*2);
        Trc.out.printf("dotter %s memdotter %s\n",
            this.dotter == null ? 0 : this.dotter.length*4+4,this.memdotter);
        Trc.out.printf("ambiguous: %s join %s %.2f fork %s %.2f parallelnr %s %.2f\n",
            this.ambiguous,
            this.joinnr,(double)this.joinnr/(double)this.tokens,
            this.forknr,(double)this.forknr/(double)this.tokens,
            this.parallelnr,(double)this.parallelnr/(double)this.tokens);
    }

    /**
     * Deliver the specified string with spaces removed.
     *
     * @param      str string
     * @return     string
     */

    private static String removeSpaces(String str){
        String s = "";
        for (int i = 0; i < str.length(); i++){
            if (str.charAt(i) == ' ') continue;
            s += str.charAt(i);
        }
        return s;
    }

    /**
     * Trace the specified message followed by the expected value and the actual one.
     *
     * @param      str message
     * @param      e expected value
     * @param      a actual value
     * @return     string
     */

    private static void trc(String s, String e, String a){
        Trc.out.printf("%s expected: |%s| actual: |%s|\n",s,e,a);
    }

    /**
     * Trace the specified message followed by the expected value and the actual one.
     *
     * @param      str message
     * @param      e expected value
     * @param      a actual value
     * @return     string
     */

    private static void trc(String s, double e, double a){
        Trc.out.printf("%s expected: |%s| actual: |%s|\n",s,e,a);
    }

    /**
     * Test that the AST built from the specified RE generates the expected RE.
     *
     * @param      t number of the test case
     * @param      str RE
     * @param      exp expected generated RE
     */

    private void testAst(int t, String str, String exp){
        showTest(t);
        this.re = str;
        buildAst();
        BStateTable dfa = null;
        if (!this.error) dfa = buildBS(this.astRoot);
        String actual = "";
        if (this.error || getsym() != -1){    // error or whole re not consumed
            actual = "error";
        } else {
            actual = this.astRoot.toRE();
        }
        if (!removeSpaces(actual).equals(removeSpaces(exp))){
            trcfail(t);
            trc("re",exp,actual);
        }
    }

    /**
     * Test that the specified text matches the specified RE and generates the expected tree.
     *
     * @param      t number of the test case
     * @param      str RE
     * @param      text text
     * @param      exp expected generated tree
     */

    private void testTree(int t, String str, String text, String exp){
        showTest(t);
        String actual = "error";
        String actualp = "error";
        String actuale = "error";
        String actualn = "error";
        doit: {
            this.re = str;
            buildAst();
            BStateTable dfa = null;
            if (!this.error) dfa = buildBS(this.astRoot);
            if (this.error || getsym() != -1){    // error or whole re not consumed
                break doit;
            }
            match(text,dfa,true,1);
            if (!this.error){
                actual = this.tree.toString(this.astMap,this.treeLen);
            }
            this.error = false;
            PATStateTable nfa = buildPAT(this.astRoot);
            if (this.error) break doit;
            matchPAT(text,nfa,true);
            if (!this.error){
                actualp = this.tree.toString(this.astMap,this.treeLen);
            }
            this.error = false;
            EarleyTables tab = astToTables(this.astRoot);
            String acte = earleyParse(text,tab,true);
            if (acte != null) actuale = acte;
            this.error = false;
            match(text,dfa,true,2);
            if (!this.error){
                actualn = this.tree.toString(this.astMap,this.treeLen);
            }
        }
        if (!actual.equals(exp)){
            trcfail(t);
            trc("tree",exp,actual);
        }
        if (!actualp.equals(exp)){
            trcfail(t + 1000);
            trc("tree",exp,actualp);
        }
        if (!actuale.equals(exp)){
            trcfail(t + 2000);
            trc("tree",exp,actuale);
        }
        if (!actualn.equals(exp)){
            trcfail(t + 3000);
            trc("tree",exp,actuale);
        }

        boolean mj = matchJava(str,text);
        if (mj == exp.equals("error")){
            Trc.out.printf("%s %s\n",mj,!exp.equals("error"));
            trcfail(t + 4000);
        }
    }

    /**
     * Test that the specified text matches the specified RE and generates the expected tree
     * represented with the parenthetised forms of trees of the Sulzmann algorithm.
     *
     * @param      t number of the test case
     * @param      str RE
     * @param      text text
     * @param      exp expected generated tree
     */

    private void testTreeSulz(int t, String str, String text, String exp){
        showTest(t);
        String actual = "error";
        doit: {
            actual = makeDeriv(str,text);
            if (this.error){
                break doit;
            }
        }
        if (!actual.equals(exp)){
            trcfail(t);
            trc("tree",exp,actual);
        }
    }

    /**
     * Verify the implementation of the Sulzmann algorithm.
     */

    private void testSulz(){
        //ckshow = true;
        feature("sulz");
        testTreeSulz(10,"a","a","a");
        testTreeSulz(11,"ab","ab","(ab)");
        testTreeSulz(12,"abc","abc","(a(bc))");
        testTreeSulz(13,"a|b","a","(a)");
        testTreeSulz(14,"a|b","b","(b)");
        testTreeSulz(15,"a*","","()");
        testTreeSulz(16,"a*","a","(a)");
        testTreeSulz(17,"a*","aa","(aa)");
        testTreeSulz(18,"a*","aaa","(aaa)");
        testTreeSulz(19,"a+","aaa","(aaa)");
        testTreeSulz(20,"(a|ab)(b|)","ab","((ab)ε)");
        testTreeSulz(21,"a**","aa","((aa))");
        testTreeSulz(22,"(a|ab)(cb|c)","abc","((ab)(c))");
        testTreeSulz(23,"(ab|a)(c|cb)","abc","((ab)(c))");
        testTreeSulz(24,"(http://)(a*).(a)*.(a)*","http://aaa.aa.aaa","((h(t(t(p(:(//))))))((aaa)(.((aa)(.(aaa))))))");
    }

    /**
     * Test the generation of all the texts with the specified limited length of the specified RE.
     *
     * @param      t number of the test case
     * @param      str RE
     * @param      len maximum length of texts
     * @param      exp expected texts
     */

    private void testAllTexts(int t, String re, int len, String[] exp){
        showTest(t);
        buildAst(re);
        if (this.error){
            trcfail(t);
        }
        String[] actual = allTexts(this.astRoot,len).toArray(new String[0]);
        Arrays.sort(actual);
        Arrays.sort(exp);
        if (!Arrays.equals(actual,exp)){
            trcfail(t);
            trc("text",Arrays.toString(exp),Arrays.toString(actual));
        }
    }

    /**
     * Run all the verification tests.
     */

    private void test(){
        ckshow = true;
        // ast
        feature("ast");
        testAst(1,"","ε∧");
        testAst(2,"a","a∧");
        testAst(3,"a b","a1b2");
        testAst(4,"a b c","a1b2c3");
        testAst(5,"a|b","a1|b2");
        testAst(6,"a|b|c","a1|b2|c3");
        testAst(7,"(a)","(∧a1)∧");
        testAst(8,"(a)*","(∧a1)*∧");
        testAst(9,"(a)+","(∧a1)+∧");
        testAst(10,"(a b)","(∧a1.1 b1.2)∧");
        testAst(11,"(a|b)","(∧a1.1|b1.2)∧");
        testAst(12,"(a (b))","(∧a1.1(1.2 b1.2.1)1.2)∧");
        testAst(13,"[a]","[∧a1]∧");
        testAst(14,"((a))","(∧(1 a1.1)1)∧");
        testAst(15,"a (b) c"," a1(2 b2.1)2 c3");
        testAst(16,"a (b*) c"," a1(2(2.1 b2.1.1)*2.1)2 c3");
        testAst(17,"ab|c"," a1.1 b1.2 | c2");

        testAst(100,"()","(∧ ε1)∧");
        testAst(101,"|","ε1 | ε2");
        testAst(102,"a|","a1 | ε2");
        testAst(103,"()*","(∧ ε1)*∧");
        testAst(104,"[]","[∧ ε1]∧");

        testAst(200,"(","error");
        testAst(201,"(a","error");
        testAst(202,"[","error");

        ckshow = true;

        // tree
        feature("tree");

        testTree(300,"a","a","(∧a∧)∧⊣$");
        testTree(301,"aa","aa","(∧(1a1)1(2a2)2)∧⊣$");
        testTree(302,"a|b","a","(∧(1a1)1)∧⊣$");
        testTree(303,"a|b","b","(∧(2b2)2)∧⊣$");
        testTree(304,"(a)*","","(∧)∧⊣$");
        testTree(305,"(a)*","a","(∧(1a1)1)∧⊣$");
        testTree(306,"(a)*","aa","(∧(1a1)1(1a1)1)∧⊣$");
        testTree(307,"(a)+","a","(∧(1a1)1)∧⊣$");
        testTree(308,"(a)+","aa","(∧(1a1)1(1a1)1)∧⊣$");
        testTree(309,"(a)","a","(∧(1a1)1)∧⊣$");
        testTree(310,"[a]","","(∧)∧⊣$");
        testTree(311,"[a]","a","(∧(1a1)1)∧⊣$");
        testTree(312,"(a)*b","b","(∧(1)1(2b2)2)∧⊣$");
        testTree(313,"(a)*b","ab","(∧(1(1.1a1.1)1.1)1(2b2)2)∧⊣$");
        testTree(314,"aaab|aaac","aaac","(∧(2(2.1a2.1)2.1(2.2a2.2)2.2(2.3a2.3)2.3(2.4c2.4)2.4)2)∧⊣$");
        testTree(315,"(ab|(a)*)*","aba","(∧(1(1.1(1.1.1a1.1.1)1.1.1(1.1.2b1.1.2)1.1.2)1.1)1(1(1.2(1.2.1a1.2.1)1.2.1)1.2)1)∧⊣$");
        testTree(316,"[a][ab]","ab","(∧(1)1(2(2.1(2.1.1a2.1.1)2.1.1(2.1.2b2.1.2)2.1.2)2.1)2)∧⊣$");
        testTree(317,"[a]([ab])[b]","ab","(∧(1(1.1a1.1)1.1)1(2(2.1)2.1)2(3(3.1b3.1)3.1)3)∧⊣$");
        testTree(318,"([a]([ab]))[b]","ab","(∧(1(1.1(1.1.1)1.1.1(1.1.2(1.1.2.1(1.1.2.1.1(1.1.2.1.1.1a1.1.2.1.1.1)1.1.2.1.1.1(1.1.2.1.1.2b1.1.2.1.1.2)1.1.2.1.1.2)1.1.2.1.1)1.1.2.1)1.1.2)1.1)1(2)2)∧⊣$");
        testTree(319,"[a](([ab])[b])","ab","(∧(1(1.1a1.1)1.1)1(2(2.1(2.1.1(2.1.1.1)2.1.1.1)2.1.1(2.1.2(2.1.2.1b2.1.2.1)2.1.2.1)2.1.2)2.1)2)∧⊣$");
        testTree(320,"[a][a]","a","(∧(1(1.1a1.1)1.1)1(2)2)∧⊣$");
        testTree(321,"([a][a])([a][a])","aaa","(∧(1(1.1(1.1.1(1.1.1.1a1.1.1.1)1.1.1.1)1.1.1(1.1.2(1.1.2.1a1.1.2.1)1.1.2.1)1.1.2)1.1)1(2(2.1(2.1.1(2.1.1.1a2.1.1.1)2.1.1.1)2.1.1(2.1.2)2.1.2)2.1)2)∧⊣$");
        testTree(322,"([a][a])([a][a])([a][a])","aaa","(∧(1(1.1(1.1.1(1.1.1.1a1.1.1.1)1.1.1.1)1.1.1(1.1.2(1.1.2.1a1.1.2.1)1.1.2.1)1.1.2)1.1)1(2(2.1(2.1.1(2.1.1.1a2.1.1.1)2.1.1.1)2.1.1(2.1.2)2.1.2)2.1)2(3(3.1(3.1.1)3.1.1(3.1.2)3.1.2)3.1)3)∧⊣$");
        testTree(323,"([a][a])*","aaa","(∧(1(1.1(1.1.1a1.1.1)1.1.1)1.1(1.2(1.2.1a1.2.1)1.2.1)1.2)1(1(1.1(1.1.1a1.1.1)1.1.1)1.1(1.2)1.2)1)∧⊣$");
        testTree(324,"[a]([ab])[b]","ab","(∧(1(1.1a1.1)1.1)1(2(2.1)2.1)2(3(3.1b3.1)3.1)3)∧⊣$");
        testTree(325,"[ab]([b]a)","aba","(∧(1(1.1(1.1.1a1.1.1)1.1.1(1.1.2b1.1.2)1.1.2)1.1)1(2(2.1(2.1.1)2.1.1(2.1.2a2.1.2)2.1.2)2.1)2)∧⊣$");
        testTree(326,"(a|ab|ba)*","aba","(∧(1(1.2(1.2.1a1.2.1)1.2.1(1.2.2b1.2.2)1.2.2)1.2)1(1(1.1a1.1)1.1)1)∧⊣$");
        testTree(327,"(aba|a*b)(aba|a*b)","ababa","(∧(1(1.1(1.1.2(1.1.2.1(1.1.2.1.1a1.1.2.1.1)1.1.2.1.1)1.1.2.1(1.1.2.2b1.1.2.2)1.1.2.2)1.1.2)1.1)1(2(2.1(2.1.1(2.1.1.1a2.1.1.1)2.1.1.1(2.1.1.2b2.1.1.2)2.1.1.2(2.1.1.3a2.1.1.3)2.1.1.3)2.1.1)2.1)2)∧⊣$");
        testTree(328,"(aba|a*b)*","ababa","(∧(1(1.2(1.2.1(1.2.1.1a1.2.1.1)1.2.1.1)1.2.1(1.2.2b1.2.2)1.2.2)1.2)1(1(1.1(1.1.1a1.1.1)1.1.1(1.1.2b1.1.2)1.1.2(1.1.3a1.1.3)1.1.3)1.1)1)∧⊣$");
        testTree(329,"(aba|ab|a)(aba|ab|a)","ababa","(∧(1(1.1(1.1.2(1.1.2.1a1.1.2.1)1.1.2.1(1.1.2.2b1.1.2.2)1.1.2.2)1.1.2)1.1)1(2(2.1(2.1.1(2.1.1.1a2.1.1.1)2.1.1.1(2.1.1.2b2.1.1.2)2.1.1.2(2.1.1.3a2.1.1.3)2.1.1.3)2.1.1)2.1)2)∧⊣$");
        testTree(330,"(aba|ab|a)*","ababa","(∧(1(1.2(1.2.1a1.2.1)1.2.1(1.2.2b1.2.2)1.2.2)1.2)1(1(1.1(1.1.1a1.1.1)1.1.1(1.1.2b1.1.2)1.1.2(1.1.3a1.1.3)1.1.3)1.1)1)∧⊣$");
        testTree(331,"(a[b])(a[b])","aba","(∧(1(1.1(1.1.1a1.1.1)1.1.1(1.1.2(1.1.2.1b1.1.2.1)1.1.2.1)1.1.2)1.1)1(2(2.1(2.1.1a2.1.1)2.1.1(2.1.2)2.1.2)2.1)2)∧⊣$");
        testTree(332,"(a[b])+","aba","(∧(1(1.1a1.1)1.1(1.2(1.2.1b1.2.1)1.2.1)1.2)1(1(1.1a1.1)1.1(1.2)1.2)1)∧⊣$");
        testTree(333,"(a*)(a*)","aa","(∧(1(1.1(1.1.1a1.1.1)1.1.1(1.1.1a1.1.1)1.1.1)1.1)1(2(2.1)2.1)2)∧⊣$");
        testTree(334,"a*(a*)","aa","(∧(1(1.1a1.1)1.1(1.1a1.1)1.1)1(2(2.1)2.1)2)∧⊣$");
        testTree(335,"(aa[b(b)])+","aabbaa","(∧(1(1.1a1.1)1.1(1.2a1.2)1.2(1.3(1.3.1(1.3.1.1b1.3.1.1)1.3.1.1(1.3.1.2(1.3.1.2.1b1.3.1.2.1)1.3.1.2.1)1.3.1.2)1.3.1)1.3)1(1(1.1a1.1)1.1(1.2a1.2)1.2(1.3)1.3)1)∧⊣$");
        testTree(336,"(a[b])+","aba","(∧(1(1.1a1.1)1.1(1.2(1.2.1b1.2.1)1.2.1)1.2)1(1(1.1a1.1)1.1(1.2)1.2)1)∧⊣$");
        testTree(337,"[a]b","b","(∧(1)1(2b2)2)∧⊣$");
        testTree(338,"a*b*","a","(∧(1(1.1a1.1)1.1)1(2)2)∧⊣$");
        testTree(400,"","","(∧)∧⊣$");
        testTree(401,"()","","(∧(1)1)∧⊣$");
        testTree(402,"()*","","(∧(1)1)∧⊣$");
        testTree(403,"()+","","(∧(1)1)∧⊣$");
        testTree(404,"[]","","(∧(1)1)∧⊣$");
        testTree(405,"a*a*","","(∧(1)1(2)2)∧⊣$");
        // ambiguous
        testTree(500,"((a)*)*","aa","(∧(1(1.1a1.1)1.1(1.1a1.1)1.1)1)∧⊣$");
        testTree(501,"(a|b|ab)*","ab","(∧(1(1.3(1.3.1a1.3.1)1.3.1(1.3.2b1.3.2)1.3.2)1.3)1)∧⊣$");
        testTree(502,"(ab|a|b)*","abab","(∧(1(1.1(1.1.1a1.1.1)1.1.1(1.1.2b1.1.2)1.1.2)1.1)1(1(1.1(1.1.1a1.1.1)1.1.1(1.1.2b1.1.2)1.1.2)1.1)1)∧⊣$");
        testTree(503,"(a|ab)(bc|c)","abc","(∧(1(1.1(1.1.2(1.1.2.1a1.1.2.1)1.1.2.1(1.1.2.2b1.1.2.2)1.1.2.2)1.1.2)1.1)1(2(2.1(2.1.2c2.1.2)2.1.2)2.1)2)∧⊣$");
        testTree(504,"(a|a*)*","aa","(∧(1(1.2(1.2.1a1.2.1)1.2.1(1.2.1a1.2.1)1.2.1)1.2)1)∧⊣$");
        testTree(505,"(a*|a)*","aa","(∧(1(1.1(1.1.1a1.1.1)1.1.1(1.1.1a1.1.1)1.1.1)1.1)1)∧⊣$");
        testTree(506,"a*|(a|b)*","aaa","(∧(1(1.1a1.1)1.1(1.1a1.1)1.1(1.1a1.1)1.1)1)∧⊣$");
        testTree(507,"(a|ab)(c|bcd)(de|e)","abcde","(∧(1(1.1(1.1.2(1.1.2.1a1.1.2.1)1.1.2.1(1.1.2.2b1.1.2.2)1.1.2.2)1.1.2)1.1)1(2(2.1(2.1.1c2.1.1)2.1.1)2.1)2(3(3.1(3.1.1(3.1.1.1d3.1.1.1)3.1.1.1(3.1.1.2e3.1.1.2)3.1.1.2)3.1.1)3.1)3)∧⊣$");
        testTree(508,"(a+|ba|aba)*b","abab","(∧(1(1.1(1.1.3(1.1.3.1a1.1.3.1)1.1.3.1(1.1.3.2b1.1.3.2)1.1.3.2(1.1.3.3a1.1.3.3)1.1.3.3)1.1.3)1.1)1(2b2)2)∧⊣$");
        testTree(509,"(a|b|ab)*","abab","(∧(1(1.3(1.3.1a1.3.1)1.3.1(1.3.2b1.3.2)1.3.2)1.3)1(1(1.3(1.3.1a1.3.1)1.3.1(1.3.2b1.3.2)1.3.2)1.3)1)∧⊣$");
        testTree(510,"a*aa","aaaa","(∧(1(1.1a1.1)1.1(1.1a1.1)1.1)1(2a2)2(3a3)3)∧⊣$");
        testTree(511,"(((ab))|a()b)*","ab","(∧(1(1.1(1.1.1(1.1.1.1(1.1.1.1.1a1.1.1.1.1)1.1.1.1.1(1.1.1.1.2b1.1.1.1.2)1.1.1.1.2)1.1.1.1)1.1.1)1.1)1)∧⊣$");
        testTree(512,"(a*|a*a*)*b","b","(∧(1(1.1(1.1.1)1.1.1)1.1)1(2b2)2)∧⊣$");
        testTree(513,"(a*a*|a*)*b","b","(∧(1(1.1(1.1.1(1.1.1.1)1.1.1.1(1.1.1.2)1.1.1.2)1.1.1)1.1)1(2b2)2)∧⊣$");
        testTree(514,"(a)*(f|j|ajf)*","aajf","(∧(1(1.1a1.1)1.1(1.1a1.1)1.1)1(2(2.1(2.1.2j2.1.2)2.1.2)2.1(2.1(2.1.1f2.1.1)2.1.1)2.1)2)∧⊣$");
        testTree(515,"(a|a)(b|b)","ab","(∧(1(1.1(1.1.1a1.1.1)1.1.1)1.1)1(2(2.1(2.1.1b2.1.1)2.1.1)2.1)2)∧⊣$");
        testTree(516,"a([b])*c","abbc","(∧(1a1)1(2(2.1(2.1.1b2.1.1)2.1.1)2.1(2.1(2.1.1b2.1.1)2.1.1)2.1)2(3c3)3)∧⊣$");
        testTree(517,"a|(a|b)","a","(∧(1a1)1)∧⊣$");
        testTree(518,"a|(a|b)","b","(∧(2(2.1(2.1.2b2.1.2)2.1.2)2.1)2)∧⊣$");
        testTree(519,"(())*","","(∧(1(1.1)1.1)1)∧⊣$");

        // Earley
        testTree(600,"","","(∧)∧⊣$");
        testTree(601,"a","a","(∧a∧)∧⊣$");
        testTree(602,"abc","abc","(∧(1a1)1(2b2)2(3c3)3)∧⊣$");
        testTree(603,"a|bc","a","(∧(1a1)1)∧⊣$");
        testTree(604,"a|bc","bc","(∧(2(2.1b2.1)2.1(2.2c2.2)2.2)2)∧⊣$");
        testTree(605,"a(b)c","abc","(∧(1a1)1(2(2.1b2.1)2.1)2(3c3)3)∧⊣$");
        testTree(606,"a(bc)d","abcd","(∧(1a1)1(2(2.1(2.1.1b2.1.1)2.1.1(2.1.2c2.1.2)2.1.2)2.1)2(3d3)3)∧⊣$");
        testTree(607,"a(b|cd)e","abe","(∧(1a1)1(2(2.1(2.1.1b2.1.1)2.1.1)2.1)2(3e3)3)∧⊣$");
        testTree(608,"a(b|cd)e","acde","(∧(1a1)1(2(2.1(2.1.2(2.1.2.1c2.1.2.1)2.1.2.1(2.1.2.2d2.1.2.2)2.1.2.2)2.1.2)2.1)2(3e3)3)∧⊣$");
        testTree(609,"a*","a","(∧(1a1)1)∧⊣$");
        testTree(610,"(a)","a","(∧(1a1)1)∧⊣$");
        testTree(611,"a*","aa","(∧(1a1)1(1a1)1)∧⊣$");
        testTree(612,"ab*c","abc","(∧(1a1)1(2(2.1b2.1)2.1)2(3c3)3)∧⊣$");
        testTree(611,"a*","","(∧)∧⊣$");
        testTree(613,"a+","a","(∧(1a1)1)∧⊣$");
        testTree(614,"[a]","","(∧)∧⊣$");
        testTree(615,"[a]","a","(∧(1a1)1)∧⊣$");
        testTree(616,"a*b","b","(∧(1)1(2b2)2)∧⊣$");
        testTree(617,"((xx)(a|b|ab)*)*","xxab","(∧(1(1.1(1.1.1(1.1.1.1x1.1.1.1)1.1.1.1(1.1.1.2x1.1.1.2)1.1.1.2)1.1.1)1.1(1.2(1.2.1(1.2.1.3(1.2.1.3.1a1.2.1.3.1)1.2.1.3.1(1.2.1.3.2b1.2.1.3.2)1.2.1.3.2)1.2.1.3)1.2.1)1.2)1)∧⊣$");

        testTree(700,"abc","ab","error");
        testTree(701,"ab","abc","error");
        testTree(702,"abd","acd","error");
        testTree(703,"ab[d]","abc","error");

        // failures
        testTree(800,"a*bc","b","error");
        testTree(801,"a(b)c","ac","error");
        testTree(802,"abc*","a","error");
        testTree(803,"a|b|c","d","error");
        testTree(804,"abc","abcd","error");
        testTree(805,"a+bc","bc","error");
        testTree(806,"ab+c","ac","error");
        testTree(807,"abc+","ab","error");
        testTree(808,"a[b]c","adc","error");
        testTree(809,"a[b]c","abbc","error");
        testTree(810,"a(b)c","ac","error");
        testTree(811,"a(b|d)c","ac","error");
        testTree(812,"a(b|d)c","aac","error");
        testTree(813,"a(b|dd)c","adc","error");
        testTree(814,"a(b|d+)c","ac","error");
        testTree(815,"a(b|[d])c","addc","error");
        testTree(816,"a","","error");
        testTree(817,"a(b|d)","abc","error");
        // generation of texts from ASTs
        feature("all texts");
        testAllTexts(0,"",0,new String[]{""});
        testAllTexts(1,"a",1,new String[]{"a"});
        testAllTexts(2,"ab",2,new String[]{"ab"});
        testAllTexts(3,"a|b",1,new String[]{"a","b"});
        testAllTexts(4,"a|b|c",1,new String[]{"a","b","c"});
        testAllTexts(5,"(a)",1,new String[]{"a"});
        testAllTexts(6,"[a]",1,new String[]{"","a"});
        testAllTexts(7,"a*",1,new String[]{"","a"});
        testAllTexts(8,"a*",2,new String[]{"","a","aa"});
        testAllTexts(9,"a+",1,new String[]{"a"});
        testAllTexts(10,"a+",2,new String[]{"a","aa"});
        testAllTexts(11,"ab|ac",2,new String[]{"ab","ac"});
        testAllTexts(12,"(a|b)(c|d)",2,new String[]{"ac","ad","bc","bd"});
        testAllTexts(13,"(a|b)c*",2,new String[]{"a","ac","b","bc"});
        testAllTexts(14,"(a|b)*",2,new String[]{"","a","b","aa","ab","ba","bb"});
    }

    /**
     * Trace the main data measured.
     *
     * @param      algo name of the algorithm
     * @param      ti time (nanoseconds)
     * @param      regex reference to the Re object
     * @param      count number of times the string has been parsed
     */

    private static void traceMeasure(String algo, long ti, Re regex, int count){
        Trc.out.printf("%s %s ms, %.2f toks/ms %.2f bytes/toks %.2f garbage bytes/toks\n",
            algo,ti/1000000,(double)regex.tokens/((double)ti/1000000.0),
            (double)regex.memory/((double)regex.tokens/(double)count),
            (double)regex.garbage/((double)regex.tokens/(double)count));
    }


    // ---------- Measuring time and memory -----------------

    /**
     * Deliver the value of the cycle counter (TSC, time stamp counter).
     *
     * @return     value
     */

    static native long getCycles();

    static {
        URL u = ClassLoader.getSystemResource
            ("Re.dll");
        System.load(u.toString().substring(6));
    }

    /** The list of values to be plotted in charts. */
    private static LinkedList<String[]> plot;

    /** The number of the algorithms in the plot data (nr of algorithms + 1. */
    private static final int NRALGOS = 6;
    /** The total time spent by each algorithm. */
    private static long[] totTime = new long[NRALGOS];

    /** The total memory used by each algorithm. */
    private static long[] totMem = new long[NRALGOS];

    /** The headings of the plot rows. */
    private static String[] algoNames = new String[]{"0","BSP","BSPP","OS","Earley","Java"};
    /** The labels of the plot rows. */
    private static String[] algoLabels = new String[]{"0",
            "BSP|blue","BSPP|violet","OS|indigo","Earley|red","Java|orange"};
    /**
     * Run all the algorithms matching the specified string concatenated rep times against
     * the specified RE for a number of times as the specified count, and collect the
     * data in the plot list. Show the test case.
     *
     * @param      t number of the test case
     * @param      str RE
     * @param      text text
     * @param      rep number of time the text is concatenated to build the one to be tested
     * @param      count number of times the matching is run
     */

    private static void testSpeed(int t, String str, String text, int rep, int count){
        testSpeed(t,str,text,rep,count,"bcpej",true,1);
    }

    /**
     * Run the specified algorithms matching the specified string concatenated rep times against
     * the specified RE for a number of times as the specified count, and collect the
     * data in the plot list. The algorithms to run are specified in a string, in which:
     * b: BSP, c: BSPP, p: PAT, e: Earley, j: java. Show the test case.
     *
     * @param      t number of the test case
     * @param      str RE
     * @param      text text
     * @param      rep number of time the text is concatenated to build the one to be tested
     * @param      count number of times the matching is run
     * @param      algos string representing the algorithms to run
     */

    private static void testSpeed(int t, String re, String text, int rep, int count,
        String algos){
        testSpeed(t,re,text,rep,count,algos,true,1);
    }

    /**
     * Run the specified algorithms matching the specified string concatenated rep times
     * against the specified RE for a number of times as the specified count, and collect the
     * data in the plot list. The algorithms to run are specified in a string, in which:
     * b: BSP, c: BSPP, p: PAT, e: Earley, j: java.
     *
     * @param      t number of the test case
     * @param      str RE
     * @param      text text
     * @param      rep number of time the text is concatenated to build the one to be tested
     * @param      count number of times the matching is run
     * @param      algos string representing the algorithms to run
     * @param      show <code>true</code> to show the test case number
     * @param      check 0: no check, 1: check match successful, -1: check match failed
     */

    private static void testSpeed(int t, String re, String text, int rep, int count,
        String algos, boolean show, int check){
        if (show) showTest(t);
        System.out.printf("-- test %s -- RE: %s text: %s * %s times\n",t,re,text,rep);
        boolean error = true;
        if (plot == null){
            plot = new LinkedList<String[]>();
            plot.add(algoLabels);
        }
        String[] plotEle = new String[NRALGOS];
        plot.add(plotEle);
        plotEle[0] = "" + rep;
        long t0 = 0;
        long t1 = 0;
        long ti = 0;
        doit: {
            if (show) Trc.out.printf("test speed RE: %s text: %s * %s times\n",
                re,text,rep);
            StringBuffer reptext = new StringBuffer();
            for (int i = 0; i < rep; i++){
                reptext.append(text);
            }
            text = reptext.toString();

            for (int i = 0; i < algos.length(); i++){
                int algo = 0;
                switch (algos.charAt(i)){
                case 'b': algo = ALGO_BSP; break;
                case 'c': algo = ALGO_BSPP; break;
                case 'p': algo = ALGO_PAT; break;
                case 'e': algo = ALGO_EARLEY; break;
                case 'j': algo = ALGO_JAVA; break;
                }
                System.gc();
                Re regex = new Re(algo);
                String origRe = re;
                if (algo == ALGO_JAVA){
                    re = re.replaceAll("\\[","(");
                    re = re.replaceAll("\\]",")?");
                }
                if (!regex.compile(re)){
                    Trc.out.printf("error %s compile RE: %s\n",algoNames[algo],re);
                    break doit;
                }
                regex.re = origRe;
                System.out.printf("   test %s %s-- %s\n",
                    t,algoNames[algo],new Date().toString());
                testit: {
                    // make a first dry run with time supervision enabled and a second one
                    // with time supervision disabled
                    regex.timeSupervise = true;
                    if (algo == ALGO_JAVA){
                        // try first my engine to make sure that it does not take too much time
                        regex.buildAst();
                        BtrkNode jroot = regex.astToBtrack();
                        regex.matchStartTime = getCycles();
                        regex.matchJava(jroot,text);
                    } else {
                        regex.matchStartTime = getCycles();
                        regex.match(text);
                    }
                    if (regex.error){
                        if (regex.errorKind != ERROR_NOMATCH){
                            // all these errors could cause java.util.regex to take a lot of time
                            ti = Long.MAX_VALUE;
                            break testit;
                        }
                    }
                    System.gc();
                    regex.tokens = 0;
                    // maxMatchTime = Long.MAX_VALUE;          // disable time supervision
                    regex.timeSupervise = false;
                    t0 = getCycles();
                    for (int k = 0; k < count; k++){
                        boolean res = regex.match(text);
                        if (check != 0){
                            if (res != (check > 0)){
                                Trc.out.printf("error match %s %s\n",regex.errorKind,algoNames[algo]);
                                Trc.out.printf("RE %s text %s res %s check %s\n",regex.re,text,res,check);
                            }
                        }
                    }
                    t1 = getCycles();
                    ti = t1 - t0 - CYCLE_BASE;
                } // testit
                if (show) traceMeasure(algoNames[algo],(long)(ti*0.45),regex,count);
                double val = 0;
                if (ti > 0) val = (double)regex.tokens/((double)ti*0.45/1000000.0); // cycles to ns
                if (ti == Long.MAX_VALUE) val = Double.NaN;
                plotEle[algo] = String.format("%.2f",val);
                totTime[algo] += ti;
                totMem[algo] += regex.memory;
            }
            error = false;
        }
        if (error){
            trcfail(t);
        }
    }

    /**
     * Deliver a string concatenating the specified one with itself the specified rep times.
     *
     * @param      str string
     * @param      rep number of times
     * @return     string
     */

    private static String rep(String str, int rep){
        StringBuffer reptext = new StringBuffer();
        for (int i = 0; i < rep; i++){
            reptext.append(str);
        }
        return reptext.toString();
    }

    /**
     * Run all the algorithms matching the specified string concatenated a number of
     * increasing times against the specified RE. Produce a chart in the html file.
     *
     * @param      re RE
     * @param      str text
     * @param      success <code>true</code> to check that the match succeeds,
     *             <code>false</code> if the match must fail
     */

    private static void testSpeeds(int t, String re, String str, boolean success){
        showTest(t);
        testSpeed(t,re,str,100,100,"bcpej",false,0);        // warm up
        plot = null;
        int rep = 10;
        String algos = "bcpej";
        int check = success ? 1 : -1;
        for (int i = 0; i < 5; i++){
            testSpeed(t+i*2,re,str,rep,10,algos,true,check);
            if (rep == 100000) break;
            testSpeed(t+i*2+1,re,str,rep*5,10,algos,true,check);
            rep *= 10;
        }

        html.printf("<p><div style=\"border:1px solid;width:1000px;\">\n");
        html.printf("<svg id=diag%s xmlns=\"http://www.w3.org/2000/svg\" " +
            "xmlns:xlink=\"http://www.w3.org/1999/xlink\" " + 
            "svg width=1000 height=380>\n<script>\n",t);
        html.printf("svgChart(\"diag%s\",\"%s\",\"%s\",[\n",t,re,str);
        for (int i = 0; i < NRALGOS; i++){
            html.printf("[");
            boolean first = true;
            for (int j = 0; j < plot.size(); j++){
                String[] ele = plot.get(j);
                if (ele[i] != null && ele[i].length() > 0){
                    if (!first) html.printf(",");
                    first = false;
                    if (i == 0 || j == 0){
                        html.printf("\"%s\"",ele[i]);
                    } else {
                        html.printf("%s",ele[i]);
                    }
                }
            }
            if (i < NRALGOS-1){
                html.printf("],\n");
            } else {
                html.printf("]\n");
            }
        }
        html.printf("])\n</script>\n</svg>\n</div>\n");
    }
    
    /** The html file for the charts. */
    private static PrintStream html;

    /** The tex file for the charts. */
    private static PrintStream tex;

    /**
     * Chart the speed of the algorithms on a selection of significant REs and strings to
     * show the behaviour of the algorithms.
     */

    private static void chartByTextLength(){
        int totMemory = 0;
        int totGarbage = 0;
        try {
            html = new PrintStream("remeasuresl.html");
        } catch (FileNotFoundException exc){
            Trc.out.printf("html measure file error\n");
            System.exit(1);
        }

        html.printf("<!DOCTYPE html>\n");
        html.printf("<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\">\n");
        html.printf("<script>\n");
        try {
            BufferedReader in = new BufferedReader(new FileReader("chart.js"));
            for (;;){
                String line = in.readLine();
                if (line == null) break;
                html.printf("%s\n",line);
            }
            in.close();
        } catch (IOException exc){
            System.out.printf("error on file %s\n",exc);
            System.exit(1);
        }
        html.printf("</script>\n");
        html.printf("</head><body>\n");
        html.printf("<h3>Comparison of RE algorithms</h3>\n");

        testSpeeds(10,"(a|b|ab)*","a",true);
        testSpeeds(20,"(a|b|ab)*","ab",true);
        testSpeeds(30,"((a)+|ba|aba)*b","abab",true);
        testSpeeds(40,"(a|b|aa|aaa|aaaa|aaaaa|aaaaab)*","aaaaab",true);
        testSpeeds(50,"(a|aa|aaa|aaaa|aaaaa|aaaaab)*","aaaaab",true);
        testSpeeds(60,"((b)*(a)*(ab)+(b)*(a)*c|(a)*(b)*(ba)+(a)*(b)*d)*","aabbbaaabd",true);
        testSpeeds(70,"((http://)(a*).(a)*.(a)*)*","http://aaa.aa.aaa",true);
        testSpeeds(80,"((a)+|ba|aba)*b","abab",true);
        testSpeeds(90,"(abcdefghij)*","abcdefghij",true);
        testSpeeds(100,"((a)(a)(a)(a)(a))*","aaaaa",true);
        testSpeeds(110,"((((h)*))*|(((((h)))*))+)*","h",true);    // BSPP slow
        testSpeeds(120,"(a|a)*b","a",false);                      // java slow

        html.printf("</body></html>\n");
        html.close();

        Trc.out.printf("total time\n");
        for (int i = 1; i < totTime.length; i++){
            Trc.out.printf("%s: %s ms\n",algoNames[i],totTime[i]/1000000);
        }
        Trc.out.printf("total memory\n");
        for (int i = 1; i < totMem.length; i++){
            Trc.out.printf("%s: %s bytes\n",algoNames[i],totMem[i]);
        }

        // Trc.out.printf("totMemory %s totGarbage %s\n",totMemory,totGarbage);
    }


    //--------- Building random REs and texts ------------------------

    // These constants tell the indexes at which specific kinds of frequencies are placed
    // in the frequence matrix; the other places in the matrix are dedicated to the AST
    // kinds and AST group kinds.

    /** The index of the element containing the frequencies of non-nested constructs. */
    private static final int FREQ_NONEST = 0;

    /** The index of the element containing the total frequencies of all constructs. */
    private static final int FREQ_TOTAL = 3;

    /** The index of the element containing the frequencies of the nested groups. */
    private static final int FREQ_GRNEST = 4;

    /** The index of the element containing the frequencies of the constructs by nesting depth. */
    private static final int FREQ_LEV = 5;

    /** The index of the element containing the frequencies of the number of sons by constructs. */
    private static final int FREQ_NRSONS = 10;

    /**
     * Compute the frequencies of constructs the occur in the specified AST and store them
     * in the specified matrix.
     *
     * @param      ast root node of the AST
     * @param      freq matrix of frequencies
     */

    private static void astFrequency(AstNode ast, int[][] freq){
        int idx = ast.kind;
        if (ast.kind == A_GRO){              // group
            idx = ast.groupKind + 6;
        }
        int fat = FREQ_NONEST;               // asts not nested
        if (ast.fat != null){
            fat = ast.fat.kind;
            if (ast.fat.kind == A_GRO){      // group
                fat = ast.fat.groupKind + 6;
            }
        }
        freq[fat][idx]++;
        freq[FREQ_TOTAL][idx]++;             // totals
        int n = 0;
        for (AstNode a = ast.son; a != null; a = a.bro){
            astFrequency(a,freq);
            n++;
        }
        freq[FREQ_NRSONS][idx] += n;         // number of sons
        switch (ast.kind){
        case A_GRO:
            if (isRepNested(ast)) freq[FREQ_GRNEST][idx]++;   // nested groups
            break;
        }
        int lev = astNestLevel(ast);
        if (lev > 9) lev = 9;
        freq[FREQ_LEV][lev]++;               // asts by levels
    }

    /**
     * Tell if the specified AST node is nested in some *|+ group.
     *
     * @param      ast reference to the AST node
     * @return     <code>true</code> if it is, <code>false</code> otherwise
     */

    private static boolean isRepNested(AstNode ast){
        boolean res = false;
        for (AstNode a = ast.fat; a != null; a = a.fat){
            if (a.kind == A_GRO && (a.groupKind == G_RE0 || a.groupKind == G_RE1)){
                res = true;
                break;
            }
        }
        return res;
    }

    /**
     * Deliver the depth of nesting of the specified AST node.
     *
     * @param      ast reference to the AST node
     * @return     nesting depth
     */

    private static int astNestLevel(AstNode ast){
        int res = 0;
        for (AstNode a = ast.fat; a != null; a = a.fat){
            res++;
        }
        return res;
    }

    /** The string representation of the kinds of constructs. */
    private static final String[] CONSTR = new String[]{
        "term","alt","conc","group","empty","void","GRO","OPT","RE0","RE1"};

    /** The string representation of the kinds of frequencies. */
    private static final String[] FREQKINDS = new String[]{
        "nonest","alt","conc","tot","grnest","lev","GRO","OPT","RE0","RE1","nrsons"};

    /**
     * Compute all the frequencies of the REs contained in the specified matrix.
     * If they are REs of the benchmark, remove the enclosing group of each of them before
     * computing the frequencies.
     *
     * @param      res matrix of REs
     * @param      bench <code>true</code> if the REs are the ones of the benchmark
     */

    private static void allFrequency(String[][] res, boolean bench){
        int[][] freq = new int[FREQKINDS.length][];
        for (int i = 0; i < freq.length; i++){
            freq[i] = new int[CONSTR.length];
        }
        for (int i = 0; i < res.length; i++){
            for (int j = 0; j < res[i].length; j++){
                Re regex = new Re();
                regex.re = res[i][j];
                regex.buildAst();
                if (regex.error || regex.getsym() != -1){
                    System.out.printf("error %s at %s\n",regex.re,regex.cursor);
                    System.exit(1);
                }
                if (bench){                    // the random generated ones
                    AstNode root = regex.astRoot.son.son.son;
                    AstNode fat = root.fat;    // save the fat, and clear it
                    root.fat = null;
                    astFrequency(root,freq);
                    root.fat = fat;            // restore fat
                } else {
                    astFrequency(regex.astRoot,freq);
                }
            }
        }
        makeFreqDistr(freq);
    }

    /**
     * Build the percentage distribution matrix from the specified frequencies martix.
     *
     * @param      freq matrix of frequencies
     */

    private static void makeFreqDistr(int[][] freq){
        // build vectors whose elements are ast kinds, and for each kind there are as
        // many as its relative frequency. They are meant to be accessed with an index that
        // is a random number 0:100. The values got have the same frequency as the one
        // measured here for structural components only (alt, conc, groups)

        freqs = new int[FREQKINDS.length][];
        for (int i = 0; i < CONSTR.length; i++){
            if (i == FREQ_LEV){
                freqs[i] = distr(freq[i],0,1,2,3,4,5,6,7,8,9);
            } else {
                freqs[i] = distr(freq[i],A_ALT,A_CON,6+G_GRO,6+G_OPT,6+G_RE0,6+G_RE1);
            }
        }

        // vector for the average numbers of sons
        freqs[FREQ_NRSONS] = new int[3];
        if (freq[FREQ_TOTAL][A_ALT] > 0){
            freqs[FREQ_NRSONS][A_ALT] = freq[FREQ_NRSONS][A_ALT]/freq[FREQ_TOTAL][A_ALT];
        }
        if (freq[FREQ_TOTAL][A_CON] > 0){
            freqs[FREQ_NRSONS][A_CON] = freq[FREQ_NRSONS][A_CON]/freq[FREQ_TOTAL][A_CON];
        }

    }

    /**
     * Trace the specified frequencies matrix in percent form.
     *
     * @param      freq matrix of frequencies
     * @param      name message to be shown before the matrix
     */

    private static void showFreq(int[] freq, String name){
        int n = 0;
        for (int i = 0; i < freq.length; i++){
            n += freq[i];
        }
        Trc.out.printf("%s",name);
        for (int i = 0; i < freq.length; i++){
            double val = 0;
            if (n != 0) val = (double)freq[i]*100.0/(double)n;
            Trc.out.printf("\t%.2f%%",val);
        }
        Trc.out.printf("%n");
    }

    /**
     * Create an array d that contains the distribution of the values specified:
     * freq is an array that contains the number of times that some things (represented
     * by indexes occur); only the entries whose indexes are passed as 2nd, ... arguments
     * are considered. The percentage of the occurrences of the things (repsented as indexes)
     * are computed. Then a vector of 100 entries is built, and if the percentage of the
     * first thing is p, then its first p entries contain the number (index) that represents
     * the thing, and likewise for the following ones. This allows to pick up randomly a
     * number of times a thing being sure that the group thus built contains things in number
     * close to their frequencies. E.g.:
     *
     *   things     3    4     8
     *   number    10   10     5    tot: 25
     *   percent   40   40    20    %
     *
     *   vector built: 0..39   3
     *                 40..79  4
     *                 80..99  8
     *
     * @param      freq matrix of frequencies
     * @param      constr number of occurences of the contructs
     * @return     distribution array
     */

    private static int[] distr(int[] freq, int ... constr){
        int[] d = new int[100];
        int n = 0;
        for (int i : constr){
            n += freq[i];
        }
        int j = 0;
        fill: for (int i : constr){
            int f = (int)Math.round((double)freq[i]*100.0/(double)n);
            for (int k = 0; k < f; k++){
                if (j >= d.length) break fill;
                d[j++] = i;
            }
        }
        // fill the unfilled entries, if any
        for (; j < d.length; j++){
            d[j] = 6+G_GRO;
        }
        return d;
    }

    /** Percentage distribution martix. */
    private static int[][] freqs;

    /**
     * Measure the frequencies of the construct of a number of REs (currently, the ones
     * taken from published benchmarks).
     */

    private static void measureFrequency(){
        // these are REs taken from http://www.boost.org/doc/libs/1_41_0/libs/regex/doc/gcc-performance.html
        // and simplified a bit for what concerns character classes (aka categories), that in a RE
        // engine are treated as characters, and other constructs that are not present in this
        // implementation.
        // The result is a curve that resembles a gaussian with top for terminals, followed
        // by concatenation, then +, |, groups, etc.
        // *+ nested in other *+ are very rare.
        String[] examples = new String[]{
            "Twain",
            "Huck(a)*",
            "(a)+ing",
            "(a)*Twain",
            "Tom|Sawyer|Huckleberry|Finn",
            "(Tom|Sawyer|Huckleberry|Finn).*river|river.*(Tom|Sawyer|Huckleberry|Finn)",
            "template(s)*<(a|b|c|c)+>(s)*(class|struct)(s)*(<(w)+>[o(s)*(x)*c](s)*)*(<(w)*>)(s)*[<x+>s*](a|bc*)",
            "(s*a(bc|d*ef)*)|(abc*|d*)|<[a|b]((0xd+)|([d*p]d+(e|E)[p|m]d+))[u](int[8|16|32|64])L>|ax*a|qy*q|" +
                "<(__asm|__cdecl|__declspec|__export|__far16|__fastcall|__fortran|__import|__pascal|__rtti|__stdcall|" +
                "_asm|_cdecl|__except|_export|_far16|_fastcall|__finally|_fortran|_import|_pascal|_stdcall|__thread|" +
                "__try|asm|auto|bool|break|case|catch|cdecl|char|class|const|const_cast|continue|default|delete|do|" +
                "double|dynamic_cast|else|enum|explicit|extern|false|float|for|friend|goto|if|inline|int|long|mutable|" +
                "namespace|new|operator|pascal|private|protected|public|register|reinterpret_cast|return|short|signed|" +
                "sizeof|static|static_cast|struct|switch|template|this|throw|true|try|typedef|typeid|typename|union|" +
                "unsigned|using|virtual|void|volatile|wchar_t|while)>",
            "s*cs*include(s)+(a*|<b*>)",
            "s*cs*include(s)+(boost(a)*|<boost(b)*>)",
            "beman|john|dave",
            "<p>a*</p>",
            "<ax+href=(s*|n+)b*>",
            "<hdx*>x*<hdx*>",
            "<img(x)+src=(\"x+\"|n+)x*>",
            "<font(x)+face=(\"x+\"|n+)x*>x*</font>"
        };
        String[][] exa = new String[1][];
        exa[0] = examples;
        allFrequency(exa,false);
    }

    /* Construct REs with (approx) a given length.
     * It builds an AST with a ()* node with an empty son, and then it picks up randomly a
     * leaf node and replaces it with a more complex one, whose type is chosen randomly and
     * with the measured frequency of constructs.
     *
     * An ast with an empty node is built (which is a leaf). Then a random number of sons
     * (leaves) is added to it. Then a leaf is picked up randomly and changed (blossomed) into
     * a node whose kind is selected according to the frequency of kinds for its level
     * respecting the allowed nesting of kinds. That node is given sons respecting the frequency
     * distribution of the number of sons for the kind at hand.
     * Note that here a leaf is an ast node without sons. Since its kind can be anything allowed
     * by the nesting rules, at the end there is a need to set properly the kind of the ones
     * that did not blossom to A_LEA.
     * This guarantees the frequencies of the kinds of nodes are more or less the same as the
     * reference ones and the number of sons too.
     * This means that the generated ast "resembles" the reference one at least in the fatness
     * of levels, and in that of the breadth of sons.
     *
     * Allowed nesting (terminals allowed on them all):
     *
     *    node     sons
     *    alt      conc group
     *    conc     group
     *    group    alt conc group
     *    none     alt conc group
     *    *+       *+ (also indirect)
     *
     * Some actual frequencies of the generated REs are different from the starting ones.
     *
     *  - there are much more nested *+
     *  - alt nodes have many leaves and few conc and in the reference it is the opposite
     *
     * There are several reasons for this. E.g.:
     *
     *  - I choose with the same frequency to append sons to any leaf regardless of its
     *    nesting depth, while in reality the less nested ones are more frequent than the
     *    deep nested ones. Perhaps I should choose more frequently the less nested ones.
     *  - leaves are changed into others: I generate the others according to their frequency
     *    hoping that the remaining ones are leaves, but I change some leaves into others
     *  - I generate terminals in alt and con, knowing that then I will replace some of them,
     *    but the quantity I replace is not the right one to achieve the reference frequency
     *  - I generate the proper number of alt nodes, and with the proper number of conc
     *    sons, but I do not blossom all of them, and at the end I turn them into leaves, thus
     *    resulting in a number of leaves sons that are greater than the frequency
     *
     * Another way to build random ASTs would be to generate ast nodes according to their
     * frequency and then try to combine them into an ast. This is not simple.
     *
     * Pseudocode
     *
     *   let r = a random construct type with probability distribution F_n
     *   root := new AST node with type r
     *   for ever do
     *       if (length of RE built from root >= target) break
     *       let d = a random nesting depth with probability distribution F_d
     *       let n = random leaf AST node whose nesting dept is closest to d
     *       if type of n == alternative or concatenation then
     *           add to n a random number of sons with probablity distribution F_s
     *           for each son do
     *               let kind = a random kind with probability distribution F_f,
     *               where f is the kind of the closest group in which the son is nested
     *               if f = terminal then set the symbol of the son to a random letter
     *           end for
     *       end if
     *   end for
     *   set the symbol of all the leaves that do not have any to a random letter
     *   let R = string representation of the RE of root
     *   return ((R)z)*, where z is an unused letter
     */

    /** The length of the generated RE. */
    private static int reLength;

    /**
     * Construct an AST representing a RE with (approx) the specified length.
     *
     * @param      target length
     * @return     reference to the root node of the AST
     */

    private AstNode buildREbyLength(int target){
        this.astSeq = 0;
        this.cursor = 0;
        Random ran = new Random();
        int r = ran.nextInt(100);
        r = freqs[FREQ_NONEST][r];
        AstNode root = newAstNode(r);
        // map to hold the incomplete leaves by nesting level, to be used below to seek
        // the nodes to blossom
        AstNode[][] leaves = new AstNode[100][];
        int[] leavesi = new int[100];
        for (int i = 0; i < leaves.length; i++){   // allocate the rows
            leaves[i] = new AstNode[100];
        }
        leaves[0][leavesi[0]++] = root;
        for (;;){
            this.reLength = removeSpaces(root.toRE(true)).length();
            if (this.reLength >= target) break;
            // pick up a random level in the tree built so far that has a leaf to blossom
            r = ran.nextInt(100);
            r = freqs[FREQ_LEV][r];
            if (leavesi[r] == 0){          // no leaves at this nesting
                // find closest level that has leaves
                int rl = r-1;
                int ru = r+1;
                r = -1;
                for (;;){
                    if (rl >= 0 && leavesi[rl] > 0){
                        r = rl;
                        break;
                    }
                    if (ru < leavesi.length && leavesi[ru] > 0){
                        r = ru;
                        break;
                    }
                    rl--;
                    ru++;
                }
                if (r < 0){
                    System.out.printf("buildREbyLength no suitable leaf\n");
                    System.exit(1);
                }
            }
            int rlev = r;
            int ra = ran.nextInt(leavesi[rlev]);
            AstNode a = leaves[rlev][ra];
            // take the chosen leaf, and populate it
            leavesi[rlev]--;
            System.arraycopy(leaves[rlev],ra+1,leaves[rlev],ra,leaves[rlev].length-ra-1);   // remove it
            int nsons = 1;
            if (a.kind == A_ALT || a.kind == A_CON){
                nsons = ran.nextInt(freqs[FREQ_NRSONS][a.kind]*2 + 4);   // add a random number of sons, never 0 or 1 ..
                if (nsons < 2) nsons = 2;                                // .. *2 to make it on average equal to the frequency
            }
            int fatkind = FREQ_NONEST;
            if (a.fat != null){
                fatkind = a.fat.kind;
                if (a.fat.kind == A_GRO){      // group
                    fatkind = a.fat.groupKind + 6;
                }
            }
            AstNode prev = null;
            for (int j = 0; j < nsons; j++){
                int newkind = freqs[fatkind][ran.nextInt(100)];
                int grkind = 0;
                if (newkind >= 6){
                    grkind = newkind - 6;
                    newkind = A_GRO;
                }
                AstNode ast = newAstNode(newkind);
                if (newkind == A_GRO){
                    ast.groupKind = grkind;
                } else if (newkind == A_LEA){
                    ast.sym = (char)('a' + ran.nextInt(10));
                } else if (newkind == A_ALT){
                    ast.altnr = j+1;
                }
                if (j == 0){
                    a.son = ast;
                } else {
                    prev.bro = ast;
                }
                ast.fat = a;
                if (newkind == A_ALT || newkind == A_CON || newkind == A_GRO){
                    leaves[rlev+1][leavesi[rlev+1]++] = ast;
                }
                prev = ast;
            }
        }
        // complete the incomplete leaves
        for (int i = 0; i < leavesi.length; i++){
            if (leavesi[i] == 0) continue;
            for (int j = 0; j < leavesi[i]; j++){
                AstNode ast = leaves[i][j];
                ast.kind = A_LEA;
                ast.sym = (char)('a' + ran.nextInt(10));
            }
        }

        // build ((re) z )*, where z is not in re, so as not to enlarge the ambiguity of large texts
        AstNode enclg = newAstNode(A_GRO);
        enclg.groupKind = G_GRO;
        enclg.son = root;
        root.fat = enclg;
        AstNode enclc = newAstNode(A_CON);
        enclc.son = enclg;
        enclc.son.bro = newAstNode(A_LEA);
        enclc.son.bro.sym = 'z';
        enclc.fat = enclg;
        AstNode encl = newAstNode(A_GRO);
        encl.groupKind = G_RE0;
        encl.son = enclc;
        root = encl;
        /*
        AstNode encl = newAstNode(A_GRO);
        encl.groupKind = G_RE0;
        encl.son = root;
        root.fat = encl;
        root = encl;
        */
        return root;
    }

    /**
     * Generate all texts up to a given length from the specified AST.
     *
     * @param      ast reference to the root node of the AST
     * @param      len length
     * @return     set of strings
     */

    private HashSet<String> allTexts(AstNode ast, int len){
        HashSet<String> res = allTexts(ast,len,0);
        return res;
    }

    /**
     * Generate all texts up to a given length from the specified AST node, which is
     * a node on a larger AST, placed at the specified nesting depth in it.
     *
     * @param      ast reference to the root node of the AST
     * @param      len length
     * @param      lev nesting depth
     * @return     set of strings
     */

    private HashSet<String> allTexts(AstNode ast, int len, int lev){
        HashSet<String> res = null;
        switch (ast.kind){
        case A_LEA:
            res = new HashSet<String>();
            res.add(String.valueOf(ast.sym));
            break;
        case A_EMP:
            res = new HashSet<String>();
            res.add("");
            break;
        case A_NUL:
            res = new HashSet<String>();
            break;
        case A_ALT:
            res = new HashSet<String>();
            for (AstNode a = ast.son; a != null; a = a.bro){
                res = union(res,allTexts(a,len,lev+1));
            }
            break;
        case A_CON:
            res = new HashSet<String>();
            res.add("");
            for (AstNode a = ast.son; a != null; a = a.bro){
                res = prod(res,allTexts(a,len,lev+1),len);
            }
            break;
        case A_GRO:
            switch (ast.groupKind){
            case G_GRO:
                res = allTexts(ast.son,len,lev+1);
                break;
            case G_OPT:
                HashSet<String> emp = new HashSet<String>();
                emp.add("");
                res = union(emp,allTexts(ast.son,len,lev+1));
                break;
            case G_RE0:
                int rep = len+1;
                // limit *|+ nested in others
                boolean nested = false;
                for (AstNode a = ast.fat; a != null; a = a.fat){
                    if (a.kind == A_GRO && (a.groupKind == G_RE0 || a.groupKind == G_RE1)){
                         nested = true;
                    }
                }
                if (nested) rep = 2;
                res = new HashSet<String>();
                for (int i = 0; i < rep; i++){
                    HashSet<String> pw = pow(allTexts(ast.son,len,lev+1),i,len);
                    if (i > 0 && pw.size() == 0) break;
                    res = union(res,pw);
                }
                break;
            case G_RE1:
                rep = len+1;
                // limit *|+ nested in others
                nested = false;
                for (AstNode a = ast.fat; a != null; a = a.fat){
                    if (a.kind == A_GRO && (a.groupKind == G_RE0 || a.groupKind == G_RE1)){
                         nested = true;
                    }
                }
                if (nested) rep = 2;
                res = new HashSet<String>();
                for (int i = 1; i < rep; i++){
                    HashSet<String> pw = pow(allTexts(ast.son,len,lev+1),i,len);
                    if (i > 0 && pw.size() == 0) break;
                    res = union(res,pw);
                }
                break;
            }
        }
        if (res.size() >= 20){    // limit size of result
            HashSet<String> res1 = new HashSet<String>();
            int n = 0;
            for (Iterator<String> i = res.iterator(); i.hasNext();){
                String e = i.next();
                res1.add(e);
                if (n++ == 20) break;
            }
            res = res1;
        }
        // if (lev == 0) System.out.printf("%s%s res %s\n",indent(lev),ast.toRE(true),res.size());
        return res;
    }

    /**
     * Deliver a set which is the union of the two specified set of strings.
     *
     * @param      s1 set
     * @param      s2 set
     * @return     set
     */

    private HashSet<String> union(HashSet<String> s1, HashSet<String> s2){
        // TRACE(H,"union %s %s\n",s1,s2);
        if (s1.size() >= 20) return s1;    // limit size of result
        s1.addAll(s2);
        return s1;
    }

    /**
     * Deliver a set which is the product of the two specified set of strings.
     * The strings that exceed the specified length are not included in the resulting set.
     * The cardinality of the resulting set is at maximum 20. Only 20 resulting strings
     * are kept.
     *
     * @param      s1 set
     * @param      s2 set
     * @param      len
     * @return     set
     */

    private HashSet<String> prod(Set<String> s1, Set<String> s2, int len){
        HashSet<String> res = new HashSet<String>();
        pr: for (String i : s1){
            for (String j : s2){
                String str = i + j;
                if (str.length() <= len) res.add(str);
                if (res.size() >= 20) break pr;    // limit size of result
            }
        }
        return res;
    }

    /**
     * Deliver a set which is the powerset of the specified set of strings.
     * The strings that exceed the specified length are not included in the resulting set.
     * The cardinality of the resulting set is at maximum 20. Only 20 resulting strings
     * are kept.
     *
     * @param      s1 set
     * @param      len
     * @return     set
     */

    private HashSet<String> pow(Set<String> s1, int e, int len){
        HashSet<String> res = new HashSet<String>();
        res.add("");
        if (e > 0){
            for (int i = 0; i < e; i++){
                Set<String> old = res;
                res = prod(res,s1,len);
                if (old.equals(res)) break;     // no change
                if (res.size() >= 20) break;    // limit size of result
            }
        }
        return res;
    }


    // ---------- Stress test -----------------

    /**
     * Test the matching of REs generated randomly and texts generated randomly from them.
     * Use all the algorithms.
     */

    private static void stressTest(){
        measureFrequency();
        Re regex = new Re();
        for (int i = 0; i < 10; i++){
            for (int j = 0; j < 10; j++){
                AstNode ast = regex.buildREbyLength((i+1)*10-4);
                String re = removeSpaces(ast.toRE(true));
                String[] samples = regex.allTexts(ast,50).toArray(new String[0]);
                String txt = "";
                for (int k = 0; k < samples.length; k++){
                    txt = samples[k];
                    if (txt.length() > 0) break;
                }

                //System.out.printf("--%s-- RE: %s text: |%s|\n",i,re,txt);
                testSpeed(i,re,txt,1,1,"bpj",false,1);
                //System.out.printf("--%s-- done\n",i);
            }
        }
    }


    // ---------- Benchmarking -----------------

    /* Benchmark with random REs and texts
     *
     * A number of REs of increasing length are created, and texts for them of random lengths.
     * The REs are placed in buckets of increasing length range: 0:10, 10:20, ...
     * For each RE, the texts are placed in buckets of increasing text length range.
     * All algorithms are then run on each RE and text bucket, and the measured times are placed
     * in a matrix that mirror the text buckets (each cell holds the total time of a text
     * bucket, a value for each of the algorithms).
     * Then, the times for a same RE and all its texts are summed up to produce the speed vs
     * length chart, and the times of a same text length range of all REs are summed up
     * to produce the speed bs text length chart.
     * This allows to chart the behaviour of algorithms for increasing lengths and text
     * lengths.
     *
     *   buckets dir   RE bucket   corresp.texts   texts for RE  text buckets   texts
     *   .------.     .-----.        .------.       .-----.       .------.      .------.
     *   | b1 --+---->| RE1 |        | b1 --+------>| RE1-+------>| tb1 -|----->| text |
     *   |------|     | RE2 |        |------|       | RE2 |       | tb2  |      | text |
     *
     *
     * The buckets for the REs are allocated, and then for each bucket, REs are generated
     * randomly for the length range of the bucket range and placed in their bucket, if not full.
     * Then texts are generated for each RE and placed in buckets.
     * In order to generate texts of increasing lengths each RE has been enclosed into a ()*.
     * The generation of texts out of ASTs is not a simple problem when there is a need
     * to limit their length.
     * I made a first attempt implementing a method that provided the next text generated
     * by an ast within a maximum given length. This turned out to be quite involved.
     * Then, I implemented the addTexts method, that instead provides all the texts within
     * a limited length together.
     * The limit must be short, otherwise a great number of texts are generated.
     * E.g. with a limit of 10, in some cases 1M texts are generated.
     * There is then a need to generate texts with a small limit and then to concatenate
     * them into longer string.
     * The concatenation of texts into longer strings for the buckets is also not a simple issue.
     * The thing is that there are a lot of combinations of concatenations that can provide
     * the desired length.
     * In a bucket we must place strings that fall within its range. The samples are at most
     * long 10 (or 11), so I concatenate them randomly until I surpass the lower bound of
     * the bucket at hand; then I add a random number of samples, from 0 to 9, refraining to
     * do it when a new concatenation would make the resulting string be longer than the upper
     * bound of the range.
     *
     * Measured values
     *
     * This is a picture of the values measured and how they are summed up:
     *
     *  REs                  texts buckets
     *  bucket 0    text bucket 0   text bucket 1 ...    text bucket 9
     *     0.0      0.0.0           0.0.1                0.0.9         |
     *     0.1      0.1.0           0.1.1                0.1.9          \  speed vs RE length 0..9
     *     ...      ...             ...                  ...            /
     *     0.9      0.9.0           0.9.1                0.9.9         |
     *  bucket 1
     *     1.0      1.0.0           1.0.1                1.0.9         |
     *     1.1      1.1.0           1.1.1                1.1.9          \  speed vs RE length 10..19
     *     ...      ...             ...                  ...            /
     *     1.9      1.9.0           1.9.1                1.9.9         |
     *  ...                                                            ...
     *              ---.  .---      ---.  .---
     *                  \/              \/
     * speed vs text length 0..99    length 100..199      ...
     *
     * The parsing time of whole text buckets is measured, and if one of the parses takes too much
     * time the whole bucket is deemed invalid. This is so because the speed for it could not
     * be computed not knowing the lengths of texts parsed successfully.
     * In order to avoid to run parses that will fail because of time, when a parse takes longer
     * than a threshold, the parses for higher text buckets stops.
     * The rationale is that if a measure fails, then it is likely that all the ones at its right
     * will fail too since they are more complex.
     *
     * Having first removed the rows that are all invalid, only the first full columns are taken
     * to produce charts. Something slightly better could be done:
     *
     *  - speed vs RE length: take all the valid values in the block of its rows, and stop
     *    at the first row block full of invalid values
     *  - speed vs text length: take all the values in each text bucket (column) and stop
     *    at the first column full of invalid values
     *  - speed vs RE length, one curve for each text length: take all the valid values
     *    in each block of cells and stop each curve at the first block full of invalid values
     *
     * I can apply it to the single measure or to the block (rows, columns, cells, depending on
     * the measure), and can stop when all the values in it are invalid or when one,
     * or 50% are invalid. However, the charts would not be much better because the values
     * obtained would be rather small as to be not significant.
     */

    /** The number of RE buckets. */
    private static final int RE_BUCKETS = 10;

    /** The number of REs for each RE bucket. */
    private static final int RE_SAMPLES = 100;

    /** The number of text buckets for each RE. */
    private static final int TEXT_BUCKETS = 10;

    /** The number of texts for each text bucket. */
    private static final int TEXT_SAMPLES = 10;

    /** The unit length of texts. */
    private static final int TEXT_SAMPLES_LENGTH = 100;

    /** A reusable array to concatenate sample texts recording their indexes. */
    private int[] fillConc = new int[1000];

    /**
     * Fill the text buckets with texts of the RE specified by its AST.
     *
     * @param      tbdir matrix of text buckets
     * @param      ast reference to the root node of the AST
     * @return     <code>true</code> if it succeeds, <code>false</code> otherwise
     */

    /* It generates strings of a maximum length and then picks them up randomly and
     * puts them in buckets, and then picks them sequentially to fill the holes.
     * It ensures that the generated strings can be parsed by java.regex in a time that does
     * not exceed the threshold (it does it here so as to avoid to do it when measuring parse
     * time, which would made the run of measurements longer).
     */

    private boolean fillTextBuckets(String[][] tbdir, AstNode ast){
        boolean res = true;
        int[] tidx = new int[tbdir.length];             // indexes to fill the text buckets
        // allocate the buckets
        for (int k = 0; k < tbdir.length; k++){
            tbdir[k] = new String[10];
        }
        // generate the samples and order them
        String[] samples = allTexts(ast,10).toArray(new String[0]);
        if (samples.length == 1 && samples[0].length() == 0){
            return false;
        }
        // System.out.printf("%s fillTextBuckets RE: %s\n",new Date().toString(),ast.toRE(true));
        Arrays.sort(samples,new Comparator<String>(){
            public int compare(String s1, String s2){
                return s1.length()-s2.length();
            }});
        int firstNotEmpty = -1;
        for (int i = 0; i < samples.length; i++){
            String s = samples[i];
            int len = s.length();
            if (len > 0){
                if (firstNotEmpty < 0) firstNotEmpty = i;
            }
        }

        // System.out.printf("%s ==> %s\n",ast.toRE(true),samples.length);
        // validate the samples
        Re regex = new Re(ALGO_BSP);
        if (!regex.compile(ast.toRE(true))){
            Trc.out.printf("!!! buildText err %s\n",regex.re);
        } else {
            for (int i = 0; i < samples.length; i++){
                // Trc.out.printf("text %s\n",samples[i]);
                if (!regex.match(samples[i])){
                    Trc.out.printf("!!! buildText err %s %s\n",regex.re,samples[i]);
                    traceAst(regex.astRoot);
                    Trc.out.printf(" --- \n");
                    traceAst(ast);
                    System.exit(1);
                }
            }
        }

        int nsamples = tbdir.length * tbdir[0].length;

        int[] conc = this.fillConc;
        Random ran = new Random();
        fill: for (int i = 0; i < tbdir.length; i++){
            int low = i*TEXT_SAMPLES_LENGTH;
            int up = (i+1)*TEXT_SAMPLES_LENGTH;
            int javacnt = 0;
            for (int k = 0; k < tbdir[i].length; k++){
                int len = 0;
                int c = 0;
                int r = 0;
                // concatenate until the lower reached
                for (;len < low;){
                    int idx = ran.nextInt(samples.length-firstNotEmpty) + firstNotEmpty;
                    len += samples[idx].length();
                    conc[c++] = idx;
                }
                // then concatenate a 0:10 random number of strings
                r = ran.nextInt(10);
                for (int l = 0; l < r; l++){
                    int idx = ran.nextInt(samples.length-firstNotEmpty) + firstNotEmpty;
                    if (len + samples[idx].length() >= up) break;
                    len += samples[idx].length();
                    conc[c++] = idx;
                }
                String str = "";
                for (int j = 0; j < c; j++){
                    str += samples[conc[j]];
                }

                // check that the text does not take too much parse time for java
                String text = str;
                if (k == 9){               // last bucket used to make long strings
                    text = rep(str,1000);  // ensure that long texts can be parsed too
                }
                // System.out.printf("java RE: %s text: %s\n",removeSpaces(ast.toRE(true)),text);
                if (!matchJava(removeSpaces(ast.toRE(true)),text)){
                    if (this.errorKind == ERROR_TIME){
                        // Trc.out.printf("!! java text discarded k: %s error %s cnt %s\n",k,this.errorKind,javacnt);
                        if (javacnt++ >= 3){
                            // System.out.printf("too many java tests, aborting\n");
                            // System.exit(1);
                            // Trc.out.printf("!! text too long to parse for java, left empty\n");
                            continue;
                        }
                        k--;
                        continue;
                    }
                }
                tbdir[i][k] = str;
                nsamples--;
                javacnt = 0;
            }
        }
        // System.out.printf("buckets filled\n");

        if (nsamples > 0){
            // Trc.out.printf("!! text buckets not filled, samples missing %s\n",nsamples);
            // System.out.printf("!! text buckets not filled, samples missing %s\n",nsamples);
            res = false;
        }

        // well, at the worst, if there is at least a text in one of the generated buckets,
        // copy it in all its holes and use it

        // check that the texts are in the proper buckets and that there are no empty buckets
        // here we have 10 buckets, each with 10 strings, lengths: 0:99 100:199 ...
        boolean failed = false;
        for (int k = 0; k < tbdir.length; k++){
            String firstNonempty = null;
            for (int l = 0; l < tbdir[k].length; l++){
                if (tbdir[k][l] != null){
                    firstNonempty = tbdir[k][l];
                    break;
                }
            }
            if (firstNonempty == null){
                res = false;
                // Trc.out.printf("!! text bucket %s empty\n",k);
            } else {
                // repair it
                int n = 0;
                for (int l = 0; l < tbdir[k].length; l++){
                    if (tbdir[k][l] == null){
                        tbdir[k][l] = firstNonempty;
                        n++;
                    }
                }
                if (n > 0){
                    // Trc.out.printf("!! text bucket %s repaired %s holes\n",k,n);
                }
            }
            for (int l = 0; l < tbdir[k].length; l++){
                if (tbdir[k][l] == null){
                    continue;
                }
                int len = tbdir[k][l].length();
                if (len < k*(TEXT_SAMPLES_LENGTH) || len >= (k+1)*(TEXT_SAMPLES_LENGTH)){
                    Trc.out.printf("!! text in wrong bucket %s %s: %s\n",k,l,len);
                    failed = true;
                }
            }
        }
        if (failed) System.exit(1);
        return res;
    }

    /** The numeric suffix of the generated sample and chart files. */
    private static final String SNUMBER = "3";

    /** The name of the samples file. */
    private static final String SAMPLES = "samples" + SNUMBER + ".ser";

    /** The numeric suffix of the measures and chart files. */
    private static final String WNUMBER = "6";

    /** The name of the measurements file for 0:1K texts. */
    private static final String MEA100 = "measure100" + WNUMBER + ".ser";

    /** The name of the measurements file for 0:1M texts. */
    private static final String MEA1000 = "measure1000" + WNUMBER + ".ser";

    /** The name of the measurements file for the compilation times. */
    private static final String MEACOMP = "measureComp" + WNUMBER + ".ser";

    /** The name of the measurements file for the memory use. */
    private static final String MEAMEM = "measureMem" + WNUMBER + ".ser";

    /** The name of the charts file. */
    private static final String CHARTMEA = "rechartmea" + WNUMBER;


    /**
     * Run the benchmark.
     */

    private void benchmark(){
        Re regex = new Re();

        // produce the REs and the texts
        System.out.printf("benchmark build REs and texts\n");

        String[][] res = new String[10][];
        String[][][][] texts = new String[res.length][][][];

        boolean serialized = true;
        try {
            FileInputStream fileIn = new FileInputStream(SAMPLES);
            ObjectInputStream in = new ObjectInputStream(fileIn);
            res = (String[][])in.readObject();
            texts = (String[][][][])in.readObject();
            in.close();
            fileIn.close();
            System.out.printf("benchmark samples read, file index: %s\n",SNUMBER);
        } catch(FileNotFoundException exc){
            serialized = false;
        } catch(IOException exc){
            exc.printStackTrace();
            System.out.printf("%s\n",exc);
            System.exit(1);
        } catch(ClassNotFoundException c){
            System.out.printf("samples not found\n");
            System.exit(1);
        }
 
        if (!serialized){
            // build the samples, there is no serialization file
            boolean someNotFilled = false;
            int[] idx = new int[res.length];           // indexes to fill the RE buckets
            for (int i = 0; i < res.length; i++){
                System.out.printf("benchmark RE bucket %s %s\n",i,new Date().toString());
                Trc.out.printf("benchmark build RE bucket %s\n",i);
                res[i] = new String[RE_SAMPLES];
                texts[i] = new String[res[i].length][][];
                boolean filled = false;
                buk: for (int j = 0; j < 1000; j++){
                    String[][] tbdir = null;
                    AstNode ast = null;
                    int c = 0;
                    fillre: {
                        for (int k = 0; k < 100; k++){    // attempt to have a RE with text buckets full
                            AstNode a = null;
                            int m = 0;
                            c = 0;
                            for (; m < 10; m++){
                                a = regex.buildREbyLength((i+1)*10-4);
                                c = removeSpaces(a.toRE(true)).length();
                                if (i*10 <= c && c < (i+1)*10*1.1) break;
                            }
                            ast = a;
                            int cb = (int)c/res.length;
                            if (cb != i){
                                // System.out.printf(".. disc RE %s c: %.2f i: %s comp %s\n",a.toRE(true),c,i,cb);
                                continue buk;             // not for this bucket
                            }
                            // build then the texts
                            tbdir = new String[10][];             // allocate buckets dir
                            if (fillTextBuckets(tbdir,ast)) break fillre;
                        }
                    }
                    // store in bucket (the RE with text buckets full, or the last one
                    String re = removeSpaces(ast.toRE(true));
                    int inbucket = idx[i];
                    res[i][inbucket] = re;
                    idx[i]++;
                    // Trc.out.printf("benchmark REs %s in bucket %s.%s length %s\n",re,i,inbucket,c);
                    texts[i][inbucket] = tbdir;
                    if (idx[i] >= res[i].length){             // bucket full
                        filled = true;
                        break buk;
                    }
                }
                if (!filled){
                    Trc.out.printf("RE bucket %s not filled\n",i);
                    someNotFilled = true;
                }
            }
            if (someNotFilled){
                System.out.printf("some RE buckets not filled\n");
            }

            try {
                FileOutputStream fileOut = new FileOutputStream(SAMPLES);
                ObjectOutputStream out = new ObjectOutputStream(fileOut);
                out.writeObject(res);
                out.writeObject(texts);
                out.close();
                fileOut.close();
            } catch(IOException exc){
                System.out.printf("serialization %s\n",exc);
            }
            System.out.printf("benchmark samplesSanityCheck\n");
            samplesSanityCheck(res,texts);
            System.out.printf("benchmark samplesSanityCheck done\n");
        }
        // samplesSanityCheck(res,texts);
        allFrequency(res,true);

        // measure the number of states of BSP and that of Berry-Sethi
        computeNrStates(res);

        System.out.printf("benchmark -0-\n");

        // measure now parse and compilation time
        int fact[] = new int[]{1,2,5, 10,20,50, 100,200,500, 1000};
        System.out.printf("-1-\n");
        long[][][][] matr100 = bench(MEA100,0,res,texts,false,fact);
        System.out.printf("-2- %s\n",matr100==null);
        long[][][][] matr1000 = bench(MEA1000,0,res,texts,true,fact);
        System.out.printf("-3- %s\n",matr1000==null);
        long[][][][] matrComp = bench(MEACOMP,1,res,texts,false,fact);
        System.out.printf("-4- %s\n",matrComp==null);

        Trc.out.printf("relative standard deviations texts lengths 0:1000\n");
        deviation(matr100,texts,false,fact);
        Trc.out.printf("relative standard deviations texts lengths 0:1M\n");
        deviation(matr1000,texts,true,fact);

        checkMeasures(matr100);
        checkMeasures(matr1000);

        double[] totTime = new double[NRALGOS-1];
        sumTime(matr100,totTime);
        sumTime(matr1000,totTime);
        System.out.printf("total parse time\n");
        for (int i = 0; i < totTime.length; i++){
            System.out.printf("%s: %.0f sec\n",algoNames[i+1],totTime[i]/1000000000);
        }

        long[][][][] matrMem = bench(MEAMEM,2,res,texts,false,fact);
        System.out.printf("-5- %s\n",matrMem==null);
        // produce charts
        try {
            html = new PrintStream(CHARTMEA + ".html");
            tex = new PrintStream(CHARTMEA + ".tex");
        } catch (FileNotFoundException exc){
            Trc.out.printf("charts measurements file error\n");
            System.exit(1);
        }
        html.printf("<!DOCTYPE html>\n");
        html.printf("<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\">\n");
        html.printf("<script>\n");
        try {
            BufferedReader in = new BufferedReader(new FileReader("chart.js"));
            for (;;){
                String line = in.readLine();
                if (line == null) break;
                html.printf("%s\n",line);
            }
            in.close();
        } catch (IOException exc){
            System.out.printf("error on file %s\n",exc);
            System.exit(1);
        }
        html.printf("</script>\n");
        html.printf("</head><body>\n");
        html.printf("<h2>Comparison of RE algorithms</h2>\n");

        html.printf("<h3>Parse speed</h3>\n");
        speedVsReLength(matr100,texts,false,algoLabels,fact,1);
        speedVsReLength(matr1000,texts,true,algoLabels,fact,2);

        speedVsTextLength(matr100,texts,false,algoLabels,fact,3);
        showMeasures(matr100,"short texts",texts,false,fact);
        speedVsTextLength(matr1000,texts,true,algoLabels,fact,4);

        showMeasures(matr1000,"long texts",texts,true,fact);
        speedAlgo(matr100,texts,false,fact,10);
        speedAlgo(matr1000,texts,true,fact,20);

        speedAlgoRe(matr100,texts,false,fact,300);
        speedAlgoRe(matr1000,texts,true,fact,40);

        html.printf("<h3>Compilation speed</h3>\n");
        speedVsReLength(matrComp,texts,false,algoLabels,fact,100);

        // chart memory usage
        html.printf("<h3>Memory usage</h3>\n");
        memoryVsTextLength(matrMem,texts,algoLabels,fact,200);
        //showMeasuresPlan(matrComp);
        html.printf("</body></html>\n");
        html.close();

        tex.close();

        /*
        long tottim = 0;
        for (int b = 0; b < NRALGOS-1; b++){
            int nre = 0;
            long tim = 0;
            long tima = 0;
            long timd = 0;
            for (int i = 0; i < res.length; i++){
                for (int j = 0; j < res[i].length; j++){    // visit bucket for a length
                    regex = new Re(b+1);
                    long t0 = getCycles();
                    boolean resu = regex.compile(res[i][j]);
                    long ti = getCycles() - t0 - CYCLE_BASE;
                    if (ti <= 0){
                        System.out.printf("!! bad measure\n");
                        ti = 0;
                    }
                    if (!resu){
                        System.out.printf("error %s compile RE: %s\n",algoNames[b+1],res[i][j]);
                    }
                    tim += ti;
                    tottim += ti;
                    nre++;
                }
            }
            System.out.printf("compile %s %s REs time %.2f\n",
                algoNames[b+1],nre,(double)tim*0.45/1000000.0);
        }
        System.out.printf("tottim %s\n",tottim);
        */
    }

    /**
     * Compute the BSP and the BS number of states on all the REs in the samples.
     *
     * @param      res matrix of REs
     * @param      texts matrix of texts
     */

    private void computeNrStates(String[][] res){
        int nrBSP = 0;
        int nrBS = 0;
        int relength = 0;
        for (int i = 0; i < res.length; i++){
            if (res[i] == null) continue;
            for (int j = 0; j < res[i].length; j++){
                if (res[i][j] == null) continue;
                this.re = res[i][j];
                relength += this.re.length();
                buildAst();
                if (!this.error) bsdfa = buildBS(astRoot);
                if (getsym() != -1){      // whole re not consumed
                    System.out.printf("error in RE %s\n",this.re);
                    break;
                }
                nrBSP += bsdfa.stateNr;

                buildAst(false);
                if (!this.error) bsdfa = buildBS(astRoot,false);
                if (getsym() != -1){      // whole re not consumed
                    System.out.printf("error in RE %s\n",this.re);
                    break;
                }
                nrBS += bsdfa.stateNr;
            }
        }
        System.out.printf("nr of states, BSP: %s BS: %s tot RE lengths: %s\n",nrBSP,nrBS,relength);
    }

    /**
     * Run the sanity check of REs and texts.
     *
     * @param      res matrix of REs
     * @param      texts matrix of texts
     */

    /* Sanity checks aims to check that all texts are parsed and the same tree obtained in all
     * algorithms. Thus, it makes the algorithm produce the linearized parse trees (which
     * testSpeedCompParse() does not). Thus, it takes more time to run than the measurements.
     * Moreover, time supervision is disabled here (the parses are done here in reasoneable
     * time).
     * Sanity checks are not done on long texts because they are repetitions of short texts,
     * which are also repetitions of texts. So, the likeness to detect errors that are not
     * detected by parsing short texts is rather low.
     */

    private void samplesSanityCheck(String[][] res, String[][][][] texts){
        int totnre = 0;
        for (int i = 0; i < res.length; i++){
            if (res[i] == null) continue;
            for (int j = 0; j < res[i].length; j++){
                if (res[i][j] != null) totnre++;
                // Trc.out.printf("%s.%s %s len %s\n",i,j,res[i][j],res[i][j].length());
            }
        }
        Trc.out.printf("sanity check of buckets RE %s and text buckets %s, nr. REs %s\n",
            res.length,texts.length,totnre);
        boolean someBadSample = false;
        int empty = 0;
        int tottxt = 0;
        int totambig = 0;
        int totambigtxt = 0;
        for (int i = 0; i < texts.length; i++){
            if (res[i] == null) continue;
            //Trc.out.printf("RE bucket %s REs: %s\n",i,res[i].length);
            int ambig = 0;
            int ambigtxt = 0;
            int nre = 0;
            int ntxt = 0;
            for (int j = 0; j < texts[i].length; j++){
                if (res[i][j] != null) nre++;
                Re r = new Re();
                r.timeSupervise = false;
                r.re = res[i][j];
                if (res[i][j] == null){
                    someBadSample = true;
                    Trc.out.printf("!! RE null bucket %s %s\n",i,j);
                    continue;
                }
                r.buildAst();
                if (r.error){
                    someBadSample = true;
                    Trc.out.printf("!! RE %s ast error\n",r.re);
                    continue;
                }
                int ln = removeSpaces(r.astRoot.toRE(true)).length();
                BStateTable dfa = r.buildBS(r.astRoot);
                if (r.error || r.getsym() != -1){
                    someBadSample = true;
                    Trc.out.printf("!! RE %s dfa error\n",r.re);
                    continue;
                }
                this.error = false;
                PATStateTable nfa = r.buildPAT(r.astRoot);
                if (r.error){
                    someBadSample = true;
                    Trc.out.printf("!! RE %s pat nfa error\n",r.re);
                    continue;
                }
                this.error = false;
                EarleyTables tab = r.astToTables(r.astRoot);
                if (dfa.isAmbig) ambig++;
                if (ln < i*10 || ln >= (i+1)*10*1.1){
                    someBadSample = true;
                    Trc.out.printf("!! RE %s not in bucket %s %s: %s\n",r.re,i,j,ln);
                }
                //Trc.out.printf("  RE %s.%s len: %s\n",i,j,ln);
                String[][] tbdir = texts[i][j];
                //Trc.out.printf("  RE %s.%s text buckets %s\n",i,j,tbdir.length);
                for (int k = 0; k < tbdir.length; k++){
                    if (tbdir[k] == null) continue;
                    // Trc.out.printf("  text bucket %s.%s.%s texts %s\n",i,j,k,tbdir.length);
                    for (int l = 0; l < tbdir[k].length; l++){
                        int len = 0;
                        if (tbdir[k][l] == null){
                            Trc.out.printf("!! text empty in bucket %s.%s.%s.%s\n",i,j,k,l);
                            empty++;
                            continue;
                        } else {
                            len = tbdir[k][l].length();
                        }
                        tottxt++;
                        ntxt++;
                        if (len < k*TEXT_SAMPLES_LENGTH || len >= (k+1)*TEXT_SAMPLES_LENGTH*1.1){
                            someBadSample = true;
                            Trc.out.printf("!! text not in bucket %s.%s.%s.%s: %s\n",i,j,k,l,len);
                        }
                        // check ambiguity of string
                        String treebsp = null;
                        String treepat = null;
                        String treeearley = null;
                        System.out.printf("sanity bsp %s.%s.%s.%s %s\n",i,j,k,l,new Date().toString());
                        r.match(tbdir[k][l],dfa,true,1);
                        if (r.error){
                            someBadSample = true;
                            Trc.out.printf("!! match bsp error in bucket %s.%s.%s.%s\n",i,j,k,l);
                        } else {
                            treebsp = r.tree.toString(r.astMap,r.treeLen);
                        }
                        if (r.ambiguous) ambigtxt++;
                        System.out.printf("sanity bsp done, now pat %s\n",new Date().toString());
                        r.matchPAT(tbdir[k][l],nfa,true);
                        if (r.error){
                            if (r.errorKind == ERROR_NOMATCH){
                                someBadSample = true;
                                Trc.out.printf("!! match pat error in bucket %s.%s.%s.%s\n",i,j,k,l);
                            }
                        } else {
                            treepat = r.tree.toString(r.astMap,r.treeLen);
                            if (!treepat.equals(treebsp)){
                                someBadSample = true;
                                Trc.out.printf("!! match pat tree error in bucket %s.%s.%s.%s\n",i,j,k,l);
                                Trc.out.printf("    bsp %s\n",treebsp);
                                Trc.out.printf("    pat %s\n",treepat);
                            }
                        }
                        System.out.printf("sanity pas done, now earley %s\n",new Date().toString());
                        treeearley = r.earleyParse(tbdir[k][l],tab,true);
                        if (treeearley == null){
                            if (r.errorKind == 0){
                                someBadSample = true;
                                Trc.out.printf("!! match earley error in bucket %s.%s.%s.%s\n",i,j,k,l);
                            } else if (r.errorKind == ERROR_TIME){
                                Trc.out.printf("!! match earley timeout error in bucket %s.%s.%s.%s\n",i,j,k,l);
                            }
                        } else {
                            if (!treeearley.equals(treebsp)){
                                someBadSample = true;
                                Trc.out.printf("!! match earley tree error in bucket %s.%s.%s.%s\n",i,j,k,l);
                                Trc.out.printf("    bsp %s\n",treebsp);
                                Trc.out.printf("    earley %s\n",treeearley);
                            }
                        }
                        System.out.printf("sanity done %s\n",new Date().toString());
                        if (someBadSample){
                            System.out.printf("!! error %s re %s text %s\n",r.errorKind,res[i][j],tbdir[k][l]);
                            System.exit(1);
                        }
                    }
                }
            }
            Trc.out.printf("RE bucket %s REs: %s ambig %s texts: %s ambig %s\n",
                i,nre,ambig,ntxt,ambigtxt);
            totambig += ambig;
            totambigtxt += ambigtxt;
        }
        Trc.out.printf("tot REs %s ambig: %s%% tot texts %s ambig: %s%%\n",
            totnre,totambig*100/totnre,tottxt,totambigtxt*100/tottxt);
        if (totnre != RE_BUCKETS*RE_SAMPLES){
            Trc.out.printf("!! bad number of REs, it should be %s\n",RE_BUCKETS*RE_SAMPLES);
        }
        if (tottxt != RE_BUCKETS*RE_SAMPLES*TEXT_BUCKETS*TEXT_SAMPLES){
            Trc.out.printf("!! bad number of texts, it should be %s\n",
                RE_BUCKETS*RE_SAMPLES*TEXT_BUCKETS*TEXT_SAMPLES);
        }
        if (empty > 0){
            someBadSample = true;
            Trc.out.printf("!! %s empty text samples of %s\n",empty,tottxt);
        }
        if (someBadSample){
            System.out.printf("some bad samples\n");
        }

    }

    /**
     * Check that the specified matrix of measurements has the proper number of elements.
     *
     * @param      matr matrix
     */

    private static void checkMeasures(long[][][][] matr){
        for (int b = 0; b < NRALGOS-1; b++){
            int n = 0;
            int ni = -1;
            int nj = -1;
            int nk = -1;
            for (int i = 0; i < matr.length; i++){                 // visit REs buckets
                if (ni < 0){
                    ni = matr[i].length;
                } else {
                    if (matr[i].length != ni){
                        Trc.out.printf("checkMeasures first level %s %s measures: %s\n",algoNames[b+1],i,ni,matr[i].length);
                    }
                }
                for (int j = 0; j < matr[i].length; j++){          // for each re
                    if (nj < 0){
                        nj = matr[i][j].length;
                    } else {
                        if (matr[i][j].length != nj){
                            Trc.out.printf("checkMeasures second level %s %s %s measures: %s\n",algoNames[b+1],i,j,nj,matr[i][j].length);
                        }
                    }
                    for (int k = 0; k < matr[i][j].length; k++){   // visit text buckets
                        if (nk < 0){
                            nk = matr[i][j][k].length;
                        } else {
                            if (matr[i][j][k].length != nk){
                                Trc.out.printf("checkMeasures third level %s %s %s %s measures: %s\n",algoNames[b+1],i,j,k,nk,matr[i][j][k].length);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Roll up the values of the measurements matrix for a same algorithm into the
     * specified vector.
     *
     * @param      matr matrix
     * @param      totTime vector
     */

    private void sumTime(long[][][][] matr, double[] totTime){
        for (int b = 0; b < NRALGOS-1; b++){
            for (int i = 0; i < matr.length; i++){                 // visit REs buckets
                for (int j = 0; j < matr[i].length; j++){          // for each re
                    for (int k = 0; k < matr[i][j].length; k++){   // visit text buckets
                        if (matr[i][j][k][b] < Long.MAX_VALUE-1){  // valid
                            totTime[b] += matr[i][j][k][b];
                        }
                    }
                }
            }
        }
    }

    /**
     * Trace the specified measurements matrix.
     *
     * @param      matr matrix
     * @param      msg message printed as heading
     * @param      texts texts
     * @param      largeTexts <code>true</code> if texts 0:1M
     * @param      fact vector telling the number of times texts are concatenated for each bucket
     */

    /* The measurements matrix has the following form:
     *
     *  RE buckets directory
     *                      RE buckets   time for text buckets  algorithms
     *       ----------     --------     ------------------     --------
     *       |  0:10 -+---->|  RE -+---->| text bucket 0 -+-----| BSP  |
     *       |--------|     |------|     |----------------|     | BSPP |
     *       | 10:20  |     | .... |     |     ...        |     | .... |  
     *       |  ....  |
     *
     */

    private void showMeasures(long[][][][] matr, String msg, String[][][][] texts,
        boolean largeTexts, int fact[]){
        Trc.out.printf("%s\n",msg);
        for (int b = 0; b < NRALGOS-1; b++){
            Trc.out.printf("%s\n",algoNames[b+1]);
            for (int i = 0; i < matr.length; i++){                 // visit REs buckets
                boolean reBucketFull = true;
                buc: for (int j = 0; j < matr[i].length; j++){     // for each re
                    for (int k = 0; k < matr[i][j].length; k++){   // visit measures of text buckets
                        if (matr[i][j][k][b] >= Long.MAX_VALUE-1){
                            reBucketFull = false;
                            break buc;
                        }
                    }
                }
                if (reBucketFull){
                    Trc.out.printf("RE %s.*",i);
                    for (int k = 0; k < matr[i][0].length; k++){   // visit text buckets
                        Trc.out.printf("\t%s",speed(matr,i,-1,k,b,texts,largeTexts,fact));
                    }
                    Trc.out.printf("\n");
                    continue;
                }
                for (int j = 0; j < matr[i].length; j++){          // for each re
                    boolean reFull = true;
                    for (int k = 0; k < matr[i][j].length; k++){   // visit text buckets
                        if (matr[i][j][k][b] >= Long.MAX_VALUE-1){
                            reFull = false;
                            break;
                        }
                    }
                    if (reFull){
                        Trc.out.printf("RE %s.%s",i,j);
                        for (int k = 0; k < matr[i][0].length; k++){   // visit text buckets
                            Trc.out.printf("\t%s",speed(matr,i,j,k,b,texts,largeTexts,fact));
                        }
                        Trc.out.printf("\n");
                        continue;
                    }
                    Trc.out.printf("RE %s.%s",i,j);
                    for (int k = 0; k < matr[i][j].length; k++){   // visit text buckets
                        if (matr[i][j][k][b] == Long.MAX_VALUE-1){
                            Trc.out.printf("\t-");
                        } else if (matr[i][j][k][b] == Long.MAX_VALUE){
                            Trc.out.printf("\t!");
                        } else {
                            Trc.out.printf("\t%s",speed(matr,i,j,k,b,texts,largeTexts,fact));
                        }
                    }
                    Trc.out.printf("\n",i,j);
                }
            }
        }
    }

    /**
     * Compute the parse speed of the texts in the specified text bucket for the specified
     * RE or of all the REs in the specified text bucket (j < 0).
     *
     * @param      matr matrix of measurements
     * @param      i number of RE bucket
     * @param      j index of RE in its bucket
     * @param      k number of text bucket
     * @param      b number of the algorithm
     * @param      texts matrix of texts
     * @param      largeTexts <code>true</code> if texts 0:1M
     * @param      fact vector telling the number of times texts are concatenated for each bucket
     * @return     speed
     */

    private int speed(long[][][][] matr, int i, int j, int k, int b, String[][][][] texts,
        boolean largeTexts, int fact[]){
        long tim = 0;
        long tok = 0;
        int n = 0;
        if (j >= 0){              // single RE
            n = j+1;
        } else {
            j = 0;                // all RE in bucket
            n = matr[i].length;
        }
        for (; j < n; j++){       // for each re, or a single one
            long v = matr[i][j][k][b];
            if (v >= Long.MAX_VALUE-1) continue;
            tim += v;
            if (tim < 0) tim = Long.MAX_VALUE;
            for (int l = 0; l < texts[i][j][k].length; l++){
                if (largeTexts){
                    String s = texts[i][j][9][l];
                    tok += (s == null ? 0 : s.length()) * fact[k];
                } else {
                    String s = texts[i][j][k][l];
                    tok += s == null ? 0 : s.length();
                }
            }
        }
        double val = 0;
        if (tim > 0) val = (double)tok/((double)tim*0.45/1000000.0);  // cycles to ns
        if (tim == Long.MAX_VALUE) val = 0;
        return (int)val;
    }

    /**
     * Compute the relative standard deviation of the values for the same text bucket across
     * the REs of the same RE bucket (for each algorithm).
     *
     * @param      matr matrix of measurements
     * @param      texts matrix of texts
     * @param      largeTexts <code>true</code> if texts 0:1M
     * @param      fact vector telling the number of times texts are concatenated for each bucket
     */

    private void deviation(long[][][][] matr, String[][][][] texts, boolean largeTexts, int[] fact){
        int[] maxCol = significantColumns(matr);
        for (int b = 0; b < NRALGOS-1; b++){
            Trc.out.printf("%s rel stddev\n",algoNames[b+1]);
            // System.out.printf("%s",algoNames[b+1]);
            for (int i = 0; i < matr.length; i++){
                Trc.out.printf("\t%s",i);
                int rown = 0;
                double rowsumy = 0;
                double rowsumyy = 0;
                for (int k = 0; k < maxCol[b]; k++){
                    int n = 0;
                    double sumy = 0;
                    double sumyy = 0;
                    for (int j = 0; j < matr[i].length; j++){    // visit RE bucket
                        long v = matr[i][j][k][b];
                        if (v >= Long.MAX_VALUE-1){              // measure not done or invalid
                            continue;
                        }
                        long tok = 0;
                        for (int l = 0; l < texts[i][j][k].length; l++){
                            if (largeTexts){
                                String s = texts[i][j][9][l];
                                tok += (s == null ? 0 : s.length()) * fact[k];
                            } else {
                                String s = texts[i][j][k][l];
                                tok += s == null ? 0 : s.length();
                            }
                        }
                        double val = 0;
                        if (v > 0) val = (double)tok/((double)v*0.45/1000000.0); // cycles to ns
                        n++;
                        sumy += val;
                        sumyy += val * val;
                    }
                    double average = sumy / n;
                    double variance = sumyy/n - average*average;
                    if (variance < 0) variance = 0.0;    // this happens because of rounding errors
                    double relStdDev = Math.sqrt(variance)/average;
                    Trc.out.printf("\t%.2f",relStdDev);
                    rown++;
                    rowsumy += relStdDev;
                    rowsumyy += relStdDev * relStdDev;
                }
                double rowaverage = rowsumy / rown;
                double rowvariance = rowsumyy/rown - rowaverage*rowaverage;
                if (rowvariance < 0) rowvariance = 0.0;    // this happens because of rounding errors
                double rowrelStdDev = Math.sqrt(rowvariance)/rowaverage;
                Trc.out.printf("\tave %.2f dev %.2f\n",rowaverage,rowrelStdDev);
                // System.out.printf("\t%.2f",rowrelStdDev);
            }
            // System.out.printf("\n");
        }
    }

    /**
     * Get the measurements for the specified samples reading them from the specified
     * serialization file or running them if the file does not exist.
     *
     * @param      fname name of the measurements file
     * @param      what 0 to measure parse times, 1 to measure compilation times, 2 to measure memory
     * @param      res matrix of REs
     * @param      texts matrix of texts
     * @param      largeTexts <code>true</code> if texts 0:1M
     * @param      fact vector telling the number of times texts are concatenated for each bucket
     * @return     matrix of measurements
     */

    private long[][][][] bench(String fname, int what, String[][] res, String[][][][] texts,
        boolean largeTexts, int[] fact){
        long[][][][] matr = null;
        boolean serialized = true;
        try {
            FileInputStream fileIn = new FileInputStream(fname);
            ObjectInputStream in = new ObjectInputStream(fileIn);
            matr = (long[][][][])in.readObject();
            in.close();
            fileIn.close();
        } catch(FileNotFoundException exc){
            serialized = false;
        } catch(IOException exc){
            exc.printStackTrace();
            System.out.printf("%s\n",exc);
            System.exit(1);
        } catch(ClassNotFoundException c){
            System.out.printf(fname + " not found\n");
            System.exit(1);
        }
        if (!serialized){
            // measure, there is no serialization file
            System.out.printf("benchmark %s\n",fname);
            if (what <= 1){
                matr = benchMeasure(what==1,res,texts,largeTexts,fact);
            } else {
                matr = benchMeasureMem(res,texts,fact);
            }
            try {
                FileOutputStream fileOut = new FileOutputStream(fname);
                ObjectOutputStream out = new ObjectOutputStream(fileOut);
                out.writeObject(matr);
                out.close();
                fileOut.close();
            } catch(IOException exc){
                System.out.printf("serialization %s\n",exc);
            }
        }
        return matr;
    }



    /**
     * Run all algorithms and record the compilation if comptime is true, otherwise
     * the parse time only. Each RE is compiled and all its tests are parsed.
     *
     * @param      comptime <code>true</code> to measure compilation times
     * @param      res matrix of REs
     * @param      texts matrix of texts
     * @param      largeTexts <code>true</code> if texts 0:1M
     * @param      fact vector telling the number of times texts are concatenated for each bucket
     * @return     matrix of measurements
     */

    private long[][][][] benchMeasure(boolean comptime, String[][] res, String[][][][] texts,
        boolean largeTexts, int[] fact){
        nomatchErrors = 0;
        int complnum = texts.length;
        // allocate matrix for measures
        long[][][][] matr = new long[complnum][][][];
        // the leaves elements contain the values for the algos
        for (int i = 0; i < matr.length; i++){                    // for all REs buckets
            matr[i] = new long[res[i].length][][];
            for (int j = 0; j < matr[i].length; j++){             // for all REs in bucket
                if (comptime){
                    matr[i][j] = new long[1][];
                } else {
                    matr[i][j] = new long[texts[i][j].length][];
                }
                for (int k = 0; k < matr[i][j].length; k++){      // for all its text buckets
                    matr[i][j][k] = new long[NRALGOS-1];
                    // fill it with a value that means "measure not done", which
                    // helps to finding invalid measures (this value would not be
                    // seen as an invalid value)
                    Arrays.fill(matr[i][j][k],Long.MAX_VALUE-1);
                }
            }
        }

        int nsamples = RE_BUCKETS * RE_SAMPLES * TEXT_BUCKETS;
        for (int m = 0; m < 3; m++){
            int nmea = 0;
            for (int i = 0; i < matr.length; i++){                  // for each re bucket
                String algos = "bcepj";
                System.out.printf("run %s %s benchmark RE bucket %s %s %s\n",m,new Date().toString(),i,comptime,largeTexts);
                Trc.out.printf("run %s %s benchmark RE bucket %s %s %s\n",m,new Date().toString(),i,comptime,largeTexts);
                int[] errkinds = new int[5];
                for (int j = 0; j < matr[i].length; j++){           // for all REs in bucket
                    // System.out.printf("  RE %s.%s %s %s\n",i,j,res[i][j],largeTexts);
                    // parse strings long 100, 500, 1000, 5000, ...
                    // System.out.printf(" %s..",j);
                    for (int k = 0; k < matr[i][j].length; k++){    // for all its text buckets
                        String[] t = null;
                        if (largeTexts){
                            t = new String[texts[i][j][9].length];
                            for (int l = 0; l < t.length; l++){
                                t[l] = rep(texts[i][j][9][l],fact[k]);
                            }
                        } else {
                            t = texts[i][j][k];
                        }
                        // System.out.printf("  parse k %s %s\n",k,algos);
                        testSpeedCompParse(0,res[i][j],t,matr[i][j][k],algos,comptime,errkinds);
                        for (int b = 0; b < matr[i][j][k].length; b++){
                            long tm = matr[i][j][k][b];
                            if (tm == Long.MAX_VALUE-1) continue;
                            if (tm >= maxMatchTime){      // too big or error, do not measure further
                                if (!comptime){
                                    algos = algos.replaceAll("bcpej".substring(b,b+1),"");
                                    System.out.printf("%s ended %s text bucket %s\n",algoNames[b+1],res[i][j],k);
                                    Trc.out.printf("%s ended %s text bucket %s time: %s max: %s\n",
                                        algoNames[b+1],res[i][j],k,tm,maxMatchTime);
                                    matr[i][j][k][b] = Long.MAX_VALUE-1;
                                }
                            }
                        }

                        nmea++;
                        // System.out.printf("  end parse k %s %s\n",k,algos);
                        // for (int a = 0; a < matr[i][j][k].length; a++){
                        //     if (matr[i][j][k][a] == Long.MAX_VALUE){
                        //         Trc.out.printf("  no %s %s.%s.%s\n",algoNames[a+1],i,j,k);
                        //     }
                        // }
                        // System.out.printf(" %s.%s",j,k);
                        // show progress meter
                        // System.out.printf(".");
                        // System.out.printf("\b%s",k);
                        // show the percentage of measures done
                        System.out.printf("\r%.2f%%",(double)nmea*100/nsamples);
                        Trc.out.printf(" %s.%s",j,k);
                    }
                }
                System.out.printf(" nmea: %s\n",nmea);
                Trc.out.printf("\n");
                boolean err = false;
                for (int j = 0; j < errkinds.length; j++){
                    if (errkinds[j] > 0){
                        err = true;
                        break;
                    }
                }
                if (err){
                    Trc.out.printf("RE bucket %s errors %s\n",i,Arrays.toString(errkinds));
                    System.out.printf("RE bucket %s errors %s\n",i,Arrays.toString(errkinds));
                }
            }
        }
        if (!comptime){
            // find invalid measures
            boolean invalidMeasures = false;
            Trc.out.printf("invalid measures\n");
            for (int b = 0; b < NRALGOS-1; b++){
                for (int i = 0; i < matr.length; i++){                 // visit REs buckets
                    for (int j = 0; j < matr[i].length; j++){          // for each re
                        for (int k = 0; k < matr[i][j].length; k++){   // visit text buckets
                            if (matr[i][j][k][b] == Long.MAX_VALUE){
                                invalidMeasures = true;
                                Trc.out.printf("%s RE bucket %s.%s text bucket %s\n",
                                    algoNames[b+1],i,j,k);
                            }
                        }
                    }
                }
            }
            if (invalidMeasures){
                System.out.printf("some invalid measures\n");
            }
        }
        if (nomatchErrors > 0){
            System.out.printf("!!! %s texts not matched\n",nomatchErrors);
        }
        return matr;
    }

    /** The number of unsuccessful matches. */
    private static int nomatchErrors;

    /**
     * Measure the compilation time or that of the parsing of all the texts in the
     * specified text bucket.
     *
     * @param      t number of the test case
     * @param      re RE
     * @param      texts text bucket
     * @param      matr vector in which the measurements of the algorithms are returned
     * @param      algos set of algorithms to run
     * @param      comp <code>true</code> to measure compilation times
     * @param      errkinds vector of number of errors of each kind
     */

     /* It sets maxMatchTime to 10 times its normal value, which is good also for long texts
      * (10 times because it parses 10 strings, the ones in each text bucket).
      * Then benchMeasure tests if its result is higher than the threshold. This test is done
      * in addition to time supervision because java.regex does not have time supervision, and
      * (having first ensured that it does not run forever), the check that the actual parse
      * time does not exceed a threshold must be done again and the decision to proceed with
      * the parses of the subsequent text buckets then made.
      * It could be possible to set instead a time supervision or a threshold that depends on
      * the max text lengths of the bucket, and the minimum significant speed.
      * Let's take a lowest speed of 100 chars/msec. For length = 1M, time = 1000000 / 100 =
      * 10^4 msec, 10 sec. Now, maxMatchTime is set to 45, which is much more than all the
      * parses done. This is simpler.
      */

    private void testSpeedCompParse(int t, String re, String[] texts,
        long[] matr, String algos, boolean comp, int[] errkinds){
        boolean error = false;
        for (int i = 0; i < algos.length(); i++){
            int algo = 0;
            switch (algos.charAt(i)){
            case 'b': algo = ALGO_BSP; break;
            case 'c': algo = ALGO_BSPP; break;
            case 'p': algo = ALGO_PAT; break;
            case 'e': algo = ALGO_EARLEY; break;
            case 'j': algo = ALGO_JAVA; break;
            }
            //System.out.printf("    %s text len %s\n",algoNames[algo],texts[0].length());
            System.gc();
            long t0 = 0;
            long t1 = 0;
            long ti = 0;
            Re regex = null;
            boolean res = true;
            if (comp){                 // compile only
                regex = new Re(algo);
                if (algo == ALGO_JAVA){
                    re = re.replaceAll("\\[","(");
                    re = re.replaceAll("\\]",")?");
                }
                for (int k = 0; k < 10; k++){
                    t0 = getCycles();
                    res = regex.compile(re);
                    t1 = getCycles();
                    ti = t1 - t0 - CYCLE_BASE;
                    if (ti > 0) break;
                }
                if (ti <= 0){
                    System.out.printf("!! bad measure\n");
                    ti = 0;
                }
                if (ti < matr[algo-1]) matr[algo-1] = ti;
                if (!res){
                    Trc.out.printf("error %s compile RE: %s\n",algoNames[algo],re);
                    error = true;
                }
            } else {
                //System.out.printf("    compile\n");
                regex = new Re(algo);
                if (algo == ALGO_JAVA){
                    re = re.replaceAll("\\[","(");
                    re = re.replaceAll("\\]",")?");
                }
                res = regex.compile(re);
                if (!res){
                    Trc.out.printf("error %s compile RE: %s\n",algoNames[algo],re);
                    error = true;
                } else {
                    long saveMaxMatchTime = maxMatchTime;
                    maxMatchTime *= 10;
                    mea: for (int k = 0; k < 10; k++){
                        t0 = getCycles();
                        regex.matchStartTime = t0;
                        for (int j = 0; j < texts.length; j++){
                            if (texts[j] == null) continue;
                            //Trc.out.printf("    %s re %s text len %s: %s\n",algoNames[algo],re,texts[j].length(),texts[j]);
                            if (!regex.match(texts[j])){
                                if (regex.errorKind == ERROR_NOMATCH){
                                    nomatchErrors++;
                                    Trc.out.printf("%s error %s match RE: %s, text |%s| len: %s\n",
                                         algoNames[algo],regex.errorKind,re,abbrev(texts[j]),texts[j].length());
                                }
                                error = true;
                                ti = Long.MAX_VALUE;
                                errkinds[regex.errorKind]++;
                                break mea;
                            }
                        }
                        t1 = getCycles();
                        ti = t1 - t0 - CYCLE_BASE;
                        if (ti > 0) break;
                    } // mea
                    maxMatchTime = saveMaxMatchTime;
                    if (ti <= 0){
                        System.out.printf("!! bad measure\n");
                        ti = 0;
                    }

                    if (ti < matr[algo-1]){
                        matr[algo-1] = ti;
                    }
                    // a measure that aborts for lack of stack or heap appears as a measure not done
                }
            }
        }
    }

    /**
     * Run all algorithms and record the use of memory. Each RE is compiled and all its
     * tests are parsed.
     *
     * @param      res matrix of REs
     * @param      texts matrix of texts
     * @param      fact vector telling the number of times texts are concatenated for each bucket
     * @return     matrix of measurements
     */

    private long[][][][] benchMeasureMem(String[][] res, String[][][][] texts, int[] fact){
        nomatchErrors = 0;
        // allocate matrix for measures
        long[][][][] matr = new long[texts.length][][][];
        // the leaves elements contain the values for the algos
        for (int i = 0; i < matr.length; i++){                    // for all REs buckets
            matr[i] = new long[res[i].length][][];
            for (int j = 0; j < matr[i].length; j++){             // for all REs in bucket
                matr[i][j] = new long[texts[i][j].length][];
                for (int k = 0; k < matr[i][j].length; k++){      // for all its text buckets
                    matr[i][j][k] = new long[NRALGOS-1];
                    // fill it with a value that means "measure not done", which
                    // helps to finding invalid measures (this value would not be
                    // seen as an invalid value)
                    Arrays.fill(matr[i][j][k],Long.MAX_VALUE-1);
                }
            }
        }

        int nsamples = RE_BUCKETS * RE_SAMPLES * TEXT_BUCKETS;
        int nmea = 0;
        for (int i = 0; i < matr.length; i++){                  // for each re bucket
            String algos = "bcep";
            System.out.printf("%s benchmark RE bucket %s\n",new Date().toString(),i);
            Trc.out.printf("%s benchmark RE bucket %s\n",new Date().toString(),i);
            int[] errkinds = new int[5];
            for (int j = 0; j < matr[i].length; j++){           // for all REs in bucket
                // parse strings long 100, 500, 1000, 5000, ...
                for (int k = 0; k < matr[i][j].length; k++){    // for all its text buckets
                    String[] t = texts[i][j][k];
                    testMemUse(0,res[i][j],t,matr[i][j][k],algos,errkinds);
                    for (int b = 0; b < matr[i][j][k].length; b++){
                        long tm = matr[i][j][k][b];
                        if (tm == Long.MAX_VALUE-1) continue;
                        if (tm >= maxMatchTime){      // too big or error, do not measure further
                            algos = algos.replaceAll("bcpej".substring(b,b+1),"");
                            System.out.printf("%s ended %s text bucket %s\n",algoNames[b+1],res[i][j],k);
                            Trc.out.printf("%s ended %s text bucket %s time: %s max: %s\n",
                                algoNames[b+1],res[i][j],k,tm,maxMatchTime);
                            matr[i][j][k][b] = Long.MAX_VALUE-1;
                        }
                    }
                    nmea++;
                    System.out.printf("\r%.2f%%",(double)nmea*100/nsamples);
                    Trc.out.printf(" %s.%s",j,k);
                }
            }
            System.out.printf(" nmea: %s\n",nmea);
            Trc.out.printf("\n");
            boolean err = false;
            for (int j = 0; j < errkinds.length; j++){
                if (errkinds[j] > 0){
                    err = true;
                    break;
                }
            }
            if (err){
                Trc.out.printf("RE bucket %s errors %s\n",i,Arrays.toString(errkinds));
                System.out.printf("RE bucket %s errors %s\n",i,Arrays.toString(errkinds));
            }
        }
        // find invalid measures
        boolean invalidMeasures = false;
        Trc.out.printf("invalid measures\n");
        for (int b = 0; b < NRALGOS-1; b++){
            for (int i = 0; i < matr.length; i++){                 // visit REs buckets
                for (int j = 0; j < matr[i].length; j++){          // for each re
                    for (int k = 0; k < matr[i][j].length; k++){   // visit text buckets
                        if (matr[i][j][k][b] == Long.MAX_VALUE){
                            invalidMeasures = true;
                            Trc.out.printf("%s RE bucket %s.%s text bucket %s\n",
                                algoNames[b+1],i,j,k);
                        }
                    }
                }
            }
        }
        if (invalidMeasures){
            System.out.printf("some invalid measures\n");
        }
        if (nomatchErrors > 0){
            System.out.printf("!!! %s texts not matched\n",nomatchErrors);
        }
        return matr;
    }

    /**
     * Measure the memory use for the parsing of all the texts in the specified text bucket.
     *
     * @param      t number of the test case
     * @param      re RE
     * @param      texts text bucket
     * @param      matr vector in which the measurements of the algorithms are returned
     * @param      algos set of algorithms to run
     * @param      errkinds vector of number of errors of each kind
     * @return     
     */

    private void testMemUse(int t, String re, String[] texts, long[] matr, String algos,
        int[] errkinds){
        boolean error = false;
        for (int i = 0; i < algos.length(); i++){
            int algo = 0;
            switch (algos.charAt(i)){
            case 'b': algo = ALGO_BSP; break;
            case 'c': algo = ALGO_BSPP; break;
            case 'p': algo = ALGO_PAT; break;
            case 'e': algo = ALGO_EARLEY; break;
            case 'j': algo = ALGO_JAVA; break;
            }
            //System.out.printf("    %s text len %s\n",algoNames[algo],texts[0].length());
            System.gc();
            long t0 = 0;
            long t1 = 0;
            long ti = 0;
            Re regex = null;
            boolean res = true;
            regex = new Re(algo);
            if (algo == ALGO_JAVA){
                re = re.replaceAll("\\[","(");
                re = re.replaceAll("\\]",")?");
            }
            res = regex.compile(re);
            if (!res){
                Trc.out.printf("error %s compile RE: %s\n",algoNames[algo],re);
                error = true;
            } else {
                long saveMaxMatchTime = maxMatchTime;
                maxMatchTime *= 10;
                mea: for (int k = 0; k < 10; k++){
                    t0 = getCycles();
                    regex.matchStartTime = t0;
                    for (int j = 0; j < texts.length; j++){
                        if (texts[j] == null) continue;
                        //Trc.out.printf("    %s re %s text len %s: %s\n",algoNames[algo],re,texts[j].length(),texts[j]);
                        if (!regex.match(texts[j])){
                            if (regex.errorKind == ERROR_NOMATCH){
                                nomatchErrors++;
                                Trc.out.printf("%s error %s match RE: %s, text |%s| len: %s\n",
                                     algoNames[algo],regex.errorKind,re,abbrev(texts[j]),texts[j].length());
                            }
                            error = true;
                            ti = Long.MAX_VALUE;
                            errkinds[regex.errorKind]++;
                            break mea;
                        }
                    }
                    t1 = getCycles();
                    ti = t1 - t0 - CYCLE_BASE;
                    if (ti > 0) break;
                } // mea
                maxMatchTime = saveMaxMatchTime;
                if (ti <= 0){
                    System.out.printf("!! bad measure\n");
                    ti = 0;
                }

                matr[algo-1] = regex.memory;
                // a measure that aborts for lack of stack or heap appears as a measure not done
            }
        }
    }

    /**
     * Deliver an abbreviation of the specified string.
     *
     * @param      s string
     * @return     string
     */

    private static String abbrev(String s){
        if (s.length() > 200){
            return s.substring(0,20) + "..." + s.substring(s.length()-10);
        }
        return s;
    }

    /**
     * Determine how many columns are full of valid values for REs that have at least one
     * valid value present for each algorithm. I.e. REs whose values are all invalid are
     * first discarded, then the first column starting from the left that has one invalid
     * value is found, and its index returned (as the number of full columns).
     *
     * @param      matr matrix of measurements
     * @return     vector containing the number of significant columns for each algorithm
     */

    private int[] significantColumns(long[][][][] matr){
        int[] maxCol = new int[NRALGOS-1];
        for (int b = 0; b < NRALGOS-1; b++){
            int max = 10;
            for (int i = 0; i < matr.length; i++){           // visit all RE buckets
                for (int j = 0; j < matr[i].length; j++){    // visit all REs in bucket
                    long v = matr[i][j][0][b];
                    if (v >= Long.MAX_VALUE-1) continue;     // invalid row
                    for (int k = 0; k < matr[i][j].length; k++){    // visit row
                        v = matr[i][j][k][b];
                        if (v >= Long.MAX_VALUE-1){          // invalid
                            if (k < max) max = k;
                            break;
                        }
                    }
                }
            }
            maxCol[b] = max;
            Trc.out.printf("significantColumns %s %s\n",b,max);
        }
        return maxCol;
    }

    /**
     * Produce a chart with the speed vs RE length.
     *
     * @param      matr matrix of measurements
     * @param      texts matrix of texts
     * @param      largeTexts <code>true</code> if texts 0:1M
     * @param      algoLabels labels of the algorithms
     * @param      fact vector telling the number of times texts are concatenated for each bucket
     * @param      nchart sequence number of the chart to produce
     */

    private void speedVsReLength(long[][][][] matr, String[][][][] texts, boolean largeTexts,
        String[] algoLabels, int fact[], int nchart){
        // chart speed vs RE length
        plot = new LinkedList<String[]>();
        plot.add(algoLabels);
        // sum up all times for a RE bucket
        int[] maxCol = null;
        if (nchart < 100){          // not the compilation times one
            maxCol = significantColumns(matr);
        }
        for (int i = 0; i < matr.length; i++){
            String[] plotEle = new String[NRALGOS];
            plot.add(plotEle);
            plotEle[0] = "" + ((i+1)*10);
            for (int b = 0; b < NRALGOS-1; b++){
                long tim = 0;
                long tok = 0;
                // visit the text buckets
                int ncols = matr[0][0].length;
                if (maxCol != null) ncols = maxCol[b];           // up to the full columns
                for (int k = 0; k < ncols; k++){
                    long timb = 0;
                    long tokb = 0;
                    for (int j = 0; j < matr[i].length; j++){    // visit bucket of a RE
                        // visit the measures of a RE
                        long v = matr[i][j][k][b];
                        if (v == Long.MAX_VALUE-1){              // measure not done
                            tokb = 0;
                            continue;
                        }
                        if (v < minmeasure) minmeasure = v;
                        timb += v;
                        if (timb < 0) timb = Long.MAX_VALUE;
                        for (int l = 0; l < texts[i][j][k].length; l++){
                            if (largeTexts){
                                String s = texts[i][j][9][l];
                                tokb += (s == null ? 0 : s.length()) * fact[k];
                            } else {
                                String s = texts[i][j][k][l];
                                tokb += s == null ? 0 : s.length();
                            }
                        }
                    }
                    if (tokb == 0) break;
                    tim += timb;
                    if (tim < 0) tim = Long.MAX_VALUE;
                    tok += tokb;
                }
                double val = 0;
                if (tim > 0) val = (double)tok/((double)tim*0.45/1000000.0);  // cycles to ns
                if (tim == Long.MAX_VALUE) val = Double.NaN;
                if (tok == 0) val = Double.NaN;
                plotEle[b+1] = String.format("%.2f",val);
            }
        }
        String xtitle = null;
        if (nchart == 100){
            xtitle = "Parser-construction speed vs RE length";
        } else {
            xtitle = "Parse speed vs RE length, texts ";
            xtitle += largeTexts ? "0:1M" : "0:1000";
        }
        addChart(nchart,"RE length (characters)","characters/ms",xtitle,"Algorithm",true,0);
    }

    /**
     * Produce a chart with the speed vs text length.
     *
     * @param      matr matrix of measurements
     * @param      texts matrix of texts
     * @param      largeTexts <code>true</code> if texts 0:1M
     * @param      algoLabels labels of the algorithms
     * @param      fact vector telling the number of times texts are concatenated for each bucket
     * @param      nchart sequence number of the chart to produce
     */

    private void speedVsTextLength(long[][][][] matr, String[][][][] texts, boolean largeTexts,
        String[] algoLabels, int fact[], int nchart){
        // sum up all times for a text bucket
        plot = new LinkedList<String[]>();
        plot.add(algoLabels);
        int[] maxCol = significantColumns(matr);
        for (int k = 0; k < 10; k++){                            // for each text bucket
            String[] plotEle = new String[NRALGOS];
            plot.add(plotEle);
            if (largeTexts){
                plotEle[0] = "" + (fact[k]*1000);
            } else {
                plotEle[0] = "" + ((k+1)*TEXT_SAMPLES_LENGTH);
            }
            for (int b = 0; b < NRALGOS-1; b++){
                if (k >= maxCol[b]){
                    plotEle[b+1] = "NaN";                        // invalid column
                    continue;
                }
                long tim = 0;
                long tok = 0;
                for (int i = 0; i < matr.length; i++){
                    long timb = 0;
                    long tokb = 0;
                    for (int j = 0; j < matr[i].length; j++){    // visit bucket of a RE
                        long v = matr[i][j][k][b];
                        if (v == Long.MAX_VALUE-1){              // measure not done
                            tokb = 0;
                            continue;
                        }
                        timb += v;
                        if (timb < 0) timb = Long.MAX_VALUE;
                        for (int l = 0; l < texts[i][j][k].length; l++){
                            if (largeTexts){
                                String s = texts[i][j][9][l];
                                tokb += (s == null ? 0 : s.length()) * fact[k];
                            } else {
                                String s = texts[i][j][k][l];
                                tokb += s == null ? 0 : s.length();
                            }
                        }
                    }
                    if (tokb > 0){
                        tok += tokb;
                        tim += timb;
                        if (tim < 0) tim = Long.MAX_VALUE;
                    }
                }
                double val = 0;
                if (tim > 0) val = (double)tok/((double)tim*0.45/1000000.0); // cycles to ns
                if (tim == Long.MAX_VALUE) val = Double.NaN;
                if (tok == 0) val = Double.NaN;
                plotEle[b+1] = String.format("%.2f",val);
            }
        }
        String xtitle = "Parse speed vs text length, texts ";
        xtitle += largeTexts ? "0:1M" : "0:1000";
        addChart(nchart,"Text length (characters)","characters/ms",xtitle,"Algorithm",false,0);
    }

    /**
     * Produce a chart with the speed vs text length by algorithm.
     *
     * @param      matr matrix of measurements
     * @param      texts matrix of texts
     * @param      largeTexts <code>true</code> if texts 0:1M
     * @param      algoLabels labels of the algorithms
     * @param      fact vector telling the number of times texts are concatenated for each bucket
     * @param      nchart sequence number of the chart to produce
     */

    private void speedAlgo(long[][][][] matr, String[][][][] texts, boolean largeTexts,
        int fact[], int nchart){
        // draw a chart for each algo, with text lengths in the x axis and curves for
        // increasing RE length
        String[] complLabels = new String[]{"0",
            "10|#000033","20|#000099","30|#0000FF","40|#009933","50|#009999",
            "60|#0099FF","70|#660000","80|#660066","90|#6600CC","100|#990000"};
        for (int b = 0; b < NRALGOS-1; b++){
            plot = new LinkedList<String[]>();
            plot.add(complLabels);
            for (int k = 0; k < 10; k++){                // visit text buckets
                String[] plotEle = new String[11];
                plot.add(plotEle);
                if (largeTexts){
                    plotEle[0] = "" + (fact[k]*1000);
                } else {
                    plotEle[0] = "" + ((k+1)*TEXT_SAMPLES_LENGTH);
                }
                for (int i = 0; i < matr.length; i++){   // visit all its REs buckets
                    long tim = 0;
                    long tok = 0;
                    rb: for (int j = 0; j < matr[i].length; j++){    // visit bucket of a RE
                        long v = matr[i][j][k][b];
                        if (v == Long.MAX_VALUE-1){                  // measure not done
                            tok = 0;
                            break rb;
                        }
                        tim += v;
                        if (tim < 0) tim = Long.MAX_VALUE;
                        for (int l = 0; l < texts[i][j][k].length; l++){
                            if (largeTexts){
                                String s = texts[i][j][9][l];
                                tok += (s == null ? 0 : s.length()) * fact[k];
                            } else {
                                String s = texts[i][j][k][l];
                                tok += s == null ? 0 : s.length();
                            }
                        }
                    }
                    double val = 0;
                    if (tim > 0) val = (double)tok/((double)tim*0.45/1000000.0); // cycles to ns
                    if (tim == Long.MAX_VALUE) val = Double.NaN;
                    if (tok == 0) val = Double.NaN;
                    plotEle[i+1] = String.format("%.2f",val);
                }
            }
            addChart(b+nchart,"Text length (characters)","characters/ms",algoNames[b+1] + " parse speed vs text length by RE length",
                "RE length",false,b+1);
        }
    }

    /**
     * Produce a chart with the speed vs RE length by algorithm.
     *
     * @param      matr matrix of measurements
     * @param      texts matrix of texts
     * @param      largeTexts <code>true</code> if texts 0:1M
     * @param      algoLabels labels of the algorithms
     * @param      fact vector telling the number of times texts are concatenated for each bucket
     * @param      nchart sequence number of the chart to produce
     */

    private void speedAlgoRe(long[][][][] matr, String[][][][] texts, boolean largeTexts,
        int fact[], int nchart){
        // draw a chart for each algo, with RE lengths in the x axis and curves for
        // increasing text length
        String[] complLabels = null;
        if (largeTexts){
            complLabels = new String[]{"0",
                "1|#000033","2|#000099","5|#0000FF","10|#009933","20|#009999",
                "50|#0099FF","100|#660000","200|#660066","500|#6600CC","1000|#990000"};
        } else {
            complLabels = new String[]{"0",
                "100|#000033","200|#000099","300|#0000FF","400|#009933","500|#009999",
                "600|#0099FF","700|#660000","800|#660066","900|#6600CC","1000|#990000"};
        }
        for (int b = 0; b < NRALGOS-1; b++){
            plot = new LinkedList<String[]>();
            plot.add(complLabels);
            for (int i = 0; i < matr.length; i++){       // visit RE buckets
                String[] plotEle = new String[11];
                plot.add(plotEle);
                plotEle[0] = "" + ((i+1)*10);
                for (int k = 0; k < TEXT_BUCKETS; k++){         // visit all its text buckets
                    // visit the REs of the current RE bucket
                    long tim = 0;
                    long tok = 0;
                    rb: for (int j = 0; j < matr[i].length; j++){   // visit all its REs
                        long v = matr[i][j][k][b];
                        if (v == Long.MAX_VALUE-1){                 // measure not done
                            tok = 0;
                        }
                        tim += v;
                        if (tim < 0) tim = Long.MAX_VALUE;
                        for (int l = 0; l < texts[i][j][k].length; l++){
                            if (largeTexts){
                                String s = texts[i][j][9][l];
                                tok += (s == null ? 0 : s.length()) * fact[k];
                            } else {
                                String s = texts[i][j][k][l];
                                tok += s == null ? 0 : s.length();
                            }
                        }
                    }
                    double val = 0;
                    if (tim > 0) val = (double)tok/((double)tim*0.45/1000000.0); // cycles to ns
                    if (tim == Long.MAX_VALUE) val = Double.NaN;
                    if (tok == 0) val = Double.NaN;
                    plotEle[k+1] = String.format("%.2f",val);
                }
            }
            addChart(b+nchart,"RE length (characters)","characters/ms",algoNames[b+1] + " parse speed vs RE length by text length",
                "Text length" + (largeTexts?" x1000":""),false,b+1);
        }
    }

    /**
     * Produce a chart with the memory vs text length.
     *
     * @param      matr matrix of measurements
     * @param      texts matrix of texts
     * @param      algoLabels labels of the algorithms
     * @param      fact vector telling the number of times texts are concatenated for each bucket
     * @param      nchart sequence number of the chart to produce
     */

    private void memoryVsTextLength(long[][][][] matr, String[][][][] texts,
        String[] algoLabels, int fact[], int nchart){
        // sum up all times for a text bucket
        plot = new LinkedList<String[]>();
        plot.add(algoLabels);
        int[] maxCol = significantColumns(matr);
        for (int k = 0; k < 10; k++){                            // for each text bucket
            String[] plotEle = new String[NRALGOS];
            plot.add(plotEle);
            plotEle[0] = "" + ((k+1)*TEXT_SAMPLES_LENGTH);
            for (int b = 0; b < NRALGOS-1; b++){
                if (k >= maxCol[b]){
                    plotEle[b+1] = "NaN";                        // invalid column
                    continue;
                }
                long mem = 0;
                long tok = 0;
                for (int i = 0; i < matr.length; i++){
                    long memb = 0;
                    long tokb = 0;
                    for (int j = 0; j < matr[i].length; j++){    // visit bucket of a RE
                        long v = matr[i][j][k][b];
                        if (v == Long.MAX_VALUE-1){              // measure not done
                            tokb = 0;
                            continue;
                        }
                        memb += v;
                        if (memb < 0) memb = Long.MAX_VALUE;
                        for (int l = 0; l < texts[i][j][k].length; l++){
                            String s = texts[i][j][k][l];
                            tokb += s == null ? 0 : s.length();
                        }
                    }
                    if (tokb > 0){
                        tok += tokb;
                        mem += memb;
                        if (mem < 0) mem = Long.MAX_VALUE;
                    }
                }
                double val = 0;
                if (mem > 0) val = (double)mem/(double)tok;
                if (mem == Long.MAX_VALUE) val = Double.NaN;
                if (tok == 0) val = Double.NaN;
                plotEle[b+1] = String.format("%.2f",val);
            }
        }
        String xtitle = "Memory vs text length, texts ";
        xtitle += "0:1000";
        addChart(nchart,"Text length (characters)","bytes/toks",xtitle,"Algorithm",false,0);
    }

    /** The sequence number of the chart to produce. */
    private static int chartNr;

    /**
     * Add a chart to the html file using the plot data.
     *
     * @param      t sequence number of the chart to produce
     * @param      xlabel label of the x-axis
     * @param      ylabel label of the y-axis
     * @param      title title of the chart
     * @param      curvesLegend legend of the curves
     * @param      xlinear <code>true</code> if x-axis linear, <code>false</code> if logarithmic
     * @param      algo number of the algorithm if chart an algorithm, 0 otherwise
     */

    private static void addChart(int t, String xlabel, String ylabel, String title,
        String curvesLegend, boolean xlinear, int algo){
        html.printf("<h3>Chart %s</h3>\n",chartNr++);
        html.printf("<p><div style=\"border:1px solid;width:1000px;\">\n");
        html.printf("<svg id=diag%s xmlns=\"http://www.w3.org/2000/svg\" " +
            "xmlns:xlink=\"http://www.w3.org/1999/xlink\" " + 
            "svg width=1000 height=380>\n<script>\n",t);
        html.printf("xAxisName = \"" + xlabel + "\";yAxisName = \"" + ylabel + "\";\n");
        if (curvesLegend != null){
            html.printf("curvesLegend = \"" + curvesLegend + "\";\n");
        }
        html.printf("svgChart(\"diag%s\",\"%s\",\"%s\",[\n",t,title,"");
        for (int i = 0; i < plot.get(0).length; i++){
            html.printf("[");
            boolean first = true;
            for (int j = 0; j < plot.size(); j++){
                String[] ele = plot.get(j);
                if (ele[i] != null && ele[i].length() > 0){
                    if (!first) html.printf(",");
                    first = false;
                    if (i == 0 || j == 0){
                        html.printf("\"%s\"",ele[i]);
                    } else {
                        html.printf("%s",ele[i]);
                    }
                }
            }
            if (i < plot.get(0).length-1){
                html.printf("],\n");
            } else {
                html.printf("]\n");
            }
        }
        html.printf("])\n</script>\n</svg>\n</div>\n");
        pstplotChart(t,xlabel,ylabel,title,"",curvesLegend,xlinear,algo);
    }

    /**
     * Add a pgf chart to the tex file using the plot data.
     *
     * @param      t sequence number of the chart to produce
     * @param      xlabel label of the x-axis
     * @param      ylabel label of the y-axis
     * @param      title title of the chart
     * @param      title2 second title of the chart
     * @param      curvesLegend legend of the curves
     * @param      xlinear <code>true</code> if x-axis linear, <code>false</code> if logarithmic
     */

    private static void pgfChart(int t, String xlabel, String ylabel, String title,
        String title2, String curvesLegend, boolean xlinear, int algo){
        tex.printf("\\subsection{Chart %s}\n",chartNr-1);
        tex.printf("\\begin{tikzpicture}\n");
        if (xlinear){
            if (t == 100){                            // compilation time
                tex.printf("\\begin{semilogyaxis}[\n");
            } else {
                tex.printf("\\begin{axis}[\n");
            }
        } else {
            tex.printf("\\begin{semilogxaxis}[\n");
        }

        // title
        if (title2.length() > 0){
            tex.printf("  title={RE: %s    text: %s},\n",verbatim(title),verbatim(title2));
        } else {
            tex.printf("  title={%s},\n",verbatim(title));
        }
        tex.printf("  xlabel={%s},\n",verbatim(xlabel));
        tex.printf("  ylabel={%s},\n",verbatim(ylabel));
        tex.printf("  grid=major,\n");
        tex.printf("  legend style={\n");
        tex.printf("    cells={anchor=east},\n");
        tex.printf("    legend pos=outer north east,\n");
        tex.printf("  },\n");
        tex.printf("  scaled ticks=false,\n");
        tex.printf("  ylabel near ticks,\n");
        tex.printf("]\n");

        // title of legend of curves
        if (curvesLegend != null){
            tex.printf("\\addlegendimage{empty legend}\n");
            tex.printf("\\addlegendentry[text depth=]{%s}\n",curvesLegend);
        }

        // draw the lines
        String[] legend = plot.get(0);                // names of algorithms
        for (int i = 1; i < legend.length; i++){      // for all algorithms
            String lab = legend[i];
            int idx = lab.indexOf("|");
            if (idx >= 0) lab = lab.substring(0,idx);
            if (lab.equals(algoNames[ALGO_BSPP])) continue;  // no charts for BSPP
            if (plot.get(1)[i].equals("NaN")) continue;
            tex.printf("\\addplot table {\nx\ty\n");
            for (int j = 1; j < plot.size(); j++){
                String[] v = plot.get(j);
                if (v[i].equals("NaN")) break;
                tex.printf("%s\t%s\n",v[0],v[i]);
            }
            tex.printf("};\n");
            tex.printf("\\addlegendentry{%s}\n",verbatim(lab));
        }

        if (xlinear){
            if (t == 100){                            // compilation time
                tex.printf("\\end{semilogyaxis}\n");
            } else {
                tex.printf("\\end{axis}\n");
            }
        } else {
            tex.printf("\\end{semilogxaxis}\n");
        }
        tex.printf("\\end{tikzpicture}\n");
    }

    /**
     * Add a pstplot chart to the tex file using the plot data.
     *
     * @see        #pgfChart(int,String,String,String,String,String,boolean)
     */

    private static void pstplotChart(int t, String xlabel, String ylabel, String title,
        String title2, String curvesLegend, boolean xlinear, int algo){
        if (algo > 0 && algo != ALGO_BSP) return;   // only BSP for the paper
        tex.printf("\\begin{figure}[ht]\\begin{center}\n");
        // define the data
        double xmax = 0;
        double ymax = 0;
        boolean ylinear = true;
        if (xlinear && t == 100){                     // compilation time
            ylinear = false;
        }
        double xmin = Math.log10(Double.parseDouble(plot.get(1)[0]));

        String[] legend = plot.get(0);                // names of algorithms/lines
        for (int i = 1; i < legend.length; i++){      // for all algorithms/lines
            String lab = legend[i];
            int idx = lab.indexOf("|");
            if (idx >= 0) lab = lab.substring(0,idx);
            if (lab.equals(algoNames[ALGO_BSPP])) continue;  // no charts for BSPP
            if (plot.get(1)[i].equals("NaN")) continue;
            tex.printf("\\savedata{\\mydata%s}[{\n",verbMacro(lab));
            boolean comma = false;
            for (int j = 1; j < plot.size(); j++){
                String[] v = plot.get(j);
                if (v[i].equals("NaN")) break;
                if (comma){
                    tex.printf(",\n");
                }
                double x = Double.parseDouble(v[0]);
                double y = Double.parseDouble(v[i]);
                if (!xlinear) x = Math.log10(x)-xmin;
                if (!ylinear) y = Math.log10(y);
                tex.printf("  {%.2f,%.2f}",x,y);
                xmax = Math.max(xmax,x);
                ymax = Math.max(ymax,y);
                comma = true;
            }
            tex.printf("}]\n");
        }
        // round ymax to the next number that has the first digit incremented and the others 0
        String ym = Long.toString(Math.round(ymax));
        int firstdigit = ym.charAt(0) - '0';
        ymax = (firstdigit + 2) * Math.pow(10,ym.length()-1);
        String xlabfactor = "";
        String ylabfactor = "";
        String xunit = "1";
        String yunit = "1";
        if (xmax >= 1000000){
            xlabfactor = "6";
            xunit = "0.000001";
            xmax /= 1000000;
        } else if (xmax >= 1000){
            xlabfactor = "3";
            xunit = "0.001";
            xmax /= 1000;
        }
        if (ymax >= 1000000){
            ylabfactor = "6";
            yunit = "0.000001";
            ymax /= 1000000;
        } else if (ymax >= 1000){
            ylabfactor = "3";
            yunit = "0.001";
            ymax /= 1000;
        }
        if (xlabfactor.length() > 0 || ylabfactor.length() > 0){
            tex.printf("\\pstScalePoints(%s,%s){}{}\n",xunit,yunit);
        }
        tex.printf("\\psset{xAxisLabel=%s,yAxisLabel=%s,llx=-0.5cm,lly=-0.5cm,ury=0.5cm," +
            "xAxisLabelPos={c,-8mm},yAxisLabelPos={-15mm,c}}\n",verbatim(xlabel),verbatim(ylabel));
        tex.printf("\\begin{psgraph}[axesstyle=frame,xticksize=0 %s,yticksize=0 %s,\n",ymax,xmax);
        tex.printf("  tickcolor=gray,\n");
        if (xlinear){
            if (t == 100){                            // compilation time
                tex.printf("  ylogBase=10,logLines=y,\n");
            }
        } else {
            tex.printf("  xlogBase=10,logLines=x,\n");
        }
        if (xlabfactor.length() > 0){
            tex.printf("  xlabelFactor=\\cdot 10^%s,\n",xlabfactor);
        }
        if (ylabfactor.length() > 0){
            tex.printf("  ylabelFactor={,}000,\n");
        }
        int Dy = 1;
        if (ymax > 10){
            Dy = 10;
        } else {
            Dy = 2;
        }
        tex.printf("  subticks=0");
        if (xlinear){
            tex.printf(",Dx=20");
        } else {
            tex.printf(",Ox=%s",xmin);
        }
        if (ylinear){
            tex.printf(",Dy=%s",Dy);
        }
        tex.printf("](0,0)(%s,%s){7cm}{5cm}\n",xmax,ymax);

        // curves
        for (int i = 1; i < legend.length; i++){      // for all algorithms/lines
            String lab = legend[i];
            int idx = lab.indexOf("|");
            if (idx >= 0) lab = lab.substring(0,idx);
            if (lab.equals(algoNames[ALGO_BSPP])) continue;  // no charts for BSPP
            if (plot.get(1)[i].equals("NaN")) continue;
            tex.printf("\\listplot[linewidth=1pt,showpoints=true,dotstyle=%s]{\\mydata%s}\n",
                dots[i-1],verbMacro(lab));
        }
        tex.printf("\\end{psgraph}\n");

        // legend
        double yleg = 9;
        double ylegdelta = 0.7;
        tex.printf("\\psset{xAxisLabel=,yAxisLabel=}\n");
        tex.printf("\\begin{psgraph}[xticksize=0 0,yticksize=0 0,axesstyle=none," +
            "labels=none,ticks=none](0,0)(10,10){2.5cm}{5cm}\n");
        if (curvesLegend != null){
            tex.printf("\\rput[l](1,%.2f){%s}\n",yleg,verbatim(curvesLegend));
            yleg -= ylegdelta;
        }
        for (int i = 1; i < legend.length; i++){      // for all algorithms/lines
            String lab = legend[i];
            int idx = lab.indexOf("|");
            if (idx >= 0) lab = lab.substring(0,idx);
            if (lab.equals(algoNames[ALGO_BSPP])) continue;  // no charts for BSPP
            if (plot.get(1)[i].equals("NaN")) continue;
            tex.printf("\\rput(1,%s){%%\n",yleg);
            tex.printf("\\psline[linewidth=1pt]{}(0,0)(4,0)\n");
            tex.printf("\\psdot[dotstyle=%s,dotsize=1mm 1](2,0)\n",dots[i-1]);
            tex.printf("\\rput[l](5,0){%s}}\n",verbatim(lab));
            yleg -= ylegdelta;
        }
        tex.printf("\\psframe(0,%s)(10,10)\n",yleg);

        // title
        String mytitle = null;
        if (title2.length() > 0){
            mytitle = String.format("RE: %s    text: %s",verbatim(title),verbatim(title2));
        } else {
            mytitle = verbatim(title);
        }
        tex.printf("\\end{psgraph}\n");
        tex.printf("\\end{center}\n");
        tex.printf("\\caption{%s}\\label{chart%s}\n",mytitle,t);
        tex.printf("\\end{figure}\n");
    }

    /** The dots used to distinguish the curves. */
    private static final String[] dots = new String[]{"*","o","square","square*",
        "diamond","diamond*","triangle","triangle*","pentagon","pentagon*"};

    /**
     * Deliver the specified string with the latex special characters escaped.
     *
     * @param      s string
     * @return     string
     */

    private static String verbatim(String s){
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++){
            char c = s.charAt(i);
            switch (c){
            case '#': case '$': case '%': case '&': case '~':
            case '_': case '^': case '\\': case '{': case '}':
                sb.append("\\");
            default:
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Deliver the specified string with the latex special characters escaped and
     * digits replaced with letters (to be used in macro names).
     *
     * @param      s string
     * @return     string
     */

    private static String verbMacro(String s){
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++){
            char c = s.charAt(i);
            switch (c){
            case '#': case '$': case '%': case '&': case '~':
            case '_': case '^': case '\\': case '{': case '}':
                sb.append("\\");
                sb.append(c);
                break;
            case '0': sb.append("a"); break;
            case '1': sb.append("b"); break;
            case '2': sb.append("c"); break;
            case '3': sb.append("d"); break;
            case '4': sb.append("e"); break;
            case '5': sb.append("f"); break;
            case '6': sb.append("g"); break;
            case '7': sb.append("h"); break;
            case '8': sb.append("i"); break;
            case '9': sb.append("j"); break;
            default:
                sb.append(c);
            }
        }
        return sb.toString();
    }


    //----- Main ------

    /** The number of cycles taken by a getCycles. */
    private static long CYCLE_BASE;

    /**
     * Measure the number of cycles taken by a getCycles.
     */

    private static long cyclesBase(){
        long c0 = getCycles();
        long c1 = getCycles();
        long c2 = getCycles();
        long c3 = getCycles();
        long c4 = getCycles();
        long c5 = getCycles();
        long c6 = getCycles();
        long c7 = getCycles();
        long c8 = getCycles();
        long c9 = getCycles();
        long min = c1-c0;
        min = Math.min(min,c2-c1);
        min = Math.min(min,c3-c2);
        min = Math.min(min,c4-c3);
        min = Math.min(min,c5-c4);
        min = Math.min(min,c6-c5);
        min = Math.min(min,c7-c6);
        min = Math.min(min,c8-c7);
        min = Math.min(min,c9-c8);
        // System.out.printf("%s\n",min);
        return min;
    }

    /**
     * Record the number of cycles taken by a getCycles.
     */

    private static void getCyclesBase(){
        long min = Long.MAX_VALUE;
        for (int i = 0; i < 1000; i++){
            CYCLE_BASE = Math.min(min,cyclesBase());
        }
        System.out.printf("minimum cycles measured %s\n",CYCLE_BASE);

        long t0 = System.nanoTime();
        long t1 = System.nanoTime();
        long t2 = System.nanoTime();
        long t3 = System.nanoTime();
        long t4 = System.nanoTime();
        long t5 = System.nanoTime();
        long t6 = System.nanoTime();
        long t7 = System.nanoTime();
        long t8 = System.nanoTime();
        long t9 = System.nanoTime();
        min = t1-t0;
        min = Math.min(min,t2-t1);
        min = Math.min(min,t3-t2);
        min = Math.min(min,t4-t3);
        min = Math.min(min,t5-t4);
        min = Math.min(min,t6-t5);
        min = Math.min(min,t7-t6);
        min = Math.min(min,t8-t7);
        min = Math.min(min,t9-t8);
        //System.out.printf("min time measured %s\n",min);
    }

    /** The lowest measurement done. */
    private static long minmeasure = Long.MAX_VALUE;

    /**
     * Main program.
     *
     * @param      args vector of the arguments
     */

    public static void main(String[] args){
        Locale.setDefault(Locale.US);
        getCyclesBase();

        Re regex = new Re();
        regex.test();
        // stressTest();

        regex.testSulz();
        /*
        // this shows how slow is the Karper compilation
        Re reg = new Re(ALGO_KARPER);
        System.out.printf("-1-\n");
        reg.compile("((aa(jeih(((f|g|f|g|g|h|h|j|j|i|e)|hgjaccfed)?)*)dd)z)*");
        System.out.printf("-2-\n");
        */
        /*
        doit: {
            String re = "abc";
            Pattern p = Pattern.compile(re);
            String txt = "xxxabcxxxabcxxx";
            int ngroups = 1;
            for (int i = 0; i < re.length(); i++){
                if (re.charAt(i) == '(') ngroups++;
            }
            Trc.out.printf("ngroups %s\n",ngroups);
            Matcher m = p.matcher(txt);
            while (m.find()){
                Trc.out.printf("start: %s end %s\n",m.start(),m.end());
            }
            // it seems that there is little difference in time with or without this
            //for (int j = 0; j < ngroups; j++){
            //    String gr = m.group(j);
            //    int len = gr == null ? 0 : gr.length();
            //    Trc.out.printf("java group: %s match %s len %s\n",j,gr,len);
            //}
        }
        //Pattern p = Pattern.compile("((a)*)*");
        //Matcher m = p.matcher("aa");
        //m.matches();
        //Trc.out.printf("java group: 0 match %s\n",m.group(0));
        //Trc.out.printf("java group: 1 match %s\n",m.group(1));
        //Trc.out.printf("java group: 2 match %s\n",m.group(2));
        if (true) return;
        */

        /*
        // this takes a very long time: > 25 minutes
        System.out.printf("%s java\n",new Date().toString());
        Pattern p = Pattern.compile("(h|h|ih(((i|a|c|c|a|i|i|j|b|a|i|b|a|a|j))+h)ahbfhba|c|i)*");
        Matcher m = p.matcher("hchcchicihcchciiicichhcichcihcchiihichiciiiihhcchicchhcihchcihiihciichhccciccichcichiihcchcihhicchcciicchcccihiiihhihihihichicihhcciccchihhhcchichchciihiicihciihcccciciccicciiiiiiiiicihhhiiiihchccchchhhhiiihchihcccchhhiiiiiiiicicichicihcciciihichhhhchihciiihhiccccccciciihhichiccchhicchicihihccichicciihcichccihhiciccccccccichhhhihihhcchchihihiihhihihihicichihiiiihhhhihhhchhichiicihhiiiiihchccccchichci");
        m.matches();
        System.out.printf("%s done java\n",new Date().toString());
        */

        measureFrequency();    // measure frequency of constructs

        ckshow = true;
        feature("speed of border cases");
        // chartByTextLength();

        feature("benchmark");
        regex.benchmark();
        if (minmeasure < CYCLE_BASE/10){
            System.out.printf("!! measurements with cycles not accurate: minmeasure %s cycle-base %s\n",
                minmeasure,CYCLE_BASE);
        }

        System.out.printf("callsBSP %s revertBSP %s\n",callsBSP,revertBSP);
    }
}
