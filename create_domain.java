// Simcenter STAR-CCM+ macro: create_domain.java
// Written by Simcenter STAR-CCM+ 17.04.008

// This subroutine creates the box fluid domain and subtracts the car from it.
// Half car: domain.side "left" keeps the -y side (default), "right" keeps the +y side.
// Corners are written for the left half (maxCorner y = 0) and mirrored in y for the right half.
// The face at y=0 is the symmetry plane L0.
// Full car: both side walls are slip walls L3.

package macro;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;

import star.common.*;
import star.base.neo.*;
import star.meshing.*;
import star.cadmodeler.*;

public class create_domain extends StarMacro {

    public void execute() {
        // Read in case.json
        JsonObject config = verify_json();
        String partName = config.getString("partName");
        JsonObject domain = config.getJsonObject("domain");
        if (domain == null) {
            throw new IllegalArgumentException("case.json is missing the 'domain' block");
        }

        String type = domain.getString("type");
        double[] minCorner = read_corner(domain, "minCorner");
        double[] maxCorner = read_corner(domain, "maxCorner");
        String side = domain.getString("side", "left");
        boolean mirrored = mirror_for_right_side(type, side, minCorner, maxCorner);
        validate_domain(type, side, domain.containsKey("side"), minCorner, maxCorner);

        Simulation simulation = getActiveSimulation();
        String label = type.equals("half") ? type + " (" + side + ")" : type;
        simulation.println("(II) Creating " + label + " car domain . . .");
        if (mirrored) {
            simulation.println("(II) Mirrored domain corners in y for right side: y from "
                + minCorner[1] + " to " + maxCorner[1]);
        }

        // Car parts are all CAD parts present before the domain is created
        List<GeometryPart> carParts = new ArrayList<>();
        for (GeometryPart part : simulation.get(SimulationPartManager.class).getParts()) {
            if (part instanceof SolidModelPart) {
                carParts.add(part);
            }
        }
        if (carParts.isEmpty()) {
            throw new IllegalStateException("No car parts found to subtract from domain");
        }

        SimpleBlockPart block = create_block(simulation, minCorner, maxCorner);
        name_block_faces(simulation, block, type, side);
        subtract_car(simulation, block, carParts, partName);

        simulation.println("(II) Domain created: " + partName);
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

    private double[] read_corner(JsonObject domain, String key) {
        JsonArray corner = domain.getJsonArray(key);
        if (corner == null || corner.size() != 3) {
            throw new IllegalArgumentException("domain." + key + " must be an [x, y, z] array");
        }
        return new double[] {
            corner.getJsonNumber(0).doubleValue(),
            corner.getJsonNumber(1).doubleValue(),
            corner.getJsonNumber(2).doubleValue()};
    }

    private boolean mirror_for_right_side(String type, String side, double[] minCorner, double[] maxCorner) {
        // Corners are written once for the left (-y) half; flip them to +y when side is right.
        // Corners already on the +y side (minCorner y = 0) are used unchanged.
        if (!type.equals("half") || !side.equals("right") || maxCorner[1] != 0.0) {
            return false;
        }
        double minY = minCorner[1];
        minCorner[1] = -maxCorner[1] + 0.0;
        maxCorner[1] = -minY;
        return true;
    }

    private void validate_domain(String type, String side, boolean sideGiven, double[] minCorner, double[] maxCorner) {
        if (!type.equals("half") && !type.equals("full")) {
            throw new IllegalArgumentException("domain.type must be 'half' or 'full', got '" + type + "'");
        }
        for (int i = 0; i < 3; i++) {
            if (minCorner[i] >= maxCorner[i]) {
                throw new IllegalArgumentException("domain.minCorner must be less than maxCorner on every axis");
            }
        }
        if (type.equals("half")) {
            if (!side.equals("left") && !side.equals("right")) {
                throw new IllegalArgumentException("domain.side must be 'left' or 'right', got '" + side + "'");
            }
            if (side.equals("left") && maxCorner[1] != 0.0) {
                throw new IllegalArgumentException("Left half car domain requires maxCorner y = 0 (symmetry plane), got minCorner y = "
                    + minCorner[1] + ", maxCorner y = " + maxCorner[1] + ". Use y from -W to 0, or set domain.side to 'right'");
            }
            if (side.equals("right") && minCorner[1] != 0.0) {
                throw new IllegalArgumentException("Right half car domain requires minCorner y = 0 (symmetry plane), got minCorner y = "
                    + minCorner[1] + ", maxCorner y = " + maxCorner[1] + ". Use y from -W to 0 (mirrored automatically) or from 0 to +W");
            }
        } else if (sideGiven) {
            getActiveSimulation().println("(WW) domain.side is ignored for a full car domain");
        }
        if (minCorner[2] != 0.0) {
            getActiveSimulation().println("(WW) domain.minCorner z is not 0, road will not be at ground level");
        }
    }

    private SimpleBlockPart create_block(Simulation simulation, double[] minCorner, double[] maxCorner) {
        MeshPartFactory meshPartFactory = simulation.get(MeshPartFactory.class);
        SimulationPartManager partManager = simulation.get(SimulationPartManager.class);
        LabCoordinateSystem labCoordinateSystem = simulation.getCoordinateSystemManager().getLabCoordinateSystem();
        Units lengthUnits = (Units) simulation.getUnitsManager().getObject("m");

        SimpleBlockPart block = meshPartFactory.createNewBlockPart(partManager);
        block.setDoNotRetessellate(true);
        block.setCoordinateSystem(labCoordinateSystem);
        block.getCorner1().setCoordinateSystem(labCoordinateSystem);
        block.getCorner1().setCoordinate(lengthUnits, lengthUnits, lengthUnits, new DoubleVector(minCorner));
        block.getCorner2().setCoordinateSystem(labCoordinateSystem);
        block.getCorner2().setCoordinate(lengthUnits, lengthUnits, lengthUnits, new DoubleVector(maxCorner));
        block.rebuildSimpleShapePart();
        block.setDoNotRetessellate(false);
        block.setPresentationName("Domain");

        return block;
    }

    private void name_block_faces(Simulation simulation, SimpleBlockPart block, String type, String side) {
        // Same layer names as the CAD-defined domain so create_regions is unchanged
        PartSurface blockSurface = block.getPartSurfaces().iterator().next();
        Vector<Integer> patches = block.getPartSurfacePatches(blockSurface);
        simulation.println("(II) Domain block patches: " + patches);
        if (patches.size() != 6) {
            throw new IllegalStateException("Expected 6 patches on domain block, found " + patches.size());
        }

        // Block part reports the patch id of each face directly
        block.splitPartSurfaceByPatch(blockSurface, Arrays.asList(block.getMinXFace()), "L1", simulation);
        block.splitPartSurfaceByPatch(blockSurface, Arrays.asList(block.getMaxXFace()), "L2", simulation);
        block.splitPartSurfaceByPatch(blockSurface, Arrays.asList(block.getMinZFace()), "L4", simulation);
        if (type.equals("half")) {
            // Symmetry plane is the y=0 face: y-max for left (-y) half, y-min for right (+y) half
            block.splitPartSurfaceByPatch(blockSurface,
                Arrays.asList(side.equals("right") ? block.getMinYFace() : block.getMaxYFace()), "L0", simulation);
        }

        int expectedSurfaces = type.equals("half") ? 5 : 4;
        if (block.getPartSurfaces().size() != expectedSurfaces) {
            throw new IllegalStateException("Domain block split into " + block.getPartSurfaces().size()
                + " surfaces, expected " + expectedSurfaces);
        }

        // Remaining faces are slip walls: outer y face and z-max (both y faces for full car)
        blockSurface.setPresentationName("L3");
    }

    private void subtract_car(Simulation simulation, SimpleBlockPart block, List<GeometryPart> carParts, String partName) {
        List<Object> inputs = new ArrayList<>();
        inputs.add(block);
        inputs.addAll(carParts);

        SubtractPartsOperation subtractOp = (SubtractPartsOperation) simulation.get(MeshOperationManager.class)
            .createSubtractPartsOperation(new NeoObjectVector(inputs.toArray()));
        subtractOp.setTargetPart(block);
        subtractOp.setPresentationName("IFR_Subtract");
        subtractOp.execute();

        // Output part takes partName so regions and meshing find it
        Collection<GeometryPart> outputs = subtractOp.getOutputParts().getObjects();
        if (outputs.isEmpty()) {
            throw new IllegalStateException("Subtract operation produced no output part");
        }
        outputs.iterator().next().setPresentationName(partName);
    }

}
