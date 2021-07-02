import net.imagej.ImageJ;
import spimdata.imageplus.ImagePlusHelper;

public class TestEllipticHIVE {

    public static void main(String... args) {
        // Testing astonishing low data export
        final ImageJ ij = new ImageJ();
        ij.ui().showUI();
        String macro = "" +
                "run(\"Open XML BDV Datasets\", \"\");\n" +
                "run(\"New Elliptic 3D Transform\", \"r1=307.0 r2=866.0 r3=772.0 rx=1.0 ry=0.83 rz=1.77 tx=1475.0 ty=974.0 tz=434.0\");\n" +
                "run(\"Elliptic 3D Transform Sources\", \"\");\n" +
                "run(\"Export elliptic 3D transformed sources\", \"\");\n" +
                "//run(\"Brightness/Contrast...\");\n";

        ij.script().run("dummy.ijm", macro, true);

        //ImagePlusHelper.wrap()
    }
}
