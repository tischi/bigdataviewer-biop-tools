package ch.epfl.biop.bdv.command;

import bdv.tools.brightness.ConverterSetup;
import bdv.util.BdvHandle;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.bdv.command.exporter.ExportToMultipleImagePlusCommand;
import ch.epfl.biop.bdv.select.SourceSelectorBehaviour;
import ch.epfl.biop.bdv.select.ToggleListener;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.ARGBType;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.behaviour.ClickBehaviour;
import org.scijava.ui.behaviour.io.InputTriggerConfig;
import org.scijava.ui.behaviour.util.Behaviours;
import sc.fiji.bdvpg.behaviour.EditorBehaviourUnInstaller;
import sc.fiji.bdvpg.behaviour.SourceAndConverterContextMenuClickBehaviour;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.scijava.command.BdvPlaygroundActionCommand;
import sc.fiji.bdvpg.scijava.command.source.BasicTransformerCommand;
import sc.fiji.bdvpg.scijava.command.source.BrightnessAdjusterCommand;
import sc.fiji.bdvpg.scijava.command.source.SourceColorChangerCommand;
import sc.fiji.bdvpg.services.SourceAndConverterServices;
import sc.fiji.bdvpg.sourceandconverter.SourceAndConverterHelper;
import sc.fiji.bdvpg.sourceandconverter.transform.SourceAffineTransformer;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static sc.fiji.bdvpg.bdv.navigate.ViewerTransformSyncStopper.MatrixApproxEquals;
import static sc.fiji.bdvpg.scijava.services.SourceAndConverterService.getCommandName;

@Plugin(type = BdvPlaygroundActionCommand.class,
        menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Display Sources On Grid")
public class OverviewerCommand implements BdvPlaygroundActionCommand {

    @Parameter
    public SourceAndConverter[] sacs;

    @Parameter
    int timepointBegin;

    @Parameter
    int nColumns;

    // sourceList;

    int currentIndex = 0;
    AffineTransform3D currentAffineTransform = new AffineTransform3D();

    final Map<SourceAndConverter<?>, SourceAndConverter<?>> transformedToOriginal = new HashMap<>();

    @Override
    public void run() {

        // Sort according to location = affine transform 3d of sources

        List<SourceAndConverter<?>> sourceList = sorter.apply(Arrays.asList(sacs));

        Map<SacProperties, List<SourceAndConverter>> sacClasses = sourceList
                .stream()
                .collect(Collectors.groupingBy(sac -> new SacProperties(sac)));

        Map<SourceAndConverter<?>, List<SacProperties>> keySetSac = sacClasses.keySet().stream().collect(Collectors.groupingBy(p -> p.sac));

        List<SourceAndConverter<?>> sortedSacs = sorter.apply(keySetSac.keySet());

        List<SourceAndConverter<?>> sacsToDisplay = new ArrayList<>();

        sortedSacs.forEach(sacKey -> {
            SacProperties sacPropsKey = keySetSac.get(sacKey).get(0);
            AffineTransform3D location = sacPropsKey.location;

            int xPos = currentIndex % nColumns;
            int yPos = currentIndex / nColumns;

            currentAffineTransform.identity();
            currentAffineTransform.preConcatenate(location.inverse());
            AffineTransform3D translator = new AffineTransform3D();
            translator.translate(xPos, yPos,0);

            currentIndex++;

            List<SourceAndConverter> sacs = sacClasses.get(sacPropsKey);// sacSortedPerLocation.get(location);

            long nPixX = sacs.get(0).getSpimSource().getSource(timepointBegin, 0).dimension(0);

            long nPixY = sacs.get(0).getSpimSource().getSource(timepointBegin, 0).dimension(1);

            long nPixZ = sacs.get(0).getSpimSource().getSource(timepointBegin, 0).dimension(2);

            long sizeMax = Math.max(nPixX, nPixY);

            sizeMax = Math.max(sizeMax, nPixZ);

            currentAffineTransform.scale(1/(double)sizeMax, 1/(double) sizeMax, 1/(double)sizeMax);

            currentAffineTransform.translate(xPos,yPos,0);

            SourceAffineTransformer sat = new SourceAffineTransformer(null, currentAffineTransform);

            List<SourceAndConverter<?>> transformedSacs =
                    sacs.stream().map(sac -> {
                        SourceAndConverter<?> trSac = sat.apply(sac);
                        transformedToOriginal.put(trSac, sac);
                        SourceAndConverterServices
                                .getSourceAndConverterService()
                                .register(trSac);

                        ConverterSetup csOrigin = SourceAndConverterServices
                                .getBdvDisplayService()
                                .getConverterSetup(sac);

                        ConverterSetup csDestination = SourceAndConverterServices
                                .getBdvDisplayService()
                                .getConverterSetup(trSac);

                        // TODO : fix potential mem leak with listeners
                        csOrigin.setupChangeListeners().add(setup -> {
                                if ((csDestination.getDisplayRangeMin() != setup.getDisplayRangeMin()) ||
                                        (csDestination.getDisplayRangeMax() != setup.getDisplayRangeMax()))
                                    csDestination.setDisplayRange(setup.getDisplayRangeMin(), setup.getDisplayRangeMax());
                                if (csDestination.supportsColor()) {
                                    if (csDestination.getColor().get() != setup.getColor().get())
                                        csDestination.setColor(new ARGBType(setup.getColor().get()));

                                }
                            }
                        );

                        csDestination.setupChangeListeners().add(setup -> {
                                    if ((csOrigin.getDisplayRangeMin() != setup.getDisplayRangeMin()) ||
                                            (csOrigin.getDisplayRangeMax() != setup.getDisplayRangeMax()))
                                        csOrigin.setDisplayRange(csOrigin.getDisplayRangeMin(), csOrigin.getDisplayRangeMax());
                                    if (csOrigin.supportsColor()) {
                                        if (csOrigin.getColor().get() != setup.getColor().get())
                                            csOrigin.setColor(new ARGBType(setup.getColor().get()));
                                    }
                                }
                        );

                        return trSac;
                    }).collect(Collectors.toList());

            sacsToDisplay.addAll(transformedSacs);
        });

        //BdvHandle bdvh =

        BdvHandle bdvh = SourceAndConverterServices.getBdvDisplayService().getNewBdv();
        SourceAndConverterServices.getBdvDisplayService().show(bdvh, sacsToDisplay.toArray(new SourceAndConverter[0]));

        AffineTransform3D currentViewLocation = new AffineTransform3D();

        bdvh.getViewerPanel().state().getViewerTransform(currentViewLocation);
        currentViewLocation.set(0,2,3);
        bdvh.getViewerPanel().state().setViewerTransform(currentViewLocation);

        SourceSelectorBehaviour ssb = (SourceSelectorBehaviour) SourceAndConverterServices.getBdvDisplayService().getDisplayMetadata(
                bdvh, SourceSelectorBehaviour.class.getSimpleName());

        new EditorBehaviourUnInstaller(bdvh).run();

        addEditorBehaviours(bdvh, ssb);

        bdvh.getViewerPanel().setNumTimepoints(getNumberOfTimepoints(sacs[0]));

    }


    void addEditorBehaviours(BdvHandle bdvh, SourceSelectorBehaviour ssb) {
        Behaviours editor = new Behaviours(new InputTriggerConfig());

        // Act on the original sources
        editor.behaviour(new SourceAndConverterContextMenuClickBehaviour( bdvh,
                () -> ssb.getSelectedSources()
                            .stream()
                            .map((sac) -> transformedToOriginal.get(sac))
                            .collect(Collectors.toSet()),
                getPopupActionsOnWrappedSource() ), "Sources Context Menu", "button3");

        // One way to chain the behaviour : install and uninstall on source selector toggling:
        // The delete key will act only when the source selection mode is on
        ssb.addToggleListener(new ToggleListener() {
            @Override
            public void isEnabled() {
                bdvh.getViewerPanel().showMessage("Selection Mode Enable");
                bdvh.getViewerPanel().showMessage(ssb.getSelectedSources().size()+" sources selected");
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

    public static String[] getPopupActionsOnWrappedSource() {
        String[] editorPopupActions = {
                "Inspect Sources",
                getCommandName(BrightnessAdjusterCommand.class),
                getCommandName(ExportToMultipleImagePlusCommand.class)};
        return editorPopupActions;
    }

    public static int getNumberOfTimepoints(SourceAndConverter<?> source) {
        int nFrames = 1;
        int iFrame = 1;
        int previous = iFrame;
        while (source.getSpimSource().isPresent(iFrame)) {
            previous = iFrame;
            iFrame *= 2;
        }
        if (iFrame>1) {
            for (int tp = previous;tp<iFrame;tp++) {
                if (!source.getSpimSource().isPresent(tp)) {
                    nFrames = tp;
                    break;
                }
            }
        }
        return nFrames;
    }

    public Function<Collection<SourceAndConverter<?>>,List<SourceAndConverter<?>>> sorter = sacs1ist -> SourceAndConverterHelper.sortDefaultGeneric(sacs1ist);

    class SacProperties {

        final AffineTransform3D location;
        long[] dims = new long[3];
        SourceAndConverter sac;

        public SacProperties(SourceAndConverter sac) {
            location = new AffineTransform3D();
            sac.getSpimSource().getSourceTransform(timepointBegin, 0, location);
            sac.getSpimSource().getSource(timepointBegin,0).dimensions(dims);
            this.sac = sac;
        }

        @Override
        public int hashCode() {
            int hash = 5;
            hash = 89  * hash + (int) dims[0] + 17 * (int) dims[1] + 57 * (int) dims[2];
            hash = hash + (int) (10 * location.get(0,0));
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof SacProperties) {
                SacProperties other = (SacProperties) obj;
                if  (
                      (MatrixApproxEquals(location.getRowPackedCopy(), other.location.getRowPackedCopy()))
                    &&(dims[0]==other.dims[0])&&(dims[1]==other.dims[1])&&(dims[2]==other.dims[2])) {
                    return true;
                } else {
                    return false;
                }

            } else {
                return false;
            }
        }

    }
}
