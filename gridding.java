// Simcenter STAR-CCM+ macro: meshing_with_volume_controls.java
// Written by Simcenter STAR-CCM+ 17.04.008

// This subroutine is responsible for meshing

package macro;

import java.util.*;

import star.common.*;
import star.base.neo.*;
import star.vis.*;
import star.cadmodeler.*;
import star.trimmer.*;
import star.meshing.*;


public class gridding extends StarMacro {

    public void execute() {
        // Read mesh config file
        

        // Setup meshing operation
        Simulation simulation = getActiveSimulation();
        create_operation(simulation);
    }

    private void create_operation(Simulation simulation) {
        SolidModelPart solidModelPart_0 = ((SolidModelPart) simulation.get(SimulationPartManager.class).getPart("FormulaStudentCar"));
        AutoMeshOperation autoMeshOperation_0 = simulation.get(MeshOperationManager.class).createAutoMeshOperation(new StringVector(new String[] {"star.resurfacer.ResurfacerAutoMesher", "star.trimmer.TrimmerAutoMesher", "star.prismmesher.PrismAutoMesher"}), new NeoObjectVector(new Object[] {solidModelPart_0}));
        autoMeshOperation_0.getMesherParallelModeOption().setSelected(MesherParallelModeOption.Type.PARALLEL);
    }
    
}
