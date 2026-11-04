// Simcenter STAR-CCM+ macro: temp.java
// Written by Simcenter STAR-CCM+ 20.06.010
package macro;

import java.util.*;

import star.common.*;
import star.base.neo.*;
import star.vis.*;
import star.flow.*;

public class temp extends StarMacro {

  public void execute() {
    execute0();
  }

  private void execute0() {

    Simulation simulation_0 = 
      getActiveSimulation();

    simulation_0.getSceneManager().createVectorScene("Vector Scene", "Outline", "Vector", null);

    Scene scene_3 = 
      simulation_0.getSceneManager().getScene("Vector Scene 1");

    scene_3.resetCamera();

    scene_3.setPresentationName("assdaf");

    VectorDisplayer vectorDisplayer_0 = 
      ((VectorDisplayer) scene_3.getDisplayerManager().getObject("Vector 1"));

    vectorDisplayer_0.setDisplayMode(VectorDisplayMode.VECTOR_DISPLAY_MODE_LIC);

    vectorDisplayer_0.setColorMode(VectorColorMode.SCALAR);

    PressureCoefficientFunction pressureCoefficientFunction_0 = 
      ((PressureCoefficientFunction) simulation_0.getFieldFunctionManager().getFunction("PressureCoefficient"));

    vectorDisplayer_0.getColoringScalar().setFieldFunction(pressureCoefficientFunction_0);

    vectorDisplayer_0.getInputParts().setQuery(null);

    PlaneSection planeSection_3 = 
      ((PlaneSection) simulation_0.getPartManager().getObject("Plane_XY"));

    vectorDisplayer_0.getInputParts().setObjects(planeSection_3);

    PartDisplayer partDisplayer_0 = 
      ((PartDisplayer) scene_3.getDisplayerManager().getObject("Outline 1"));

    partDisplayer_0.setVisibilityOverrideMode(DisplayerVisibilityOverride.HIDE_ALL_PARTS);

    SymmetricRepeat symmetricRepeat_0 = 
      ((SymmetricRepeat) simulation_0.getTransformManager().getObject("Domain.L0 1"));

    vectorDisplayer_0.setVisTransform(symmetricRepeat_0);

    CurrentView currentView_2 = 
      scene_3.getCurrentView();

    currentView_2.setInput(new DoubleVector(new double[] {2.0987936340033, -0.9400426541335867, -0.06343727268227184}), new DoubleVector(new double[] {2.0987936340033, -0.9400426541335867, 24.434426912436486}), new DoubleVector(new double[] {0.0, 1.0, 0.0}), 9.246621004453472, 0, 30.0);

    currentView_2.setInput(new DoubleVector(new double[] {1.9058932451580917, -0.2072127323460721, -0.003306062513697583}), new DoubleVector(new double[] {1.9058932451580917, -0.2072127323460721, 12.99951301719034}), new DoubleVector(new double[] {0.0, 1.0, 0.0}), 9.246621004453472, 0, 30.0);

    LICSettings lICSettings_0 = 
      vectorDisplayer_0.getLICSettings();

    lICSettings_0.setLineLength(1.0);

    lICSettings_0.setNoiseFrequency(0.317);

    currentView_2.setInput(new DoubleVector(new double[] {0.5012885064794717, 0.2006524759866265, -0.0034763905241597115}), new DoubleVector(new double[] {0.5012885064794717, 0.2006524759866265, 2.9970085644531728}), new DoubleVector(new double[] {0.0, 1.0, 0.0}), 9.246621004453472, 0, 30.0);

    lICSettings_0.setIntensity(0.115);

    lICSettings_0.setIntensity(0.545);

    lICSettings_0.setIntensity(0.345);

    lICSettings_0.setIntensity(0.449);
  }
}
