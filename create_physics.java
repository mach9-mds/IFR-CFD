// Simcenter STAR-CCM+ macro: create_physics.java
// Written by Simcenter STAR-CCM+ 17.04.008

// This subroutine creates a physics continuum from physics.json


package macro;

import java.util.*;

import star.common.*;
import star.material.*;
import star.coupledflow.*;
import star.turbulence.*;
import star.flow.*;
import star.kwturb.*;
import star.metrics.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;



public class create_physics extends StarMacro {

    public void execute() {
        // First verify physics.json
        JsonObject config = verify_json();
        getActiveSimulation().println("(II) physics config read successfully");

        // Set up continuum
        createPhysics(config);
        getActiveSimulation().println("(II) physics continuum created");

        // Set physics ICs
        setICs();
        getActiveSimulation().println("(II) physics initial conditions set");

    }

    public JsonObject verify_json() {
        // json verification
        Path configPath = Paths.get(resolvePath("physics.json")).toAbsolutePath().normalize();

        JsonObject json;

        try (InputStream input = Files.newInputStream(configPath);
                JsonReader reader = Json.createReader(input)) {
            json = reader.readObject();
            json.getString("turbModel");
            json.getString("gasModel");
            json.getBoolean("coupled");
            } catch (IOException | RuntimeException exception) {
                throw new IllegalArgumentException("Could not read case.json: " + exception.getMessage(), exception);
            }

        return json;
    }

    public void createPhysics(JsonObject json) {
        // Setting up physics continuum
        Simulation simulation_0 = getActiveSimulation();
        simulation_0.println("(II) Creating physics continuum . . .");

        PhysicsContinuum physicsContinuum_1 = 
        simulation_0.getContinuumManager().createContinuum(PhysicsContinuum.class);

        physicsContinuum_1.enable(ThreeDimensionalModel.class);
        physicsContinuum_1.enable(SingleComponentGasModel.class);
        physicsContinuum_1.enable(SteadyModel.class);

        Boolean coupled = json.getBoolean("coupled");
        if (coupled) {
            physicsContinuum_1.enable(CoupledFlowModel.class);
            simulation_0.println("(II) Using coupled solver");
        }
        
        String gasModel = json.getString("gasModel");
        if ("constantDens".equals(gasModel)) {
            physicsContinuum_1.enable(ConstantDensityModel.class);
            simulation_0.println("(II) Using constant density gas model");
        }

        String turbModel = json.getString("turbModel");
        if ("kwSST".equals(turbModel)) {
            physicsContinuum_1.enable(TurbulentModel.class);
            physicsContinuum_1.enable(RansTurbulenceModel.class);
            physicsContinuum_1.enable(KOmegaTurbulence.class);
            physicsContinuum_1.enable(SstKwTurbModel.class);
            physicsContinuum_1.enable(KwAllYplusWallTreatment.class);
            simulation_0.println("(II) Using KwSST turbulence model");
        } 

    }

    public void setICs() {
        // Setting initial conditions
        Simulation simulation_0 = getActiveSimulation();
        simulation_0.println("(II) Setting initial conditions from global parameters");

        Units units_0 = simulation_0.getUnitsManager().getPreferredUnits(Dimensions.Builder().velocity(1).build());
        Units units_1 = simulation_0.getUnitsManager().getPreferredUnits(Dimensions.Builder().build());
        Units units_2 = simulation_0.getUnitsManager().getPreferredUnits(Dimensions.Builder().length(1).build());

        PhysicsContinuum physicsContinuum_0 = ((PhysicsContinuum) simulation_0.getContinuumManager().getContinuum("Physics 1"));

        // Turb intensity
        TurbulenceIntensityProfile turbulenceIntensityProfile_0 = physicsContinuum_0.getInitialConditions().get(TurbulenceIntensityProfile.class);
        turbulenceIntensityProfile_0.getMethod(ConstantScalarProfileMethod.class).getQuantity().setDefinition("${IFR_turb_intensity}");

        // Turb specification
        physicsContinuum_0.getInitialConditions().get(KwTurbSpecOption.class).setSelected(KwTurbSpecOption.Type.INTENSITY_LENGTH_SCALE);
        TurbulentLengthScaleProfile turbulentLengthScaleProfile_0 = physicsContinuum_0.getInitialConditions().get(TurbulentLengthScaleProfile.class);
        turbulentLengthScaleProfile_0.getMethod(ConstantScalarProfileMethod.class).getQuantity().setDefinition("${IFR_turb_length}");
        TurbulentVelocityScaleProfile turbulentVelocityScaleProfile_0 = 
        physicsContinuum_0.getInitialConditions().get(TurbulentVelocityScaleProfile.class);
        turbulentVelocityScaleProfile_0.getMethod(ConstantScalarProfileMethod.class).getQuantity().setDefinition("${IFR_turb_vel}");
        
        // Velocity
        VelocityProfile velocityProfile_0 = 
        physicsContinuum_0.getInitialConditions().get(VelocityProfile.class);
        velocityProfile_0.getMethod(ConstantVectorProfileMethod.class).getQuantity().setDefinition("$${IFR_vel}");
    }
    
}
