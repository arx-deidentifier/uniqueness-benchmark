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
import org.deidentifier.arx.ARXLattice.ARXNode;
import org.deidentifier.arx.ARXPopulationModel;
import org.deidentifier.arx.ARXResult;
import org.deidentifier.arx.ARXSolverConfiguration;
import org.deidentifier.arx.AttributeType.Hierarchy;
import org.deidentifier.arx.Data;
import org.deidentifier.arx.criteria.PopulationUniqueness;
import org.deidentifier.arx.io.CSVHierarchyInput;
import org.deidentifier.arx.metric.Metric;
import org.deidentifier.arx.metric.Metric.AggregateFunction;
import org.deidentifier.arx.risk.ModelPitman;
import org.deidentifier.arx.risk.RiskModelPopulationUniqueness.PopulationUniquenessModel;

import de.linearbits.subframe.Benchmark;
import de.linearbits.subframe.analyzer.ValueBuffer;

/**
 * Experiment to evaluate the impact of optimizations.
 *
 * @author Fabian Prasser
 * @author Florian Kohlmayer
 */
public abstract class ExperimentImpactPolygamma {


    /** The benchmark instance */
    private static final Benchmark BENCHMARK   = new Benchmark(new String[] { "Dataset"});
    /** TOTAL */
    public static final int         TIME     = BENCHMARK.addMeasure("time");
    /** TOTAL */
    public static final int         CHECKS     = BENCHMARK.addMeasure("checks");
    /** VALUE */
    private static final double[][] SOLVER_START_VALUES    = getSolverStartValues();
    /** VALUE */
    private static final double     POPULATION_USA         = 318.9 * Math.pow(10d, 6d);
    /** VALUE */
    private static final int        REPETITIONS            = 3;
    /** START_INDEX */
    private static int              START_INDEX            = 0;

    /**
     * Main
     * @param args
     * @throws IOException
     */
    public static void main(String[] args) throws IOException {
        
        ModelPitman.USE_POLYGAMMA = false;
        
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
        BENCHMARK.addAnalyzer(TIME, new ValueBuffer());
        BENCHMARK.addAnalyzer(CHECKS, new ValueBuffer());

        // Perform
        String[] datasets = new String[] { "adult", "cup", "fars", "atus", "ihis" };
        for (int i = START_INDEX; i < datasets.length; i++) {
            System.out.println(datasets[i]);
            BENCHMARK.addRun(datasets[i]);
            analyze(datasets[i]);
            BENCHMARK.getResults().write(new File("results/impact-no-polygamma.csv"));
        }
    }

    private static void analyze(String dataset) throws IOException {
        
        Data data = getDataObject(dataset);
        
        // Uniqueness
        ARXConfiguration config = ARXConfiguration.create();
        config.setMetric(Metric.createPrecomputedLossMetric(1.0d, 0.5d, AggregateFunction.GEOMETRIC_MEAN));
        config.setMaxOutliers(1d);
        config.addCriterion(new PopulationUniqueness(0.01d,
                                                     PopulationUniquenessModel.PITMAN,
                                                     ARXPopulationModel.create((long)POPULATION_USA), 
                                                     ARXSolverConfiguration.create().preparedStartValues(SOLVER_START_VALUES)
                                                     .iterationsPerTry(10)));
        
        ARXAnonymizer anonymizer = new ARXAnonymizer();

        // Warmup
        ARXResult result = anonymizer.anonymize(data, config);
        data.getHandle().release();

        int checks = 0;
        long time = System.currentTimeMillis();
        for (int i=0; i<REPETITIONS; i++) {
            System.out.println((" - Run: " + (i+1) + " of " + REPETITIONS));
            result = anonymizer.anonymize(data, config);
            if (i==0) {
                for (ARXNode[] level : result.getLattice().getLevels()) {
                    for (ARXNode node : level) {
                        if (node.isChecked()) {
                            checks++;
                        }
                    }
                }
            }
            data.getHandle().release();
        }
        double timeUniqueness = (double)(System.currentTimeMillis() - time) / (double)REPETITIONS;
        
        BENCHMARK.addValue(TIME, timeUniqueness);
        BENCHMARK.addValue(CHECKS, checks);
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
