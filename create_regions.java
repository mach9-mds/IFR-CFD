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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonArray;


public class create_regions extends StarMacro {
    
    public void execute() {
        JsonObject config = verify_json();
        String partName = config.getString("partName");
        boolean halfCar = config.getJsonObject("domain").getString("type").equals("half");

        assign_regions(partName);

        Simulation simulation = getActiveSimulation();
        Region region = simulation.getRegionManager().getRegion(partName);
        set_standard_regions(region, simulation, halfCar);

        if (config.containsKey("wheels")) {
            set_wheels(region, simulation, config);
        }
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

    private Boundary find_boundary(Region region, String layer) {
        // Subtract output prefixes surface names (e.g. "Domain.L1"), so match on suffix
        for (Boundary boundary : region.getBoundaryManager().getBoundaries()) {
            String name = boundary.getPresentationName();
            if (name.equals(layer) || name.endsWith("." + layer)) {
                return boundary;
            }
        }
        throw new IllegalStateException("No boundary found for layer " + layer + " in region " + region.getPresentationName());
    }

    private void assign_regions(String partName) {
        // Assign parts to regions, each surface individual region
        Simulation simulation_0 = getActiveSimulation();
        GeometryPart domainPart_0 = simulation_0.get(SimulationPartManager.class).getPart(partName);
        simulation_0.getRegionManager().newRegionsFromParts(new NeoObjectVector(new Object[] {domainPart_0}), "OneRegionPerPart", null, "OneBoundaryPerPartSurface", null, "OneFeatureCurve", null, RegionManager.CreateInterfaceMode.BOUNDARY, "OneEdgeBoundaryPerPart", null);
    }

    private void set_standard_regions(Region region, Simulation simulation, boolean halfCar) {
        // Define units
        Units units_0 = simulation.getUnitsManager().getPreferredUnits(Dimensions.Builder().build());
        Units units_1 = simulation.getUnitsManager().getPreferredUnits(Dimensions.Builder().length(1).build());
        Units units_2 = simulation.getUnitsManager().getPreferredUnits(Dimensions.Builder().velocity(1).build());

        // Symmetry plane (half car only, full car side walls are L3 slip walls)
        // (need to implement logic for two sided with cornering cases in future)
        if (halfCar) {
            Boundary boundary_1 = find_boundary(region, "L0");
            SymmetryBoundary symmetryBoundary_0 = ((SymmetryBoundary) simulation.get(ConditionTypeManager.class).get(SymmetryBoundary.class));
            boundary_1.setBoundaryType(symmetryBoundary_0);
        }

        // Velocity Inlet
        Boundary boundary_2 = find_boundary(region, "L1");
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
        Boundary boundary_3 = find_boundary(region, "L2");
        PressureBoundary pressureBoundary_0 = ((PressureBoundary) simulation.get(ConditionTypeManager.class).get(PressureBoundary.class));
        boundary_3.setBoundaryType(pressureBoundary_0);

        // Slip Walls
        // (might need to add more layers here if walls and roof different layer)
        Boundary boundary_4 = find_boundary(region, "L3");
        boundary_4.getConditions().get(WallShearStressOption.class).setSelected(WallShearStressOption.Type.SLIP);
        
        // Road
        Boundary boundary_5 = find_boundary(region, "L4");
        boundary_5.getConditions().get(WallSlidingOption.class).setSelected(WallSlidingOption.Type.VECTOR);
        WallRelativeVelocityProfile wallRelativeVelocityProfile_0 = boundary_5.getValues().get(WallRelativeVelocityProfile.class);
        wallRelativeVelocityProfile_0.getMethod(ConstantVectorProfileMethod.class).getQuantity().setDefinition("$${IFR_vel}");
        
    }

    private void set_wheels(Region region, Simulation simulation, JsonObject config) {
        // Road velocity (as ${IFR_vel}), road at z = 0
        JsonObject params = config.getJsonObject("globalParams");
        JsonArray velDir = params.getJsonArray("velocityDirection");
        double velMag = params.getJsonNumber("velocityMagnitude").doubleValue();
        double vx = velMag * velDir.getJsonNumber(0).doubleValue();
        double vy = velMag * velDir.getJsonNumber(1).doubleValue();

        Units units_0 = ((Units) simulation.getUnitsManager().getObject("m"));
        Units units_1 = ((Units) simulation.getUnitsManager().getObject(""));
        LabCoordinateSystem labCoordinateSystem_0 = simulation.getCoordinateSystemManager().getLabCoordinateSystem();

        JsonObject wheels = config.getJsonObject("wheels");
        for (String layer : wheels.keySet()) {
            JsonArray c = wheels.getJsonObject(layer).getJsonArray("centre");
            JsonArray a = wheels.getJsonObject(layer).getJsonArray("axis");
            double cx = c.getJsonNumber(0).doubleValue(), cy = c.getJsonNumber(1).doubleValue(), cz = c.getJsonNumber(2).doubleValue();
            double ax = a.getJsonNumber(0).doubleValue(), ay = a.getJsonNumber(1).doubleValue(), az = a.getJsonNumber(2).doubleValue();

            // Rotation rate: road speed / hub height, sign so the contact patch moves with the road
            double omega = (vy * ax - vx * ay) / Math.sqrt(ax * ax + ay * ay + az * az) / cz;

            // Hub coordinate system
            CartesianCoordinateSystem cartesianCoordinateSystem_0 = labCoordinateSystem_0.getLocalCoordinateSystemManager().createLocalCoordinateSystem(CartesianCoordinateSystem.class, "Cartesian");
            cartesianCoordinateSystem_0.getOrigin().setCoordinate(units_0, units_0, units_0, new DoubleVector(new double[] {cx, cy, cz}));
            cartesianCoordinateSystem_0.setPresentationName("WheelAxis_" + layer);

            // Rotating wall about the axle
            Boundary boundary_0 = find_boundary(region, layer);
            boundary_0.getConditions().get(WallSlidingOption.class).setSelected(WallSlidingOption.Type.LOCAL_ROTATION_RATE);
            ReferenceFrame referenceFrame_0 = boundary_0.getValues().get(ReferenceFrame.class);
            referenceFrame_0.setCoordinateSystem(cartesianCoordinateSystem_0);
            referenceFrame_0.getAxisVector().setComponentsAndUnits(ax, ay, az, units_1);
            WallRelativeRotationProfile wallRelativeRotationProfile_0 = boundary_0.getValues().get(WallRelativeRotationProfile.class);
            Units units_2 = ((Units) simulation.getUnitsManager().getObject("radian/s"));
            wallRelativeRotationProfile_0.getMethod(ConstantScalarProfileMethod.class).getQuantity().setValueAndUnits(omega, units_2);

            simulation.println("(II) Wheel " + layer + " rotating at " + omega + " rad/s");
        }
    }
}
