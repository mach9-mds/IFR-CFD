# IFR-CFD

Automated external-aero CFD setup for STAR-CCM+ 17.04. Export the car as a STEP file, fill in `case.json`, and run one macro. It sets up the geometry, domain, physics, boundary conditions, mesh controls and initialisation.

## Running

1. Export the car as a STEP file. It must be **car only** (no domain), made of watertight surface bodies, with these conventions:
   - centreline at y = 0, ground at z = 0, flow in +x
   - tyres sunk slightly below z = 0
   - surfaces named by layer (e.g. `L100`, `L101`, `L20`), with each rotating wheel on its own layer, e.g L20,L21,L22...
2. Edit `case.json` (see below), and `physics.json` if needed.
3. Open STAR-CCM+ and create a **new, empty simulation**.
4. Go to **File → Macro → Play Macro** and select `orchestration.java`.

Keep all `.java` and `.json` files in the same folder, because the subroutines find each other relative to that folder. The macro only sets the case up: it doesn't generate the mesh or run the solver, so do both from the GUI afterwards. Check the output window for `(II)` progress lines and `(WW)` warnings.

## case.json

| Key | Meaning |
|---|---|
| `stepFile` | Relative Path to the car STEP file |
| `partName` | Name given to the fluid domain part and region |
| `domain.type` | `half` (symmetry plane at y = 0) or `full` |
| `domain.side` | Half car only. `left` keeps the −y side (default if omitted), `right` keeps the +y side |
| `domain.minCorner` / `maxCorner` | Absolute box corners [x, y, z] in m. For `half`, write the box for the left side (`maxCorner` y = 0); with `side: right` it is mirrored to +y automatically. Corners already on the +y side (`minCorner` y = 0) are also accepted |
| `globalParams` | Freestream velocity magnitude and direction, turbulence intensity, length scale and velocity scale |
| `wheels` | Optional. One entry per wheel layer (e.g. `L20`) with `centre` (hub centre [x, y, z] in m) and `axis` (axle direction [x, y, z], either sign). Omit for stationary wheels |
| `initialise` | `"GSI,<levels>"` for grid sequencing (coupled solver only); anything else gives constant initialisation |
| `meshConfig.globalSetup.baseSize` | Base mesh size (m) |
| `meshConfig.volumeSetup` | Refinement boxes: `minCorner`, `maxCorner`, anisotropic `size` [x, y, z]. Make them span both sides of the car (full width) so the same boxes work for a left half, a right half or a full car |
| `meshConfig.surfaceSetup` | Per-layer surface controls. `size` [target, min], `pl` [prism layers, total thickness], `curv` (points per circle), `prox` (points in gap), `growth` (surface growth rate) |
| `PostProcessing.PlaneSections` | Per-plane movement range. Each key is a plane name (`Plane_XY`, `Plane_XZ`, `Plane_YZ`); `min` / `max` define the sweep range along the plane's normal axis. Write `Plane_XZ` for the left side (y ≤ 0); with `domain.side: right` it is mirrored to +y automatically |

`physics.json` holds `turbModel` (`kwSST`), `gasModel` (`constantDens`) and `coupled` (true/false).

## Subroutines

`orchestration.java` runs these in order:

1. **`import_geom.java`**: imports every body in the STEP file into 3D-CAD, runs CAD repair, and creates one part per body.
2. **`create_domain.java`**: builds a box from `domain` and names its faces:
   - `L1` inlet (x-min), `L2` outlet (x-max), `L4` road (z-min)
   - `L0` symmetry (the y = 0 face: y-max for a `left` half, y-min for a `right` half), half car only
   - `L3` slip walls (the remaining faces)

   It then subtracts the car from the box and names the result `partName`.
3. **`global_params.java`**: creates the global parameters (`IFR_vel`, `IFR_turb_*`) that the later steps reference.
4. **`create_physics.java`**: creates the physics continuum from `physics.json` and sets the initial conditions from the global parameters.
5. **`create_regions.java`**: creates the region from the domain part. It assigns symmetry, velocity inlet, pressure outlet, slip-wall and moving-road conditions by layer name; car surfaces stay as no-slip walls. For each layer in `wheels` it creates a coordinate system `WheelAxis_<layer>` at the hub and sets that wall to rotate (Local Rotation Rate) about `axis`. The rate is road speed / hub height (`centre` z, with the road at z = 0), signed so the tyre moves with the road where it touches it, and is printed to the output window.
6. **`gridding.java`**: creates the automated mesh operation (surface remesher, trimmer, prism layers) with the base size, the volume refinement boxes and the per-layer surface controls.
7. **`initialise.java`**: turns on grid sequencing initialisation if `initialise` asks for it.
8. **`post_processing.java`**: generates the required derived parts, builds the scenes for the quantities of interest and records the animation.
