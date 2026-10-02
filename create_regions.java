// Simcenter STAR-CCM+ macro: regions_bcs.java
// Written by Simcenter STAR-CCM+ 17.04.008

// This subroutine sets up the regions and BCs

package macro;

import java.util.*;

import star.common.*;
import star.base.neo.*;
import star.turbulence.*;
import star.cadmodeler.*;
import star.flow.*;
import star.kwturb.*;



public class create_regions extends StarMacro {
    
    public void execute() {
        assign_regions();

        Simulation simulation = getActiveSimulation();
        // (need to read region from json)
        Region region = simulation.getRegionManager().getRegion("FormulaStudentCar");
        set_standard_regions(region, simulation);

        // Add logic for wheels in future, will also need a dedicated layer name
    }

    private void assign_regions() {
        // Assign parts to regions, each surface individual region
        Simulation simulation_0 = getActiveSimulation();
        SolidModelPart solidModelPart_0 = ((SolidModelPart) simulation_0.get(SimulationPartManager.class).getPart("FormulaStudentCar"));
        simulation_0.getRegionManager().newRegionsFromParts(new NeoObjectVector(new Object[] {solidModelPart_0}), "OneRegionPerPart", null, "OneBoundaryPerPartSurface", null, "OneFeatureCurve", null, RegionManager.CreateInterfaceMode.BOUNDARY, "OneEdgeBoundaryPerPart", null);
    }

    private void set_standard_regions(Region region, Simulation simulation) {
        // Define units
        Units units_0 = simulation.getUnitsManager().getPreferredUnits(Dimensions.Builder().build());
        Units units_1 = simulation.getUnitsManager().getPreferredUnits(Dimensions.Builder().length(1).build());
        Units units_2 = simulation.getUnitsManager().getPreferredUnits(Dimensions.Builder().velocity(1).build());

        // Symmetry plane 
        // (need to implement logic for two sided with cornering cases in future)
        Boundary boundary_1 = region.getBoundaryManager().getBoundary("L0");
        SymmetryBoundary symmetryBoundary_0 = ((SymmetryBoundary) simulation.get(ConditionTypeManager.class).get(SymmetryBoundary.class));
        boundary_1.setBoundaryType(symmetryBoundary_0);

        // Velocity Inlet
        Boundary boundary_2 = region.getBoundaryManager().getBoundary("L1");
        InletBoundary inletBoundary_0 = ((InletBoundary) simulation.get(ConditionTypeManager.class).get(InletBoundary.class));
        boundary_2.setBoundaryType(inletBoundary_0);

        boundary_2.getConditions().get(InletVelocityOption.class).setSelected(InletVelocityOption.Type.COMPONENTS);
        boundary_2.getConditions().get(KwTurbSpecOption.class).setSelected(KwTurbSpecOption.Type.INTENSITY_LENGTH_SCALE);

        VelocityProfile velocityProfile_0 = boundary_2.getValues().get(VelocityProfile.class);
        velocityProfile_0.getMethod(ConstantVectorProfileMethod.class).getQuantity().setDefinition("$${IFR_vel}");

        TurbulenceIntensityProfile turbulenceIntensityProfile_0 = boundary_2.getValues().get(TurbulenceIntensityProfile.class);
        turbulenceIntensityProfile_0.getMethod(ConstantScalarProfileMethod.class).getQuantity().setDefinition("${IFR_turb_intensity}");
        
        TurbulentLengthScaleProfile turbulentLengthScaleProfile_0 = boundary_2.getValues().get(TurbulentLengthScaleProfile.class);
        turbulentLengthScaleProfile_0.getMethod(ConstantScalarProfileMethod.class).getQuantity().setDefinition("${IFR_turb_length}");

        // Pressure Outlet
        Boundary boundary_3 = region.getBoundaryManager().getBoundary("L2");
        PressureBoundary pressureBoundary_0 = ((PressureBoundary) simulation.get(ConditionTypeManager.class).get(PressureBoundary.class));
        boundary_3.setBoundaryType(pressureBoundary_0);

        // Slip Walls
        // (might need to add more layers here if walls and roof different layer)
        Boundary boundary_4 = region.getBoundaryManager().getBoundary("L3");
        boundary_4.getConditions().get(WallShearStressOption.class).setSelected(WallShearStressOption.Type.SLIP);
        
        // Road
        Boundary boundary_5 = region.getBoundaryManager().getBoundary("L4");
        boundary_5.getConditions().get(WallSlidingOption.class).setSelected(WallSlidingOption.Type.VECTOR);
        WallRelativeVelocityProfile wallRelativeVelocityProfile_0 = boundary_5.getValues().get(WallRelativeVelocityProfile.class);
        wallRelativeVelocityProfile_0.getMethod(ConstantVectorProfileMethod.class).getQuantity().setDefinition("$${IFR_vel}");
        
    }

}
