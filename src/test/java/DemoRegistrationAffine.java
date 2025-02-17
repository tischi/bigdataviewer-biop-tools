import bdv.util.BdvFunctions;
import bdv.util.BdvHandle;
import bdv.util.BdvOptions;
import bdv.util.BdvStackSource;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.bdv.select.SelectedSourcesListener;
import ch.epfl.biop.bdv.select.SourceSelectorBehaviour;
import ch.epfl.biop.bdv.select.ToggleListener;
import ch.epfl.biop.bdv.command.register.Elastix2DAffineRegisterCommand;
import ch.epfl.biop.bdv.command.register.Elastix2DAffineRegisterServerCommand;
import ij.IJ;
import ij.ImagePlus;
import mpicbg.spim.data.SpimData;
import mpicbg.spim.data.XmlIoSpimData;
import mpicbg.spim.data.generic.AbstractSpimData;
import net.imagej.ImageJ;
import net.imagej.patcher.LegacyInjector;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.ARGBType;
import org.scijava.command.CommandModule;
import org.scijava.command.CommandService;
import org.scijava.ui.behaviour.ClickBehaviour;
import org.scijava.ui.behaviour.io.InputTriggerConfig;
import org.scijava.ui.behaviour.util.Behaviours;
import sc.fiji.bdvpg.sourceandconverter.SourceAndConverterAndTimeRange;
import sc.fiji.bdvpg.sourceandconverter.SourceAndConverterHelper;
import sc.fiji.bdvpg.sourceandconverter.transform.SourceTransformHelper;
import spimdata.imageplus.SpimDataFromImagePlusGetter;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Future;


public class DemoRegistrationAffine {

    static {
        LegacyInjector.preinit();
    }

    static SourceAndConverter fixedSource;

    static SourceAndConverter movingSource;

    static final ImageJ ij = new ImageJ();

    static public void main(String... args) throws Exception {

        ij.ui().showUI();

        // Creates a demo bdv frame with demo images
        BdvHandle bdvh = initAndShowSources();

        // Setup a source selection mode with a trigger input key that toggles it on and off
        SourceSelectorBehaviour ssb = new SourceSelectorBehaviour(bdvh, "E");

        // Adds a listener which displays the events - either GUI or programmatically triggered
        ssb.addSelectedSourcesListener(new SelectedSourcesListener() {
            @Override
            public void selectedSourcesUpdated(Collection<SourceAndConverter<?>> selectedSources, String triggerMode) {
                if (selectedSources.size()==1) {
                    bdvh.getViewerPanel().showMessage("Fixed Source Set ");
                    fixedSource = selectedSources.stream().findAny().get();
                    movingSource = null;
                }
            }

            @Override
            public void lastSelectionEvent(Collection<SourceAndConverter<?>> lastSelectedSources, String mode, String triggerMode) {
                bdvh.getViewerPanel().showMessage(mode + " " + lastSelectedSources.size());
                if ((lastSelectedSources.size()==1)&&(fixedSource!=null)) {
                    bdvh.getViewerPanel().showMessage("Moving Source Set ");
                    movingSource = lastSelectedSources.stream().findAny().get();
                    if (movingSource==fixedSource) {
                        movingSource = null;
                    }
                } else {
                    bdvh.getViewerPanel().showMessage("Fixed and Moving Source Reset");
                    fixedSource = null;
                    movingSource = null;
                }
            }
        });

        // Example of simple behaviours that can be added on top of the source selector
        // Here it adds an editor behaviour which only action is to remove the selected sources from the window
        // When the delete key is pressed
        addEditorBehaviours(bdvh, ssb);

        // Programmatic API Demo : triggers a list of actions separated in time
        // programmaticAPIDemo(bdvh, ssb);
        ssb.enable();
    }

    static BdvHandle initAndShowSources() throws Exception {
        // load and convert the famous blobs image
        ImagePlus imp = IJ.openImage("src/test/resources/blobs.tif");
        RandomAccessibleInterval blob = ImageJFunctions.wrapReal(imp);

        // load 3d mri image spimdataset
        SpimData sd = new XmlIoSpimData().load("src/test/resources/mri-stack.xml");

        // Display mri image
        BdvStackSource bss = BdvFunctions.show(sd).get(0);
        bss.setDisplayRange(0,255);

        // Gets reference of BigDataViewer
        BdvHandle bdvh = bss.getBdvHandle();
        bss.removeFromBdv();
        // Defines location of blobs image
        AffineTransform3D m = new AffineTransform3D();
        m.rotate(2,Math.PI/20);
        m.translate(0, -40,0);

        // Display first blobs image
        bss = BdvFunctions.show(blob, "Blobs 1", BdvOptions.options().sourceTransform(m).addTo(bdvh));
        bss.setColor(new ARGBType(ARGBType.rgba(255,0,0,0)));

        // Defines location of blobs image
        m.identity();
        m.rotate(2,Math.PI/25);
        m.translate(0,-60,0);

        // Display second blobs image
        bss = BdvFunctions.show(blob, "Blobs 2", BdvOptions.options().sourceTransform(m).addTo(bdvh));
        bss.setColor(new ARGBType(ARGBType.rgba(0,255,255,0)));
        /*
        // Defines location of blobs image
        m.identity();
        m.rotate(2,Math.PI/6);
        m.rotate(0,Math.PI/720);
        m.translate(312,256,0);

        // Display third blobs image
        BdvFunctions.show(blob, "Blobs Rot Z Y ", BdvOptions.options().sourceTransform(m).addTo(bdvh));
        */
        // Sets BigDataViewer view
        m.identity();
        m.scale(1);
        m.translate(150,0,0);

        bdvh.getViewerPanel().state().setViewerTransform(m);
        bdvh.getViewerPanel().requestRepaint();


        ImagePlus impRGB = IJ.openImage("src/test/resources/blobsrgb.tif");
        AbstractSpimData sdblob = (new SpimDataFromImagePlusGetter()).apply(impRGB);
        AffineTransform3D at3D = new AffineTransform3D();
        at3D.rotate(2,15);
        List<BdvStackSource<?>> bssL = BdvFunctions.show(sdblob, BdvOptions.options().addTo(bdvh));
        //SourceAndConverter rgbSac = bssL.get(0).getSources().get(0);
        //bssL.remove(0);





        return bdvh;
    }

    static void addEditorBehaviours(BdvHandle bdvh, SourceSelectorBehaviour ssb) {
        Behaviours editor = new Behaviours(new InputTriggerConfig());

        ClickBehaviour delete = (x, y) -> bdvh.getViewerPanel().state().removeSources(ssb.getSelectedSources());

        ClickBehaviour registerLocal = (x,y) -> {
            if ((movingSource==null)||(fixedSource==null)) {
                bdvh.getViewerPanel().showMessage("Please define a fixed and a moving source");
            } else {
                // Go for the registration - on a selected rectangle
                Future<CommandModule> task = ij.context()
                        .getService(CommandService.class)
                        .run(Elastix2DAffineRegisterCommand.class, true,
                            "sac_fixed", fixedSource,
                            "tpFixed", 0,
                            "levelFixedSource", 0,
                            "sac_moving", movingSource,
                            "tpMoving", 0,
                            "levelMovingSource", 0,
                            "pxSizeInCurrentUnit", 1,
                            "interpolate", false,
                            "showImagePlusRegistrationResult", true,
                            "px",-50,
                            "py",-10,
                            "pz",0,
                            "sx",250,
                            "sy",250
                        );

                Thread t = new Thread(() -> {
                    try {
                        AffineTransform3D at3d = (AffineTransform3D) task.get().getOutput("at3D");
                        SourceTransformHelper.mutate(at3d, new SourceAndConverterAndTimeRange(movingSource,0));
                        bdvh.getViewerPanel().requestRepaint();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
                t.start();
            }
        };

        ClickBehaviour registerRemote = (x,y) -> {
            if ((movingSource==null)||(fixedSource==null)) {
                bdvh.getViewerPanel().showMessage("Please define a fixed and a moving source");
            } else {
                // Go for the registration - on a selected rectangle
                Future<CommandModule> task = ij.context()
                        .getService(CommandService.class)
                        .run(Elastix2DAffineRegisterServerCommand.class, true,
                                "sac_fixed", fixedSource,
                                "tpFixed", 0,
                                "levelFixedSource", 0,
                                "sac_moving", movingSource,
                                "tpMoving", 0,
                                "levelMovingSource", 0,
                                "pxSizeInCurrentUnit", 1,
                                "interpolate", false,
                                "showImagePlusRegistrationResult", false,// true,
                                "px",-50,
                                "py",-10,
                                "pz",0,
                                "sx",250,
                                "sy",250,
                                //"serverURL","" // Local
                                "serverURL", "http://localhost:8090",//"http://15.188.34.238:8090", //http://localhost:8090"
                                "taskInfo", ""
                        );

                Thread t = new Thread(() -> {
                    try {
                        AffineTransform3D at3d = (AffineTransform3D) task.get().getOutput("at3D");
                        SourceTransformHelper.mutate(at3d, new SourceAndConverterAndTimeRange(movingSource,0));
                        bdvh.getViewerPanel().requestRepaint();
                    } catch (Exception e) {

                    }
                });
                t.start();
            }
        };

        editor.behaviour(delete, "remove-sources-from-bdv", new String[]{"DELETE"});
        editor.behaviour(registerLocal, "register-sources-local", new String[]{"R"});
        editor.behaviour(registerRemote, "register-sources-remote", new String[]{"S"});

        // One way to chain the behaviour : install and uninstall on source selector toggling:
        // The delete key will act only when the source selection mode is on
        ssb.addToggleListener(new ToggleListener() {
            @Override
            public void isEnabled() {
                bdvh.getViewerPanel().showMessage("Selection Mode Enable");
                //bdvh.getViewerPanel().showMessage(ssb.getSelectedSources().size()+" sources selected");
                // Enable the editor behaviours when the selector is enabled
                editor.install(bdvh.getTriggerbindings(), "sources-editor");
            }

            @Override
            public void isDisabled() {
                bdvh.getViewerPanel().showMessage("Selection Mode Disable");
                // Disable the editor behaviours the selector is disabled
                bdvh.getTriggerbindings().removeInputTriggerMap("sources-editor");
                bdvh.getTriggerbindings().removeBehaviourMap("sources-editor");
            }
        });
    }

}
