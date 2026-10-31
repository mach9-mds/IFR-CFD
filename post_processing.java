// Simcenter STAR-CCM+ macro: macro.java
// Written by Simcenter STAR-CCM+ 20.06.010
package macro;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import javax.swing.Action;

import star.common.*;
import star.base.neo.*;
import star.screenplay.operations.*;
import star.vis.*;
import star.cadmodeler.*;
import star.screenplay.*;
import star.screenplay.operations.vis.*;
import star.screenplay.vis.*;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonArray;

public class post_processing extends StarMacro {

  public void execute() {
        JsonObject config = verify_json();
        Simulation simulation = getActiveSimulation();
        SetDerivedPart(config, simulation);
  }

  private JsonObject verify_json() {
    Path configPath = Paths.get(resolvePath("case.json")).toAbsolutePath().normalize();
      JsonObject json;

      try (InputStream input = Files.newInputStream(configPath);
              JsonReader reader = Json.createReader(input)) {
          json = reader.readObject();
          } catch (IOException | RuntimeException exception) {
              throw new IllegalArgumentException("Could not read case.json: " + exception.getMessage(), exception);
          }

      return json;
  }



  //set derived parts
  private void SetDerivedPart(JsonObject config, Simulation simulation)
  {
    JsonObject sections = config.getJsonObject("PostProcessing").getJsonObject("PlaneSections");
    JsonObject xy = sections.getJsonObject("Plane_XY");
    JsonObject xz = sections.getJsonObject("Plane_XZ");
    JsonObject yz = sections.getJsonObject("Plane_YZ");


    double min_z = xy.getJsonNumber("min").doubleValue();
    double max_z = xy.getJsonNumber("max").doubleValue();

    double min_y = xz.getJsonNumber("min").doubleValue();
    double max_y = xz.getJsonNumber("max").doubleValue();

    double min_x = yz.getJsonNumber("min").doubleValue();
    double max_x = yz.getJsonNumber("max").doubleValue();

    PlaneSection Plane_XY = 
      (PlaneSection) simulation.getPartManager().createImplicitPart(new ArrayList<>(Collections.<NamedObject>emptyList()), new DoubleVector(new double[] {0.0, 0.0, 1.0}), new DoubleVector(new double[] {0.0, 0.0, min_z}), 0, 1, new DoubleVector(new double[] {0.0}), null);

    Region region_0 = 
      simulation.getRegionManager().getRegion("FormulaStudentCar");
    Plane_XY.getInputParts().setQuery(null);
    Plane_XY.getInputParts().setObjects(region_0);

    PlaneSection Plane_XZ = 
      (PlaneSection) simulation.getPartManager().createImplicitPart(new ArrayList<>(Collections.<NamedObject>emptyList()), new DoubleVector(new double[] {0.0, 1.0, 0.0}), new DoubleVector(new double[] {0.0, min_y, 0.0}), 0, 1, new DoubleVector(new double[] {0.0}), null);

    Plane_XZ.getInputParts().setQuery(null);
    Plane_XZ.getInputParts().setObjects(region_0);

    PlaneSection Plane_YZ = 
      (PlaneSection) simulation.getPartManager().createImplicitPart(new ArrayList<>(Collections.<NamedObject>emptyList()), new DoubleVector(new double[] {1.0, 0.0, 0.0}), new DoubleVector(new double[] {min_x, 0.0, 0.0}), 0, 1, new DoubleVector(new double[] {0.0}), null);

    Plane_YZ.getInputParts().setQuery(null);
    Plane_YZ.getInputParts().setObjects(region_0);

    Plane_XY.setPresentationName("Plane_XY");
    Plane_XZ.setPresentationName("Plane_XZ");
    Plane_YZ.setPresentationName("Plane_YZ"); 

  }
}