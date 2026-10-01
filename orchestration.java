// Simcenter STAR-CCM+ macro: macro_in_macro.java
// Written by Simcenter STAR-CCM+ 17.04.008

package macro;
import java.util.*;

import star.common.*;
import star.base.neo.*;
import java.io.*;

public class orchestration extends StarMacro {

  public void execute() {
    execute0();
  }

  private void execute0() {

    // Geometry import
    new StarScript(getActiveRootObject(), new File(resolvePath("import_geom.java"))).play();

    // Create parts
    new StarScript(getActiveRootObject(), new File(resolvePath("create_parts.java"))).play();

  }
}