package ch.epfl.biop.bdv.command.importer;

import ch.epfl.biop.bdv.bioformats.BioFormatsMetaDataHelper;
import ch.epfl.biop.bdv.bioformats.bioformatssource.BioFormatsBdvOpener;
import ch.epfl.biop.bdv.bioformats.command.BioformatsBigdataviewerBridgeDatasetCommand;
import ch.epfl.biop.bdv.bioformats.export.spimdata.BioFormatsConvertFilesToSpimData;
import ch.epfl.biop.bdv.bioformats.imageloader.FileIndex;
import ch.epfl.biop.bdv.bioformats.imageloader.SeriesNumber;
import ch.epfl.biop.bdv.command.exporter.BdvViewToImagePlusExportCommand;
import ch.epfl.biop.spimdata.qupath.MinimalQuPathProject;
import ch.epfl.biop.spimdata.qupath.ProjectIO;
import ch.epfl.biop.spimdata.qupath.QuPathEntryEntity;
import ch.epfl.biop.spimdata.qupath.QuPathToSpimData;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import net.imglib2.realtransform.AffineTransform3D;
import ome.units.quantity.Length;
import ome.units.unit.Unit;
import org.apache.commons.io.FilenameUtils;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Warning : a qupath project may have its source reordered and or removed :
 * - not all entries will be present in the qupath project
 * Limitations : only images
 */

@Plugin(type = Command.class,
        menuPath = ScijavaBdvDefaults.RootMenu+"BDVDataset>Open [QuPath Project]"
        )
public class QuPathProjectToBDVDatasetCommand extends BioformatsBigdataviewerBridgeDatasetCommand {

    private static Logger logger = LoggerFactory.getLogger(QuPathProjectToBDVDatasetCommand.class);

    @Parameter
    File quPathProject;

    @Parameter(type = ItemIO.OUTPUT)
    AbstractSpimData spimData;

    /*@Parameter(required = false, label="Physical units of the dataset", choices = {"MILLIMETER","MICROMETER","NANOMETER"})
    public String unit = "MILLIMETER";

    @Parameter(label = "Split RGB channels")
    boolean splitrgbchannels;*/

    @Override
    public void run() {

        //BioformatsBigdataviewerBridgeDatasetCommand settings = new BioformatsBigdataviewerBridgeDatasetCommand();
        //settings.splitrgbchannels = splitrgbchannels;
        //settings.unit = unit;

        try {
            spimData = (new QuPathToSpimData()).getSpimDataInstance(
                    //new URI(Paths.get(quPathProject.getAbsolutePath()).toString()),
                    quPathProject.toURI(),
                    getOpener("")
                    );
        } catch (Exception e) {
            e.printStackTrace();
        }

        /*try {

            JsonObject projectJson = ProjectIO.loadRawProject(quPathProject);
            Gson gson = new Gson();

            MinimalQuPathProject project = gson.fromJson(projectJson, MinimalQuPathProject.class);

            logger.debug("Opening QuPath project "+project.uri);

            Set<QuPathBioFormatsSourceIdentifier> quPathSourceIdentifiers = new HashSet<>();

            List<URI> allURIs = new ArrayList<>();
            project.images.forEach(image -> {
                logger.debug("Opening qupath image "+image);
                QuPathBioFormatsSourceIdentifier identifier = new QuPathBioFormatsSourceIdentifier();
                if (image.serverBuilder.builderType.equals("rotated")) {
                    String angleDegreesStr = image.serverBuilder.rotation.substring(7);//"ROTATE_ANGLE" for instance "ROTATE_0", "ROTATE_270", etc
                    logger.debug("Rotated image server ("+angleDegreesStr+")");
                    if (angleDegreesStr.equals("NONE")) {
                        identifier.angleRotationZAxis = 0;
                    } else {
                        identifier.angleRotationZAxis = (Double.valueOf(angleDegreesStr) / 180.0) * Math.PI;
                    }
                    image.serverBuilder = image.serverBuilder.builder;
                }

                if (image.serverBuilder.builderType.equals("uri")) {
                    logger.debug("URI image server");
                    if (image.serverBuilder.providerClassName.equals("qupath.lib.images.servers.bioformats.BioFormatsServerBuilder")) {
                        if (!allURIs.contains(image.serverBuilder.uri)) {
                            allURIs.add(image.serverBuilder.uri);
                        }
                        try {

                            URI uri = new URI(image.serverBuilder.uri.getScheme(), image.serverBuilder.uri.getHost(), image.serverBuilder.uri.getPath(), null);
                            // This appears to work more reliably than converting to a File
                            String filePath = Paths.get(uri).toString();

                            identifier.uri = image.serverBuilder.uri;
                            identifier.sourceFile = filePath;
                            identifier.indexInQuPathProject = project.images.indexOf(image);
                            identifier.entryID = project.images.get(identifier.indexInQuPathProject).entryID;

                            int seriesArgIndex =  image.serverBuilder.args.indexOf("--series");

                            if (seriesArgIndex==-1) {
                                logger.error("Series not found in qupath project server builder!");
                                identifier.bioformatsIndex = -1;
                            } else {
                                identifier.bioformatsIndex = Integer.valueOf(image.serverBuilder.args.get(seriesArgIndex + 1));
                            }

                            logger.debug(identifier.toString());
                            quPathSourceIdentifiers.add(identifier);

                        } catch (URISyntaxException e) {
                            logger.error("URI Syntax error "+e.getMessage());
                            e.printStackTrace();
                        }

                    } else {
                        logger.error("Unsupported "+image.serverBuilder.providerClassName+" class name provider");
                    }
                } else {
                    logger.error("Unsupported "+image.serverBuilder.builderType+" server builder");
                }
            });

            List<BioFormatsBdvOpener> openers = new ArrayList<>();
            for (URI uri : allURIs) {
                URI uri2 = new URI(uri.getScheme(), uri.getHost(), uri.getPath(), null);
                // This appears to work more reliably than converting to a File
                String filePath = Paths.get(uri2).toString();
                BioFormatsBdvOpener opener = getOpener(filePath);

                // For rotated image server
                //if (!ignoreRotations) {
                    AffineTransform3D at3D = new AffineTransform3D();

                    double zAxisRotation =
                            quPathSourceIdentifiers.stream()
                                    .filter(identifier -> identifier.uri == uri)
                                    .findFirst()
                                    .get()
                                    .angleRotationZAxis;

                    at3D.rotate(2, zAxisRotation);
                    opener.setPositionPostTransform(at3D); //.flipPositionX();
                //}

                openers.add(opener);
            }

            spimData = BioFormatsConvertFilesToSpimData.getSpimData(openers);

            // Removing sources not present in QuPath
            spimData.getSequenceDescription().getViewSetups().keySet().forEach(key -> {
                BasicViewSetup bvs = (BasicViewSetup) spimData.getSequenceDescription().getViewSetups().get(key);
                FileIndex fi = bvs.getAttribute(FileIndex.class);
                SeriesNumber sn = bvs.getAttribute(SeriesNumber.class);

                bvs.setAttribute(new QuPathEntryEntity(-1, FilenameUtils.getBaseName(quPathProject.toString()) +"_[entry:unidentified]"));

                for (QuPathBioFormatsSourceIdentifier identifier : quPathSourceIdentifiers) {
                    if (fi.getName().equals(identifier.sourceFile)) {
                        if (sn.getId() == identifier.bioformatsIndex) {
                            logger.debug("identifier.entryID = "+identifier.entryID);
                            QuPathEntryEntity qpent = new QuPathEntryEntity(identifier.entryID, FilenameUtils.getBaseName(quPathProject.toString()) +"_[entry:"+identifier.entryID+"]");
                            qpent.setName(FilenameUtils.getBaseName(quPathProject.toString()) +"_[entry:"+identifier.entryID+"]");
                            qpent.setQuPathProjectionLocation(quPathProject.getAbsolutePath());
                            bvs.setAttribute(qpent);
                        }
                    }
                }
            });

        } catch (IOException | URISyntaxException e) {
            logger.error("Exception thrown: "+e.getMessage());
            e.printStackTrace();
        }*/
    }

    public BioFormatsBdvOpener getOpener(String datalocation) {
        Unit bfUnit = BioFormatsMetaDataHelper.getUnitFromString(this.unit);
        Length positionReferenceFrameLength = new Length(this.refframesizeinunitlocation, bfUnit);
        Length voxSizeReferenceFrameLength = new Length(this.refframesizeinunitvoxsize, bfUnit);
        BioFormatsBdvOpener opener = BioFormatsBdvOpener.getOpener()
                .location(datalocation).unit(this.unit)
                //.auto()
                .ignoreMetadata();
        if (!this.switchzandc.equals("AUTO")) {
            opener = opener.switchZandC(this.switchzandc.equals("TRUE"));
        }

        if (!this.usebioformatscacheblocksize) {
            opener = opener.cacheBlockSize(this.cachesizex, this.cachesizey, this.cachesizez);
        }

        if (!this.positioniscenter.equals("AUTO")) {
            if (this.positioniscenter.equals("TRUE")) {
                opener = opener.centerPositionConvention();
            } else {
                opener = opener.cornerPositionConvention();
            }
        }

        if (!this.flippositionx.equals("AUTO") && this.flippositionx.equals("TRUE")) {
            opener = opener.flipPositionX();
        }

        if (!this.flippositiony.equals("AUTO") && this.flippositiony.equals("TRUE")) {
            opener = opener.flipPositionY();
        }

        if (!this.flippositionz.equals("AUTO") && this.flippositionz.equals("TRUE")) {
            opener = opener.flipPositionZ();
        }

        opener = opener.unit(this.unit);
        opener = opener.positionReferenceFrameLength(positionReferenceFrameLength);
        opener = opener.voxSizeReferenceFrameLength(voxSizeReferenceFrameLength);
        if (this.splitrgbchannels) {
            opener = opener.splitRGBChannels();
        }

        return opener;
    }


}
