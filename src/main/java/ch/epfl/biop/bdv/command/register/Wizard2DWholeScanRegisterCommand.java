package ch.epfl.biop.bdv.command.register;

import bdv.tools.brightness.ConverterSetup;
import bdv.util.BdvHandle;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.bdv.command.userdefinedregion.GetUserPointsCommand;
import ch.epfl.biop.bdv.command.userdefinedregion.GetUserRectangleCommand;
import ij.gui.WaitForUserDialog;
import net.imglib2.RealPoint;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.RealTransform;
import org.scijava.ItemIO;
import org.scijava.ItemVisibility;
import org.scijava.command.CommandService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.bdv.BdvHandleHelper;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.scijava.command.BdvPlaygroundActionCommand;
import sc.fiji.bdvpg.scijava.services.SourceAndConverterBdvDisplayService;
import sc.fiji.bdvpg.services.SourceAndConverterServices;
import sc.fiji.bdvpg.sourceandconverter.register.BigWarpLauncher;
import sc.fiji.bdvpg.sourceandconverter.transform.SourceRealTransformer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import static bdv.util.RealTransformHelper.BigWarpFileFromRealTransform;

@Plugin(type = BdvPlaygroundActionCommand.class,
        menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Register>Wizard Align Slides (2D)",
        headless = false // User interface required
        )
public class Wizard2DWholeScanRegisterCommand implements BdvPlaygroundActionCommand{

    @Parameter(visibility = ItemVisibility.MESSAGE)
    String message = "<html><h2>Automated WSI registration wizard</h2><br/>"+
            "Images should be opened with bigdataviewer-playground<br/>"+
            "Automated registrations requires elastix.<br/>"+
            "</html>";

    @Parameter
    CommandService cs;

    @Parameter(label = "Fixed reference source")
    SourceAndConverter fixed;

    @Parameter(label = "Moving source used for registration to the reference")
    SourceAndConverter moving;

    @Parameter(label = "Sources to transform, including the moving source if needed")
    SourceAndConverter[] sourcesToTransform;

    @Parameter
    BdvHandle bdvh;

    @Parameter(label = "0 - Auto affine registration")
    boolean automatedAffineRegistration = true;

    @Parameter(label = "1 - Auto spline registration")
    boolean automatedSplineRegistration = true;

    @Parameter(label = "2 - Manual spline registration (BigWarp)")
    boolean manualSplineRegistration = true;

    @Parameter(label = "Show results of automated registrations (breaks parallelization)")
    boolean showDetails = false;

    @Parameter
    boolean verbose;

    @Parameter
    SourceAndConverterBdvDisplayService sacbds;

    @Parameter(type = ItemIO.OUTPUT)
    SourceAndConverter[] transformedSources;

    @Parameter(type = ItemIO.OUTPUT)
    RealTransform transformation;

    BiConsumer<String,String> waitForUser = (windowtitle, message) -> {
        WaitForUserDialog dialog = new WaitForUserDialog(windowtitle,message);
        dialog.show();
    };

    // Rectangle properties
    List<RealPoint> corners;
    double topLeftX, topLeftY, bottomRightX, bottomRightY, cx, cy;


    List<RealPoint> landmarks = new ArrayList<>();

    @Override
    public void run() {
        // Make sure the relevant sources are displayed

        showImagesIfNecessary();

        waitForUser.accept("Prepare your bigdataviewer window","Fit the image onto the bdv window.");

        if ((!automatedAffineRegistration)&&(!automatedSplineRegistration)&&(!manualSplineRegistration)) {
            System.err.println("You need to select at least one sort of registration!");
            return;
        }

        try {

            // Ask the user to select the region that should be aligned ( a rectangle ) - in any case
            getUserRectangle();

            if (automatedSplineRegistration) {
                // Ask the user to select the points where the fine tuning should be performed
                getUserLandmarks();
                if (landmarks.size()<4) {
                    System.err.println("At least 4 points should be selected");
                    return;
                }
            } else {
                // Let's keep corners for editing registration
                for (RealPoint pt : corners) {
                    landmarks.add(new RealPoint(pt)); // points are being copied
                }
            }

            // Conversion of RealPoints to String representation for next command launching
            String ptCoords = "";
            if (automatedSplineRegistration) {
                for (RealPoint pt : landmarks) {
                    ptCoords += pt.getDoublePosition(0) + "," + pt.getDoublePosition(1) + ",";
                }
            }

            // Coarse and spline registration - if selected by the user
            transformation = (RealTransform) cs.run(RegisterWholeSlideScans2DCommand.class, true,
                        "globalRefSource", fixed,
                               "currentRefSource", moving,
                               "ptListCoordinates", ptCoords,
                               "topLeftX", topLeftX,
                               "topLeftY", topLeftY,
                               "bottomRightX", bottomRightX,
                               "bottomRightY", bottomRightY,
                               "showDetails", showDetails,
                               "verbose", verbose,
                               "performFirstCoarseAffineRegistration",  automatedAffineRegistration,
                               "performSecondSplineRegistration", automatedSplineRegistration
                    ).get().getOutput("tst");

            if (manualSplineRegistration) {
                // The user wants big warp to correct landmark points
                List<SourceAndConverter> movingSacs = Arrays.stream(new SourceAndConverter[]{moving}).collect(Collectors.toList());

                List<SourceAndConverter> fixedSacs = Arrays.stream(new SourceAndConverter[]{fixed}).collect(Collectors.toList());

                List<ConverterSetup> converterSetups = Arrays.stream(new SourceAndConverter[]{moving}).map(src -> SourceAndConverterServices.getSourceAndConverterDisplayService().getConverterSetup(src)).collect(Collectors.toList());

                converterSetups.addAll(Arrays.stream(new SourceAndConverter[]{fixed}).map(src -> SourceAndConverterServices.getSourceAndConverterDisplayService().getConverterSetup(src)).collect(Collectors.toList()));

                // Launch BigWarp
                BigWarpLauncher bwl = new BigWarpLauncher(movingSacs, fixedSacs, "Big Warp", converterSetups);
                bwl.set2d();
                bwl.run();

                // Output bdvh handles -> will be put in the object service
                BdvHandle bdvhQ = bwl.getBdvHandleQ();
                BdvHandle bdvhP = bwl.getBdvHandleP();

                bdvhP.getViewerPanel().state().setViewerTransform(BdvHandleHelper.getViewerTransformWithNewCenter(bdvhP, new double[]{0,0,0}));
                bdvhQ.getViewerPanel().state().setViewerTransform(BdvHandleHelper.getViewerTransformWithNewCenter(bdvhQ, new double[]{0,0,0}));

                SourceAndConverterServices.getSourceAndConverterDisplayService().pairClosing(bdvhQ,bdvhP);

                bdvhP.getViewerPanel().requestRepaint();
                bdvhQ.getViewerPanel().requestRepaint();

                bwl.getBigWarp().getLandmarkFrame().repaint();

                bwl.getBigWarp().loadLandmarks(BigWarpFileFromRealTransform(transformation));

                bwl.getBigWarp().setIsMovingDisplayTransformed(true);

                AffineTransform3D newLocation = BdvHandleHelper.getViewerTransformWithNewCenter(bdvhP, new double[]{cx,cy,0});

                // Center window on the center of the user rectangle
                bdvhP.getViewerPanel().state().setViewerTransform(newLocation);
                bdvhQ.getViewerPanel().state().setViewerTransform(newLocation);
                
                waitForUser.accept("Manual spline registration", "Please perform carefully your registration then press ok.");

                transformation = bwl.getBigWarp().getTransformation();

                bwl.getBigWarp().closeAll();
            }

            // Now transforms all the sources required to be transformed

            SourceRealTransformer srt = new SourceRealTransformer(null,transformation);

            transformedSources = Arrays.stream(sourcesToTransform)
                    .map(srt)
                    .toArray(SourceAndConverter[]::new);

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private void getUserLandmarks() throws Exception {
        landmarks = (List<RealPoint>) cs
                .run(GetUserPointsCommand.class, true,
                        "messageForUser", "Select the position of the landmarks that will be used for the registration (at least 4).",
                        "timeOutInMs", -1)
                .get().getOutput("pts");
    }

    private void getUserRectangle() throws Exception {
        corners = (List<RealPoint>) cs
                .run(GetUserRectangleCommand.class, true,
                        "messageForUser", "Select a rectangular region for the region you'd like to register.",
                        "timeOutInMs", -1)
                .get().getOutput("pts");

        topLeftX = Math.min(corners.get(0).getDoublePosition(0),corners.get(1).getDoublePosition(0) );
        topLeftY = Math.min(corners.get(0).getDoublePosition(1),corners.get(1).getDoublePosition(1) );
        bottomRightX = Math.max(corners.get(0).getDoublePosition(0),corners.get(1).getDoublePosition(0) );
        bottomRightY = Math.max(corners.get(0).getDoublePosition(1),corners.get(1).getDoublePosition(1) );

        cx = (topLeftX+bottomRightY)/2.0;
        cy = (topLeftY+bottomRightY)/2.0;
    }

    private void showImagesIfNecessary() {
        if (!bdvh.getViewerPanel().state().containsSource(fixed)) {
            sacbds.show(bdvh,fixed);
        }

        if (!bdvh.getViewerPanel().state().containsSource(moving)) {
            sacbds.show(bdvh,moving);
        }
    }

    void fitBdvOnRectangle(BdvHandle bdvh) {
        // TODO
    }
}
