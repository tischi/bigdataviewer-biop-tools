package ch.epfl.biop.bdv.transform.ellipticaltransform;

import bdv.img.WarpedSource;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.bdv.scijava.command.BdvSourceAndConverterFunctionalInterfaceCommand;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;


import static ch.epfl.biop.bdv.scijava.command.Info.ScijavaBdvRootMenu;

@Plugin(type = Command.class, menuPath = ScijavaBdvRootMenu+"Bdv>Edit Sources>Transform>Elliptical>Create Elliptical transform and transform sources")
public class EllipticalTransformCommand extends BdvSourceAndConverterFunctionalInterfaceCommand {

    @Parameter
    double r1, r2, r3, //radius of axes 1 2 3 of ellipse
            theta, phi, angle_en, // 3D rotation euler angles maybe not the best parametrization
            tx, ty, tz; // ellipse center

    @Parameter(type = ItemIO.OUTPUT)
    Elliptical3DTransform e3Dt;

    // -- Initializable methods --

    public EllipticalTransformCommand() {

        this.f = src -> {

            e3Dt.setParameters(
                    "r1", r1,
                    "r2", r2,
                    "r3", r3,
                    "theta", theta,
                    "phi", phi,
                    "angle_en", angle_en,
                    "tx", tx,
                    "ty", ty,
                    "tz", tz);
            WarpedSource ws = new WarpedSource(src.getSpimSource(),src.getSpimSource().getName()+"_EllipticalTransform");

            e3Dt.updateNotifiers.add(() -> {
                ws.updateTransform(e3Dt);
                this.bdvh_out.getViewerPanel().requestRepaint();
            }); // TODO avoid memory leak somehow...

            ws.setIsTransformed(true);
            SourceAndConverter sac;
            if (src.asVolatile()!=null) {
                WarpedSource vws = new WarpedSource(src.asVolatile().getSpimSource(),src.asVolatile().getSpimSource().getName()+"_EllipticalTransform");

                e3Dt.updateNotifiers.add(() -> {
                    vws.updateTransform(e3Dt);
                }); // TODO avoid memory leak somehow...

                SourceAndConverter vsac = new SourceAndConverter(vws, src.asVolatile().getConverter());
                sac = new SourceAndConverter(ws, src.getConverter(),vsac);
                return sac;
            } else {
                sac = new SourceAndConverter<>(ws, src.getConverter());
                return sac;
            }
        };
    }


    //private List<ModuleItem<Double>> transformParamsItems = new ArrayList<>();

    /*public void init() {
        Elliptical3DTransform.getParamsName().forEach(p -> {
            final ModuleItem<Double> item = new DefaultMutableModuleItem<>(getInfo(),
                    p, double.class);
            item.setLabel(p);
            transformParamsItems.add(item);
            getInfo().addInput(item);
        });
    }*/

    public void initCommand() {
        e3Dt = new Elliptical3DTransform();
    }
}
