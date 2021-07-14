package ch.epfl.biop.bdv.command.transform;

import bdv.util.BdvHandle;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.bdv.command.userdefinedregion.BoxSelectorCommand;
import ij.IJ;
import ij.ImagePlus;
import net.imglib2.RealInterval;
import net.imglib2.realtransform.AffineTransform3D;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.command.CommandModule;
import org.scijava.command.CommandService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.scijava.command.BdvPlaygroundActionCommand;
import sc.fiji.bdvpg.scijava.services.SourceAndConverterBdvDisplayService;
import sc.fiji.bdvpg.sourceandconverter.SourceAndConverterHelper;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Future;
import java.util.function.Function;

/**
 * Command used to export an elliptical transformed source
 */

@Plugin(type = BdvPlaygroundActionCommand.class, menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Transform>Export elliptic 3D transformed sources (interactive box)")
public class EasyExportEllipticProjection implements Command {

    @Parameter(label = "Select the elliptic transformed sources", callback = "validateMessage")
    SourceAndConverter[] sacs;

    @Parameter(type = ItemIO.OUTPUT)
    public ImagePlus imp_out;

    @Parameter
    CommandService cs;

    @Parameter
    SourceAndConverterBdvDisplayService displayService;

    @Override
    public void run() {
        try {

            List<SourceAndConverter> sources = sorter.apply(Arrays.asList(sacs));

            BdvHandle bdvh = displayService.getNewBdv();
            displayService.show(bdvh, sources.toArray(new SourceAndConverter[0]));

            AffineTransform3D view = new AffineTransform3D();

            view.scale(1.0/bdvh.getViewerPanel().getWidth());



            // At least one source
            if ((sacs==null)||(sacs.length==0)) {
                IJ.log("No selected source. Abort command.");
                return;
            }

            CommandModule boxSelector =
                    cs.run(BoxSelectorCommand.class, true,
                            "bdvh", bdvh,
                                    "message", "Select (r, theta, phi) bounds to crop",
                                    "zmin", -4.0*Math.PI,
                                    "zmax", +4.0*Math.PI,
                                    "ymin", -2.0*Math.PI,
                                    "ymax", +2.0*Math.PI,
                                    "xmin", 0,
                                    "xmax", 3
                                    /*"zmin_ini", -4.0*Math.PI,
                                    "zmax_ini", +4.0*Math.PI,
                                    "ymin_ini", -2.0*Math.PI,
                                    "ymax_ini", +2.0*Math.PI,
                                    "xmin_ini", 0.7,
                                    "xmax_ini", 1.3*/
                            ).get();


            if ((Boolean) boxSelector.getOutput("validResult")) {
                RealInterval interval = (RealInterval) boxSelector.getOutput("interval");
                Future<CommandModule> imageGetter =
                        cs.run(ExportEllipticProjection.class, true,
                                "sacs", sacs,
                                "rMin", interval.realMin(0),
                                "rMax", interval.realMax(0),
                                "thetaMin", interval.realMin(1)*180.0/Math.PI,
                                "thetaMax", interval.realMax(1)*180.0/Math.PI,
                                "phiMin", interval.realMin(2)*180.0/Math.PI,
                                "phiMax", interval.realMax(2)*180.0/Math.PI);

                imp_out = (ImagePlus) imageGetter.get().getOutput("imp_out");
            } else {
                IJ.log("Invalid box");
            }

            displayService.closeBdv(bdvh);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public Function<Collection<SourceAndConverter>, List<SourceAndConverter>> sorter = sacslist -> SourceAndConverterHelper.sortDefaultNoGeneric(sacslist);

}
