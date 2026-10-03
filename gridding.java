// Simcenter STAR-CCM+ macro: meshing_with_volume_controls.java
// Written by Simcenter STAR-CCM+ 17.04.008

// This subroutine is responsible for meshing

package macro;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.*;

import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.Json;
import javax.json.JsonArray;

import star.common.*;
import star.base.neo.*;
import star.vis.*;
import star.cadmodeler.*;
import star.trimmer.*;
import star.meshing.*;


public class gridding extends StarMacro {

    public void execute() {
        // Read mesh config file
        JsonObject config = verify_json();
        String partName = config.getString("partName");
        JsonObject mesh_config = config.getJsonObject("meshConfig");
        JsonObject global_setup = mesh_config.getJsonObject("globalSetup");
        JsonObject volume_setup = mesh_config.getJsonObject("volumeSetup");
        //JsonObject surface_setup = mesh_config.getJsonObject("surfaceSetup");

        // Setup meshing operation
        Simulation simulation = getActiveSimulation();
        AutoMeshOperation meshOp = create_operation(simulation, partName);

        // Global controls
        create_global_controls(simulation, meshOp, global_setup);

        // Volume controls
        create_volume_controls(simulation, meshOp, volume_setup);

        simulation.println("(II) Mesh operation and volume controls configured");
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

    private AutoMeshOperation create_operation(Simulation simulation, String partName) {
        SolidModelPart solidModelPart_0 = ((SolidModelPart) simulation.get(SimulationPartManager.class).getPart(partName));
        AutoMeshOperation autoMeshOperation_0 = simulation.get(MeshOperationManager.class).createAutoMeshOperation(new StringVector(new String[] {"star.resurfacer.ResurfacerAutoMesher", "star.trimmer.TrimmerAutoMesher", "star.prismmesher.PrismAutoMesher"}), new NeoObjectVector(new Object[] {solidModelPart_0}));
        autoMeshOperation_0.getMesherParallelModeOption().setSelected(MesherParallelModeOption.Type.PARALLEL);

        return autoMeshOperation_0;
    }

    private void create_global_controls(Simulation simulation, AutoMeshOperation meshOp, JsonObject globalSetup) {
        // Base size
        double base = globalSetup.getJsonNumber("baseSize").doubleValue();
        Units units_0 = ((Units) simulation.getUnitsManager().getObject("m"));
        meshOp.getDefaultValues().get(BaseSize.class).setValueAndUnits(base, units_0);
    }

    private void create_volume_controls(Simulation simulation, AutoMeshOperation meshOp, JsonObject volumeSetup) {
        MeshPartFactory meshPartFactory = simulation.get(MeshPartFactory.class);
        SimulationPartManager partManager = simulation.get(SimulationPartManager.class);
        LabCoordinateSystem labCoordinateSystem = simulation.getCoordinateSystemManager().getLabCoordinateSystem();
        Units lengthUnits = (Units) simulation.getUnitsManager().getObject("m");

        for (String boxName : volumeSetup.keySet()) {
            JsonObject boxConfig = volumeSetup.getJsonObject(boxName);
            JsonArray minCorner = boxConfig.getJsonArray("minCorner");
            JsonArray maxCorner = boxConfig.getJsonArray("maxCorner");
            JsonArray size = boxConfig.getJsonArray("size");

            SimpleBlockPart block = meshPartFactory.createNewBlockPart(partManager);
            block.setDoNotRetessellate(true);
            block.setCoordinateSystem(labCoordinateSystem);
            block.getCorner1().setCoordinateSystem(labCoordinateSystem);
            block.getCorner1().setCoordinate(
                lengthUnits, lengthUnits, lengthUnits,
                new DoubleVector(new double[] {
                    minCorner.getJsonNumber(0).doubleValue(),
                    minCorner.getJsonNumber(1).doubleValue(),
                    minCorner.getJsonNumber(2).doubleValue()}));
            block.getCorner2().setCoordinateSystem(labCoordinateSystem);
            block.getCorner2().setCoordinate(
                lengthUnits, lengthUnits, lengthUnits,
                new DoubleVector(new double[] {
                    maxCorner.getJsonNumber(0).doubleValue(),
                    maxCorner.getJsonNumber(1).doubleValue(),
                    maxCorner.getJsonNumber(2).doubleValue()}));
            block.rebuildSimpleShapePart();
            block.setDoNotRetessellate(false);
            block.setPresentationName(boxName);

            VolumeCustomMeshControl volumeControl = meshOp.getCustomMeshControls().createVolumeControl();
            volumeControl.setPresentationName(boxName);
            volumeControl.getGeometryObjects().setQuery(null);
            volumeControl.getGeometryObjects().setObjects(block);

            VolumeControlTrimmerSizeOption sizeOption =
                volumeControl.getCustomConditions().get(VolumeControlTrimmerSizeOption.class);
            sizeOption.setTrimmerAnisotropicSizeOption(true);

            TrimmerAnisotropicSize anisotropicSize =
                volumeControl.getCustomValues().get(TrimmerAnisotropicSize.class);
            anisotropicSize.getRelativeOrAbsoluteOption().setSelected(RelativeOrAbsoluteOption.Type.ABSOLUTE);
            anisotropicSize.setXSize(true);
            anisotropicSize.setYSize(true);
            anisotropicSize.setZSize(true);
            anisotropicSize.getAbsoluteXSize().getValue().setValueAndUnits(
                size.getJsonNumber(0).doubleValue(), lengthUnits);
            anisotropicSize.getAbsoluteYSize().getValue().setValueAndUnits(
                size.getJsonNumber(1).doubleValue(), lengthUnits);
            anisotropicSize.getAbsoluteZSize().getValue().setValueAndUnits(
                size.getJsonNumber(2).doubleValue(), lengthUnits);

            simulation.println("(II) Created volume control: " + boxName);
        }
    }
    
}
