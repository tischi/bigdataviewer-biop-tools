package ch.epfl.biop.bdv.command.exporter;

import bdv.tools.brightness.ConverterSetup;
import bdv.util.BdvHandle;
import bdv.viewer.SourceAndConverter;
import ij.ImagePlus;
import net.imglib2.RealPoint;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.RealType;
import org.scijava.ItemIO;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.bdv.BdvHandleHelper;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.scijava.command.BdvPlaygroundActionCommand;
import sc.fiji.bdvpg.services.SourceAndConverterServices;
import sc.fiji.bdvpg.sourceandconverter.SourceAndConverterHelper;
import sc.fiji.bdvpg.sourceandconverter.importer.EmptySourceAndConverterCreator;
import sc.fiji.bdvpg.sourceandconverter.transform.SourceResampler;
import spimdata.imageplus.ImagePlusHelper;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Export sources as an ImagePlus object according to the orientation
 * of a bdv window.
 *
 * Many options are available for a full control of the way the export from bdv source to
 * a resliced ImagePlus can be performed.
 *
 * See {@link BasicBdvViewToImagePlusExportCommand} for a more simpler command with
 * reasonable estimated default values
 *
 * Stack is virtual and cached by default
 * @param <T> non-volatile pixel type
 */

@Plugin(type = BdvPlaygroundActionCommand.class,
        menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Export>Current BDV View To ImagePlus")
public class BdvViewToImagePlusExportCommand<T extends RealType<T>> implements BdvPlaygroundActionCommand {

    @Parameter(label = "BigDataViewer Frame")
    public BdvHandle bdv_h;

    @Parameter(label = "Capture Name")
    String captureName = "Capture_00";

    @Parameter(required = false)
    SourceAndConverter[] sacs;

    @Parameter(label = "Include all sources from current Bdv Frame")
    boolean allSources;

    @Parameter(label="Mipmap level, 0 for highest resolution")
    public int mipmapLevel = 0;

    @Parameter(label="Match bdv frame window size", persist=false, callback = "matchXYBDVFrame")
    public boolean matchWindowSize=false;

    @Parameter(label = "Total Size X (physical unit)", callback = "matchXYBDVFrame")
    public double xSize = 100;

    @Parameter(label = "Total Size Y (physical unit)", callback = "matchXYBDVFrame")
    public double ySize = 100;

    @Parameter(label = "Half Thickness Z (above and below, physical unit, 0 for a single slice)")
    public double zSize = 100;

    @Parameter(label = "Start Timepoint (included, starts at 0)")
    public int timepointBegin = 0;

    @Parameter(label = "End Timepoint (excluded)")
    public int timepointEnd = 0;

    @Parameter(label = "XY Pixel size sampling (physical unit)", callback = "changePhysicalSampling")
    public double samplingXYInPhysicalUnit = 1;

    @Parameter(label = "Z Pixel size sampling (physical unit)", callback = "changePhysicalSampling")
    public double samplingZInPhysicalUnit = 1;

    @Parameter(label = "Interpolate")
    public boolean interpolate = true;

    @Parameter(label = "Ignore Source LUT (check for RGB)")
    public boolean ignoreSourceLut = false;

    @Parameter(label = "Make Composite")
    public boolean makeComposite = true;

    @Parameter(label = "Cache the resampled image")
    public boolean cacheImage = true;

    // Output imageplus window
    @Parameter(type = ItemIO.OUTPUT)
    public ImagePlus compositeImage;

    @Parameter(type = ItemIO.OUTPUT)
    public Map<SourceAndConverter, ImagePlus> singleChannelImages = new HashMap<>();

    String unitOfFirstSource=" ";

    public Consumer<String> errlog = (s) -> System.err.println(s);

    List<SourceAndConverter<?>> sourceList;

    AffineTransform3D at3D;

    @Override
    public void run() {

        // Sanity checks
        // 1. Timepoints : at least one timepoint
        if (timepointEnd<=timepointBegin) {
            timepointEnd = timepointBegin+1;
        }

        // 2. At least one source
        if (allSources) {
            if (bdv_h.getViewerPanel().state().getSources().size()==0) {
                errlog.accept("No source present in Bdv. Abort command.");
                return;
            }
        } else {
            if ((sacs==null)||(sacs.length==0)) {
                errlog.accept("No selected source. Abort command.");
                return;
            }
        }

        if (allSources) {
            sourceList = sorter.apply(bdv_h.getViewerPanel().state().getSources());
        } else {
            sourceList = sorter.apply(Arrays.asList(sacs));
        }

        boolean timepointsOk = true;

        for (SourceAndConverter source : sourceList) {
            for (int tp = timepointBegin;tp<timepointEnd;tp++) {
                timepointsOk = timepointsOk&&source.getSpimSource().isPresent(tp);
            }
        }

        if (!timepointsOk) {
            System.err.println("Invalid number of timepoints");
            return;
        }

        SourceAndConverter model = createModelSource();

        if (makeComposite) {
            if (sourceList.stream().map(sac -> sac.getSpimSource().getType().getClass()).distinct().count()>1) {
                errlog.accept("Cannot make composite because all sources are not of the same type");
                makeComposite = false;
            }
        }

        // The core of it : resampling each source with the model
        List<SourceAndConverter> resampledSourceList = sourceList
                .stream()
                .map(sac -> new SourceResampler(sac,model,true, cacheImage, interpolate).get())
                .collect(Collectors.toList());

        resampledSourceList.forEach(sac -> {
            SourceAndConverterServices.getSourceAndConverterService().remove(sac);
        });

        SourceAndConverterServices.getSourceAndConverterService().register(model);

        // Fetch the unit of the first source
        updateUnit();

        if ((makeComposite)&&(sourceList.size()>1)) {
            Map<SourceAndConverter, ConverterSetup> mapCS = new HashMap<>();
            sourceList.forEach(src -> mapCS.put(resampledSourceList.get(sourceList.indexOf(src)), bdv_h.getConverterSetups().getConverterSetup(src)));

            Map<SourceAndConverter, Integer> mapMipmap = new HashMap<>();
            sourceList.forEach(src -> mapMipmap.put(resampledSourceList.get(sourceList.indexOf(src)), mipmapLevel));

            compositeImage = ImagePlusHelper.wrap(
                    resampledSourceList,
                    mapCS,
                    mapMipmap,
                    timepointBegin,
                    timepointEnd,
                    ignoreSourceLut);

            compositeImage.setTitle(BdvHandleHelper.getWindowTitle(bdv_h));
            ImagePlusHelper.storeExtendedCalibrationToImagePlus(compositeImage, at3D.inverse(), unitOfFirstSource, timepointBegin);
        } else {
            resampledSourceList.forEach(source -> {
                ImagePlus singleChannel = ImagePlusHelper.wrap(
                        source,
                        bdv_h.getConverterSetups().getConverterSetup(sourceList.get(resampledSourceList.indexOf(source))),
                        mipmapLevel,
                        timepointBegin,
                        timepointEnd,
                        ignoreSourceLut);
                singleChannelImages.put(source, singleChannel);
                singleChannel.setTitle(source.getSpimSource().getName());
                ImagePlusHelper.storeExtendedCalibrationToImagePlus(singleChannel, at3D.inverse(), unitOfFirstSource, timepointBegin);
                if (resampledSourceList.size()>1) {
                    singleChannel.show();
                } else {
                    compositeImage = singleChannel;
                }
            });
        }
    }

    private SourceAndConverter createModelSource() {
        // Origin is in fact the point 0,0,0 of the image
        // Get current big dataviewer transformation : source transform and viewer transform
        at3D = new AffineTransform3D(); // Empty Transform
        // viewer transform
        bdv_h.getViewerPanel().state().getViewerTransform(at3D); // Get current transformation by the viewer state and puts it into sourceToImgPlus
        //Center on the display center of the viewer ...
        double w = bdv_h.getViewerPanel().getDisplay().getWidth();
        double h = bdv_h.getViewerPanel().getDisplay().getHeight();
        // Center on the display center of the viewer ...
        at3D.translate(-w / 2, -h / 2, 0);
        // Getting an image independent of the view scaling unit (not sure)
        double xNorm = getNormTransform(0, at3D);//trans
        at3D.scale(1/xNorm);

        at3D.scale(1./samplingXYInPhysicalUnit, 1./samplingXYInPhysicalUnit, 1./samplingZInPhysicalUnit);
        at3D.translate((xSize/(2*samplingXYInPhysicalUnit)), (ySize/(2*samplingXYInPhysicalUnit)), (zSize/(samplingZInPhysicalUnit)));

        long nPx = (long)(xSize / samplingXYInPhysicalUnit);
        long nPy = (long)(ySize / samplingXYInPhysicalUnit);
        long nPz;
        if (samplingZInPhysicalUnit==0) {
            nPz = 1;
        } else {
            nPz = 1+(long)(zSize / (samplingZInPhysicalUnit/2.0)); // TODO : check div by 2
        }

        // At least a pixel in all directions
        if (nPz == 0) nPz = 1;
        if (nPx == 0) nPx = 1;
        if (nPy == 0) nPy = 1;

        return new EmptySourceAndConverterCreator(captureName, at3D.inverse(), nPx, nPy, nPz).get();
    }

    /**
     * Returns the norm of an axis after an affinetransform is applied
     * @param axis axis of the affine transform
     * @param t affine transform measured
     * @return the norm of an axis after an affinetransform is applied
     */
    static public double getNormTransform(int axis, AffineTransform3D t) {
        double f0 = t.get(axis,0);
        double f1 = t.get(axis,1);
        double f2 = t.get(axis,2);
        return Math.sqrt(f0 * f0 + f1 * f1 + f2 * f2);
    }

    /**
     * Returns the distance between two RealPoint pt1 and pt2
     * @param pt1 first point
     * @param pt2 second point
     * @return the distance between two RealPoint pt1 and pt2
     */
    static public double distance(RealPoint pt1, RealPoint pt2) {
        assert pt1.numDimensions()==pt2.numDimensions();
        double dsquared = 0;
        for (int i=0;i<pt1.numDimensions();i++) {
            double diff = pt1.getDoublePosition(i)-pt2.getDoublePosition(i);
            dsquared+=diff*diff;
        }
        return Math.sqrt(dsquared);
    }

    // -- Initializers --

    /**
     * Initializes xSize(Pix) and ySize(Pix) according to the current BigDataViewer window
     */
    public void matchXYBDVFrame() {
        if (matchWindowSize) {
            // Gets window size
            double w = bdv_h.getViewerPanel().getDisplay().getWidth();
            double h = bdv_h.getViewerPanel().getDisplay().getHeight();

            // Get global coordinates of the top left position  of the viewer
            RealPoint ptTopLeft = new RealPoint(3); // Number of dimension
            bdv_h.getViewerPanel().displayToGlobalCoordinates(0, 0, ptTopLeft);

            // Get global coordinates of the top right position  of the viewer
            RealPoint ptTopRight = new RealPoint(3); // Number of dimension
            bdv_h.getViewerPanel().displayToGlobalCoordinates(0, w, ptTopRight);

            // Get global coordinates of the top right position  of the viewer
            RealPoint ptBottomLeft = new RealPoint(3); // Number of dimension
            bdv_h.getViewerPanel().displayToGlobalCoordinates(h,0, ptBottomLeft);

            // Gets physical size of pixels based on window size, image sampling size and user requested pixel size
            this.xSize=distance(ptTopLeft, ptTopRight);
            this.ySize=distance(ptTopLeft, ptBottomLeft);
        }
    }

    // -- Initializers --

    public void updateUnit() {
        if ((sourceList.size()>0) && (sourceList.get(0)!=null)) {
            if (sourceList.get(0).getSpimSource().getVoxelDimensions() != null) {
                unitOfFirstSource = sourceList.get(0).getSpimSource().getVoxelDimensions().unit();
            }
        }
    }

    public Function<Collection<SourceAndConverter<?>>,List<SourceAndConverter<?>>> sorter = sacs1ist -> SourceAndConverterHelper.sortDefaultGeneric(sacs1ist);



}
