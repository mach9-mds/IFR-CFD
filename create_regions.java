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

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.nio.charset.StandardCharsets;


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

        // CAD parameter export (path relative to case.json, like stepFile)
        Map<String, String> cadParams = null;
        String cadSource = null;
        if (config.containsKey("cadParamsFile")) {
            Path csv = Paths.get(resolvePath("case.json")).toAbsolutePath().normalize().getParent()
                .resolve(config.getString("cadParamsFile")).normalize();
            cadSource = csv.getFileName().toString();
            cadParams = read_cad_params(csv);
            simulation.println("(II) Read " + cadParams.size() + " CAD parameters from " + csv);
        }

        for (String layer : wheels.keySet()) {
            double[][] geometry = wheel_geometry(simulation, wheels.getJsonObject(layer), layer, cadParams, cadSource);
            double cx = geometry[0][0], cy = geometry[0][1], cz = geometry[0][2];
            double ax = geometry[1][0], ay = geometry[1][1], az = geometry[1][2];

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
    // Returns {centre (m), axis} for one wheel: from the CAD export if the wheel has a cadPrefix,
    // otherwise from its centre/axis arrays in case.json
    private double[][] wheel_geometry(Simulation simulation, JsonObject wheel, String layer,
            Map<String, String> cadParams, String cadSource) {
        double[] centre;
        double[] axis;
        if (wheel.containsKey("cadPrefix")) {
            String prefix = wheel.getString("cadPrefix");
            if (cadParams == null) {
                throw new IllegalArgumentException("Wheel " + layer + " has cadPrefix '" + prefix
                    + "' but case.json has no cadParamsFile");
            }
            centre = cad_vector(cadParams, prefix + "_Centre", true, cadSource);
            axis = cad_vector(cadParams, prefix + "_Axis", false, cadSource);
            if (wheel.containsKey("centre") || wheel.containsKey("axis")) {
                simulation.println("(WW) Wheel " + layer + ": centre/axis in case.json ignored, using "
                    + prefix + "_* from " + cadSource);
            }
            simulation.println("(II) Wheel " + layer + ": hub centre and axis from " + prefix + "_* in " + cadSource);
        } else {
            centre = json_vector(wheel, "centre", layer);
            axis = json_vector(wheel, "axis", layer);
            simulation.println("(II) Wheel " + layer + ": hub centre and axis from case.json");
        }

        double axisLength = Math.sqrt(axis[0] * axis[0] + axis[1] * axis[1] + axis[2] * axis[2]);
        if (axisLength < 1e-9) {
            throw new IllegalArgumentException("Wheel " + layer + " has a zero-length axis");
        }
        if (Math.abs(axisLength - 1.0) > 1e-3) {
            simulation.println("(WW) Wheel " + layer + ": axis length is " + axisLength + ", not 1 (only its direction is used)");
        }
        if (centre[2] <= 0.0) {
            throw new IllegalArgumentException("Wheel " + layer + " hub height (centre z) must be above the road (z = 0), got " + centre[2] + " m");
        }
        if (centre[2] > 1.0) {
            simulation.println("(WW) Wheel " + layer + ": hub height " + centre[2] + " m looks too large, check centre is in metres");
        }
        return new double[][] {centre, axis};
    }

    private double[] json_vector(JsonObject wheel, String key, String layer) {
        JsonArray array = wheel.getJsonArray(key);
        if (array == null || array.size() != 3) {
            throw new IllegalArgumentException("Wheel " + layer + " needs '" + key + "' as an [x, y, z] array, or a cadPrefix");
        }
        return new double[] {
            array.getJsonNumber(0).doubleValue(),
            array.getJsonNumber(1).doubleValue(),
            array.getJsonNumber(2).doubleValue()};
    }

    // Reads the CSV written by export_cfd_params.bas: one "<tree path>\<name>,<value>" per line,
    // lines starting with # are comments. Keys are the bare parameter names (path dropped).
    private Map<String, String> read_cad_params(Path csv) {
        if (!Files.isRegularFile(csv)) {
            throw new IllegalArgumentException("CAD parameter file not found: " + csv + " (check cadParamsFile in case.json)");
        }
        List<String> lines;
        try {
            // Latin-1 never fails to decode, whatever code page the CATIA macro wrote with
            lines = Files.readAllLines(csv, StandardCharsets.ISO_8859_1);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Could not read CAD parameter file " + csv + ": " + exception.getMessage(), exception);
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int comma = line.lastIndexOf(',');
            if (comma < 0) {
                throw new IllegalArgumentException("Bad line in " + csv.getFileName() + " (expected name,value): " + line);
            }
            String name = line.substring(0, comma).trim();
            name = name.substring(name.lastIndexOf('\\') + 1);
            values.put(name, line.substring(comma + 1).trim());
        }
        return values;
    }

    private static final Pattern CAD_VALUE =
        Pattern.compile("([-+]?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][-+]?\\d+)?)\\s*([A-Za-z]*)");

    // Returns {base+"X", base+"Y", base+"Z"} from the CAD export: lengths converted to m
    // from whatever unit CATIA wrote, other values must have no unit
    private double[] cad_vector(Map<String, String> values, String base, boolean isLength, String source) {
        String[] components = {"X", "Y", "Z"};
        List<String> missing = new ArrayList<>();
        for (String component : components) {
            if (!values.containsKey(base + component)) {
                missing.add(base + component);
            }
        }
        if (!missing.isEmpty()) {
            // List near-misses (e.g. "RL_CentreX.1" after a CATIA rename clash) to make the fix obvious
            String prefix = base.substring(0, base.indexOf('_') + 1);
            List<String> similar = new ArrayList<>();
            for (String name : values.keySet()) {
                if (name.startsWith(prefix)) {
                    similar.add(name);
                }
            }
            throw new IllegalArgumentException(source + " is missing " + missing
                + (similar.isEmpty() ? "" : ". Parameters starting with " + prefix + " in the file: " + similar));
        }

        double[] vector = new double[3];
        for (int i = 0; i < 3; i++) {
            String name = base + components[i];
            String raw = values.get(name);
            Matcher match = CAD_VALUE.matcher(raw);
            if (!match.matches()) {
                throw new IllegalArgumentException(name + " in " + source + " is not a number: '" + raw + "'");
            }
            double number = Double.parseDouble(match.group(1));
            String unit = match.group(2).toLowerCase(Locale.ROOT);
            if (!isLength) {
                if (!unit.isEmpty()) {
                    throw new IllegalArgumentException(name + " in " + source + " should have no unit: '" + raw + "'");
                }
                vector[i] = number;
                continue;
            }
            switch (unit) {
                case "mm": vector[i] = number * 1e-3; break;
                case "cm": vector[i] = number * 1e-2; break;
                case "m":  vector[i] = number; break;
                case "in": vector[i] = number * 0.0254; break;
                case "":   throw new IllegalArgumentException(name + " in " + source + " has no length unit: '" + raw + "'");
                default:   throw new IllegalArgumentException(name + " in " + source + " has an unknown length unit: '" + raw + "'");
            }
        }
        return vector;
    }

}
