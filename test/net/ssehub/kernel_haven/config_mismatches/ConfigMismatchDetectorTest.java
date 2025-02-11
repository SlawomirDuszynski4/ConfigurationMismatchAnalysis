/*
 * Copyright 2017-2019 University of Hildesheim, Software Systems Engineering
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.ssehub.kernel_haven.config_mismatches;

import static net.ssehub.kernel_haven.util.logic.FormulaBuilder.and;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

import net.ssehub.kernel_haven.SetUpException;
import net.ssehub.kernel_haven.analysis.AnalysisComponent;
import net.ssehub.kernel_haven.code_model.CodeBlock;
import net.ssehub.kernel_haven.code_model.CodeElement;
import net.ssehub.kernel_haven.code_model.SourceFile;
import net.ssehub.kernel_haven.fe_analysis.Settings.SimplificationType;
import net.ssehub.kernel_haven.fe_analysis.fes.FeatureEffectFinder;
import net.ssehub.kernel_haven.fe_analysis.pcs.PcFinder;
import net.ssehub.kernel_haven.logic_utils.AdamsAwesomeSimplifier;
import net.ssehub.kernel_haven.test_utils.TestAnalysisComponentProvider;
import net.ssehub.kernel_haven.test_utils.TestConfiguration;
import net.ssehub.kernel_haven.util.logic.Variable;
import net.ssehub.kernel_haven.util.null_checks.NonNull;
import net.ssehub.kernel_haven.variability_model.VariabilityModel;
import net.ssehub.kernel_haven.variability_model.VariabilityModelDescriptor.ConstraintFileType;
import net.ssehub.kernel_haven.variability_model.VariabilityVariable;

/**
 * Tests the {@link ConfigMismatchDetector}.
 * @author El-Sharkawy
 * @author Slawomir Duszynski
 *
 */
@SuppressWarnings("null")
public class ConfigMismatchDetectorTest extends AbstractFinderTests<ConfigMismatchResult> {

    private AnalysisComponent<VariabilityModel> vm;
    
    private final Variable varA = new Variable("ALPHA");
    private final Variable varB = new Variable("BETA");
    private final Variable varG = new Variable("GAMMA");
    
    /**
     * Tests if a contradiction is detected.
     */
    @Test
    public void testDetectionOfContradiction() {
        // Load Variability Model: not A, B
        setVarModel(new File("testdata/NotAAndB.cnf"));
        
        // Mock code file: B is nested in A
        CodeBlock element = prepareNesting(varB, varA);
        List<ConfigMismatchResult> results = detectConfigMismatches(element);
        
        Assert.assertEquals(2, results.size());
        // Variable A is not problematic
        assertFacts(results.get(0), varA.getName(), "1", MismatchResultType.FORMULA_MORE_GENERAL);
        // Problematic variable is B: its effect contradicts the variability model
        assertFacts(results.get(1), varB.getName(), "ALPHA", MismatchResultType.CONTRADICTION);
    }
 
    /**
     * Tests if a dead feature in the overlap is detected.
     */
    @Test
    public void testDetectionOfDeadOverlap() {
        // Load Variability Model: A conflicts B
        setVarModel(new File("testdata/AConflictsB.cnf"));
        
        // Mock code file: B is nested in A; B => A
        CodeBlock element = prepareNesting(varB, varA);
        List<ConfigMismatchResult> results = detectConfigMismatches(element);
        
        Assert.assertEquals(2, results.size());
        // Variable A is not problematic
        assertFacts(results.get(0), varA.getName(), "1", MismatchResultType.FORMULA_MORE_GENERAL);
        // Problematic variable is B, which can only be dead
        assertFacts(results.get(1), varB.getName(), "ALPHA", MismatchResultType.PARTIAL_OVERLAP_DEAD);
    }    
    
    /**
     * Tests if a more general feature model is detected.
     */
    @Test
    public void testDetectionOfFmMoreGeneral() {
        // Load Variability Model: A => B
        setVarModel(new File("testdata/ANestedInB.cnf"));
        
        // Mock code file: A is nested in B and G
        CodeBlock element = new CodeBlock(varG);
        CodeBlock nestedElement = new CodeBlock(and(varB, varG));
        CodeBlock nestedElement2 = new CodeBlock(and(and(varB, varG), varA));
        nestedElement.addNestedElement(nestedElement2);
        element.addNestedElement(nestedElement);
        List<ConfigMismatchResult> results = detectConfigMismatches(element);
        
        Assert.assertEquals(3, results.size());
        // Variable A
        assertFacts(results.get(0), varA.getName(), "BETA && GAMMA", MismatchResultType.VM_MORE_GENERAL);
        // Variable B
        assertFacts(results.get(1), varB.getName(), "GAMMA", MismatchResultType.PARTIAL_OVERLAP);
        // Variable G is not having any effect
        assertFacts(results.get(2), varG.getName(), "1", MismatchResultType.CONSISTENT);
    }
    
    /**
     * Tests if a more general feature effect is detected.
     */
    @Test
    public void testDetectionOfEffectMoreGeneral() {
        // Load Variability Model: A <=> B
        setVarModel(new File("testdata/AEqualsB.cnf"));
        
        // Mock code file: A is nested in B
        CodeBlock element = prepareNesting(varA, varB);
        List<ConfigMismatchResult> results = detectConfigMismatches(element);
        
        Assert.assertEquals(2, results.size());
        // Variable A
        assertFacts(results.get(0), varA.getName(), "BETA", MismatchResultType.FORMULA_MORE_GENERAL);
        // Variable B
        assertFacts(results.get(1), varB.getName(), "1", MismatchResultType.FORMULA_MORE_GENERAL);
    }
    
     /**
     * Tests if a code nesting, opposite to the nesting in the variability model can be identified as partial overlap.
     */
    @Test
    public void testDetectionOfPartialOverlap1() {
        // Load Variability Model: A is nested in B
        setVarModel(new File("testdata/ANestedInB.cnf"));
        
        // Mock code file: B is nested in A
        CodeBlock element = prepareNesting(varB, varA);
        List<ConfigMismatchResult> results = detectConfigMismatches(element);
        
        // One mismatch detected
        Assert.assertEquals(2, results.size());
        // Variable A is not problematic
        assertFacts(results.get(0), varA.getName(), "1", MismatchResultType.FORMULA_MORE_GENERAL);
        // Problematic variable is B
        assertFacts(results.get(1), varB.getName(), "ALPHA", MismatchResultType.PARTIAL_OVERLAP);
    }
    
    /**
     * Tests if a code nesting, referring to a different variable can be identified as partial overlap.
     */
    @Test
    public void testDetectionOfPartialOverlap2() {
        // Load Variability Model: A is nested in B
        setVarModel(new File("testdata/ANestedInB.cnf"));
        
        // Mock code file: G is nested in B; G => B
        CodeBlock element = prepareNesting(varG, varB);
        List<ConfigMismatchResult> results = detectConfigMismatches(element);
        
        Assert.assertEquals(2, results.size());
        // Variable B
        assertFacts(results.get(0), varB.getName(), "1", MismatchResultType.CONSISTENT);
        // Variable G
        assertFacts(results.get(1), varG.getName(), "BETA", MismatchResultType.PARTIAL_OVERLAP);
        //PARTIAL_OVERLAP because: 
        //   - A=1 B=0 G=0 allowed by f.effect, but not by the model
        //   - A=0 B=0 G=1 allowed by the model, but not by the f.effect
    }
    
    
    /**
     * Tests if a code nesting is not reported if it is used in the same way as specified by the variability model.
     */
    @Test
    public void testNoFalseReportForCorrectUsage() {
        // Load Variability Model: A is nested in B
        setVarModel(new File("testdata/ANestedInB.cnf"));
        
        // Mock code file: A is nested in B
        CodeBlock element = prepareNesting(varA, varB);
        List<ConfigMismatchResult> results = detectConfigMismatches(element);
        
        Assert.assertEquals(2, results.size());
        assertFacts(results.get(0), varA.getName(), "BETA", MismatchResultType.CONSISTENT);
        assertFacts(results.get(1), varB.getName(), "1", MismatchResultType.CONSISTENT);
    }
    
    /**
     * Tests if free variable in model, which can be used in all situations in code is consistent.
     */
    @Test
    public void testUnConstrainedVariableIsConsistent() {
        // Load Variability Model: A is nested in B
        setVarModel(new File("testdata/ANestedInB.cnf"));
        
        // Mock code file: only GAMMA in one block
        CodeBlock element = new CodeBlock(varG);
        List<ConfigMismatchResult> results = detectConfigMismatches(element);
        
        Assert.assertEquals(1, results.size());
        assertFacts(results.get(0), varG.getName(), "1", MismatchResultType.CONSISTENT);
    }
    
    /**
     * Tests unspecified variable found.
     */
    @Test
    public void testUnDefinedVariable() {
        // Load Variability Model: A is nested in B
        setVarModel(new File("testdata/ANestedInB.cnf"));
        
        // Mock code file: only A_UNDEFINED_VAR in one block
        Variable varU = new Variable("A_UNDEFINED_VAR");
        CodeBlock element = new CodeBlock(varU);
        List<ConfigMismatchResult> results = detectConfigMismatches(element);
        
        // One mismatch detected
        Assert.assertEquals(1, results.size());
        assertFacts(results.get(0), varU.getName(), "1", MismatchResultType.VARIABLE_NOT_DEFINED);
    }
    
    /**
     * Tests unspecified variable in constraint found.
     */
    @Test
    public void testUnDefinedConstraints() {
        // Load Variability Model: A is nested in B
        setVarModel(new File("testdata/ANestedInB.cnf"));
        
        // Mock code file: B is nested in A_UNDEFINED_VAR
        Variable varAUndef = new Variable("A_UNDEFINED_VAR");
        CodeBlock element = prepareNesting(varB, varAUndef);
        List<ConfigMismatchResult> results = detectConfigMismatches(element);
        
        Assert.assertEquals(2, results.size());
        // Variable A_UNDEFINED_VAR is not defined
        assertFacts(results.get(0), varAUndef.getName(), "1", MismatchResultType.VARIABLE_NOT_DEFINED);
        assertFacts(results.get(1), varB.getName(), "A_UNDEFINED_VAR", MismatchResultType.FORMULA_NOT_SUPPORTED);
    }

    /**
     * Loads and sets the variability model based on the given CNF file.
     * @param cnfFile A CNF representation of the variability model (must exist).
     */
    private void setVarModel(File cnfFile) {
        Assert.assertTrue("VarModel file does not exist: " + cnfFile.getAbsolutePath(), cnfFile.exists());
        
        Set<VariabilityVariable> variables = new HashSet<>();
        VariabilityVariable alpha = new VariabilityVariable("ALPHA", "bool", 1);
        variables.add(alpha);
        variables.add(new VariabilityVariable("BETA", "bool", 2));
        variables.add(new VariabilityVariable("GAMMA", "bool", 3));
        VariabilityModel varModel = new VariabilityModel(cnfFile, variables);
        varModel.getDescriptor().setConstraintFileType(ConstraintFileType.DIMACS);
        
        Assert.assertNotNull("Error: VariabilityModel not initialized.", varModel);
        try {
            vm = new TestAnalysisComponentProvider<VariabilityModel>(varModel);
        } catch (SetUpException e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
        }
    }

    private CodeBlock prepareNesting(Variable varBefore, Variable varAfter) { //variable => effectVariable
        CodeBlock element = new CodeBlock(varAfter);
        CodeBlock nestedElement = new CodeBlock(and(varAfter, varBefore));
        element.addNestedElement(nestedElement);
        return element;
    }
    
    private void assertFacts(ConfigMismatchResult var, String name, String effect, MismatchResultType mismatch) {
        Assert.assertEquals(name, var.getVariable());
       	Assert.assertEquals(effect, AdamsAwesomeSimplifier.simplify(var.getFeatureEffect()).toString());
        Assert.assertEquals(mismatch.getDescription(), var.getResult());
    }
    
    /**
     * Runs the {@link ConfigMismatchDetector} on the passed element and returns the result for testing.
     * @param element A mocked element, which should be analyzed by the {@link ConfigMismatchDetector}. 
     * @return The detected configuration mismatches.
     */
    private List<ConfigMismatchResult> detectConfigMismatches(CodeElement<?> element) {
        return super.runAnalysis(element, SimplificationType.NO_SIMPLIFICATION);
    }
    
    @Override
    protected AnalysisComponent<ConfigMismatchResult> callAnalysor(@NonNull TestConfiguration tConfig,
            @NonNull AnalysisComponent<SourceFile<?>> cmComponent) throws SetUpException {
        
        PcFinder pcFinder = new PcFinder(tConfig, cmComponent);
        FeatureEffectFinder feFinder = new FeatureEffectFinder(tConfig, pcFinder);
        ConfigMismatchDetector cmDetector = new ConfigMismatchDetector(tConfig, vm, feFinder);
        cmDetector.execute();
        
        return cmDetector;
    }

}
