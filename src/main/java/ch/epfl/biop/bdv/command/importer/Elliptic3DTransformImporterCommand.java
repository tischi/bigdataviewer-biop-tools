package ch.epfl.biop.bdv.command.importer;

import bdv.util.Elliptical3DTransform;
import org.scijava.Context;
import org.scijava.ItemIO;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.scijava.command.BdvPlaygroundActionCommand;
import sc.fiji.persist.ScijavaGsonHelper;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;

@Plugin(type = BdvPlaygroundActionCommand.class, menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Transform>Import Elliptic 3D Transform")
public class Elliptic3DTransformImporterCommand implements BdvPlaygroundActionCommand {

    @Parameter
    Context context;

    @Parameter(label="Json file")
    File file;

    @Parameter(type = ItemIO.OUTPUT)
    private Elliptical3DTransform e3Dt;

    @Override
    public void run() {
        e3Dt = readTransformFromFile(file);
    }

    private Elliptical3DTransform readTransformFromFile(File file)
    {
        try
        {
           return ScijavaGsonHelper.getGson( context ).fromJson( new FileReader( file ), Elliptical3DTransform.class );
        } catch ( FileNotFoundException e )
        {
            e.printStackTrace();
        }
        return null;
    }
}
