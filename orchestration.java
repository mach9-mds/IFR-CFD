// Simcenter STAR-CCM+ macro: macro_in_macro.java
// Written by Simcenter STAR-CCM+ 17.04.008

// This is the main orchestration script to run from star ccm. 
// This will run all required subroutines based on an input .json file.

package macro;
import java.util.*;

import javax.swing.JFileChooser;

import star.common.*;
import star.base.neo.*;
import java.io.*;
import java.nio.file.Path;

public class orchestration extends StarMacro {

  public void execute() {

    // Geometry import
    new StarScript(getActiveRootObject(), new File(resolvePath("import_geom.java"))).play();

    // Create global parameters
    new StarScript(getActiveRootObject(), new File(resolvePath("global_params.java"))).play();

    // Create physics continuum
    new StarScript(getActiveRootObject(), new File(resolvePath("create_physics.java"))).play();

    // Create regions and bcs
    new StarScript(getActiveRootObject(), new File(resolvePath("create_regions.java"))).play();

    // Meshing
    new StarScript(getActiveRootObject(), new File(resolvePath("gridding.java"))).play();

    // Initialisation
    new StarScript(getActiveRootObject(), new File(resolvePath("initialise.java"))).play();

  }
}