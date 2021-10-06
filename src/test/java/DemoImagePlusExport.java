import bdv.util.BdvHandle;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.bdv.command.exporter.BasicBdvViewToImagePlusExportCommand;
import ij.IJ;
import mpicbg.spim.data.generic.AbstractSpimData;
import net.imagej.ImageJ;
import net.imagej.patcher.LegacyInjector;
import net.imglib2.realtransform.AffineTransform3D;
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
        BdvHandle bdvHandle = displayImage();

        exportImagePlus( bdvHandle );

        IJ.wait(1000);

        rotate( bdvHandle );

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
                        "samplingxyinphysicalunit", 1,
                        "samplingzinphysicalunit", 1,
                        "interpolate", true,
                        "unit", "px",
                        "selected_timepoints_str", "",
                        "export_mode", "Normal",
                        "range", ""
                );
    }
}
