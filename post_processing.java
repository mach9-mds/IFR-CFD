// Simcenter STAR-CCM+ macro: macro.java
// Written by Simcenter STAR-CCM+ 20.06.010

/* ---------------------------- Import Libraries ---------------------------- */
package macro;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import star.common.*;
import star.base.neo.*;
import star.base.report.*;

import star.screenplay.operations.*;
import star.vis.*;
import star.cadmodeler.*;
import star.screenplay.*;
import star.screenplay.operations.neo.*;
import star.common.screenplay.traits.*;
import star.screenplay.operations.vis.*;
import star.screenplay.vis.*;
import star.flow.*;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonArray;
/* -------------------------------------------------------------------------- */
// post_processing.java
public class post_processing extends StarMacro {
  /* ----------------------- Public Function Definitions ---------------------- */
  private Simulation simulation;                  
  private Region region_FormulaStudentCar;
  //Boundaries
  private Boundary boundary_bodyworkL100;
  private Boundary boundary_bodyworkL101;
  private Boundary boundary_wheelsL20;
  private Boundary[] boundaries;
  //Plane Sections
  private PlaneSection Plane_XY;
  private PlaneSection Plane_XZ;
  private PlaneSection Plane_YZ;

  private ReportAnnotation reportAnnotation_CL;
  private ReportAnnotation reportAnnotation_CD;
  private ReportAnnotation reportAnnotation_CLF;
  private ReportAnnotation reportAnnotation_CLR;
  private ReportAnnotation reportAnnotation_CoP_Y0_X;
  private ReportAnnotation reportAnnotation_CoP_Y0_Y;
  private double min_z; 
  private double max_z;

  private double min_y; 
  private double max_y;

  private double min_x;
  private double max_x;

  /* -------------------------------------------------------------------------- */

  // global class to hold scene parameters
  private static class SceneParam
  {
    String sceneName;
    Boundary[] boundaryName;
    PlaneSection[] planeName;
    String fieldFunction;
    double licLength;
    double licNoiseFrequency;
    double licIntensity;
    boolean symmetry;

    double[] viewRight;
    double[] viewUp;

    double[] camPos;
    double[] camUp;
    double[] camFocus;
    double parallelScale;

    SceneParam(String sceneName, Boundary[] boundaryName, PlaneSection[] planeName,String fieldFunction, double licLength, double licNoiseFrequency, double licIntensity, boolean symmetry, double[] viewRight, double[] viewUp, double[] camPos, double[] camUp,
               double[] camFocus, double parallelScale)
    {
      this.sceneName = sceneName;
      this.boundaryName = boundaryName;
      this.planeName = planeName;
      this.fieldFunction = fieldFunction;
      this.licLength = licLength;
      this.licNoiseFrequency = licNoiseFrequency;
      this.licIntensity = licIntensity;
      this.symmetry = symmetry;

      this.viewRight = viewRight;
      this.viewUp = viewUp;

      this.camPos = camPos;
      this.camUp = camUp;
      this.camFocus = camFocus;
      this.parallelScale = parallelScale;
    }
  }

  // global class to hold ScreenPlay parameters
  private static class ScreenPlayParam
  {
      Scene scene;
      String screenplayName;
      double duration;
      PlaneSection plane;
      double[] position;

      ScreenPlayParam(Scene scene, String screenplayName, double duration, PlaneSection plane, double[] position)
      {
          this.scene = scene;
          this.screenplayName = screenplayName;
          this.duration = duration;
          this.plane = plane;
          this.position = position;
      }
  }

 private void ScreenPlays(ScreenPlayParam p)
  {
    Screenplay screenplay = 
        simulation.get(ScreenplayManager.class).createScreenplay(p.scene);
    screenplay.setPresentationName(p.screenplayName);

    Action action = screenplay.getActionManager().createAction(Action.class);
    action.setTime(0.0);
    action.setRow(0);
    action.setDuration(p.duration);

    Point3dKeyframeSequence originSequence = 
        action.getOperationManager().createOperation(Point3dKeyframeSequence.class);

    // Set the screenplay reference to the plane section and property
    ScreenplayReference ref = 
        new ScreenplayReference(p.plane, ScreenplayProperty.PlaneSection_Origin, 1);
    originSequence.setScreenplayReferences(ref);
    originSequence.setPresentationName("Origin");

    // Create keyframes for the default
    originSequence.createDefaultValues();

    // End keyframe
    Point3dKeyframe endKeyframe = 
        (Point3dKeyframe) originSequence.getKeyframeManager().getKeyframe(1);
    Units units = (Units) simulation.getUnitsManager().getObject("m");
    ((Coordinate) endKeyframe.getSource()).setCoordinate(
        units, units, units, new DoubleVector(p.position));
  }

  /* ------------------ Center of pressure report parameters ------------------ */
  private static class CoP
  {
    String reportName;
    Boundary[] boundaryName;
    double[] direction;   
    double[] normal;
    CoP(String reportName, Boundary[] boundaryName, double[] direction, double[] normal)
    {
      this.reportName = reportName;
      this.boundaryName = boundaryName;
      this.direction = direction;
      this.normal = normal;
    }
  }
  /* ------------------- Force coefficient report parameters ------------------ */
  private static class ForceCoefficient
  {
    String reportName;
    Boundary[] boundaryName;
    double[] direction;
    double referenceArea;

    ForceCoefficient(String reportName, Boundary[] boundaryName, double[] direction, double referenceArea)
    {
        this.reportName = reportName;
        this.boundaryName = boundaryName;
        this.direction = direction;
        this.referenceArea = referenceArea;
    }
  }

/* ------------------------- Main execution function ------------------------ */
  public void execute() 
      {
        /* ------------------------------ Json Reading ------------------------------ */
        JsonObject config = verify_json();
        JsonObject sections = config.getJsonObject("PostProcessing").getJsonObject("PlaneSections");
        JsonObject xy = sections.getJsonObject("Plane_XY");
        JsonObject xz = sections.getJsonObject("Plane_XZ");
        JsonObject yz = sections.getJsonObject("Plane_YZ");
        min_z = xy.getJsonNumber("min").doubleValue();
        max_z = xy.getJsonNumber("max").doubleValue();

        min_y = xz.getJsonNumber("min").doubleValue();
        max_y = xz.getJsonNumber("max").doubleValue();

        min_x = yz.getJsonNumber("min").doubleValue();
        max_x = yz.getJsonNumber("max").doubleValue();

        // Get the active simulation
        simulation = getActiveSimulation();
        // Get the region for the Formula Student Cat            
        region_FormulaStudentCar = simulation.getRegionManager().getRegion("FormulaStudentCar");
        // Initialize all boundaries
        initBoundaries();
        boundaries = new Boundary[] {boundary_bodyworkL100,boundary_bodyworkL101,boundary_wheelsL20};
        // Initialize the Field Functions
        FieldFunctions(config);
        // Create the derived parts (plane sections)
        SetDerivedPart();
        // Create the geometry scene
        Geometry_Scene(config);

        /* ----------------------- Create necessarily reports ----------------------- */
        CoP CoP_Y0_X = new CoP("CoP_Y0_X", boundaries, new double[]{1.0, 0.0, 0.0}, new double[]{0.0, 1.0, 0.0});
        CoP_Reports(CoP_Y0_X);
        CoP CoP_Y0_Y = new CoP("CoP_Y0_Y", boundaries, new double[]{0.0, 1.0, 0.0}, new double[]{0.0, 1.0, 0.0});
        CoP_Reports(CoP_Y0_Y);

        ForceCoefficient CL_A = new ForceCoefficient("CL_A", boundaries, new double[]{0.0, 0.0, 1.0}, 1.0);
        FC_Reports(CL_A);
        ForceCoefficient CD_A = new ForceCoefficient("CD_A", boundaries, new double[]{1.0, 0.0, 0.0}, 1.0);
        FC_Reports(CD_A);
        
        
        // Rear axle forece coefficient report
        ExpressionReport expressionReport_1 = 
          simulation.getReportManager().create("star.base.report.ExpressionReport");
        expressionReport_1.setDefinition("-${CoP_Y0_X}*${CL_A}/1.54\n");
        expressionReport_1.setPresentationName("CLR_A");
        // Front axle forece coefficient report
        ExpressionReport expressionReport_2 = 
          simulation.getReportManager().create("star.base.report.ExpressionReport");
        expressionReport_2.setDefinition("(${CoP_Y0_X}-1.54)*${CL_A}/1.54\n");
        expressionReport_2.setPresentationName("CLF_A");

        simulation.getReportManager().getGroupsManager().createGroup("Force Coefficients");
        ((ClientServerObjectGroup) simulation.getReportManager().getGroupsManager().getObject("Force Coefficients")).getGroupsManager().groupObjects("Force Coefficients", new ArrayList<>(Arrays.<ClientServerObject>asList(expressionReport_1, expressionReport_2)));
        // Group the reports into a single group
        ForceCoefficientReport forceCoefficientReport_CD = 
          ((ForceCoefficientReport) simulation.getReportManager().getReport("CD_A"));
        reportAnnotation_CD = 
          simulation.getAnnotationManager().createReportAnnotation(forceCoefficientReport_CD);

        ForceCoefficientReport forceCoefficientReport_CL = 
          ((ForceCoefficientReport) simulation.getReportManager().getReport("CL_A"));
        reportAnnotation_CL = 
          simulation.getAnnotationManager().createReportAnnotation(forceCoefficientReport_CL);

        ExpressionReport expressionReport_CLF = 
          ((ExpressionReport) simulation.getReportManager().getReport("CLF_A"));
        reportAnnotation_CLF = 
          simulation.getAnnotationManager().createReportAnnotation(expressionReport_CLF);

        ExpressionReport expressionReport_CLR = 
          ((ExpressionReport) simulation.getReportManager().getReport("CLR_A"));
        reportAnnotation_CLR = 
          simulation.getAnnotationManager().createReportAnnotation(expressionReport_CLR);
        
          ((ClientServerObjectGroup) simulation.getReportManager().getGroupsManager().getObject("Force Coefficients")).getGroupsManager().groupObjects("Force Coefficients", new ArrayList<>(Arrays.<ClientServerObject>asList(forceCoefficientReport_CD, forceCoefficientReport_CL,expressionReport_CLF, expressionReport_CLR)));


        simulation.getReportManager().getGroupsManager().createGroup("Center of Pressure");
        CenterOfLoadsReport centerOfLoadsReport_20 = 
          ((CenterOfLoadsReport) simulation.getReportManager().getReport("CoP_Y0_X"));
        reportAnnotation_CoP_Y0_X = 
          simulation.getAnnotationManager().createReportAnnotation(centerOfLoadsReport_20);
        CenterOfLoadsReport centerOfLoadsReport_21 = 
          ((CenterOfLoadsReport) simulation.getReportManager().getReport("CoP_Y0_Y"));
        reportAnnotation_CoP_Y0_Y = 
          simulation.getAnnotationManager().createReportAnnotation(centerOfLoadsReport_21);
        ((ClientServerObjectGroup) simulation.getReportManager().getGroupsManager().getObject("Center of Pressure")).getGroupsManager().groupObjects("Center of Pressure", new ArrayList<>(Arrays.<ClientServerObject>asList(centerOfLoadsReport_20, centerOfLoadsReport_21)));

        /* ----------------------------- Generate scenes ---------------------------- */
        // Pressure Coefficients for sweep planes
        SceneParam pxy = new SceneParam("Scalar_PC_Plane_XY_Sweep",
              new Boundary[] {}, new PlaneSection[] {Plane_XY},
              "PressureCoefficient",1,0.25,0.25,true,
              new double[] {1.0, 0.0, 0.0},                               
              new double[] {0.0, 1.0, 0.0},                                  
              new double[] {1.966440256229979, 0.06035306756886705, 0.029999973712259376}, 
              new double[] {0.0, 1.0, 0.0},
              new double[] {1.966440256229979, 0.06035306756886705, 9.605823827550937},1.8608723771684792);       
        SceneParam pxz = new SceneParam("Scalar_PC_Plane_XZ_Sweep",
              new Boundary[] {}, new PlaneSection[] {Plane_XZ},
              "PressureCoefficient",1,0.317,0.449,false,
              new double[] {1.0, 0.0, 0.0},    
              new double[] {0.0, 0.0, 1.0},   
              new double[] {-0.026332151265919118, 17.74828759089466, 1.4235777041461664},  
              new double[] {0.0, 0.0, 1.0},
              new double[] {-0.026332151265919118, -0.500000057103339, 1.4235777041461664},1.8608723771684792);   
        SceneParam pyz = new SceneParam("Scalar_PC_Plane_YZ_Sweep",
              new Boundary[] {}, new PlaneSection[] {Plane_YZ},
              "PressureCoefficient",1,0.317,0.449,true, 
              new double[] {0.0, 1.0, 0.0},   
              new double[] {0.0, 0.0, 1.0},    
              new double[] {6.407300692987433, 0.06634924768046527, 0.7160578966233986},
              new double[] {0.0, 0.0, 1.0},
              new double[] {-0.3861792438850876, 0.06634924768046527, 0.4916695678563186},0.5322977739075953);  
        Scene Scalar_PC_Plane_XY_Sweep = Scalar_Scene(pxy,config);
        Scene Scalar_PC_Plane_XZ_Sweep = Scalar_Scene(pxz,config);
        Scene Scalar_PC_Plane_YZ_Sweep = Scalar_Scene(pyz,config);

        // Total Pressure Coefficients for sweep planes 
        SceneParam tpxy = new SceneParam("Scalar_TPC_Plane_XY_Sweep",
              new Boundary[] {}, new PlaneSection[] {Plane_XY},
              "TotalPressureCoefficient",1,0.317,0.449,true,
              new double[] {1.0, 0.0, 0.0},                                   
              new double[] {0.0, 1.0, 0.0},                                 
              new double[] {1.966440256229979, 0.06035306756886705, 0.029999973712259376}, 
              new double[] {0.0, 1.0, 0.0},
              new double[] {1.966440256229979, 0.06035306756886705, 9.605823827550937},1.8608723771684792);       
        SceneParam tpxz = new SceneParam("Scalar_TPC_Plane_XZ_Sweep",
              new Boundary[] {}, new PlaneSection[] {Plane_XZ},
              "TotalPressureCoefficient",1,0.317,0.449,false,
              new double[] {1.0, 0.0, 0.0},    
              new double[] {0.0, 0.0, 1.0},    
              new double[] {-0.026332151265919118, 17.74828759089466, 1.4235777041461664}, 
              new double[] {0.0, 0.0, 1.0},
              new double[] {-0.026332151265919118, -0.500000057103339, 1.4235777041461664},1.8608723771684792);   
        SceneParam tpyz = new SceneParam("Scalar_TPC_Plane_YZ_Sweep",
              new Boundary[] {}, new PlaneSection[] {Plane_YZ},
              "TotalPressureCoefficient",1,0.317,0.449,true, 
              new double[] {0.0, 1.0, 0.0},    
              new double[] {0.0, 0.0, 1.0},   
              new double[] {6.407300692987433, 0.06634924768046527, 0.7160578966233986},  
              new double[] {0.0, 0.0, 1.0},
              new double[] {-0.3861792438850876, 0.06634924768046527, 0.4916695678563186},0.5322977739075953);   
        Scene Scalar_TPC_Plane_XY_Sweep=Scalar_Scene(tpxy,config);
        Scene Scalar_TPC_Plane_XZ_Sweep=Scalar_Scene(tpxz,config);
        Scene Scalar_TPC_Plane_YZ_Sweep=Scalar_Scene(tpyz,config);

        // Pressure Coefficients for the surface scenes
        SceneParam pc_s = new SceneParam("Scalar_PC_Surface",
            boundaries, new PlaneSection[] {},
            "PressureCoefficient",1,0.317,0.449,true,
            new double[] {1.0, 0.0, 0.0},                                   
            new double[] {0.0, 1.0, 0.0},                                 
            new double[] {1.966440256229979, 0.06035306756886705, 0.029999973712259376}, 
            new double[] {0.0, 1.0, 0.0},
            new double[] {1.966440256229979, 0.06035306756886705, 9.605823827550937},1.8608723771684792);       
        Scene Scalar_PC_Surface=Scalar_Scene(pc_s,config);

        // Total Pressure Coefficients for the surface scenes
        SceneParam tpc_s = new SceneParam("Scalar_TPC_Surface",
            boundaries, new PlaneSection[] {},
            "TotalPressureCoefficient",1,0.317,0.449,true,
            new double[] {1.0, 0.0, 0.0},                                   
            new double[] {0.0, 1.0, 0.0},                                 
            new double[] {1.966440256229979, 0.06035306756886705, 0.029999973712259376}, 
            new double[] {0.0, 1.0, 0.0},
            new double[] {1.966440256229979, 0.06035306756886705, 9.605823827550937},1.8608723771684792);       
        Scene Scalar_TPC_Surface=Scalar_Scene(tpc_s,config);

        simulation.getSceneManager().getGroupsManager().createGroup("PC");
         ((ClientServerObjectGroup) simulation.getSceneManager().getGroupsManager().getObject("PC")).getGroupsManager().groupObjects("PC", new ArrayList<>(Arrays.<ClientServerObject>asList(Scalar_PC_Plane_XY_Sweep, Scalar_PC_Plane_XZ_Sweep, Scalar_PC_Plane_YZ_Sweep, Scalar_PC_Surface)));
        simulation.getSceneManager().getGroupsManager().createGroup("TPC");
         ((ClientServerObjectGroup) simulation.getSceneManager().getGroupsManager().getObject("TPC")).getGroupsManager().groupObjects("TPC", new ArrayList<>(Arrays.<ClientServerObject>asList(Scalar_TPC_Plane_XY_Sweep, Scalar_TPC_Plane_XZ_Sweep, Scalar_TPC_Plane_YZ_Sweep, Scalar_TPC_Surface)));

        ScreenPlayParam spxy = new ScreenPlayParam(Scalar_PC_Plane_XY_Sweep, "ScreenPlay_PC_XY", 10.0, Plane_XY, new double[]{0.0, 0.0, max_z});
        ScreenPlayParam spxz = new ScreenPlayParam(Scalar_PC_Plane_XZ_Sweep, "ScreenPlay_PC_XZ", 10.0, Plane_XZ, new double[]{0.0, max_y, 0.0});  
        ScreenPlayParam spyz = new ScreenPlayParam(Scalar_PC_Plane_YZ_Sweep, "ScreenPlay_PC_YZ", 10.0, Plane_YZ, new double[]{max_x, 0.0, 0.0});  

        ScreenPlays(spxy);
        ScreenPlays(spxz);
        ScreenPlays(spyz);

        }
      



        

/* ------------------------ Initialize the boundaries ----------------------- */
 private void initBoundaries() {
    boundary_bodyworkL100 = region_FormulaStudentCar.getBoundaryManager().getBoundary("BODYWORK.L100");
    boundary_bodyworkL101 = region_FormulaStudentCar.getBoundaryManager().getBoundary("BODYWORK.L101");
    boundary_wheelsL20   = region_FormulaStudentCar.getBoundaryManager().getBoundary("WHEELS.L20");
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
  private void SetDerivedPart()
  {
    

    Plane_XY = 
      (PlaneSection) simulation.getPartManager().createImplicitPart(new ArrayList<>(Collections.<NamedObject>emptyList()), new DoubleVector(new double[] {0.0, 0.0, 1.0}), new DoubleVector(new double[] {0.0, 0.0, min_z}), 0, 1, new DoubleVector(new double[] {0.0}), null);
    Plane_XY.getInputParts().setQuery(null);
    Plane_XY.getInputParts().setObjects(region_FormulaStudentCar);
    

    Plane_XZ = 
      (PlaneSection) simulation.getPartManager().createImplicitPart(new ArrayList<>(Collections.<NamedObject>emptyList()), new DoubleVector(new double[] {0.0, 1.0, 0.0}), new DoubleVector(new double[] {0.0, min_y, 0.0}), 0, 1, new DoubleVector(new double[] {0.0}), null);

    Plane_XZ.getInputParts().setQuery(null);
    Plane_XZ.getInputParts().setObjects(region_FormulaStudentCar);

    Plane_YZ = 
      (PlaneSection) simulation.getPartManager().createImplicitPart(new ArrayList<>(Collections.<NamedObject>emptyList()), new DoubleVector(new double[] {1.0, 0.0, 0.0}), new DoubleVector(new double[] {min_x, 0.0, 0.0}), 0, 1, new DoubleVector(new double[] {0.0}), null);

    Plane_YZ.getInputParts().setQuery(null);
    Plane_YZ.getInputParts().setObjects(region_FormulaStudentCar);

    Plane_XY.setPresentationName("Plane_XY");
    Plane_XZ.setPresentationName("Plane_XZ");
    Plane_YZ.setPresentationName("Plane_YZ"); 

  }

  private void Geometry_Scene(JsonObject config)
  {
    
    simulation.getSceneManager().createGeometryScene("Geometry Scene","Outline","Surface",0,null);

    Scene Geo_Scene_Planes = simulation.getSceneManager().getScene("Geometry Scene 1");
    PartDisplayer Outline_1 = 
    ((PartDisplayer) Geo_Scene_Planes.getDisplayerManager().getObject("Outline 1"));
    Outline_1.setVisibilityOverrideMode(DisplayerVisibilityOverride.HIDE_ALL_PARTS);
    PartDisplayer Surface_1 = 
    ((PartDisplayer) Geo_Scene_Planes.getDisplayerManager().getObject("Surface 1"));
    Surface_1.getInputParts().setQuery(null);
    Surface_1.getInputParts().setObjects(boundary_bodyworkL100, boundary_bodyworkL101, boundary_wheelsL20, Plane_XY, Plane_XZ, Plane_YZ);
    Geo_Scene_Planes.getCurrentView().setInput(new DoubleVector(new double[] {1.2250540246782942, 0.7592745041476743, 0.7966067948106756}), new DoubleVector(new double[] {10.25645524801995, -22.91452925054066, 7.316052195332163}), new DoubleVector(new double[] {-0.08732152603594914, 0.2333868801547426, 0.9684552210931716}), 4.959849764688727, 1, 30.0);
  }

  private Scene Scalar_Scene(SceneParam p, JsonObject config)
  {
    simulation.getSceneManager().createVectorScene("Vector Scene", "Outline", "Vector", null);
    Scene scene = simulation.getSceneManager().getScene("Vector Scene 1");
    scene.setPresentationName(p.sceneName);
    PartDisplayer Outline_1 = 
      ((PartDisplayer) scene.getDisplayerManager().getObject("Outline 1"));
    Outline_1.setVisibilityOverrideMode(DisplayerVisibilityOverride.HIDE_ALL_PARTS);
    VectorDisplayer Vector_1 = 
      ((VectorDisplayer) scene.getDisplayerManager().getObject("Vector 1"));
    Vector_1.getInputParts().setQuery(null);
    
    
    List<NamedObject> allParts = new ArrayList<>();
    if (p.boundaryName != null) {
        allParts.addAll(Arrays.asList(p.boundaryName));
    }
    if (p.planeName != null) {
        allParts.addAll(Arrays.asList(p.planeName));
    }
    Vector_1.getInputParts().setObjects(allParts.toArray(new NamedObject[0]));


    Vector_1.setDisplayMode(VectorDisplayMode.VECTOR_DISPLAY_MODE_LIC);
    Vector_1.setColorMode(VectorColorMode.SCALAR);
    //Surface_1.getInputParts().setObjects(boundary_bodyworkL100, boundary_bodyworkL101, boundary_wheelsL20, Plane_XY, Plane_XZ, Plane_YZ);    
    if (p.symmetry)
    {
      SymmetricRepeat symmetricRepeat_0 = 
        ((SymmetricRepeat) simulation.getTransformManager().getObject("Domain.L0 1"));
        Vector_1.setVisTransform(symmetricRepeat_0);
    }
    FieldFunction pressureCoefficientFunction_0 = simulation.getFieldFunctionManager().getFunction(p.fieldFunction);
      Vector_1.getColoringScalar().setFieldFunction(pressureCoefficientFunction_0);
    //LIC Setting
    LICSettings lICSettings_0 = 
    Vector_1.getLICSettings();
    lICSettings_0.setLineLength(p.licLength);
    lICSettings_0.setNoiseFrequency(p.licNoiseFrequency);
    lICSettings_0.setIntensity(p.licIntensity);
    CurrentView currentView_6 = 
      scene.getCurrentView();
    scene.setViewOrientation(new DoubleVector(p.viewRight), new DoubleVector(p.viewUp));
    currentView_6.setInput(new DoubleVector(p.camPos), new DoubleVector(p.camFocus), new DoubleVector(p.camUp), p.parallelScale, 1, 30.0);
    scene.getAnnotationPropManager().getAnnotationGroup().setObjects(reportAnnotation_CL, reportAnnotation_CD, reportAnnotation_CLF, reportAnnotation_CLR, reportAnnotation_CoP_Y0_X);
    /* -------------------------------------------------------------------------- */
    ReportAnnotationProp reportAnnotationProp_CL = 
      ((ReportAnnotationProp) scene.getAnnotationPropManager().getObject("CL_A"));
    reportAnnotationProp_CL.setPosition(new DoubleVector(new double[] {0.0, 0.3, 0.0}));

    ReportAnnotationProp reportAnnotationProp_CD = 
      ((ReportAnnotationProp) scene.getAnnotationPropManager().getObject("CD_A"));
    reportAnnotationProp_CD.setPosition(new DoubleVector(new double[] {0.0, 0.35, 0.0}));

    ReportAnnotationProp reportAnnotationProp_CLF = 
      ((ReportAnnotationProp) scene.getAnnotationPropManager().getObject("CLF_A"));
    reportAnnotationProp_CLF.setPosition(new DoubleVector(new double[] {0.0, 0.4, 0.0}));
    ReportAnnotationProp reportAnnotationProp_CLR = 
      ((ReportAnnotationProp) scene.getAnnotationPropManager().getObject("CLR_A"));
    reportAnnotationProp_CLR.setPosition(new DoubleVector(new double[] {0.0, 0.45, 0.0}));
    ReportAnnotationProp reportAnnotationProp_CoP_Y0_X = 
      ((ReportAnnotationProp) scene.getAnnotationPropManager().getObject("CoP_Y0_X"));
    reportAnnotationProp_CoP_Y0_X.setPosition(new DoubleVector(new double[] {0.0, 0.5, 0.0}));
    /* -------------------------------------------------------------------------- */

    return scene;
  }

  private void CoP_Reports(CoP CoP_i)
  {
    CenterOfLoadsReport centerOfLoadsReport_3 = 
    simulation.getReportManager().create("star.flow.CenterOfLoadsReport");

    centerOfLoadsReport_3.setPresentationName(CoP_i.reportName);
    Units units_0 = simulation.getUnitsManager().getPreferredUnits(Dimensions.Builder().build());
    centerOfLoadsReport_3.getDirection().setComponentsAndUnits(CoP_i.direction[0], CoP_i.direction[1], CoP_i.direction[2], units_0);
    centerOfLoadsReport_3.getForceOption().setSelected(ForceReportForceOption.Type.PRESSURE);
  
    centerOfLoadsReport_3.getReferencePressure().setDefinition("${IFR_ref_pressure}");
    centerOfLoadsReport_3.setLocationOption(CenterOfLoadsLocationOption.ReferencePlane);
    ReferencePlaneDefinition referencePlaneDefinition_0 = 
      centerOfLoadsReport_3.getReferencePlane();
    Units units_m = 
      ((Units) simulation.getUnitsManager().getObject("m"));
    referencePlaneDefinition_0.getOriginCoordinate().setCoordinate(units_m, units_m, units_m, new DoubleVector(new double[] {0.0, 0.0, 0.0}));
    referencePlaneDefinition_0.getNormalCoordinate().setCoordinate(units_m, units_m, units_m, new DoubleVector(CoP_i.normal));  
    centerOfLoadsReport_3.getParts().setObjects(new NeoObjectVector(CoP_i.boundaryName));
    centerOfLoadsReport_3.setUseIdealization(true);
  }

  private void FC_Reports(ForceCoefficient CL_i)
{
    ForceCoefficientReport forceCoefficientReport = 
        (ForceCoefficientReport) simulation.getReportManager().create("star.flow.ForceCoefficientReport");
    forceCoefficientReport.setPresentationName(CL_i.reportName);
    // direction
    Units units_0 = simulation.getUnitsManager().getPreferredUnits(Dimensions.Builder().build());
    forceCoefficientReport.getDirection().setComponentsAndUnits(
        CL_i.direction[0], CL_i.direction[1], CL_i.direction[2], units_0);
    // type of force: pressure+viscous
    forceCoefficientReport.getForceOption().setSelected(ForceReportForceOption.Type.PRESSURE_AND_SHEAR);

    // ref pressure
    forceCoefficientReport.getReferencePressure().setDefinition("${IFR_ref_pressure}");

    // ref density, velocity, and area
    forceCoefficientReport.getReferenceDensity().setDefinition("${IFR_ref_density}");
    forceCoefficientReport.getReferenceVelocity().setDefinition("${IFR_vel_mag}");
    // ref area: if using CLA (area=1), set to 1; if using CL, fill with actual reference area
    forceCoefficientReport.getReferenceArea().setValue(1.0);
    // part
    forceCoefficientReport.getParts().setObjects(new NeoObjectVector(CL_i.boundaryName));
}
  

  private void FieldFunctions(JsonObject config)
  {

    PressureCoefficientFunction pressureCoefficientFunction_0 = 
      ((PressureCoefficientFunction) simulation.getFieldFunctionManager().getFunction("PressureCoefficient"));
    pressureCoefficientFunction_0.getReferenceDensity().setDefinition("${IFR_ref_density}");
    pressureCoefficientFunction_0.getReferencePressure().setDefinition("${IFR_ref_pressure}");
    pressureCoefficientFunction_0.getReferenceVelocity().setDefinition("${IFR_vel_mag}");
    
    TotalPressureCoefficientFunction TotalPressureCoefficientFunction_0 = 
      ((TotalPressureCoefficientFunction) simulation.getFieldFunctionManager().getFunction("TotalPressureCoefficient"));
    TotalPressureCoefficientFunction_0.getReferenceDensity().setDefinition("${IFR_ref_density}");
    TotalPressureCoefficientFunction_0.getReferencePressure().setDefinition("${IFR_ref_pressure}");
    TotalPressureCoefficientFunction_0.getReferenceVelocity().setDefinition("${IFR_vel_mag}");
  }

}

