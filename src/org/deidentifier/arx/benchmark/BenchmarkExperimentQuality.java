
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
import org.deidentifier.arx.criteria.KAnonymity;
import org.deidentifier.arx.criteria.PopulationUniqueness;
import org.deidentifier.arx.io.CSVHierarchyInput;
import org.deidentifier.arx.metric.Metric;
import org.deidentifier.arx.metric.Metric.AggregateFunction;
import org.deidentifier.arx.risk.RiskModelPopulationUniqueness.PopulationUniquenessModel;

import de.linearbits.subframe.Benchmark;
import de.linearbits.subframe.analyzer.ValueBuffer;

/**
 * Test for data transformations.
 *
 * @author Fabian Prasser
 * @author Florian Kohlmayer
 */
public abstract class BenchmarkExperimentQuality {


    /** The benchmark instance */
    private static final Benchmark BENCHMARK   = new Benchmark(new String[] { "Dataset", "Model"});
    /** TOTAL */
    public static final int         UTILITY_001_UNIQUENESS     = BENCHMARK.addMeasure("utility-(0.01)-uniqueness");
    /** TOTAL */
    public static final int         UTILITY_005_UNIQUENESS     = BENCHMARK.addMeasure("utility-(0.05)-uniqueness");
    /** TOTAL */
    public static final int         UTILITY_2_ANONYMITY      = BENCHMARK.addMeasure("utility-(2)-anonymity");
    /** TOTAL */
    public static final int         UTILITY_5_ANONYMITY      = BENCHMARK.addMeasure("utility-(5)-anonymity");
    /** VALUE */
    private static final double[][] SOLVER_START_VALUES    = getSolverStartValues();
    /** VALUE */
    private static final double     POPULATION_USA         = 318.9 * Math.pow(10d, 6d);
    /** START_INDEX */
    private static int              START_INDEX            = 0;

    /**
     * Main
     * @param args
     * @throws IOException
     */
    public static void main(String[] args) throws IOException {
        
        // Parse commandline
        if (args != null && args.length != 0) {
            
            int index = -1;
            try {
                index = Integer.parseInt(args[0]);
            } catch (Exception e) {
                index = -1;
            }
            if (index != -1) {
                START_INDEX = index;
            } else {
                START_INDEX = 0;
            }
        }

        // Init
        BENCHMARK.addAnalyzer(UTILITY_5_ANONYMITY, new ValueBuffer());
        BENCHMARK.addAnalyzer(UTILITY_2_ANONYMITY, new ValueBuffer());
        BENCHMARK.addAnalyzer(UTILITY_005_UNIQUENESS, new ValueBuffer());
        BENCHMARK.addAnalyzer(UTILITY_001_UNIQUENESS, new ValueBuffer());

        // Perform
        String[] datasets = new String[] { "adult", "cup", "fars", "atus", "ihis" };
        for (int i = START_INDEX; i < datasets.length; i++) {
            for (BenchmarkUtilityMeasure measure : new BenchmarkUtilityMeasure[]{BenchmarkUtilityMeasure.ENTROPY,
                                                                                 BenchmarkUtilityMeasure.LOSS}){
                System.out.println(datasets[i] + " - " + measure.toString());
                BENCHMARK.addRun(datasets[i], measure.toString());
                analyze(datasets[i], measure);
                BENCHMARK.getResults().write(new File("results/quality.csv"));
            }
        }
    }

    /**
     * Run
     * @param dataset
     * @param measure
     * @throws IOException
     */
    private static void analyze(String dataset, BenchmarkUtilityMeasure measure) throws IOException {
        
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
        config.addCriterion(new PopulationUniqueness(0.01d,
                                                     PopulationUniquenessModel.PITMAN,
                                                     ARXPopulationModel.create((long)POPULATION_USA), 
                                                     ARXSolverConfiguration.create().preparedStartValues(SOLVER_START_VALUES)
                                                     .iterationsPerTry(10)));
        
        ARXAnonymizer anonymizer = new ARXAnonymizer();

        // Run
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
        BENCHMARK.addValue(UTILITY_001_UNIQUENESS, utility);
        data.getHandle().release();
        
        /* ******************************************************************
           *******************************************************************/
        // Uniqueness
        config = ARXConfiguration.create();
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
        config.addCriterion(new PopulationUniqueness(0.05d,
                                                     PopulationUniquenessModel.PITMAN,
                                                     ARXPopulationModel.create((long)POPULATION_USA), 
                                                     ARXSolverConfiguration.create().preparedStartValues(SOLVER_START_VALUES)
                                                     .iterationsPerTry(10)));
        
        anonymizer = new ARXAnonymizer();

        // Run
        result = anonymizer.anonymize(data, config);
        utility = Double.valueOf(result.getGlobalOptimum().getMaximumInformationLoss().toString());
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
        BENCHMARK.addValue(UTILITY_005_UNIQUENESS, utility);
        data.getHandle().release();

        /* ******************************************************************
           *******************************************************************/
        // Uniqueness
        config = ARXConfiguration.create();
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
        config.addCriterion(new KAnonymity(5));
        
        anonymizer = new ARXAnonymizer();

        // Run
        result = anonymizer.anonymize(data, config);
        utility = Double.valueOf(result.getGlobalOptimum().getMaximumInformationLoss().toString());
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
        BENCHMARK.addValue(UTILITY_5_ANONYMITY, utility);
        data.getHandle().release();

        /* ******************************************************************
           *******************************************************************/
        // Uniqueness
        config = ARXConfiguration.create();
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
        config.addCriterion(new KAnonymity(2));
        
        anonymizer = new ARXAnonymizer();

        // Run
        result = anonymizer.anonymize(data, config);
        utility = Double.valueOf(result.getGlobalOptimum().getMaximumInformationLoss().toString());
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
        BENCHMARK.addValue(UTILITY_2_ANONYMITY, utility);
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
                final CSVHierarchyInput hier = new CSVHierarchyInput(file, Charset.defaultCharset(),  ';');
                final String attributeName = matcher.group(1);
                if (data.getHandle().getColumnIndexOf(attributeName) < columns) {
                    data.getDefinition().setAttributeType(attributeName, Hierarchy.create(hier.getHierarchy()));
                }
            }
        }
    }
}
