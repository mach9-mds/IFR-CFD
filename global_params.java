// Simcenter STAR-CCM+ macro: global_params.java
// Written by Simcenter STAR-CCM+ 17.04.008

// This subroutine creates global parameters referenced downstream

package macro;

import java.util.*;

import star.common.*;
import star.base.neo.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonArray;

public class global_params extends StarMacro {
    
    public void execute() {
        // Read in case.json
        JsonObject config = verify_json();

        // Create parameters
        createParameters(config);

    }

    public JsonObject verify_json() {
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

    public void createParameters(JsonObject json) {
        Simulation simulation_0 = getActiveSimulation();
        simulation_0.println("(II) Creating global paramaters . . .");
        
        // Read params into types
        JsonObject params = json.getJsonObject("globalParams");

        double vel_mag = params.getJsonNumber("velocityMagnitude").doubleValue();
        JsonArray vel_dir = params.getJsonArray("velocityDirection");
        double dirX = vel_dir.getJsonNumber(0).doubleValue();
        double dirY = vel_dir.getJsonNumber(1).doubleValue();
        double dirZ = vel_dir.getJsonNumber(2).doubleValue();

        double turb_intens = params.getJsonNumber("turbulenceIntensity").doubleValue();
        double turb_length = params.getJsonNumber("turbulenceLength").doubleValue();
        double turb_vel = params.getJsonNumber("turbulenceVelocity").doubleValue();

        // Create units
        Units units_0 = simulation_0.getUnitsManager().getPreferredUnits(Dimensions.Builder().velocity(1).build());
        Units units_1 = simulation_0.getUnitsManager().getPreferredUnits(Dimensions.Builder().build());
        Units units_2 = simulation_0.getUnitsManager().getPreferredUnits(Dimensions.Builder().length(1).build());

        // Create parameters
        // Velocity
        simulation_0.get(GlobalParameterManager.class).createGlobalParameter(ScalarGlobalParameter.class, "Scalar");
        ScalarGlobalParameter scalarGlobalParameter_0 = ((ScalarGlobalParameter) simulation_0.get(GlobalParameterManager.class).getObject("Scalar"));
        scalarGlobalParameter_0.setPresentationName("IFR_vel_mag");
        scalarGlobalParameter_0.setDimensions(Dimensions.Builder().length(1).time(-1).build());
        scalarGlobalParameter_0.getQuantity().setValueAndUnits(vel_mag, units_0);

        simulation_0.get(GlobalParameterManager.class).createGlobalParameter(VectorGlobalParameter.class, "Vector");
        VectorGlobalParameter vectorGlobalParameter_0 = ((VectorGlobalParameter) simulation_0.get(GlobalParameterManager.class).getObject("Vector"));
        vectorGlobalParameter_0.setPresentationName("IFR_vel_dir");
        vectorGlobalParameter_0.getQuantity().setComponentsAndUnits(dirX, dirY, dirZ, units_1);

        simulation_0.get(GlobalParameterManager.class).createGlobalParameter(VectorGlobalParameter.class, "Vector");
        VectorGlobalParameter vectorGlobalParameter_1 = ((VectorGlobalParameter) simulation_0.get(GlobalParameterManager.class).getObject("Vector"));
        vectorGlobalParameter_1.setPresentationName("IFR_vel");
        vectorGlobalParameter_1.getQuantity().setDefinition("${IFR_vel_mag} * $${IFR_vel_dir}");
        vectorGlobalParameter_1.setDimensions(Dimensions.Builder().length(1).time(-1).build());

        //Turbulence
        simulation_0.get(GlobalParameterManager.class).createGlobalParameter(ScalarGlobalParameter.class, "Scalar");
        ScalarGlobalParameter scalarGlobalParameter_1 = ((ScalarGlobalParameter) simulation_0.get(GlobalParameterManager.class).getObject("Scalar"));
        scalarGlobalParameter_1.setPresentationName("IFR_turb_intensity");
        scalarGlobalParameter_1.getQuantity().setValueAndUnits(turb_intens, units_1);

        simulation_0.get(GlobalParameterManager.class).createGlobalParameter(ScalarGlobalParameter.class, "Scalar");
        ScalarGlobalParameter scalarGlobalParameter_2 = ((ScalarGlobalParameter) simulation_0.get(GlobalParameterManager.class).getObject("Scalar"));
        scalarGlobalParameter_2.setPresentationName("IFR_turb_length");
        scalarGlobalParameter_2.setDimensions(Dimensions.Builder().length(1).build());
        scalarGlobalParameter_2.getQuantity().setValueAndUnits(turb_length, units_2);

        simulation_0.get(GlobalParameterManager.class).createGlobalParameter(ScalarGlobalParameter.class, "Scalar");
        ScalarGlobalParameter scalarGlobalParameter_3 = ((ScalarGlobalParameter) simulation_0.get(GlobalParameterManager.class).getObject("Scalar"));
        scalarGlobalParameter_3.setPresentationName("IFR_turb_vel");
        scalarGlobalParameter_3.setDimensions(Dimensions.Builder().length(1).time(-1).build());
        scalarGlobalParameter_3.getQuantity().setValueAndUnits(turb_vel, units_0);
  
    }

}
