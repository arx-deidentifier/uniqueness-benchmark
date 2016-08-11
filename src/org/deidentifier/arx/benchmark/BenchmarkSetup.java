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

import java.io.IOException;
import java.nio.charset.Charset;

import org.deidentifier.arx.AttributeType.Hierarchy;
import org.deidentifier.arx.Data;

/**
 * This class encapsulates most of the parameters of a benchmark run
 * @author Fabian Prasser
 */
public class BenchmarkSetup {
    
    public static enum BenchmarkDataset {
        ADULT {
            @Override
            public String toString() {
                return "Adult";
            }
        },
        CUP {
            @Override
            public String toString() {
                return "Cup";
            }
        },
        FARS {
            @Override
            public String toString() {
                return "Fars";
            }
        },
        ATUS {
            @Override
            public String toString() {
                return "Atus";
            }
        },
        IHIS {
            @Override
            public String toString() {
                return "Ihis";
            }
        },
    }
    
    public static enum BenchmarkPrivacyModel {
        K_ANONYMITY {
            @Override
            public String toString() {
                return "k-anonymity";
            }
        },
        UNIQUENESS_DANKAR {
            @Override
            public String toString() {
                return "p-uniqueness (dankar)";
            }
        },
        UNIQUENESS_PITMAN {
            @Override
            public String toString() {
                return "p-uniqueness (pitman)";
            }
        },
        UNIQUENESS_SNB {
            @Override
            public String toString() {
                return "p-uniqueness (snb)";
            }
        },
        UNIQUENESS_ZAYATZ {
            @Override
            public String toString() {
                return "p-uniqueness (zayatz)";
            }
        }, 
        UNIQUENESS_SAMPLE {
            @Override
            public String toString() {
                return "p-sample-uniqueness";
            }
        },
    }
    
    public static enum BenchmarkUtilityMeasure {
        ENTROPY {
            @Override
            public String toString() {
                return "Entropy";
            }
        },
        LOSS {
            @Override
            public String toString() {
                return "Loss";
            }
        },
    }
    
    /**
     * Configures and returns the dataset
     * @param dataset
     * @param criteria
     * @return
     * @throws IOException
     */
    
    public static Data getData(BenchmarkDataset dataset) throws IOException {
        Data data = null;
        switch (dataset) {
        case ADULT:
            data = Data.create("data/adult.csv", Charset.defaultCharset(), ';');
            break;
        case ATUS:
            data = Data.create("data/atus.csv", Charset.defaultCharset(), ';');
            break;
        case CUP:
            data = Data.create("data/cup.csv", Charset.defaultCharset(), ';');
            break;
        case FARS:
            data = Data.create("data/fars.csv", Charset.defaultCharset(), ';');
            break;
        case IHIS:
            data = Data.create("data/ihis.csv", Charset.defaultCharset(), ';');
            break;
        default:
            throw new RuntimeException("Invalid dataset");
        }
        
        for (String qi : getQuasiIdentifyingAttributes(dataset)) {
            data.getDefinition().setAttributeType(qi, getHierarchy(dataset, qi));
        }
        
        return data;
    }
    
    /**
     * Returns all datasets
     * @return
     */
    public static BenchmarkDataset[] getDatasets() {
        return new BenchmarkDataset[] {
                BenchmarkDataset.ADULT,
                BenchmarkDataset.CUP,
                BenchmarkDataset.FARS,
                BenchmarkDataset.ATUS,
                BenchmarkDataset.IHIS
        };
    }
    
    /**
     * Returns the generalization hierarchy for the dataset and attribute
     * @param dataset
     * @param attribute
     * @return
     * @throws IOException
     */
    public static Hierarchy getHierarchy(BenchmarkDataset dataset, String attribute) throws IOException {
        switch (dataset) {
        case ADULT:
            return Hierarchy.create("hierarchies/adult_hierarchy_" + attribute + ".csv", Charset.defaultCharset(), ';');
        case ATUS:
            return Hierarchy.create("hierarchies/atus_hierarchy_" + attribute + ".csv", Charset.defaultCharset(), ';');
        case CUP:
            return Hierarchy.create("hierarchies/cup_hierarchy_" + attribute + ".csv", Charset.defaultCharset(), ';');
        case FARS:
            return Hierarchy.create("hierarchies/fars_hierarchy_" + attribute + ".csv", Charset.defaultCharset(), ';');
        case IHIS:
            return Hierarchy.create("hierarchies/ihis_hierarchy_" + attribute + ".csv", Charset.defaultCharset(), ';');
        default:
            throw new RuntimeException("Invalid dataset");
        }
    }
    
    public static int getK(double uniqueness) {
        if (uniqueness == 0.001d) {
            return 3;
        } else if (uniqueness == 0.002d) {
            return 4;
        } else if (uniqueness == 0.003d) {
            return 5;
        } else if (uniqueness == 0.004d) {
            return 10;
        } else if (uniqueness == 0.005d) {
            return 15;
        } else if (uniqueness == 0.006d) {
            return 20;
        } else if (uniqueness == 0.007d) {
            return 25;
        } else if (uniqueness == 0.008d) {
            return 50;
        } else if (uniqueness == 0.009d) {
            return 75;
        } else if (uniqueness == 0.01d) {
            return 100;
        } else {
            throw new IllegalArgumentException("Unknown uniqueness parameter");
        }
    }
    
    /**
     * Returns the quasi-identifiers for the dataset
     * @param dataset
     * @return
     */
    public static String[] getQuasiIdentifyingAttributes(BenchmarkDataset dataset) {
        switch (dataset) {
        case ADULT:
            return new String[] {   "age",
                                    "education",
                                    "marital-status",
                                    "native-country",
                                    "race",
                                    "salary-class",
                                    "sex",
                                    "workclass",
                                    "occupation" };
        case ATUS:
            return new String[] {   "Age",
                                    "Birthplace",
                                    "Citizenship status",
                                    "Labor force status",
                                    "Marital status",
                                    "Race",
                                    "Region",
                                    "Sex",
                                    "Highest level of school completed" };
        case CUP:
            return new String[] {   "AGE",
                                    "GENDER",
                                    "INCOME",
                                    "MINRAMNT",
                                    "NGIFTALL",
                                    "STATE",
                                    "ZIP",
                                    "RAMNTALL" };
        case FARS:
            return new String[] {   "iage",
                                    "ideathday",
                                    "ideathmon",
                                    "ihispanic",
                                    "iinjury",
                                    "irace",
                                    "isex",
                                    "istatenum" };
        case IHIS:
            return new String[] {   "AGE",
                                    "MARSTAT",
                                    "PERNUM",
                                    "QUARTER",
                                    "RACEA",
                                    "REGION",
                                    "SEX",
                                    "YEAR",
                                    "EDUC" };
        default:
            throw new RuntimeException("Invalid dataset");
        }
    }

    /**
     * Returns a set of utility measures
     * @return
     */
    public static BenchmarkUtilityMeasure[] getUtilityMeasures() {
        return new BenchmarkUtilityMeasure[]{BenchmarkUtilityMeasure.ENTROPY,
                                             BenchmarkUtilityMeasure.LOSS};
    }

}
