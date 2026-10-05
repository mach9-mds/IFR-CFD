// Simcenter STAR-CCM+ macro: gsi_on.java
// Written by Simcenter STAR-CCM+ 17.04.008

// This subroutine is used to set up and run initialisation

package macro;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.*;
import java.util.AbstractMap.SimpleImmutableEntry;

import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.Json;
import javax.json.JsonArray;

import star.common.*;
import star.base.neo.*;
import star.coupledflow.*;


public class initialise extends StarMacro {
    
    public void execute() {

        Simulation simulation = getActiveSimulation();

        // Read config file
        String init_method = read_json();

        // If GSI
        if (init_method.startsWith("GSI")) {
            int levels = 10;
            String[] parts = init_method.split(",");
            if (parts.length > 1) {
                try {
                    levels = Integer.parseInt(parts[1].trim());
                } catch (NumberFormatException exception) {
                    simulation.println("(WW) Invalid GSI levels, defaulting to 10");
                    levels = 10;
                }
            }
            simulation.println("(II) Using GSI initialisation with " + levels + " levels");
            enable_GSI(simulation, levels);
        } else {
            simulation.println("(II) Using constant initialisation");
        }
    }

    private String read_json() {
        Path configPath = Paths.get(resolvePath("case.json")).toAbsolutePath().normalize();
        String init_method;
        
        try (InputStream input = Files.newInputStream(configPath);
                JsonReader reader = Json.createReader(input)) {
            JsonObject json = reader.readObject();
            init_method = json.getString("initialise");
            } catch (IOException | RuntimeException exception) {
                throw new IllegalArgumentException("Could not read case.json: " + exception.getMessage(), exception);
            }

        return init_method;
    }

    private void enable_GSI(Simulation simulation, int levels) {
        // Currently will break if not running coupled solver
        CoupledImplicitSolver coupledImplicitSolver_0 = ((CoupledImplicitSolver) simulation.getSolverManager().getSolver(CoupledImplicitSolver.class));
        coupledImplicitSolver_0.getExpertInitManager().getExpertInitOption().setSelected(ExpertInitOption.Type.GRID_SEQ_METHOD);
        GridSequencingInit gridSequencingInit_0 = ((GridSequencingInit) coupledImplicitSolver_0.getExpertInitManager().getInit());
        gridSequencingInit_0.setMaxGSLevels(levels);
        simulation.println("(II) GSI enabled");
    }

}
