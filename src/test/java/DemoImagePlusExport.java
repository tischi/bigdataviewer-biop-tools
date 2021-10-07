import bdv.util.BdvHandle;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.bdv.command.exporter.BasicBdvViewToImagePlusExportCommand;
import ij.IJ;
import mpicbg.spim.data.generic.AbstractSpimData;
import net.imagej.ImageJ;
import net.imagej.patcher.LegacyInjector;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.util.LinAlgHelpers;
import org.scijava.command.CommandService;
import sc.fiji.bdvpg.bdv.navigate.ViewerTransformAdjuster;
import sc.fiji.bdvpg.services.SourceAndConverterServices;
import sc.fiji.bdvpg.sourceandconverter.display.BrightnessAutoAdjuster;
import sc.fiji.bdvpg.spimdata.importer.SpimDataFromXmlImporter;

public class DemoImagePlusExport
{
    static final ImageJ ij = new ImageJ();

    static {
        LegacyInjector.preinit();
    }

    static public void main(String... args) {
        ij.ui().showUI();

        demo();
    }


    public static void demo() {

        final AffineTransform3D transform3D = new AffineTransform3D();
        transform3D.set(
                new double[]{
                 2.0,  0.0,  0.0, -57.978333333333296,
                 0.0,  0.0,  1.0, 36.18833333333334,
                 0.0, -2.0,  0.0, 197.79999999999998} );

        final double[] offset = new double[ 3 ];
        transform3D.inverse().apply( new double[3], offset );

        BdvHandle bdvHandle = displayImage();

        exportImagePlus( bdvHandle );

        // needed as otherwise it fetches
        // already here the rotated image
        IJ.wait(1000);

        rotate( bdvHandle );

        // The y and z axis are permuted but otherwise the
        // coordinate values are correct, i.e. the same
        // as in BDV
        exportImagePlus( bdvHandle );
    }

    private static BdvHandle displayImage()
    {
        final String filePath = "src/test/resources/mri-stack.xml";
        // Import SpimData
        SpimDataFromXmlImporter importer = new SpimDataFromXmlImporter(filePath);

        AbstractSpimData spimData = importer.get();

        SourceAndConverter sac = SourceAndConverterServices
                .getSourceAndConverterService()
                .getSourceAndConverterFromSpimdata(spimData)
                .get(0);

        // Creates a BdvHandle
        BdvHandle bdvHandle = SourceAndConverterServices.getBdvDisplayService().getActiveBdv();

        // Show the sourceandconverter
        SourceAndConverterServices.getBdvDisplayService().show(bdvHandle, sac);
        new BrightnessAutoAdjuster(sac, 0).run();
        new ViewerTransformAdjuster(bdvHandle, sac).run();
        return bdvHandle;
    }

    private static void rotate( BdvHandle bdvHandle )
    {
        AffineTransform3D affineTransform3D = new AffineTransform3D();
        bdvHandle.getViewerPanel().state().getViewerTransform(affineTransform3D);
        affineTransform3D.rotate( 0, Math.PI / 2 );
        affineTransform3D.translate( new double[]{0,180,-150} );
        bdvHandle.getViewerPanel().state().setViewerTransform( affineTransform3D );
    }

    private static void exportImagePlus( BdvHandle bdvHandle )
    {
        ij.context()
                .getService( CommandService.class)
                .run( BasicBdvViewToImagePlusExportCommand.class, true,
                        "bdv_h", bdvHandle,
                        "capturename", "image",
                        "zsize", 20,
                        "samplingxyinphysicalunit", 2,
                        "samplingzinphysicalunit", 1,
                        "interpolate", true,
                        "unit", "px",
                        "selected_timepoints_str", "",
                        "export_mode", "Normal",
                        "range", ""
                );
    }
}
