package ch.epfl.biop.bdv.command.userdefinedregion;

import bdv.util.BdvHandle;
import ch.epfl.biop.bdv.gui.GraphicalHandle;
import ch.epfl.biop.bdv.gui.XYRectangleGraphicalHandle;
import net.imagej.ImageJ;
import net.imglib2.RealPoint;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

@Plugin(type = Command.class, menuPath = ScijavaBdvDefaults.RootMenu+"BDV>Get Bdv User Points")
public class GetUserPointsCommand implements Command {

    @Parameter(required = false)
    Function<RealPoint, GraphicalHandle> graphicalHandleSupplier;

    @Parameter
    BdvHandle bdvh;

    @Parameter
    String messageForUser;

    @Parameter(required = false)
    int timeOutInMs=-1;

    @Parameter(type = ItemIO.OUTPUT)
    List<RealPoint> pts = new ArrayList<>();

    @Override
    public void run() {
        PointsSelectorBehaviour psb = new PointsSelectorBehaviour(bdvh, messageForUser, graphicalHandleSupplier);
        psb.install();
        pts = psb.waitForSelection(timeOutInMs);
        psb.uninstall();
    }

    public static void main(String... args) throws Exception {

        final ImageJ ij = new ImageJ();
        ij.ui().showUI();
        BdvHandle bdvh = BdvSourcesDemo.initAndShowSources();

        ij.command().run(GetUserPointsCommand.class, true,
                "bdvh", bdvh,
                "timeOutInMs", -1,
                "messageForUser", "Select your point of interest",
                "graphicalHandleSupplier",
                (Function<RealPoint, GraphicalHandle>) realPoint ->
                        new XYRectangleGraphicalHandle(
                                bdvh.getViewerPanel().state(),
                                () -> realPoint,
                                () -> 20d,
                                () -> 20d,
                                () -> PointsSelectorBehaviour.defaultLandmarkColor ));

    }
}
