package ch.epfl.biop.sourceandconverter.exporter;

import bdv.viewer.SourceAndConverter;
import ij.ImagePlus;
import net.imglib2.realtransform.AffineTransform3D;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sc.fiji.bdvpg.services.SourceAndConverterServices;
import sc.fiji.bdvpg.sourceandconverter.SourceAndConverterHelper;
import sc.fiji.bdvpg.sourceandconverter.transform.SourceResampler;
import spimdata.imageplus.ImagePlusHelper;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ImagePlusSampler {

    private static Logger logger = LoggerFactory.getLogger(ImagePlusSampler.class);

    final Builder builder;

    public static Builder Builder() {
        return new Builder();
    }

    public ImagePlusSampler(Builder builder) {
        this.builder = builder;
    }

    public ImagePlus get() throws UnsupportedOperationException {

        if (builder.sacs.length == 0) {
            throw  new UnsupportedOperationException("No input sources");
        }

        List<SourceAndConverter<?>> sourceList = builder.sorter.apply(Arrays.asList(builder.sacs));

        if (sourceList.stream().map(sac -> sac.getSpimSource().getType().getClass()).distinct().count()>1) {
           throw new UnsupportedOperationException("Cannot make composite because all sources are not of the same type");
        }

        // The core of it : resampling each source with the model
        List<SourceAndConverter> resampledSourceList = sourceList
                .stream()
                .map(sac -> new SourceResampler(sac,builder.model,true, builder.cache, builder.interpolate).get())
                .collect(Collectors.toList());

        SourceAndConverterServices.getSourceAndConverterService().register(builder.model);

        Map<SourceAndConverter, Integer> mapMipmap = new HashMap<>();
        sourceList.forEach(src -> {
            int mipmapLevel = SourceAndConverterHelper.bestLevel(src, builder.t0, builder.sizePixelY);
            logger.debug("Mipmap level chosen for source ["+src.getSpimSource().getName()+"] : "+mipmapLevel);
            mapMipmap.put(resampledSourceList.get(sourceList.indexOf(src)), mipmapLevel);
        });

        ImagePlus compositeImage = ImagePlusHelper.wrap(
                resampledSourceList,
                mapMipmap,
                builder.t0,
                builder.nTimepoints,
                builder.timeStep);

        compositeImage.setTitle(builder.imageName);
        AffineTransform3D at3D = new AffineTransform3D();
        builder.model.getSpimSource().getSourceTransform(builder.t0, 0, at3D);
        ImagePlusHelper.storeExtendedCalibrationToImagePlus(compositeImage, at3D, builder.unit, builder.t0);
        compositeImage.show();

        return compositeImage;
    }

    public static class Builder {

        String imageName = "Image";
        SourceAndConverter[] sacs = new SourceAndConverter[0];
        SourceAndConverter<?> model;
        int t0 = 0;
        int nTimepoints = 1;
        boolean interpolate;
        double sizePixelX = 1;
        double sizePixelY = 1;
        double sizePixelZ = 1;
        int timeStep = 1;
        boolean cache = true;
        String unit;
        Function<Collection<SourceAndConverter<?>>,List<SourceAndConverter<?>>> sorter = sacs1ist -> SourceAndConverterHelper.sortDefaultGeneric(sacs1ist);

        public Builder sources(SourceAndConverter[] sacs) {
            this.sacs = sacs;
            return this;
        }

        public Builder sort(Function<Collection<SourceAndConverter<?>>,List<SourceAndConverter<?>>> sorter) {
            this.sorter = sorter;
            return this;
        }

        public Builder setModel(SourceAndConverter<?> model) {
            this.model = model;
            return this;
        }

        public Builder timeRange(
                int t0,
                int nTimepoints) {
            this.t0 =t0;
            this.nTimepoints = nTimepoints;
            return this;
        }

        public Builder interpolate(boolean flag) {
            this.interpolate = flag;
            return this;
        }

        public Builder spaceSampling(double sizePixelX,
                                double sizePixelY,
                                double sizePixelZ) {
            this.sizePixelX = sizePixelX;
            this.sizePixelY = sizePixelY;
            this.sizePixelZ = sizePixelZ;
            return this;
        }

        public Builder timeSampling(int timeStep) {
            this.timeStep = timeStep;
            return this;
        }

        public Builder cache(boolean flag) {
            this.cache = flag;
            return this;
        }

        public Builder unit(String unit) {
            this.unit = unit;
            return this;
        }

        public Builder title(String title) {
            this.imageName = title;
            return this;
        }

        public ImagePlusSampler build() {
            return new ImagePlusSampler(this);
        }
    }
}
