package ch.epfl.biop.bdv.command.userdefinedregion;

import bdv.util.BdvHandle;
import bdv.util.BdvOptions;
import bdv.util.BdvOverlaySource;
import bdv.viewer.ViewerPanel;
import bdv.viewer.ViewerState;
import ch.epfl.biop.bdv.gui.card.CardHelper;
import ch.epfl.biop.bdv.gui.graphicalhandle.CircleGraphicalHandle;
import ch.epfl.biop.bdv.gui.graphicalhandle.GraphicalHandle;
import net.imglib2.RealPoint;
import net.imglib2.realtransform.AffineTransform3D;
import org.scijava.ui.behaviour.Behaviour;
import org.scijava.ui.behaviour.ClickBehaviour;
import org.scijava.ui.behaviour.io.InputTriggerConfig;
import org.scijava.ui.behaviour.util.Behaviours;
import org.scijava.ui.behaviour.util.TriggerBehaviourBindings;

import javax.swing.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

import static bdv.ui.BdvDefaultCards.*;
import static bdv.util.BdvFunctions.showOverlay;
import static ch.epfl.biop.bdv.command.userdefinedregion.RectangleSelectorBehaviour.box;

/**
 * Appends and controls a {@link PointsSelectorBehaviour} in a {@link BdvHandle}
 *
 * It is used in conjuncion with a BdvOverlay layer {@link PointsSelectorOverlay} which can be retrieved with
 * {@link PointsSelectorBehaviour#getPointsSelectorOverlay()}
 *
 * The selections can be triggered by GUI actions in the linked {@link PointsSelectorOverlay} or
 * directly programmatically
 *
 * @author Nicolas Chiaruttini, BIOP, EPFL, 2020
 */

public class PointsSelectorBehaviour {

    final public static String POINTS_SELECTOR_MAP = "points-selector";

    final PointsSelectorOverlay pointsOverlay;

    BdvOverlaySource bos;

    final BdvHandle bdvh;

    final TriggerBehaviourBindings triggerbindings;

    final ViewerPanel viewer;

    final Behaviours behaviours;

    boolean isInstalled; // flag for the toggle action

    JPanel pane;

    private boolean navigationEnabled = false;

    AffineTransform3D initialView;

    final String userCardKey = "Points Selection";

    final Function<RealPoint, GraphicalHandle> graphicalHandleSupplier;

    Map<RealPoint, GraphicalHandle> ptToGraphicalHandle = new ConcurrentHashMap<>();

    /**
     * Construct a SourceSelectorBehaviour
     * @param bdvh BdvHandle associated to this behaviour
     * @param message to display to the user as overlay on bdv
     */
    public PointsSelectorBehaviour(BdvHandle bdvh, String message,
                                   Function<RealPoint, GraphicalHandle> graphicalHandleSupplier) {
        this.bdvh = bdvh;
        this.triggerbindings = bdvh.getTriggerbindings();
        this.viewer = bdvh.getViewerPanel();
        if (graphicalHandleSupplier == null) {
            this.graphicalHandleSupplier = (coords) -> new DefaultCircularHandle( () -> coords, bdvh.getViewerPanel().state(), 20);
        } else {
            this.graphicalHandleSupplier = graphicalHandleSupplier;
        }

        pointsOverlay = new PointsSelectorOverlay(viewer, this);

        behaviours = new Behaviours( new InputTriggerConfig(), "bdv" );

        initialView = bdvh.getViewerPanel().state().getViewerTransform();

        JButton restoreView = new JButton("Restore initial view");

        restoreView.addActionListener((e)-> {
            bdvh.getViewerPanel().state().setViewerTransform(initialView);
        });

        JButton navigationButton = new JButton("Enable navigation");
        navigationButton.addActionListener((e) -> {
            if (navigationEnabled) {
                triggerbindings.addBehaviourMap(POINTS_SELECTOR_MAP, behaviours.getBehaviourMap());
                triggerbindings.addInputTriggerMap(POINTS_SELECTOR_MAP, behaviours.getInputTriggerMap(), "transform", "bdv");
                bdvh.getKeybindings().addInputMap("blocking-source-selector_rectangle", new InputMap(), "bdv", "navigation");
                navigationEnabled = false;
                navigationButton.setText("Re-enable navigation");
            } else {
                triggerbindings.removeBehaviourMap( POINTS_SELECTOR_MAP );
                triggerbindings.removeInputTriggerMap( POINTS_SELECTOR_MAP );
                bdvh.getKeybindings().removeInputMap("blocking-source-selector");
                navigationEnabled = true;
                navigationButton.setText("Enable point selection");
            }
        });

        JButton confirmationButton = new JButton("Confirm points");
        confirmationButton.addActionListener((e) -> {
            userDone = true;
        });

        JButton clearAllPointsButton = new JButton("Clear points");
        clearAllPointsButton.addActionListener((e) -> {
            clearPoints();
        });

        pane = box(false,new JLabel(message), box(false,navigationButton, restoreView), clearAllPointsButton, confirmationButton);
    }

    /**
     * @return the overlay layer associated with the source selector
     */
    public PointsSelectorOverlay getPointsSelectorOverlay() {
        return pointsOverlay;
    }

    /**
     *
     * @return the BdhHandle associated to this Selector
     */
    public BdvHandle getBdvHandle() {
        return bdvh;
    }

    /**
     * Activate the selection mode
     */
    public synchronized void enable() {
        if (!isInstalled) {
            install();
        }
    }

    /**
     * Deactivate the selection mode
     */
    public synchronized void disable() {
        if (isInstalled) {
            uninstall();
        }
    }

    public synchronized boolean isEnabled() {
        return isInstalled;
    }

    /**
     * Completely disassociate the selector with this BdvHandle
     * TODO safe in terms of freeing memory ?
     */
    public void remove() {
        disable();
    }

    CardHelper.CardState iniCardState;

    /**
     * Private : call enable instead
     */
    synchronized void install() {
        isInstalled = true;
        pointsOverlay.addSelectionBehaviours(behaviours);
        behaviours.behaviour((ClickBehaviour) (x, y) -> {
            bos.removeFromBdv();
            uninstall(); userDone = true;
        }, "cancel-set-points", new String[]{"ESCAPE"});

        triggerbindings.addBehaviourMap(POINTS_SELECTOR_MAP, behaviours.getBehaviourMap());
        triggerbindings.addInputTriggerMap(POINTS_SELECTOR_MAP, behaviours.getInputTriggerMap(), "transform", "bdv");
        bos = showOverlay(pointsOverlay, "Point_Selector_Overlay", BdvOptions.options().addTo(bdvh));
        bdvh.getKeybindings().addInputMap("blocking-source-selector_points", new InputMap(), "bdv", "navigation");

        iniCardState = CardHelper.getCardState(bdvh);

        bdvh.getSplitPanel().setCollapsed(false);
        bdvh.getCardPanel().setCardExpanded(DEFAULT_SOURCEGROUPS_CARD, false);
        bdvh.getCardPanel().setCardExpanded(DEFAULT_VIEWERMODES_CARD, false);
        bdvh.getCardPanel().setCardExpanded(DEFAULT_SOURCES_CARD, false);
        bdvh.getCardPanel().addCard(userCardKey, pane, true);
    }

    public void addBehaviour(Behaviour behaviour, String behaviourName, String[] triggers) {
        behaviours.behaviour(behaviour, behaviourName, triggers);
    }

    /**
     * Private : call disable instead
     */
    synchronized void uninstall() {
        isInstalled = false;
        bos.removeFromBdv(); // NPE ??
        triggerbindings.removeBehaviourMap( POINTS_SELECTOR_MAP );
        triggerbindings.removeInputTriggerMap( POINTS_SELECTOR_MAP );
        bdvh.getKeybindings().removeInputMap("blocking-source-selector");

        bdvh.getCardPanel().removeCard(userCardKey);
        CardHelper.restoreCardState(bdvh, iniCardState);
    }

    private volatile List<RealPoint> points = new ArrayList<>();

    List<RealPoint> getPoints() {
        return new ArrayList<>(points);
    }

    void addPoint(RealPoint newPt) {
        ptToGraphicalHandle.put(newPt, graphicalHandleSupplier.apply(newPt));
        points.add(newPt);
    }

    public Collection<GraphicalHandle> getGraphicalHandles() {
        return ptToGraphicalHandle.values();
    }

    void clearPoints() {
        ptToGraphicalHandle.clear();
        points.clear();
        bdvh.getBdvHandle().getViewerPanel().requestRepaint();
    }

    public List<RealPoint> waitForSelection() {
        return waitForSelection(-1);
    }

    public List<RealPoint> waitForSelection(int timeOutInMs) {
        int totalTime = 0;
        if (timeOutInMs>0) {
            while (!(isUserDone())&&(totalTime<timeOutInMs)) {
                try {
                    Thread.sleep(33);
                    totalTime+=33;
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        } else {
            while (!(isUserDone())) {
                try {
                    Thread.sleep(33);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        userDone = false;
        return points;
    }

    volatile boolean userDone = false;

    private boolean isUserDone() {
        return userDone;
    }

    public static Integer[] defaultLandmarkColor = new Integer[]{200, 240, 24, 128};

    public class DefaultCircularHandle extends CircleGraphicalHandle {

        public DefaultCircularHandle(Supplier<RealPoint> globalCoord,
                                     final ViewerState vState,
                                     int radius) {
            super(null, null, null, null,
                    () -> {
                        RealPoint pt = new RealPoint(3);
                        vState.getViewerTransform().apply(globalCoord.get(), pt);
                        return new Integer[]{(int) pt.getDoublePosition(0), (int) pt.getDoublePosition(1), 0};
                    },
                    () -> {
                        RealPoint pt = new RealPoint(3);
                        vState.getViewerTransform().apply(globalCoord.get(), pt);
                        double distZ = pt.getDoublePosition(2);
                        if (Math.abs(distZ)<radius) {
                            return (int) (4 + Math.sqrt((radius) * (radius) - distZ * distZ));
                        } else {
                            return 4;
                        }
                    },
                    () -> defaultLandmarkColor);
        }

    }
}
