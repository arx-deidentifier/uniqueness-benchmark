/*
 * Benchmark of methods for controlling population unqiueness with ARX
 * Copyright 2016 - Fabian Prasser
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.deidentifier.arx.benchmark;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.deidentifier.arx.ARXAnonymizer;
import org.deidentifier.arx.ARXConfiguration;
import org.deidentifier.arx.ARXPopulationModel;
import org.deidentifier.arx.ARXResult;
import org.deidentifier.arx.ARXSolverConfiguration;
import org.deidentifier.arx.AttributeType.Hierarchy;
import org.deidentifier.arx.Data;
import org.deidentifier.arx.benchmark.BenchmarkSetup.BenchmarkUtilityMeasure;
import org.deidentifier.arx.criteria.PopulationUniqueness;
import org.deidentifier.arx.io.CSVHierarchyInput;
import org.deidentifier.arx.metric.Metric;
import org.deidentifier.arx.metric.Metric.AggregateFunction;
import org.deidentifier.arx.risk.RiskEstimateBuilder;
import org.deidentifier.arx.risk.RiskModelPopulationUniqueness.PopulationUniquenessModel;

import de.linearbits.subframe.Benchmark;
import de.linearbits.subframe.analyzer.ValueBuffer;

/**
 * Test for data transformations.
 *
 * @author Fabian Prasser
 * @author Florian Kohlmayer
 */
public abstract class BenchmarkExperimentResidual {

    /** The benchmark instance */
    private static final Benchmark  BENCHMARK                     = new Benchmark(new String[] { "Dataset", "Model", "Threshold"});
    /** VALUE */
    public static final int         TIME                             = BENCHMARK.addMeasure("time");
    /** VALUE */
    public static final int         QUALITY                          = BENCHMARK.addMeasure("quality");
    /** VALUE */
    public static final int         SAMPLE_UNIQUENESS                = BENCHMARK.addMeasure("SU");
    /** VALUE */
    public static final int         POPULATION_UNIQUENESS_USA        = BENCHMARK.addMeasure("PU (USA)");
    /** VALUE */
    public static final int         POPULATION_UNIQUENESS_CALIFORNIA = BENCHMARK.addMeasure("PU (California)");
    /** VALUE */
    public static final int         POPULATION_UNIQUENESS_LA         = BENCHMARK.addMeasure("PU (Los Angeles)");
    /** VALUE */
    private static final double     POPULATION_USA                   = 318.9 * Math.pow(10d, 6d);
    /** VALUE */
    private static final double     POPULATION_CALIFORNIA            = 39.14 * Math.pow(10d, 6d);
    /** VALUE */
    private static final double     POPULATION_LA                    = 4.031 * Math.pow(10d, 6d);
    /** CONFIG */
    private static final ARXSolverConfiguration SOLVER_CONFIG                    = ARXSolverConfiguration.create()
                                                                                                         .preparedStartValues(getSolverStartValues())
                                                                                                         .iterationsPerTry(10);

    /**
     * Main
     * @param args
     * @throws IOException
     */
    public static void main(String[] args) throws IOException {
    
        // Init
        BENCHMARK.addAnalyzer(TIME, new ValueBuffer());
        BENCHMARK.addAnalyzer(QUALITY, new ValueBuffer());
        BENCHMARK.addAnalyzer(SAMPLE_UNIQUENESS, new ValueBuffer());
        BENCHMARK.addAnalyzer(POPULATION_UNIQUENESS_USA, new ValueBuffer());
        BENCHMARK.addAnalyzer(POPULATION_UNIQUENESS_CALIFORNIA, new ValueBuffer());
        BENCHMARK.addAnalyzer(POPULATION_UNIQUENESS_LA, new ValueBuffer());

        // Perform
        String[] datasets = new String[] { "adult", "fars", "atus", "ihis", "cup"};
        for (int i = 0; i < datasets.length; i++) {
            for (BenchmarkUtilityMeasure measure : new BenchmarkUtilityMeasure[]{BenchmarkUtilityMeasure.LOSS}){
                System.out.println(datasets[i] +" - " + measure.toString());
                for (double threshold : new double[]{0.01d, 0.05d}) {
                    System.out.println(" - Threshold: " + threshold);
                        BENCHMARK.addRun(datasets[i], measure.toString(), String.valueOf(threshold));
                        analyze(datasets[i], measure, threshold);
                        BENCHMARK.getResults().write(new File("results/residual.csv"));
                }
            }
        }
    }

    /**
     * Run
     * @param dataset
     * @param measure
     * @param threshold
     * @param population
     * @throws IOException
     */
    private static void analyze(String dataset, BenchmarkUtilityMeasure measure, double threshold) throws IOException {
        
        // ********************
        
        Data data = getDataObject(dataset);
        
        // Uniqueness
        ARXConfiguration config = ARXConfiguration.create();
        switch (measure) {
        case ENTROPY:
            config.setMetric(Metric.createPrecomputedNormalizedEntropyMetric(1.0d, AggregateFunction.SUM));
            break;
        case LOSS:
            config.setMetric(Metric.createPrecomputedLossMetric(1.0d, AggregateFunction.GEOMETRIC_MEAN));
            break;
        default:
            throw new IllegalArgumentException("");
        }
        config.setMaxOutliers(1d);
        config.addCriterion(new PopulationUniqueness(threshold,
                                                     PopulationUniquenessModel.PITMAN,
                                                     ARXPopulationModel.create((long)POPULATION_USA), 
                                                     SOLVER_CONFIG));
        
        ARXAnonymizer anonymizer = new ARXAnonymizer();

        // Warmup
        long time = System.currentTimeMillis();
        ARXResult result = anonymizer.anonymize(data, config);
        double utility = Double.valueOf(result.getGlobalOptimum().getMaximumInformationLoss().toString());
        switch (measure) {
        case ENTROPY:
            utility = 1 - (utility / (double) data.getDefinition()
                                                  .getQuasiIdentifyingAttributes()
                                                  .size());
            break;
        case LOSS:
            utility = 1 - utility;
            break;
        default:
            throw new IllegalArgumentException("");
        }
        time = System.currentTimeMillis() - time;
        
        RiskEstimateBuilder estimator = result.getOutput().getRiskEstimator(ARXPopulationModel.create((long)POPULATION_USA), SOLVER_CONFIG);
        double su = estimator.getSampleBasedUniquenessRisk().getFractionOfUniqueTuples();
        double pu_usa = estimator.getPopulationBasedUniquenessRisk().getFractionOfUniqueTuplesPitman();
        estimator = result.getOutput().getRiskEstimator(ARXPopulationModel.create((long)POPULATION_CALIFORNIA), SOLVER_CONFIG);
        double pu_california = estimator.getPopulationBasedUniquenessRisk().getFractionOfUniqueTuplesPitman();
        estimator = result.getOutput().getRiskEstimator(ARXPopulationModel.create((long)POPULATION_LA), SOLVER_CONFIG);
        double pu_la = estimator.getPopulationBasedUniquenessRisk().getFractionOfUniqueTuplesPitman();
        BENCHMARK.addValue(TIME, time);
        BENCHMARK.addValue(QUALITY, utility);
        BENCHMARK.addValue(SAMPLE_UNIQUENESS, su);
        BENCHMARK.addValue(POPULATION_UNIQUENESS_USA, pu_usa);
        BENCHMARK.addValue(POPULATION_UNIQUENESS_CALIFORNIA, pu_california);
        BENCHMARK.addValue(POPULATION_UNIQUENESS_LA, pu_la);
        data.getHandle().release();
    }

    /**
     * Returns the data object for the test case.
     *
     * @param dataset
     * @return
     * @throws IOException
     */
    private static Data getDataObject(final String dataset) throws IOException {
        
        // Load dataset
        final Data data = Data.create("./data/"+dataset+".csv", Charset.defaultCharset(), ';');
        
        // Load hierarchies
        prepareDataObject(dataset, data, Integer.MAX_VALUE);
        return data;
    }

    /**
     * Creates start values for the solver
     * @return
     */
    private static double[][] getSolverStartValues() {
        double[][] result = new double[16][];
        int index = 0;
        for (double d1 = 0d; d1 < 1d; d1 += 0.33d) {
            for (double d2 = 0d; d2 < 1d; d2 += 0.33d) {
                result[index++] = new double[] { d1, d2 };
            }
        }
        return result;
    }

    /**
     * Loads hierarchies
     * @param dataset
     * @param data
     * @param columns
     * @return
     * @throws IOException
     */
    private static void prepareDataObject(final String dataset, final Data data, int columns) throws IOException {
        
        // Read generalization hierachies
        final FilenameFilter hierarchyFilter = new FilenameFilter() {
            @Override
            public boolean accept(final File dir, final String name) {
                if (name.matches(dataset+"_hierarchy_(.)+.csv")) {
                    return true;
                } else {
                    return false;
                }
            }
        };
        
        final File testDir = new File("./hierarchies");
        final File[] genHierFiles = testDir.listFiles(hierarchyFilter);
        final Pattern pattern = Pattern.compile("_hierarchy_(.*?).csv");
        
        for (final File file : genHierFiles) {
            final Matcher matcher = pattern.matcher(file.getName());
            if (matcher.find()) {
                final CSVHierarchyInput hier = new CSVHierarchyInput(file, Charset.defaultCharset(), ';');
                final String attributeName = matcher.group(1);
                if (data.getHandle().getColumnIndexOf(attributeName) < columns) {
                    data.getDefinition().setAttributeType(attributeName, Hierarchy.create(hier.getHierarchy()));
                }
            }
        }
    }
}
